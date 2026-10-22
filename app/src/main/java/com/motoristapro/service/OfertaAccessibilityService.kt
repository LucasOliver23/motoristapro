package com.motoristapro.service

import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.LocalDate
import java.time.Instant
import com.motoristapro.data.repository.PACOTE_99
import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.motoristapro.diagnostico.RegistroErros
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.local.entity.OfertaRecebida
import com.motoristapro.jornada.JornadaService
import com.motoristapro.data.repository.nomePlataforma
import com.motoristapro.ui.NavegacaoRapida
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
    // Com o handler, um erro numa tarefa (salvar, registrar, conferir o
    // resumo) fica anotado e o leitor continua; sem ele, o processo inteiro caía.
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main + RegistroErros.handler("leitor"))

    private var overlay: OverlayOferta? = null
    private var cartaoResumo: CartaoResumo? = null

    /** Resumo da 99 que já ganhou cartão (não reabre a cada evento da mesma tela). */
    private var resumoTratado: String? = null

    /** Resumos em que o motorista tocou "Agora não". */
    private val resumosDispensados = mutableSetOf<String>()
    private var limites: LimitesOferta? = null
    private var bolha: BolhaFlutuante? = null
    private var painel: PainelBolha? = null

    /** O turno aberto, o lucro e a meta de hoje, para a bolha e o painel dela. */
    @Volatile private var jornadaAberta: Jornada? = null
    @Volatile private var lucroHojeCentavos: Long = 0
    @Volatile private var metaDoDiaCentavos: Long = 0

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

    private val varrerRunnable = Runnable {
        varreduraPendenteDesde = 0L
        varrerTelaComSeguranca()
    }

    /** Desde quando há uma varredura esperando (0 = nenhuma). */
    private var varreduraPendenteDesde = 0L

    /** Último evento de cada app: com duas ofertas na tela, vale a do app que mexeu por último. */
    private val ultimoEvento = HashMap<String, Long>()

    /** Lista de endereços de risco do motorista. */
    private var risco: EnderecosDeRisco? = null

    /** Aviso falado da oferta (desligado por padrão). */
    private var voz: AvisoVoz? = null

    /** Leitura por imagem (criada só quando algum app não expõe texto). */
    private var leitorOcr: LeitorOcr? = null
    private var ultimoOcrEm = 0L

    /**
     * Apps em que a oferta só foi reconhecida pela imagem (ex.: 99), com a hora
     * em que isso aconteceu. Vale [VALIDADE_OCR_MS]: antes era para sempre, e
     * uma leitura por imagem na Uber (segurar a bolha) deixava o app tirando
     * print do mapa parado a cada 1,2 s até o celular reiniciar.
     */
    private val pacotesQuePrecisamOcr = HashMap<String, Long>()

    private fun precisaOcr(pacote: String): Boolean {
        val em = pacotesQuePrecisamOcr[pacote] ?: return false
        if (System.currentTimeMillis() - em > VALIDADE_OCR_MS) {
            pacotesQuePrecisamOcr.remove(pacote)
            return false
        }
        return true
    }

    /** Thread só para percorrer a árvore de acessibilidade (ver [varrerTela]). */
    private val threadTrabalho = HandlerThread("leitor-varredura").apply { start() }
    private val trabalho = Handler(threadTrabalho.looper)

    /** Uma coleta em andamento; evento novo no meio só marca [varrerDeNovo]. */
    private var varrendo = false
    private var varrerDeNovo = false

    /** "Ler agora" (bolha): se o texto não achar oferta, tenta pela imagem no fim da varredura. */
    private var ocrSeNaoAchar = false

    /** Leituras por imagem seguidas sem oferta (ou com falha): cada uma alonga a espera da próxima. */
    private var ocrVaziosSeguidos = 0

    /** Corridas registradas há pouco (assinatura -> hora): toque duplo não vira duas corridas. */
    private val registradasAgora = HashMap<String, Long>()

    // ================================================================== ciclo de vida

    override fun onServiceConnected() {
        super.onServiceConnected()
        // TUDO aqui dentro protegido: uma exceção na partida (sem permissão de
        // sobreposição, voz indisponível, banco ocupado) derrubava o serviço, e
        // o Android desliga sozinho o serviço de acessibilidade que quebra —
        // é por isso que o leitor "desativava sozinho" sem o motorista mexer.
        try {
            conectar()
        } catch (e: Throwable) {
            RegistroErros.anotar("conectar o leitor", e)
            // Sem cartão não há leitura: mostra "desligado" em vez de fingir que lê.
            _conectado.value = overlay != null && limites != null
        }
    }

    private fun conectar() {
        instancia = this
        _conectado.value = true
        AvisoLeitor.marcarLigado(this)

        limites = LimitesOferta(this)
        risco = EnderecosDeRisco(this)
        voz = AvisoVoz(this).apply { preparar() }
        val app = application as MotoristaApp
        val repo = app.repository

        overlay = OverlayOferta(this).apply {
            limites = this@OfertaAccessibilityService.limites
            aoDispensar = { assinaturaDispensada = ultimaAssinatura }
            aoRegistrar = { oferta -> registrarOferta(oferta) }
            aoVerLocal = { endereco ->
                escopo.launch { AcoesOferta.abrirLocal(this@OfertaAccessibilityService, endereco) }
            }
        }
        cartaoResumo = CartaoResumo(this)
        painel = PainelBolha(this).apply {
            estado = {
                EstadoBolha(
                    lucroCentavos = lucroHojeCentavos,
                    metaCentavos = metaDoDiaCentavos,
                    turnoSegundos = jornadaAberta?.segundosRodando(System.currentTimeMillis()),
                    turnoPausado = jornadaAberta?.pausada == true,
                    leitorLendo = _conectado.value,
                    vozLigada = voz?.ativo == true
                )
            }
            aoLerAgora = { lerAgora() }
            aoAlternarVoz = {
                voz?.let { it.ativo = !it.ativo }
                aplicarPreferencias()
            }
            aoAbrirAba = { aba ->
                NavegacaoRapida.pedir(aba)
                bolha?.abrirApp()
            }
            aoAlternarPausa = { alternarPausaDoTurno() }
            aoEncerrarTurno = { encerrarTurnoPelaBolha() }
            // Abrir turno pede a localização: quem pede é o Início, então o app abre lá.
            aoIniciarTurno = {
                NavegacaoRapida.iniciarJornadaPedido.value = true
                NavegacaoRapida.pedir(0)
                bolha?.abrirApp()
            }
            aoRegistrarCorrida = {
                NavegacaoRapida.novaCorridaPedida.value = true
                NavegacaoRapida.pedir(1)
                bolha?.abrirApp()
            }
        }
        bolha = BolhaFlutuante(this).apply {
            // Plano B do leitor: segurar o dedo na bolha manda ler a tela agora,
            // com imagem inclusive. Serve quando a oferta nao chega sozinha.
            aoSegurar = { lerAgora() }
            // Toque curto abre o menu com as ações do turno.
            aoTocar = { painel?.alternar() }
        }
        aplicarPreferencias()

        // Bolha: lucro de hoje + início do turno, reativos ao banco.
        escopo.launch {
            try {
                combine(repo.resumoHoje(), repo.jornadaAtiva(), repo.configuracao()) { r, j, cfg ->
                    Triple(r.lucroLiquidoCentavos, j, cfg.metaLucroDiarioCentavos)
                }
                    .collect { (lucro, jornada, meta) ->
                        jornadaAberta = jornada
                        lucroHojeCentavos = lucro
                        metaDoDiaCentavos = meta
                        bolha?.atualizar(lucro, meta, jornada)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao observar dados da bolha", e)
            }
        }

        // Já começa a juntar meta e medidas da régua: a primeira oferta não espera o banco.
        (application as MotoristaApp).regua

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
        // Chamado dezenas de vezes por segundo: uma exceção aqui é o caminho
        // mais curto para o sistema matar o leitor no meio do turno.
        try {
            val pacote = event?.packageName?.toString() ?: return
            if (pacote !in PACOTES_ALVO && pacote !in PACOTES_FOTO) return

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    val agora = System.currentTimeMillis()
                    ultimoEvento[pacote] = agora
                    // Janela nova (o card da oferta abrindo) zera a espera da
                    // leitura por imagem: ela só cresce com o mapa parado, nunca
                    // pode atrasar a oferta que acabou de chegar.
                    if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) ocrVaziosSeguidos = 0
                    // Debounce com prazo: o mapa do inDrive (ou o cronômetro da
                    // oferta) dispara eventos sem parar, e um debounce puro
                    // adiava a leitura para sempre — a corrida da Uber que tocava
                    // ao mesmo tempo nunca era lida. Passou do prazo, lê já.
                    if (varreduraPendenteDesde == 0L) varreduraPendenteDesde = agora
                    if (agora - varreduraPendenteDesde < ESPERA_MAXIMA_MS) {
                        handler.removeCallbacks(varrerRunnable)
                        handler.postDelayed(varrerRunnable, DEBOUNCE_MS)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao tratar evento de acessibilidade", e)
        }
    }

    override fun onInterrupt() {
        overlay?.esconder()
    }

    override fun onDestroy() {
        // Avisa na barra de notificações que o leitor caiu. Sem isto o motorista
        // só descobre horas depois, quando vai ver as corridas do dia e não há
        // nenhuma — que é exatamente o que vinha acontecendo.
        runCatching { AvisoLeitor.aoCair(this) }
        handler.removeCallbacksAndMessages(null)
        trabalho.removeCallbacksAndMessages(null)
        runCatching { threadTrabalho.quitSafely() }
        overlay?.liberar()
        overlay = null
        cartaoResumo?.liberar()
        cartaoResumo = null
        bolha?.liberar()
        bolha = null
        painel?.liberar()
        painel = null
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
        } catch (e: Throwable) {
            RegistroErros.anotar("varrer a tela", e)
        }
    }

    /**
     * A varredura em duas partes. A COLETA (percorrer a árvore de acessibilidade
     * de todas as janelas — até 1500 nós, cada um uma chamada entre processos)
     * roda na thread [trabalho]; o resto (interpretar, cartão, histórico) volta
     * para a principal, onde mora o estado do leitor. Na principal, a coleta
     * travava o cartão e a bolha a cada 0,4 s com o mapa mexendo.
     */
    private fun varrerTela() {
        if (overlay == null || limites == null) return
        if (varrendo) {
            varrerDeNovo = true
            return
        }
        varrendo = true
        trabalho.post {
            val coleta = try {
                coletar()
            } catch (e: Throwable) {
                RegistroErros.anotar("coletar a tela", e)
                null
            }
            handler.post {
                varrendo = false
                try {
                    if (coleta != null) continuarVarredura(coleta)
                    if (ocrSeNaoAchar) {
                        ocrSeNaoAchar = false
                        if (overlay?.visivel != true && ocrDisponivel()) solicitarOcr(ultimoPacote ?: OfertaInDrive.PACOTE)
                    }
                } catch (e: Throwable) {
                    RegistroErros.anotar("varrer a tela", e)
                }
                // Chegou evento novo durante a coleta: lê mais uma vez, já com a tela nova.
                if (varrerDeNovo) {
                    varrerDeNovo = false
                    varrerTelaComSeguranca()
                }
            }
        }
    }

    /** O que a thread de [trabalho] leu da tela. */
    private class Coleta(
        val foto: String?,
        val textosFrente: List<String>,
        val semDescricoes: List<Leitura>,
        val comDescricoes: List<Leitura>
    )

    /**
     * Até quando a coleta atual pode rodar (relógio de [SystemClock.uptimeMillis]).
     * Só a thread de [trabalho] mexe nele.
     *
     * Cada nó lido é uma chamada entre processos, e o Android espera até 5 s
     * por cada uma quando o app de corrida está ocupado (mapa redesenhando,
     * app travado). Sem prazo, uma coleta podia levar minutos: nesse tempo
     * nenhuma oferta nova era lida e o leitor parecia ter fechado.
     */
    private var prazoColeta = 0L

    /** Só leitura da árvore: nada de estado do leitor aqui (roda fora da thread principal). */
    private fun coletar(): Coleta {
        prazoColeta = SystemClock.uptimeMillis() + ORCAMENTO_COLETA_MS
        val foto = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            ?.takeIf { it in PACOTES_FOTO }
        if (foto != null) return Coleta(foto, coletarTextosDaFrente(), emptyList(), emptyList())
        // As duas leituras de uma vez: a com descrições só era feita quando a
        // primeira não achava oferta — que é quase sempre (mapa sem oferta).
        return Coleta(null, emptyList(), coletarLeituras(incluirDescricoes = false), coletarLeituras(incluirDescricoes = true))
    }

    private fun continuarVarredura(c: Coleta) {
        if (overlay == null || limites == null) return

        // Foto de uma oferta aberta no WhatsApp, Facebook, Instagram, Telegram
        // ou na Galeria: lê só pela IMAGEM, e só com a foto em tela cheia (poucos
        // textos na tela). O texto da conversa nunca vira oferta — "R$ 20" e
        // "5 km" numa mensagem não são corrida.
        val foto = c.foto
        if (foto != null) {
            if (c.textosFrente.size <= MAX_TEXTOS_FOTO && ocrDisponivel()) solicitarOcr(foto)
            else processarResultado(null, foto)
            return
        }

        // 1ª tentativa: só textos visíveis. 2ª: inclui descrições de acessibilidade de todos os nós
        // (alguns apps colocam o texto do card só na descrição do container).
        var leituras = c.semDescricoes
        var resultado = interpretar(leituras)
        if (resultado == null) {
            val comDescricoes = c.comDescricoes
            resultado = interpretar(comDescricoes)
            if (resultado != null || leituras.all { it.textos.isEmpty() }) leituras = comDescricoes
        }
        if (leituras.isEmpty()) return   // nenhuma janela de app-alvo na tela

        // Tela "Detalhes da corrida" da 99 (depois da corrida): não é oferta.
        // Confere se a corrida está no app e, se não estiver, oferece registrar.
        val textos99 = leituras.filter { it.pacote == PACOTE_99 }.flatMap { it.textos }
        if (ResumoCorrida99.ehResumo(textos99)) {
            registrarDiagnostico(
                textos99, listOf(PACOTE_99), leituras.size, null, origem = "texto",
                motivo = "resumo de corrida já terminada (não é oferta)"
            )
            ResumoCorrida99.ler(textos99)?.let { tratarResumo(it) }
            return
        }
        if (resumoTratado != null) {
            resumoTratado = null
            cartaoResumo?.esconder()
        }

        // 3ª tentativa: o app não expõe o valor para a acessibilidade (ex.: 99) -> lê a IMAGEM da tela.
        // Só usa OCR quando o app praticamente não expõe textos, ou quando já sabemos que ele precisa
        // (evita ficar tirando print à toa em telas que têm texto mas não têm oferta).
        val semValorEmTexto = leituras.none { l -> l.textos.any { it.contains("R$") } }
        val quaseSemTexto = leituras.sumOf { it.textos.size } < 15
        val pacoteOcr = leituras.any { precisaOcr(it.pacote) }

        // inDrive: se o motorista esta decidindo uma corrida mas a leitura nao
        // fechou, falta o km da viagem — o mapa pode nao estar na arvore de
        // acessibilidade. A imagem da tela resolve, e vale a pena o print aqui
        // porque sem isso nao ha cartao nenhum.
        val inDriveIncompleto = resultado == null &&
            leituras.any { it.pacote == OfertaInDrive.PACOTE } &&
            OfertaInDrive.ehTelaDeDetalhe(leituras.flatMap { it.textos })

        // Janela de app-alvo SEM UMA LINHA DE TEXTO é a assinatura de tela que a
        // acessibilidade não enxerga (a 99 desenha o card por cima do mapa). Aí
        // a leitura por imagem não é preferência, é o único caminho: se estiver
        // desligada, liga sozinha — senão o leitor fica aberto sem ler nada, que
        // é pior do que gastar um pouco mais de bateria.
        val telaMuda = leituras.isNotEmpty() && leituras.all { it.textos.isEmpty() }
        if (telaMuda && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prefs = (application as MotoristaApp).preferencias
            if (!prefs.ocrAtivo) {
                prefs.ocrAtivo = true
                Log.i(TAG, "Leitura por imagem ligada sozinha: app-alvo sem texto nenhum")
            }
        }

        // Com vários apps abertos, o texto de UM (a lista do inDrive, cheia de
        // R$) escondia que o OUTRO (o card da 99, que não tem texto) precisava
        // da imagem. A conta agora é app por app.
        val appSemTexto = leituras.groupBy { it.pacote }.entries.firstOrNull { (p, janelas) ->
            val t = janelas.flatMap { it.textos }
            t.none { it.contains("R$") } && (t.size < 15 || precisaOcr(p))
        }?.key

        if (resultado == null && ocrDisponivel() &&
            (inDriveIncompleto || telaMuda || appSemTexto != null ||
                (semValorEmTexto && (quaseSemTexto || pacoteOcr)))
        ) {
            solicitarOcr(appSemTexto ?: leituras.first().pacote)
            return
        }

        // Chegou aqui com a tela muda e sem OCR: diz POR QUE, em vez de mostrar
        // um diagnóstico vazio que não explica nada.
        if (telaMuda && !ocrDisponivel()) {
            registrarDiagnostico(
                emptyList(), leituras.map { it.pacote }, leituras.size, null,
                origem = "texto",
                motivo = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
                    "este app não mostra o texto da oferta e o Android deste aparelho é " +
                        "anterior ao 11, que é onde a leitura por imagem funciona"
                else "este app não mostra o texto da oferta e a leitura por imagem está desligada"
            )
            return
        }


        registrarDiagnostico(leituras.flatMap { it.textos }, leituras.map { it.pacote }, leituras.size,
            resultado?.oferta, origem = "texto", motivo = motivoInDrive(leituras, resultado))
        if (leituras.all { it.textos.isEmpty() }) return
        processarResultado(resultado?.oferta, resultado?.pacote, resultado?.total ?: 1)
    }

    /**
     * Meta e medidas do motorista para a régua do lucro por hora. O cartão
     * recebe a mesma, para pintar os números e escrever a conta igual.
     */
    private fun reguaAtual(): ParametrosRegua? =
        (application as MotoristaApp).regua.parametros.value.also { overlay?.regua = it }

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
        // Ainda na tela: renova o "visto por último", para a mesma oferta não
        // ser gravada de novo enquanto não sai da tela.
        salvasRecentes.computeIfPresent(oferta.assinatura) { _, _ -> System.currentTimeMillis() }

        // Mesma oferta ainda na tela: não redesenha, e não reabre se o motorista fechou.
        if (oferta.assinatura == assinaturaDispensada) return
        if (oferta.assinatura == ultimaAssinatura && ov.visivel) return
        ultimaAssinatura = oferta.assinatura

        val classe = lim.classificar(oferta, tarifaMinimaCentavos, custoKmCentavos, reguaAtual())
        val alertaRisco = risco?.alerta(oferta.enderecos)
        Log.d(
            TAG,
            "Oferta: $oferta  R$/km=%.2f  R$/h=%.2f  -> %s".format(
                oferta.reaisPorKm, oferta.reaisPorHora, classe.rotulo
            )
        )
        // "Peca R$ X e vira BOA" so no inDrive.
        //
        // E o unico app em que existe contraproposta: ali o motorista toca em
        // "Ofereça sua tarifa" e pede o valor. Na 99, na Uber e no iFood o preco
        // ja vem fechado — a linha so ocupava o cartao com um conselho que nao
        // da para seguir, e ainda empurrava os numeros que decidem a corrida
        // para baixo nos 12 segundos que ele tem para responder.
        val sugestao = if (pacote == OfertaInDrive.PACOTE) {
            lim.valorParaFicarBoa(oferta, tarifaMinimaCentavos, custoKmCentavos, reguaAtual())
        } else {
            null
        }
        ov.mostrar(
            oferta, classe, custoKmCentavos,
            totalNaTela = totalNaTela,
            alertaRisco = alertaRisco,
            sugestao = sugestao
        )
        voz?.falar(oferta, classe, alertaRisco)
        // Oferta lida de uma FOTO (WhatsApp, Galeria...) é só para avaliar: não
        // entra no histórico nem na notificação, que são das ofertas de verdade.
        if (pacote in PACOTES_FOTO) return
        salvarNoHistorico(oferta, classe)
        ofertaDaNotificacao = oferta to pacote
        if ((application as MotoristaApp).preferencias.notificacaoOferta.value) {
            NotificacaoOferta.mostrar(this, oferta, classe, custoKmCentavos, pacote)
        }
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

    /**
     * A captura pegou a NOSSA tela, não a do aplicativo de corrida.
     *
     * Acontece porque o print é tirado alguns décimos depois do pedido: nesse
     * intervalo o motorista pode ter saído da Uber/99 e aberto o MotoristaPro —
     * e aí o OCR lê o nosso próprio menu, o diagnóstico vira lixo e apaga a
     * última leitura boa, que era justamente a que interessava.
     */
    private fun pareceNossaTela(linhas: List<String>): Boolean {
        val marcas = listOf(
            "motoristapro", "motorista pro", "diagnóstico do leitor", "diagnostico do leitor",
            "copiar textos", "leitor de ofertas", "meu veículo e custos", "suas faixas"
        )
        val achadas = marcas.count { marca ->
            linhas.any { it.lowercase().contains(marca) }
        }
        return achadas >= 2
    }

    /** Limita o OCR a uma captura por [INTERVALO_OCR_MS]; pedidos no meio do intervalo são reagendados. */
    /**
     * Espera entre duas leituras por imagem. Com oferta na tela, a mínima; a
     * cada leitura vazia seguida ela dobra (até [INTERVALO_OCR_MAX_MS], 3 s): o mapa
     * parado não precisa de print a cada 1,2 s, e o card da 99 fica 15 s na tela.
     */
    private fun intervaloOcr(): Long =
        (INTERVALO_OCR_MS shl (ocrVaziosSeguidos - 1).coerceIn(0, 3)).coerceAtMost(INTERVALO_OCR_MAX_MS)

    private fun solicitarOcr(pacote: String) {
        val ocr = leitorOcr ?: LeitorOcr(this).also { leitorOcr = it }
        if (ocr.ocupado) return

        // Só tira print se um app de corrida estiver MESMO na frente agora. Sem
        // esta conferência o leitor capturava a tela do próprio MotoristaPro e
        // gastava o intervalo de captura à toa.
        val emFrente = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (emFrente != null && emFrente !in PACOTES_ALVO && emFrente !in PACOTES_FOTO) return
        // Quem manda é quem está na frente AGORA: o pacote que veio do pedido
        // pode ser de uma leitura de alguns décimos atrás, de outro aplicativo.
        val alvo = emFrente?.takeIf { it in PACOTES_ALVO || it in PACOTES_FOTO } ?: pacote
        val espera = intervaloOcr() - (System.currentTimeMillis() - ultimoOcrEm)
        if (espera > 0) {
            handler.removeCallbacks(varrerRunnable)
            handler.postDelayed(varrerRunnable, espera)
            return
        }
        ultimoOcrEm = System.currentTimeMillis()
        ocr.ler { linhas ->
            if (linhas == null) {
                ocrVaziosSeguidos++
                // A captura falhou — quase sempre porque o Android recusa dois
                // prints muito seguidos. Antes isto era silencioso: o leitor
                // parecia "não ler nada" e o diagnóstico mostrava a leitura por
                // texto anterior, que não tinha nada a ver com o problema.
                registrarDiagnostico(
                    emptyList(), listOf(alvo), 1, null,
                    origem = "imagem (OCR)",
                    motivo = "a captura da tela falhou — tentando de novo em seguida"
                )
                // Tenta de novo só algumas vezes: tela protegida (banco, senha)
                // falha sempre, e repetir sem fim era bateria jogada fora.
                if (ocrVaziosSeguidos <= MAX_FALHAS_OCR) {
                    handler.removeCallbacks(varrerRunnable)
                    handler.postDelayed(varrerRunnable, intervaloOcr())
                }
                return@ler
            }
            // Capturou a nossa própria tela: não é leitura, é ruído. Sai sem
            // tocar no diagnóstico, para a última leitura de verdade continuar lá.
            if (pareceNossaTela(linhas)) {
                Log.d(TAG, "Captura descartada: era a tela do próprio MotoristaPro")
                return@ler
            }
            // A oferta de um app pode chegar POR CIMA de outro (o card da Uber
            // sobre o inDrive aberto): o app "da frente" nem sempre é o dono do
            // card. Tenta primeiro ele e depois os outros que mexeram há pouco.
            val agoraOcr = System.currentTimeMillis()
            val candidatos = (listOf(alvo) + ultimoEvento.entries
                .filter { agoraOcr - it.value < JANELA_APPS_ATIVOS_MS }
                .sortedByDescending { it.value }
                .map { it.key }).distinct()
            val achado = candidatos.firstNotNullOfOrNull { escolher(linhas, it) }
            val motivo = if (achado == null && alvo == OfertaInDrive.PACOTE) {
                OfertaInDrive.porQueNaoLeu(linhas, listaInDriveValida())
            } else null
            registrarDiagnostico(linhas, listOf(alvo), 1, achado?.oferta, origem = "imagem (OCR)", motivo = motivo)
            if (achado != null) {
                pacotesQuePrecisamOcr[achado.pacote] = System.currentTimeMillis()
                ocrVaziosSeguidos = 0
            } else {
                ocrVaziosSeguidos++
            }
            processarResultado(achado?.oferta, achado?.pacote ?: alvo, achado?.total ?: 1)
        }
    }

    /**
     * As corridas que a lista do inDrive mostrou por último.
     *
     * A lista é a única tela em que as duas distâncias de cada corrida aparecem.
     * Guardamos para completar o detalhe quando ele abrir — por isso a validade
     * curta: o motorista rola a lista o tempo todo e oferta velha some do app.
     */
    private var listaInDrive: List<OfertaInDrive.CorridaDaLista> = emptyList()
    private var listaInDriveEm = 0L

    private fun guardarListaInDrive(corridas: List<OfertaInDrive.CorridaDaLista>) {
        if (corridas.isEmpty()) return
        // Junta com o que já havia: rolar a lista troca quais ficam na tela, e a
        // corrida que o motorista abre pode ter saído de vista no meio do caminho.
        val juntas = (corridas + listaInDrive)
            .distinctBy { it.valor to Math.round(it.kmBusca * 10) }
            .take(MAX_CORRIDAS_LISTA)
        listaInDrive = juntas
        listaInDriveEm = System.currentTimeMillis()
    }

    private fun listaInDriveValida(): List<OfertaInDrive.CorridaDaLista> =
        if (System.currentTimeMillis() - listaInDriveEm < VALIDADE_LISTA_MS) listaInDrive
        else emptyList()

    /** Oferta escolhida + de onde veio + quantas ofertas havia na tela (listas mostram várias). */
    private class Achado(val oferta: Oferta, val pacote: String, val total: Int)

    /**
     * Tenta cada janela separadamente (o card costuma ser uma janela só dele, e isso evita
     * misturar números do mapa/tela de fundo). Se nenhuma sozinha tiver oferta, junta todas.
     * Em telas com várias corridas, escolhe a de melhor R$/km.
     */
    private fun interpretar(leituras: List<Leitura>): Achado? {
        // Vários apps ligados ao mesmo tempo (Uber + 99 + inDrive): cada app é
        // lido SEPARADO. Antes, qualquer janela do inDrive na tela fazia o
        // leitor olhar só para ele — a corrida da Uber que chegava por cima
        // passava batido — e o "junta tudo" do fim misturava números de apps
        // diferentes numa oferta só.
        val doInDrive = leituras.filter { it.pacote == OfertaInDrive.PACOTE }
        val achadoInDrive = if (doInDrive.isNotEmpty()) interpretarInDrive(doInDrive) else null

        val outras = leituras.filter { it.pacote != OfertaInDrive.PACOTE && it.textos.isNotEmpty() }
        val achadoOutro = outras.firstNotNullOfOrNull { escolher(it.textos, it.pacote) }
            ?: outras.groupBy { it.pacote }.entries.firstNotNullOfOrNull { (pacote, janelas) ->
                if (janelas.size > 1) escolher(janelas.flatMap { it.textos }, pacote) else null
            }

        // Duas ofertas na tela ao mesmo tempo: vale a do app que mexeu por último
        // (é a que acabou de tocar).
        return listOfNotNull(achadoInDrive, achadoOutro).maxByOrNull { ultimoEvento[it.pacote] ?: 0L }
    }

    private fun interpretarInDrive(doInDrive: List<Leitura>): Achado? {
        // inDrive: o mapa e o cartao sao JANELAS DIFERENTES. A etiqueta verde do
        // mapa traz o km da viagem e o cartao traz so o da busca — olhar janela
        // por janela devolvia a primeira que desse certo, que era a do cartao.
        // Resultado real: 4,4 km numa corrida de 4,4 de busca + 6,0 de viagem.
        //
        // Passando pela lista: guarda as corridas e NAO mostra cartao nenhum.
        // E ali que estao os dois km (ponto A e ponto B) de cada corrida.
        val janelasDaLista = doInDrive.filter { OfertaInDrive.ehLista(it.textos) }
        janelasDaLista.forEach { guardarListaInDrive(OfertaInDrive.extrairDaLista(it.textos)) }

        // O detalhe abre como uma folha POR CIMA da lista, e a lista continua
        // na arvore de acessibilidade. Juntar tudo trazia os "N km" de uma
        // duzia de outras corridas para a conta desta — por isso a janela da
        // lista fica de fora na hora de ler a oferta aberta.
        val doDetalhe = doInDrive.filterNot { OfertaInDrive.ehLista(it.textos) }
            .flatMap { it.textos }
        if (OfertaInDrive.ehTelaDeDetalhe(doDetalhe)) {
            return escolher(doDetalhe, OfertaInDrive.PACOTE)
        }
        // Nenhuma janela de oferta aberta: se o que havia era a lista, nao
        // mostra nada; senao tenta o conjunto (tela de transicao, OCR etc.).
        if (janelasDaLista.isNotEmpty()) return null
        val todosInDrive = doInDrive.flatMap { it.textos }
        if (OfertaInDrive.ehLista(todosInDrive)) {
            guardarListaInDrive(OfertaInDrive.extrairDaLista(todosInDrive))
            return null
        }
        return escolher(todosInDrive, OfertaInDrive.PACOTE)
    }

    private fun escolher(textos: List<String>, pacote: String): Achado? {
        // A regra mora em EscolhaOferta: o "Testar com print" usa a mesma.
        val e = EscolhaOferta.escolher(textos, pacote, listaInDriveValida()) ?: return null
        return Achado(e.oferta, pacote, e.total)
    }

    /**
     * Por que a tela do inDrive não virou cartão — a mesma separação de janelas
     * que o leitor usa, para o diagnóstico não explicar uma tela diferente da
     * que foi lida.
     */
    private fun motivoInDrive(leituras: List<Leitura>, resultado: Achado?): String? {
        if (resultado != null) return null
        val doInDrive = leituras.filter { it.pacote == OfertaInDrive.PACOTE }
        if (doInDrive.isEmpty()) return null
        val doDetalhe = doInDrive.filterNot { OfertaInDrive.ehLista(it.textos) }.flatMap { it.textos }
        val textos = if (OfertaInDrive.ehTelaDeDetalhe(doDetalhe)) doDetalhe
            else doInDrive.flatMap { it.textos }
        return OfertaInDrive.porQueNaoLeu(textos, listaInDriveValida())
    }

    /** Guarda a última leitura com cara de oferta para a tela de diagnóstico (aba Mais). */
    private fun registrarDiagnostico(
        todos: List<String>,
        pacotes: List<String>,
        janelas: Int,
        oferta: Oferta?,
        origem: String,
        motivo: String? = null
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
            origem = origem,
            motivo = if (oferta == null) motivo else null
        )
        if (LOG_TEXTOS && todos.isNotEmpty()) Log.d(TAG, "Textos ($origem): ${todos.joinToString(" | ")}")
    }

    /** Quantos textos a janela da frente tem (só conta; para na marca do limite da foto). */
    private fun coletarTextosDaFrente(): List<String> {
        val raiz = runCatching { rootInActiveWindow }.getOrNull() ?: return emptyList()
        return try {
            val saida = ArrayList<String>(MAX_TEXTOS_FOTO + 2)
            percorrer(raiz, saida, 0, incluirDescricoes = false)
            saida
        } catch (e: Exception) {
            emptyList()
        } finally {
            reciclar(raiz)
        }
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
        if (prazoColeta > 0L && SystemClock.uptimeMillis() > prazoColeta) return

        // Texto gigante (descrição de um container inteiro, página web) não é
        // oferta e só deixa a interpretação lenta na thread principal.
        val texto = no.text?.toString()?.trim()?.take(MAX_CARACTERES_TEXTO)
        if (!texto.isNullOrEmpty()) saida += texto

        val descricao = no.contentDescription?.toString()?.trim()?.take(MAX_CARACTERES_TEXTO)
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

    /** A oferta que a notificação está mostrando (e o app dela). */
    private var ofertaDaNotificacao: Pair<Oferta, String?>? = null

    /** Botão "Registrar" da notificação. */
    fun registrarDaNotificacao() {
        handler.post {
            val (oferta, pacote) = ofertaDaNotificacao ?: return@post
            // O id do histórico só vale se a oferta da notificação ainda for a última lida.
            val id = ultimaOfertaId.takeIf { oferta.assinatura == ultimaAssinatura }
            registrarOferta(oferta, pacote, id)
        }
    }

    private fun registrarOferta(
        oferta: Oferta,
        pacote: String? = ultimoPacote,
        idHistorico: Long? = ultimaOfertaId
    ) {
        val repo = (application as MotoristaApp).repository
        // Toque duplo, ou cartão + notificação: a mesma oferta só vira UMA corrida.
        val agora = System.currentTimeMillis()
        registradasAgora.entries.removeAll { agora - it.value > JANELA_REGISTRO_MS }
        if (registradasAgora.containsKey(oferta.assinatura)) return
        registradasAgora[oferta.assinatura] = agora
        // Registrou (pelo cartão ou pela notificação): a notificação sai, para
        // não registrar a mesma corrida duas vezes.
        ofertaDaNotificacao = null
        NotificacaoOferta.cancelar(this)
        escopo.launch {
            runCatching {
                repo.registrarCorridaDaOferta(oferta.valor, oferta.km, oferta.minutos, pacote, oferta.enderecos)
                if (idHistorico != null) repo.marcarOfertaRegistrada(idHistorico)
            }
                .onSuccess { overlay?.marcarRegistrada() }
                .onFailure {
                    registradasAgora.remove(oferta.assinatura)
                    RegistroErros.anotar("registrar corrida", it)
                }
        }
    }

    private fun tratarResumo(r: ResumoCorrida) {
        val assinatura = r.assinatura
        if (assinatura == resumoTratado || assinatura in resumosDispensados) return
        resumoTratado = assinatura
        val repo = (application as MotoristaApp).repository
        val zona = ZoneId.systemDefault()
        // Sem a data na tela, a corrida acabou agora: começou "duração" atrás.
        val momento = r.quando?.atZone(zona)?.toInstant()?.toEpochMilli()
            ?: (System.currentTimeMillis() - r.segundos * 1000)
        val hora = Instant.ofEpochMilli(momento).atZone(zona).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        val ehHoje = Instant.ofEpochMilli(momento).atZone(zona).toLocalDate() == LocalDate.now()
        val quando = if (ehHoje) "hoje $hora" else Instant.ofEpochMilli(momento).atZone(zona)
            .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
        escopo.launch {
            runCatching {
                val existente = repo.corridaDoResumo99(r, momento)
                if (existente != null) {
                    cartaoResumo?.mostrarRegistrada(existente.valorCentavos, quando, agora = false)
                } else {
                    cartaoResumo?.mostrarNova(
                        r, quando,
                        onRegistrar = {
                            escopo.launch {
                                runCatching { repo.registrarDoResumo99(r, momento) }
                                    .onSuccess { cartaoResumo?.mostrarRegistrada(r.valorCentavos, quando, agora = true) }
                                    .onFailure {
                                        Log.e(TAG, "Falha ao registrar pelo resumo", it)
                                        cartaoResumo?.esconder()
                                    }
                            }
                        },
                        onAgoraNao = { resumosDispensados += assinatura }
                    )
                }
            }.onFailure { Log.e(TAG, "Falha ao conferir o resumo da 99", it) }
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

    /**
     * Leitura sob encomenda: o motorista segurou o dedo na bolha.
     *
     * Zera a memoria do que ja foi mostrado (senao a mesma oferta nao reaparece)
     * e, se a leitura por texto nao fechar, cai direto na leitura por imagem.
     */
    /** "Pausar turno" / "Retomar turno" do painel da bolha: o mesmo do botão do Início. */
    private fun alternarPausaDoTurno() {
        val repo = (application as MotoristaApp).repository
        escopo.launch {
            runCatching {
                val j = repo.obterJornadaAtiva() ?: return@runCatching
                if (j.pausada) repo.retomarJornada(j.id) else repo.pausarJornada(j.id)
            }.onFailure { Log.e(TAG, "Falha ao pausar pela bolha", it) }
        }
    }

    /**
     * "Encerrar turno" do painel (já confirmado lá). Igual ao botão da notificação:
     * com o GPS rodando, o serviço do turno grava os km e o fim; sem ele, fecha no banco.
     */
    private fun encerrarTurnoPelaBolha() {
        if (JornadaService.rodando.value) {
            JornadaService.parar(this)
            return
        }
        val repo = (application as MotoristaApp).repository
        escopo.launch {
            runCatching { repo.obterJornadaAtiva()?.let { repo.encerrarJornada(it.id) } }
                .onFailure { Log.e(TAG, "Falha ao encerrar pela bolha", it) }
        }
    }

    fun lerAgora() {
        assinaturaDispensada = null
        ultimaAssinatura = null
        varredurasVazias = 0
        ocrVaziosSeguidos = 0
        // A leitura agora termina depois (a coleta roda fora da thread principal):
        // a leitura por imagem, se o texto não achar oferta, vem no fim dela.
        ocrSeNaoAchar = true
        varrerTelaComSeguranca()
    }

    /** Usado pelo botão "Testar" do Dashboard: mostra uma oferta fictícia por 6 segundos. */
    fun mostrarTeste() {
        val ov = overlay ?: return
        val lim = limites ?: return
        val o = Oferta(
            valor = 18.40, km = 11.0, minutos = 26,
            enderecos = listOf("Av. Paulista, 1578 - São Paulo"),
            nota = 4.82
        )
        val classe = lim.classificar(o, tarifaMinimaCentavos, custoKmCentavos, reguaAtual())
        val alerta = risco?.alerta(o.enderecos)
        ov.mostrar(
            o, classe, custoKmCentavos, duracaoMs = 6_000, alertaRisco = alerta,
            sugestao = lim.valorParaFicarBoa(o, tarifaMinimaCentavos, custoKmCentavos, reguaAtual())
        )
        voz?.falar(o, classe, alerta)
    }

    /**
     * "Testar com print" ▸ "Ver no cartão": a oferta lida do print no cartão
     * de verdade, por 8 segundos. Não entra no histórico nem fala.
     * false = leitor desligado (o cartão mora nele).
     */
    fun mostrarOfertaDoPrint(o: Oferta, pacote: String?): Boolean {
        val ov = overlay ?: return false
        val lim = limites ?: return false
        val classe = lim.classificar(o, tarifaMinimaCentavos, custoKmCentavos, reguaAtual())
        val sugestao = if (pacote == OfertaInDrive.PACOTE) {
            lim.valorParaFicarBoa(o, tarifaMinimaCentavos, custoKmCentavos, reguaAtual())
        } else {
            null
        }
        ov.mostrar(
            o, classe, custoKmCentavos, duracaoMs = 8_000,
            alertaRisco = risco?.alerta(o.enderecos), sugestao = sugestao
        )
        return true
    }

    companion object {
        private const val TAG = "MotoristaPro"
        private const val DEBOUNCE_MS = 120L

        /** Mesmo com eventos sem parar, a tela é lida pelo menos a cada 0,4 s. */
        private const val ESPERA_MAXIMA_MS = 400L

        /** App que mexeu nos últimos 15 s conta como "ligado" para a leitura por imagem. */
        private const val JANELA_APPS_ATIVOS_MS = 15_000L
        private const val MAX_PROFUNDIDADE = 80
        private const val MAX_TEXTOS = 1500

        /** Tempo máximo de uma coleta da árvore (as duas leituras juntas). */
        private const val ORCAMENTO_COLETA_MS = 1_500L

        /** Corte de cada texto lido: oferta nenhuma tem mais do que isto numa linha. */
        private const val MAX_CARACTERES_TEXTO = 300
        // Conta a partir da última vez que a oferta foi VISTA, não da primeira:
        // a mesma oferta parada na tela não duplica, mas outra corrida igual
        // (mesmo valor, km e tempo) que chega um minuto depois é outra corrida.
        private const val JANELA_DUPLICADA_MS = 60 * 1000L
        /**
         * Intervalo mínimo entre duas capturas de tela.
         *
         * O Android recusa prints a menos de 1 segundo um do outro
         * (ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT). Com os 800 ms de antes, a
         * segunda captura de cada rajada de eventos batia nesse limite e falhava
         * calada — e a 99, que só é lida por imagem, simplesmente não aparecia.
         */
        private const val INTERVALO_OCR_MS = 1_200L

        /** Teto da espera entre leituras por imagem sem oferta. */
        private const val INTERVALO_OCR_MAX_MS = 3_000L

        /** Capturas que falham seguidas antes de parar de insistir (até o próximo evento da tela). */
        private const val MAX_FALHAS_OCR = 4

        /** Quanto tempo "precisa de imagem" vale para um app depois de uma leitura por imagem. */
        private const val VALIDADE_OCR_MS = 10 * 60 * 1000L

        /** A mesma oferta registrada de novo dentro disto é toque duplo. */
        private const val JANELA_REGISTRO_MS = 2 * 60 * 1000L
        private const val VALIDADE_LISTA_MS = 10 * 60 * 1000L
        private const val MAX_CORRIDAS_LISTA = 40

        /** Loga todos os textos lidos (para calibrar as Regex). Mude para false na versão final. */
        val LOG_TEXTOS = com.motoristapro.BuildConfig.DEBUG

        const val PACOTE_IFOOD = EscolhaOferta.PACOTE_IFOOD

        /** Mantenha em sincronia com android:packageNames em accessibility_service_config.xml. */
        /**
         * Apps onde chega FOTO de oferta (print mandado no grupo, salvo na
         * galeria). Lidos só pela imagem e só com a foto aberta. De propósito
         * NÃO é "todos os apps": ler a tela de qualquer app inclui o banco.
         * Mantenha em sincronia com accessibility_service_config.xml.
         */
        val PACOTES_FOTO = setOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "com.facebook.katana", "com.facebook.lite",
            "com.instagram.android",
            "org.telegram.messenger",
            "com.miui.gallery", "com.google.android.apps.photos",
            "com.sec.android.gallery3d", "com.android.gallery3d", "com.coloros.gallery3d",
            "com.oneplus.gallery", "com.motorola.gallery"
        )

        /** Tela de foto aberta tem poucos textos (botões); conversa ou feed tem muitos. */
        private const val MAX_TEXTOS_FOTO = 25

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
    /** Quando não reconheceu: em uma frase, o que faltou (hoje só o inDrive explica). */
    val motivo: String? = null,
    /** "texto" (acessibilidade) ou "imagem (OCR)". */
    val origem: String = "texto"
)
