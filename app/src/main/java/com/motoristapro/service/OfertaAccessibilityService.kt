package com.motoristapro.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.entity.OfertaRecebida
import com.motoristapro.data.repository.nomePlataforma
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Núcleo do app: lê a tela da Uber/99, extrai a oferta e mostra R$/km e R$/h por cima.
 *
 * Fluxo:
 *   evento de acessibilidade (só dos PACOTES_ALVO)
 *     -> debounce de 250 ms (o app dispara dezenas de eventos por segundo)
 *     -> varre os nós de texto de todas as janelas da Uber/99
 *     -> OfertaParser.extrair()
 *     -> LimitesOferta.classificar()   (verde / amarelo / vermelho)
 *     -> OverlayOferta.mostrar()
 *
 * Declarado no AndroidManifest com BIND_ACCESSIBILITY_SERVICE e configurado por
 * res/xml/accessibility_service_config.xml.
 */
class OfertaAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var overlay: OverlayOferta? = null
    private var limites: LimitesOferta? = null
    private var bolha: BolhaFlutuante? = null

    /** Id no histórico da oferta exibida (para marcar como registrada). */
    private var ultimaOfertaId: Long? = null

    /** Ofertas já salvas no histórico nos últimos minutos (assinatura -> quando), evita duplicar. */
    private val salvasRecentes = LinkedHashMap<String, Long>()

    /** Pacote (Uber/99) de onde veio a última oferta lida — define a plataforma ao registrar. */
    private var ultimoPacote: String? = null

    // Valores da Configuração (Room), mantidos em memória para não consultar o banco a cada oferta.
    @Volatile private var custoKmCentavos: Long = 0
    @Volatile private var tarifaMinimaCentavos: Long = 0

    /** Última oferta exibida (evita redesenhar a mesma oferta a cada evento). */
    private var ultimaAssinatura: String? = null

    /** Oferta que o motorista fechou com um toque: não reabre enquanto ela continuar na tela. */
    private var assinaturaDispensada: String? = null

    /** Leituras seguidas sem oferta; ao chegar em 2, consideramos que o card sumiu. */
    private var varredurasVazias = 0

    private val varrerRunnable = Runnable { varrerTelaComSeguranca() }

    /** Lista de endereços de risco do motorista. */
    private var risco: EnderecosDeRisco? = null

    /** Aviso falado da oferta (desligado por padrão). */
    private var voz: AvisoVoz? = null

    /** Leitura por imagem (criada só quando algum app não expõe texto). */
    private var leitorOcr: LeitorOcr? = null
    private var ultimoOcrEm = 0L

    /** Apps em que a oferta só foi reconhecida pela imagem (ex.: 99). */
    private val pacotesQuePrecisamOcr = mutableSetOf<String>()

    // ================================================================== ciclo de vida

    override fun onServiceConnected() {
        super.onServiceConnected()
        instancia = this
        _conectado.value = true

        limites = LimitesOferta(this)
        risco = EnderecosDeRisco(this)
        voz = AvisoVoz(this).apply { preparar() }
        val app = application as MotoristaApp
        val repo = app.repository

        overlay = OverlayOferta(this).apply {
            aoDispensar = { assinaturaDispensada = ultimaAssinatura }
            aoRegistrar = { oferta -> registrarOferta(oferta) }
            aoVerLocal = { endereco ->
                escopo.launch { AcoesOferta.abrirLocal(this@OfertaAccessibilityService, endereco) }
            }
        }
        bolha = BolhaFlutuante(this)
        aplicarPreferencias()

        // Bolha: lucro de hoje + início do turno, reativos ao banco.
        escopo.launch {
            try {
                combine(repo.resumoHoje(), repo.jornadaAtiva()) { r, j -> r.lucroLiquidoCentavos to j?.inicioEm }
                    .collect { (lucro, inicio) -> bolha?.atualizar(lucro, inicio) }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao observar dados da bolha", e)
            }
        }

        // Custo/km e tarifa mínima sempre atualizados a partir do banco.
        escopo.launch {
            try {
                repo.configuracao().collect { cfg ->
                    custoKmCentavos = cfg.custoKmCentavos
                    tarifaMinimaCentavos = cfg.tarifaMinimaCentavos
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao observar configuração", e)
            }
        }
        Log.i(TAG, "Serviço conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pacote = event?.packageName?.toString() ?: return
        if (pacote !in PACOTES_ALVO) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                handler.removeCallbacks(varrerRunnable)
                handler.postDelayed(varrerRunnable, DEBOUNCE_MS)
            }
        }
    }

    override fun onInterrupt() {
        overlay?.esconder()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.liberar()
        overlay = null
        bolha?.liberar()
        bolha = null
        leitorOcr?.fechar()
        leitorOcr = null
        voz?.liberar()
        voz = null
        risco = null
        escopo.cancel()
        instancia = null
        _conectado.value = false
        super.onDestroy()
    }

    // ================================================================== leitura da tela

    /** Nenhuma exceção de leitura/parsing pode derrubar o serviço (o sistema o desativaria). */
    private fun varrerTelaComSeguranca() {
        try {
            varrerTela()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao varrer a tela", e)
        }
    }

    private fun varrerTela() {
        if (overlay == null || limites == null) return

        // 1ª tentativa: só textos visíveis. 2ª: inclui descrições de acessibilidade de todos os nós
        // (alguns apps colocam o texto do card só na descrição do container).
        var leituras = coletarLeituras(incluirDescricoes = false)
        var resultado = interpretar(leituras)
        if (resultado == null) {
            val comDescricoes = coletarLeituras(incluirDescricoes = true)
            resultado = interpretar(comDescricoes)
            if (resultado != null || leituras.all { it.textos.isEmpty() }) leituras = comDescricoes
        }
        if (leituras.isEmpty()) return   // nenhuma janela de app-alvo na tela

        // 3ª tentativa: o app não expõe o valor para a acessibilidade (ex.: 99) -> lê a IMAGEM da tela.
        // Só usa OCR quando o app praticamente não expõe textos, ou quando já sabemos que ele precisa
        // (evita ficar tirando print à toa em telas que têm texto mas não têm oferta).
        val semValorEmTexto = leituras.none { l -> l.textos.any { it.contains("R$") } }
        val quaseSemTexto = leituras.sumOf { it.textos.size } < 15
        val pacoteOcr = leituras.any { it.pacote in pacotesQuePrecisamOcr }
        if (resultado == null && semValorEmTexto && (quaseSemTexto || pacoteOcr) && ocrDisponivel()) {
            solicitarOcr(leituras.first().pacote)
            return
        }

        registrarDiagnostico(leituras.flatMap { it.textos }, leituras.map { it.pacote }, leituras.size,
            resultado?.oferta, origem = "texto")
        if (leituras.all { it.textos.isEmpty() }) return
        processarResultado(resultado?.oferta, resultado?.pacote, resultado?.total ?: 1)
    }

    /** Mostra/atualiza/esconde a janela a partir do resultado (vale para leitura por texto e por imagem). */
    private fun processarResultado(oferta: Oferta?, pacote: String?, totalNaTela: Int = 1) {
        val ov = overlay ?: return
        val lim = limites ?: return
        if (oferta == null) {
            // Card sumiu (aceita/recusada/expirou): esconde e zera o estado após 2 leituras vazias.
            if (++varredurasVazias >= 2) {
                ov.esconder()
                ultimaAssinatura = null
                assinaturaDispensada = null
            }
            return
        }
        ultimoPacote = pacote
        varredurasVazias = 0

        // Mesma oferta ainda na tela: não redesenha, e não reabre se o motorista fechou.
        if (oferta.assinatura == assinaturaDispensada) return
        if (oferta.assinatura == ultimaAssinatura && ov.visivel) return
        ultimaAssinatura = oferta.assinatura

        val classe = lim.classificar(oferta, tarifaMinimaCentavos, custoKmCentavos)
        val alertaRisco = risco?.alerta(oferta.enderecos)
        Log.d(
            TAG,
            "Oferta: $oferta  R$/km=%.2f  R$/h=%.2f  -> %s".format(
                oferta.reaisPorKm, oferta.reaisPorHora, classe.rotulo
            )
        )
        // "Peca R$ X e vira BOA": vale em qualquer app como medida do buraco, e no
        // inDrive vira acao direta — os botoes de contraproposta estao ali embaixo.
        val sugestao = lim.valorParaFicarBoa(oferta, tarifaMinimaCentavos, custoKmCentavos)
        ov.mostrar(
            oferta, classe, custoKmCentavos,
            totalNaTela = totalNaTela,
            alertaRisco = alertaRisco,
            sugestao = sugestao
        )
        voz?.falar(oferta, classe, alertaRisco)
        salvarNoHistorico(oferta, classe)
    }

    /** Grava a oferta no histórico (uma vez por oferta, mesmo que ela reapareça na tela). */
    private fun salvarNoHistorico(oferta: Oferta, classe: Classificacao) {
        val agora = System.currentTimeMillis()
        salvasRecentes.entries.removeAll { agora - it.value > JANELA_DUPLICADA_MS }
        if (salvasRecentes.containsKey(oferta.assinatura)) return
        salvasRecentes[oferta.assinatura] = agora

        val repo = (application as MotoristaApp).repository
        val registro = OfertaRecebida(
            recebidaEm = agora,
            plataforma = nomePlataforma(ultimoPacote),
            valorCentavos = Math.round(oferta.valor * 100),
            metros = Math.round(oferta.km * 1000),
            minutos = oferta.minutos,
            paradas = oferta.paradas,
            classificacao = classe.name,
            nota = oferta.nota,
            origem = oferta.enderecos.firstOrNull(),
            // Só quando a tela mostrou DOIS endereços: com um só, o destino
            // ficaria igual à origem e o histórico mentiria.
            destino = oferta.enderecos.takeIf { it.size > 1 }?.lastOrNull()
        )
        ultimaOfertaId = null
        escopo.launch {
            runCatching { repo.salvarOferta(registro) }
                .onSuccess { ultimaOfertaId = it }
                .onFailure { Log.e(TAG, "Falha ao salvar oferta no histórico", it) }
        }
    }

    // ================================================================== leitura pela imagem (OCR)

    private fun ocrDisponivel(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (application as MotoristaApp).preferencias.ocrAtivo

    /** Limita o OCR a uma captura por [INTERVALO_OCR_MS]; pedidos no meio do intervalo são reagendados. */
    private fun solicitarOcr(pacote: String) {
        val ocr = leitorOcr ?: LeitorOcr(this).also { leitorOcr = it }
        if (ocr.ocupado) return
        val espera = INTERVALO_OCR_MS - (System.currentTimeMillis() - ultimoOcrEm)
        if (espera > 0) {
            handler.removeCallbacks(varrerRunnable)
            handler.postDelayed(varrerRunnable, espera)
            return
        }
        ultimoOcrEm = System.currentTimeMillis()
        ocr.ler { linhas ->
            if (linhas == null) return@ler      // falha na captura: tenta no próximo evento
            val achado = escolher(linhas, pacote)
            registrarDiagnostico(linhas, listOf(pacote), 1, achado?.oferta, origem = "imagem (OCR)")
            if (achado != null) pacotesQuePrecisamOcr += pacote
            processarResultado(achado?.oferta, pacote, achado?.total ?: 1)
        }
    }

    /** Oferta escolhida + de onde veio + quantas ofertas havia na tela (listas mostram várias). */
    private class Achado(val oferta: Oferta, val pacote: String, val total: Int)

    /**
     * Tenta cada janela separadamente (o card costuma ser uma janela só dele, e isso evita
     * misturar números do mapa/tela de fundo). Se nenhuma sozinha tiver oferta, junta todas.
     * Em telas com várias corridas, escolhe a de melhor R$/km.
     */
    private fun interpretar(leituras: List<Leitura>): Achado? {
        for (l in leituras) {
            if (l.textos.isEmpty()) continue
            escolher(l.textos, l.pacote)?.let { return it }
        }
        val todos = leituras.flatMap { it.textos }
        if (todos.isEmpty()) return null
        val pacote = leituras.firstOrNull { it.textos.isNotEmpty() }?.pacote ?: return null
        return escolher(todos, pacote)
    }

    private fun escolher(textos: List<String>, pacote: String): Achado? {
        // inDrive tem leitor proprio: a tela de lista mostra uma duzia de ofertas
        // de uma vez (o leitor comum faria um Frankenstein) e os botoes de
        // contraproposta tem valores MAIORES que o da corrida.
        if (pacote == OfertaInDrive.PACOTE) {
            val oferta = OfertaInDrive.extrair(textos) ?: return null
            return Achado(oferta, pacote, 1)
        }

        // Corrida de passageiro sempre mostra minutos; entrega (99 Entrega Food, iFood)
        // muitas vezes mostra só "Distância total X km" — aí aceitamos sem o tempo.
        val exigirTempo = pacote != PACOTE_IFOOD && !OfertaParser.pareceEntrega(textos)
        val ofertas = OfertaParser.extrairVarias(textos, exigirTempo = exigirTempo)
        val melhor = ofertas.maxByOrNull { it.reaisPorKm } ?: return null
        return Achado(melhor, pacote, ofertas.size)
    }

    /** Guarda a última leitura com cara de oferta para a tela de diagnóstico (aba Mais). */
    private fun registrarDiagnostico(
        todos: List<String>,
        pacotes: List<String>,
        janelas: Int,
        oferta: Oferta?,
        origem: String
    ) {
        val relevante = oferta != null || todos.any { it.contains("R$") || it.contains("km", ignoreCase = true) }
        // Também registra quando o app-alvo está aberto mas não expõe nenhum texto (sinal importante).
        if (!relevante && todos.isNotEmpty()) return
        _diagnostico.value = Diagnostico(
            momento = System.currentTimeMillis(),
            pacotes = pacotes.distinct(),
            janelas = janelas,
            textos = todos.take(200),
            reconhecida = oferta?.let {
                "R$ %.2f • %.2f km • %d min • R$ %.2f/km".format(it.valor, it.km, it.minutos, it.reaisPorKm)
            },
            origem = origem
        )
        if (LOG_TEXTOS && todos.isNotEmpty()) Log.d(TAG, "Textos ($origem): ${todos.joinToString(" | ")}")
    }

    /** Textos de uma janela de um app-alvo. */
    private class Leitura(val pacote: String, val textos: List<String>)

    /**
     * Varre TODAS as janelas interativas (não só a ativa): o card de oferta costuma ser
     * uma janela própria sobre o mapa. Exige flagRetrieveInteractiveWindows.
     */
    private fun coletarLeituras(incluirDescricoes: Boolean): List<Leitura> {
        val raizes: List<AccessibilityNodeInfo> = try {
            windows.mapNotNull { it.root }.ifEmpty { listOfNotNull(rootInActiveWindow) }
        } catch (e: Exception) {
            listOfNotNull(rootInActiveWindow)
        }

        val leituras = ArrayList<Leitura>()
        for (raiz in raizes) {
            try {
                val pacote = raiz.packageName?.toString()
                if (pacote != null && pacote in PACOTES_ALVO) {
                    val saida = ArrayList<String>(64)
                    percorrer(raiz, saida, 0, incluirDescricoes)
                    leituras += Leitura(pacote, saida)
                }
            } finally {
                reciclar(raiz)
            }
        }
        return leituras
    }

    /** Busca em profundidade com limites de profundidade e quantidade, para nunca travar a UI. */
    private fun percorrer(
        no: AccessibilityNodeInfo,
        saida: MutableList<String>,
        profundidade: Int,
        incluirDescricoes: Boolean
    ) {
        if (profundidade > MAX_PROFUNDIDADE || saida.size >= MAX_TEXTOS) return

        val texto = no.text?.toString()?.trim()
        if (!texto.isNullOrEmpty()) saida += texto

        val descricao = no.contentDescription?.toString()?.trim()
        if (!descricao.isNullOrEmpty() && descricao != texto) {
            // Sem incluirDescricoes, só folhas sem texto: a descrição de um container costuma
            // repetir o texto dos filhos (o parser também ignora trechos repetidos).
            if (incluirDescricoes || (texto.isNullOrEmpty() && no.childCount == 0)) saida += descricao
        }

        for (i in 0 until no.childCount) {
            val filho = no.getChild(i) ?: continue
            try {
                percorrer(filho, saida, profundidade + 1, incluirDescricoes)
            } finally {
                reciclar(filho)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun reciclar(no: AccessibilityNodeInfo) {
        // Android 13+: recycle() é no-op. Antes disso, devolve o nó ao pool e evita vazamento.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                no.recycle()
            } catch (e: IllegalStateException) {
                // Nó já reciclado: ignorar.
            }
        }
    }

    // ================================================================== ações

    private fun registrarOferta(oferta: Oferta) {
        val repo = (application as MotoristaApp).repository
        val pacote = ultimoPacote
        val idHistorico = ultimaOfertaId
        escopo.launch {
            runCatching {
                repo.registrarCorridaDaOferta(oferta.valor, oferta.km, oferta.minutos, pacote)
                if (idHistorico != null) repo.marcarOfertaRegistrada(idHistorico)
            }
                .onSuccess { overlay?.marcarRegistrada() }
                .onFailure { Log.e(TAG, "Falha ao registrar corrida", it) }
        }
    }

    /** Relê as preferências (chamado pela tela "Mais" ao ligar/desligar a bolha). */
    fun aplicarPreferencias() {
        val prefs = (application as MotoristaApp).preferencias
        if (prefs.bolhaAtiva) bolha?.mostrar() else bolha?.esconder()
        // A voz guarda o próprio estado; aqui só garantimos o motor criado/liberado.
        voz?.let { if (it.ativo) it.preparar() else it.liberar() }
    }

    // ================================================================== teste manual

    /** Usado pelo botão "Testar" do Dashboard: mostra uma oferta fictícia por 6 segundos. */
    fun mostrarTeste() {
        val ov = overlay ?: return
        val lim = limites ?: return
        val o = Oferta(
            valor = 18.40, km = 11.0, minutos = 26,
            enderecos = listOf("Av. Paulista, 1578 - São Paulo"),
            nota = 4.82
        )
        val classe = lim.classificar(o, tarifaMinimaCentavos, custoKmCentavos)
        val alerta = risco?.alerta(o.enderecos)
        ov.mostrar(
            o, classe, custoKmCentavos, duracaoMs = 6_000, alertaRisco = alerta,
            sugestao = lim.valorParaFicarBoa(o, tarifaMinimaCentavos, custoKmCentavos)
        )
        voz?.falar(o, classe, alerta)
    }

    companion object {
        private const val TAG = "MotoristaPro"
        private const val DEBOUNCE_MS = 120L
        private const val MAX_PROFUNDIDADE = 80
        private const val MAX_TEXTOS = 1500
        private const val JANELA_DUPLICADA_MS = 5 * 60 * 1000L
        private const val INTERVALO_OCR_MS = 800L

        /** Loga todos os textos lidos (para calibrar as Regex). Mude para false na versão final. */
        const val LOG_TEXTOS = true

        const val PACOTE_IFOOD = "br.com.ifood.driver.app"

        /** Mantenha em sincronia com android:packageNames em accessibility_service_config.xml. */
        val PACOTES_ALVO = setOf(
            "com.ubercab.driver",   // Uber Driver
            "com.app99.driver",     // 99 Motorista
            PACOTE_IFOOD,           // iFood para Entregadores
            OfertaInDrive.PACOTE    // inDrive
        )

        private val _diagnostico = MutableStateFlow<Diagnostico?>(null)

        /** Última leitura de tela de um app-alvo (aba Mais > Diagnóstico do leitor). */
        val diagnostico: StateFlow<Diagnostico?> = _diagnostico.asStateFlow()

        private val _conectado = MutableStateFlow(false)

        /** true enquanto o serviço está conectado e lendo a tela (observável pela UI). */
        val conectado: StateFlow<Boolean> = _conectado.asStateFlow()

        /** Instância conectada (null se o serviço estiver desligado). */
        @Volatile
        var instancia: OfertaAccessibilityService? = null
            private set
    }
}

/** O que o leitor viu na última tela relevante (para calibrar o parser sem Logcat). */
data class Diagnostico(
    val momento: Long,
    val pacotes: List<String>,
    val janelas: Int,
    val textos: List<String>,
    /** Resumo da oferta reconhecida, ou null se não reconheceu. */
    val reconhecida: String?,
    /** "texto" (acessibilidade) ou "imagem (OCR)". */
    val origem: String = "texto"
)
