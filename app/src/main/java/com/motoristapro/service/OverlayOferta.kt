package com.motoristapro.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

/**
 * Gerenciador da janela flutuante que mostra R$/km e R$/h por cima da Uber/99.
 *
 * Tipo de janela: TYPE_ACCESSIBILITY_OVERLAY
 *  - exclusiva de serviços de acessibilidade, dispensa a permissão "Sobrepor a outros apps";
 *  - fica acima dos próprios pop-ups de oferta (que já são overlays).
 *  - Só funciona com o Context do AccessibilityService conectado (passe `this` no serviço).
 *
 * Flags:
 *  - FLAG_NOT_FOCUSABLE    -> não rouba foco nem teclado; o motorista continua usando a Uber/99.
 *  - FLAG_NOT_TOUCH_MODAL  -> toques fora da janela passam para o app de baixo.
 *  - FLAG_LAYOUT_IN_SCREEN -> posiciona em relação à tela inteira.
 *
 * Cores: verde (BOA), amarelo (MÉDIA), vermelho (RUIM) — vêm de [Classificacao].
 * Toda operação no WindowManager é protegida: uma falha no overlay nunca derruba o serviço.
 * Deve ser chamado na main thread (o AccessibilityService já entrega eventos nela).
 */
class OverlayOferta(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** O que o motorista escolheu em Mais > Estilo do cartao (relido a cada oferta). */
    private val estilo = EstiloCartao(context)

    /**
     * As faixas do motorista, para pintar cada número na sua própria cor.
     * Entregue pelo serviço; null = cartão de uma cor só, como antes.
     */
    var limites: LimitesOferta? = null

    /** Meta e medidas do motorista (régua do lucro por hora); quem mostra o cartão atualiza. */
    var regua: ParametrosRegua? = null
    private val handler = Handler(Looper.getMainLooper())
    private val esconderRunnable = Runnable { esconder() }
    private val ptBr = Locale("pt", "BR")

    private var raiz: LinearLayout? = null
    private lateinit var tvTitulo: TextView
    private lateinit var tvPrincipal: TextView
    private lateinit var tvDetalhe: TextView
    private lateinit var tvRisco: TextView
    private lateinit var tvSugestao: TextView
    private lateinit var btRegistrar: TextView
    private lateinit var btLocal: TextView

    /** Oferta exibida no momento (alvo dos botões). */
    private var ofertaAtual: Oferta? = null

    /** O motorista já arrastou o cartão com o dedo? Se sim, a posição dele manda. */
    private var arrastado = false

    /** Botão "Registrar corrida": o serviço grava no banco. */
    var aoRegistrar: ((Oferta) -> Unit)? = null

    /** Botão "Ver embarque": o serviço abre o Street View/Maps no endereço. */
    var aoVerLocal: ((String) -> Unit)? = null

    /** Chamado quando o motorista toca para fechar (o serviço usa para não reabrir a mesma oferta). */
    var aoDispensar: (() -> Unit)? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = dp(40)                                   // abaixo da status bar
    }

    val visivel: Boolean get() = raiz?.isAttachedToWindow == true || raizNova?.isAttachedToWindow == true

    /** O cartão branco (tema NOVO): montado de novo a cada oferta. */
    private var raizNova: View? = null
    private var btRegistrarNovo: TextView? = null

    /**
     * Mostra (ou atualiza, se já estiver na tela) o resultado da oferta.
     * @param custoKmCentavos custo/km da Configuração; 0 = não mostra lucro estimado.
     * @param duracaoMs tempo até esconder sozinho.
     */
    fun mostrar(
        o: Oferta,
        classe: Classificacao,
        custoKmCentavos: Long,
        duracaoMs: Long? = null,
        totalNaTela: Int = 1,
        alertaRisco: String? = null,
        sugestao: SugestaoDeTarifa? = null
    ) {
        val cfg = estilo.ler()
        if (cfg.tema == TemaCartao.NOVO || cfg.tema == TemaCartao.ESCURO) {
            removerAntigo()
            mostrarNovo(o, classe, custoKmCentavos, cfg, duracaoMs, totalNaTela, alertaRisco, sugestao)
            return
        }
        removerNovo()
        val v = raiz ?: criarView().also { raiz = it }

        aplicarEstilo(v, cfg, classe)

        val sufixo = if (totalNaTela > 1) "   (melhor de $totalNaTela)" else ""
        val valorNaFaixa = if (cfg.mostrarApp) "  •  ${moeda(o.valor)}" else ""
        tvTitulo.text = "${classe.rotulo}$valorNaFaixa$sufixo"
        tvTitulo.visibility = if (cfg.avisoEmDestaque) View.VISIBLE else View.GONE

        // Os tres primeiros campos escolhidos vão na linha grande (o que decide a
        // corrida de relance); o resto desce para o rodapé, na mesma ordem.
        val escolhidos = cfg.camposComValor(o, custoKmCentavos)
        tvPrincipal.text = linhaPrincipal(escolhidos.take(3), o, cfg, custoKmCentavos)

        val partes = escolhidos.drop(3).map { it.second }.toMutableList()
        linhaRegua(o)?.let { partes.add(0, it) }
        if (o.paradas > 1 && cfg.campos.none { it == CampoCartao.PARADAS }) {
            partes += "${o.paradas} paradas • ${moeda(o.valor / o.paradas)} cada"
        }
        // Devolução nunca é opcional: é risco de trabalhar de graça.
        if (o.devolucao) partes += "Pode ter devolução"
        tvDetalhe.text = partes.joinToString("  •  ")
        tvDetalhe.visibility = if (partes.isEmpty()) View.GONE else View.VISIBLE

        // "Peça R$ X": no inDrive o motorista toca no botão de contraproposta
        // logo abaixo; nos outros apps serve para ele saber o tamanho do buraco.
        if (sugestao == null) {
            tvSugestao.visibility = View.GONE
        } else {
            tvSugestao.text = "\u2191 Peça ${moeda(sugestao.valorSugerido)} " +
                "(+${moeda(sugestao.aumento)}) e vira BOA"
            tvSugestao.visibility = View.VISIBLE
        }

        // Faixa de risco: só aparece quando o endereço bate com a lista do motorista.
        if (alertaRisco.isNullOrBlank()) {
            tvRisco.visibility = View.GONE
        } else {
            tvRisco.text = "ÁREA DE RISCO: $alertaRisco"
            tvRisco.visibility = View.VISIBLE
        }

        ofertaAtual = o
        btRegistrar.text = "Registrar corrida"
        btRegistrar.isEnabled = true
        btLocal.visibility = if (o.enderecos.isNotEmpty()) View.VISIBLE else View.GONE

        try {
            if (v.isAttachedToWindow) wm.updateViewLayout(v, params) else wm.addView(v, params)
        } catch (e: Exception) {
            // Ex.: serviço desconectado (BadTokenException) ou view em estado inválido.
            Log.e(TAG, "Falha ao exibir overlay", e)
            return
        }

        handler.removeCallbacks(esconderRunnable)
        handler.postDelayed(esconderRunnable, duracaoMs ?: cfg.duracaoMs)
    }

    /**
     * Pinta e posiciona o cartão conforme a [ConfigCartao].
     *
     * Feito a cada oferta (e não na criação da view) porque o motorista pode ter
     * mudado o estilo entre uma corrida e outra, sem reiniciar o serviço.
     */
    /**
     * A linha grande, com cada número na cor da SUA faixa.
     *
     * A cor do cartão continua sendo a da pior métrica — é ela que manda aceitar
     * ou recusar. Mas dentro dela, ver que o R$/km está verde e o R$/hora
     * vermelho diz na hora o que está errado: a corrida paga bem o quilômetro e
     * mal o tempo, que é outra decisão (pegar quando o trânsito abrir).
     *
     * Só no tema escuro/claro: no colorido o fundo já é o semáforo, e texto
     * verde sobre fundo verde não se lê.
     */
    /**
     * "Lucro R$ 36/h · meta R$ 42/h · estimado": a conta da régua em uma linha.
     * "estimado" enquanto o app ainda usa o padrão (60% do turno com corrida,
     * 15 km/h) em vez do que mediu do motorista.
     */
    private fun linhaRegua(o: Oferta): String? {
        val a = regua?.let { ReguaLucroHora.avaliar(o, it) } ?: return null
        return buildString {
            append("Lucro ${moedaInteira(a.lucroPorHoraReais)}/h · meta ${moedaInteira(a.alvoPorHoraReais)}/h")
            if (a.tempoPeloKm) append(" · tempo pelo km")
            if (a.estimado) append(" · estimado")
            if (regua?.custoBasico == true) append(" · custo básico")
        }
    }

    private fun moedaInteira(v: Double): String = "R$ " + Math.round(v)

    private fun linhaPrincipal(
        campos: List<Pair<CampoCartao, String>>,
        o: Oferta,
        cfg: ConfigCartao,
        custoKmCentavos: Long
    ): CharSequence {
        val juntos = campos.joinToString("   ") { it.second }
        val lim = limites ?: return juntos
        if (cfg.tema == TemaCartao.COLORIDO) return juntos

        val texto = SpannableStringBuilder()
        campos.forEachIndexed { i, (campo, valor) ->
            if (i > 0) texto.append("   ")
            val inicio = texto.length
            texto.append(valor)
            val faixa = lim.faixaDoCampo(campo, o, custoKmCentavos, regua) ?: return@forEachIndexed
            texto.setSpan(
                ForegroundColorSpan(faixa.corFundo or 0xFF000000.toInt()),
                inicio, texto.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return texto
    }

    private fun aplicarEstilo(v: View, cfg: ConfigCartao, classe: Classificacao) {
        val fundo = when (cfg.tema) {
            TemaCartao.COLORIDO -> classe.corFundo
            TemaCartao.NOVO -> 0xFFFFFFFF.toInt()
            TemaCartao.ESCURO -> 0xF20E1216.toInt()
            TemaCartao.CLARO -> 0xF5F2F4F7.toInt()
        }
        (v.background as? GradientDrawable)?.setColor(fundo)

        val corTexto = if (cfg.tema == TemaCartao.CLARO) 0xFF101720.toInt() else 0xFFFFFFFF.toInt()
        // No tema escuro/claro a faixa perde o fundo colorido, então a COR DELA passa
        // a ser o semáforo — senão o motorista perde a decisão de relance.
        val corFaixa = if (cfg.tema == TemaCartao.COLORIDO) corTexto else classe.corFundo or 0xFF000000.toInt()
        tvTitulo.setTextColor(corFaixa)
        tvPrincipal.setTextColor(corTexto)
        tvDetalhe.setTextColor(corTexto)
        btRegistrar.setTextColor(corTexto)
        btLocal.setTextColor(corTexto)

        val e = cfg.tamanho.escala
        tvTitulo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * e)
        tvPrincipal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * e)
        tvDetalhe.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * e)
        tvRisco.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * e)
        tvSugestao.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * e)
        tvSugestao.setTextColor(corTexto)
        btRegistrar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * e)
        btLocal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * e)

        v.alpha = cfg.opacidade / 100f

        posicionar(cfg)
    }

    /** Posição escolhida no estilo — só se o motorista não tiver arrastado o cartão com o dedo. */
    private fun posicionar(cfg: ConfigCartao) {
        if (!arrastado) {
            when (cfg.posicao) {
                PosicaoCartao.TOPO -> {
                    params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    params.y = dp(40)
                }
                PosicaoCartao.CENTRO -> {
                    params.gravity = Gravity.CENTER
                    params.y = 0
                }
                PosicaoCartao.BAIXO -> {
                    params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    params.y = dp(90)
                }
            }
        }
    }

    /** Feedback visual depois que a corrida foi gravada. */
    fun marcarRegistrada() {
        btRegistrarNovo?.let {
            it.text = "Registrada"
            it.isEnabled = false
        }
        if (::btRegistrar.isInitialized) {
            btRegistrar.text = "Registrada"
            btRegistrar.isEnabled = false
        }
        handler.removeCallbacks(esconderRunnable)
        handler.postDelayed(esconderRunnable, 2_500)
    }

    /** Remove a janela da tela (seguro chamar várias vezes). */
    fun esconder() {
        handler.removeCallbacks(esconderRunnable)
        removerNovo()
        removerAntigo()
    }

    private fun removerNovo() {
        val v = raizNova ?: return
        if (v.isAttachedToWindow) {
            runCatching { wm.removeViewImmediate(v) }.onFailure { Log.w(TAG, "Falha ao remover cartão", it) }
        }
        raizNova = null
        btRegistrarNovo = null
    }

    private fun removerAntigo() {
        val v = raiz ?: return
        if (v.isAttachedToWindow) {
            try {
                wm.removeViewImmediate(v)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao remover overlay", e)
            }
        }
    }

    /** Libera tudo; chame no onDestroy do serviço. */
    fun liberar() {
        esconder()
        handler.removeCallbacksAndMessages(null)
        raiz = null
        aoDispensar = null
        aoRegistrar = null
        aoVerLocal = null
        ofertaAtual = null
    }

    // ------------------------------------------------------------ cartão branco

    /**
     * O cartão do visual 4.7: etiqueta ACEITAR / ATENÇÃO / RECUSAR em cima, e
     * embaixo os 3 números que decidem a corrida, cada um com a barrinha na cor
     * da SUA faixa. Verde/amarelo/vermelho de verdade aqui (não o azul do app):
     * é o que o olho do motorista já lê como "pode ir / cuidado / não".
     */
    private fun mostrarNovo(
        o: Oferta,
        classe: Classificacao,
        custoKmCentavos: Long,
        cfg: ConfigCartao,
        duracaoMs: Long?,
        totalNaTela: Int,
        alertaRisco: String?,
        sugestao: SugestaoDeTarifa?
    ) {
        removerNovo()
        val e = cfg.tamanho.escala
        val cor = cfg.paleta.cor(classe)
        // Escuro: o mesmo cartão em fundo escuro. Os dois são compactos (4.9.1),
        // do tamanho do conteúdo e não da tela, com a etiqueta ACEITAR/ATENÇÃO/
        // RECUSAR em cima — o motorista lê a decisão antes dos números.
        val escuro = cfg.tema == TemaCartao.ESCURO
        val fundo = if (escuro) 0xFF121212.toInt() else BRANCO
        val corNumero = if (escuro) BRANCO else ESCURO

        val etiqueta = TextView(context).apply {
            text = when (classe) {
                Classificacao.BOA -> "ACEITAR"
                Classificacao.MEDIA -> "ATENÇÃO"
                Classificacao.RUIM -> "RECUSAR"
            }
            setTextColor(BRANCO)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * e)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(r(14), r(14), r(14), r(14), r(14), r(14), r(4), r(4))
                setColor(cor)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), 0, 0, dp(-2)) }
        }

        val melhorDe = if (totalNaTela > 1) "  ·  melhor de $totalNaTela" else ""
        val topo = TextView(context).apply {
            text = buildString {
                append(moeda(o.valor))
                if (o.minutos > 0) append("  ·  ${o.minutos} min")
                append(String.format(ptBr, "  ·  %.1f km", o.km))
                append(melhorDe)
            }
            setTextColor(corNumero)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * e)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(5))
        }

        val metricas = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(2))
            cfg.numeros(o, custoKmCentavos).forEach { n ->
                val faixa = limites?.faixaDoCampo(n.campo, o, custoKmCentavos, regua)
                addView(metrica(n.rotulo, n.valor, faixa?.let { cfg.paleta.cor(it) } ?: CINZA, e, corNumero, escuro))
            }
        }

        val avisos = buildList {
            linhaRegua(o)?.let { add(it) }
            if (o.paradas > 1) add("${o.paradas} paradas · ${moeda(o.valor / o.paradas)} cada")
            if (o.devolucao) add("Pode ter devolução")
            sugestao?.let { add("Peça ${moeda(it.valorSugerido)} (+${moeda(it.aumento)}) e vira boa") }
        }

        val caixa = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = r(14)
                setColor(fundo)
                setStroke(dp(3), cor)
            }
            addView(topo)
            addView(View(context).apply {
                setBackgroundColor(if (escuro) 0xFF262D36.toInt() else 0xFFEDEFF2.toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            })
            addView(metricas)
            if (!alertaRisco.isNullOrBlank()) {
                addView(faixaAviso("ÁREA DE RISCO: $alertaRisco", cfg.paleta.ruim, BRANCO, e))
            }
            if (avisos.isNotEmpty()) {
                addView(texto(12f * e, false).apply {
                    text = avisos.joinToString("   ")
                    setTextColor(if (escuro) 0xFFB0B7C0.toInt() else 0xFF4A5260.toInt())
                    gravity = Gravity.START
                    setPadding(0, dp(2), 0, dp(4))
                })
            }
            // Botões só para quem ligou em Aparência do cartão: sem eles o cartão
            // fica do tamanho dos números (e a notificação tem os mesmos atalhos).
            if (cfg.botoes) addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
                val reg = chip("Registrar", e, escuro) { ofertaAtual?.let { aoRegistrar?.invoke(it) } }
                btRegistrarNovo = reg
                addView(reg)
                o.enderecos.firstOrNull()?.let { emb -> addView(chip("Embarque", e, escuro) { aoVerLocal?.invoke(emb) }) }
                o.enderecos.takeIf { it.size > 1 }?.lastOrNull()?.let { dest ->
                    addView(chip("Destino", e, escuro) { aoVerLocal?.invoke(dest) })
                }
            })
        }

        // A janela é da largura da tela; o cartão fica centrado e do tamanho do conteúdo.
        val bloco = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            alpha = cfg.opacidade / 100f
            addView(etiqueta)
            addView(caixa, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val v = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), 0, dp(12), 0)
            addView(bloco, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        tornarArrastavel(v)
        posicionar(cfg)
        ofertaAtual = o

        try {
            wm.addView(v, params)
            raizNova = v
        } catch (ex: Exception) {
            Log.e(TAG, "Falha ao exibir o cartão", ex)
            return
        }
        handler.removeCallbacks(esconderRunnable)
        handler.postDelayed(esconderRunnable, duracaoMs ?: cfg.duracaoMs)
    }

    private fun metrica(rotulo: String, valor: String, cor: Int, e: Float, corValor: Int, escuro: Boolean) =
        LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, dp(16), 0) }
        addView(TextView(context).apply {
            text = rotulo
            setTextColor(if (escuro) 0xFF9AA3AE.toInt() else 0xFF5A6270.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * e)
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(View(context).apply {
                background = GradientDrawable().apply { cornerRadius = r(2); setColor(cor) }
                layoutParams = LinearLayout.LayoutParams(dp(3), dp((20 * e).toInt())).apply { setMargins(0, 0, dp(4), 0) }
            })
            addView(TextView(context).apply {
                text = valor
                setTextColor(corValor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * e)
                typeface = Typeface.DEFAULT_BOLD
            })
        })
    }

    private fun chip(rotulo: String, e: Float, escuro: Boolean, onClick: () -> Unit) = TextView(context).apply {
        text = rotulo
        setTextColor(if (escuro) 0xFFE6EAF0.toInt() else 0xFF2E3440.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * e)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(10), dp(7), dp(10), dp(7))
        background = GradientDrawable().apply {
            cornerRadius = r(10)
            setColor(if (escuro) 0xFF1F262E.toInt() else 0xFFF0F2F5.toInt())
        }
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, dp(6), 0) }
    }

    private fun faixaAviso(t: String, fundo: Int, corTexto: Int, e: Float) = TextView(context).apply {
        text = t
        setTextColor(corTexto)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * e)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(10), dp(5), dp(10), dp(5))
        background = GradientDrawable().apply { cornerRadius = r(10); setColor(fundo) }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(2), 0, dp(6)) }
    }

    private fun r(v: Int): Float = dp(v).toFloat()

    // ------------------------------------------------------------------ view

    /** View montada em código: não depende de layouts XML nem de tema. */
    @SuppressLint("ClickableViewAccessibility")
    private fun criarView(): LinearLayout {
        // Tamanhos iniciais; aplicarEstilo() reajusta conforme a escolha do motorista.
        tvTitulo = texto(15f, negrito = true)
        tvPrincipal = texto(24f, negrito = true)
        tvDetalhe = texto(14f, negrito = false)
        tvSugestao = texto(15f, negrito = true).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0x33FFFFFF)
            }
            visibility = View.GONE
        }
        tvRisco = texto(14f, negrito = true).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0xCC000000.toInt())
            }
            visibility = View.GONE
        }
        btRegistrar = botao("Registrar corrida") { ofertaAtual?.let { aoRegistrar?.invoke(it) } }
        btLocal = botao("Ver embarque") {
            ofertaAtual?.enderecos?.firstOrNull()?.let { aoVerLocal?.invoke(it) }
        }
        val linhaBotoes = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            addView(btRegistrar)
            addView(btLocal)
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Classificacao.MEDIA.corFundo)
            }
            addView(tvTitulo)
            addView(tvPrincipal)
            addView(tvDetalhe)
            addView(tvSugestao)
            addView(tvRisco)
            addView(linhaBotoes)
        }.also { tornarArrastavel(it) }
    }

    /** Arrastar na vertical reposiciona; toque simples fecha. */
    @SuppressLint("ClickableViewAccessibility")
    private fun tornarArrastavel(alvo: View) {
        alvo.apply {
            var yInicial = 0
            var toqueY = 0f
            var moveu = false
            setOnTouchListener { view, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        yInicial = params.y
                        toqueY = ev.rawY
                        moveu = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = (ev.rawY - toqueY).toInt()
                        if (abs(dy) > dp(6)) {
                            moveu = true
                            arrastado = true
                        }
                        params.y = (yInicial + dy).coerceAtLeast(0)
                        if (view.isAttachedToWindow) {
                            runCatching { wm.updateViewLayout(view, params) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moveu) {
                            esconder()
                            aoDispensar?.invoke()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun texto(sp: Float, negrito: Boolean) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        if (negrito) typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }
    }

    /** Botão compacto com fundo translúcido (os filhos clicáveis recebem o toque antes do arrasto). */
    private fun botao(rotulo: String, onClick: () -> Unit) = TextView(context).apply {
        text = rotulo
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(0xFFFFFFFF.toInt())
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0x33FFFFFF)
        }
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(4), 0, dp(4), 0) }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    /** Formata em reais; valores não finitos (nunca deveriam chegar aqui) viram "—". */
    private fun moeda(v: Double): String =
        if (v.isFinite()) String.format(ptBr, "R$ %.2f", v) else "—"

    private companion object {
        const val TAG = "MotoristaPro"
        const val VERDE = 0xFF22A06B.toInt()
        const val AMARELO = 0xFFE8A800.toInt()
        const val VERMELHO = 0xFFE5484D.toInt()
        const val CINZA = 0xFFB0B7C0.toInt()
        const val BRANCO = 0xFFFFFFFF.toInt()
        const val ESCURO = 0xFF101720.toInt()
    }
}
