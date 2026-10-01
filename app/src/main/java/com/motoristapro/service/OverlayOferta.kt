package com.motoristapro.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    private val esconderRunnable = Runnable { esconder() }
    private val ptBr = Locale("pt", "BR")

    private var raiz: LinearLayout? = null
    private lateinit var tvTitulo: TextView
    private lateinit var tvPrincipal: TextView
    private lateinit var tvDetalhe: TextView
    private lateinit var btRegistrar: TextView
    private lateinit var btLocal: TextView

    /** Oferta exibida no momento (alvo dos botões). */
    private var ofertaAtual: Oferta? = null

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

    val visivel: Boolean get() = raiz?.isAttachedToWindow == true

    /**
     * Mostra (ou atualiza, se já estiver na tela) o resultado da oferta.
     * @param custoKmCentavos custo/km da Configuração; 0 = não mostra lucro estimado.
     * @param duracaoMs tempo até esconder sozinho.
     */
    fun mostrar(
        o: Oferta,
        classe: Classificacao,
        custoKmCentavos: Long,
        duracaoMs: Long = 15_000,
        totalNaTela: Int = 1
    ) {
        val v = raiz ?: criarView().also { raiz = it }

        (v.background as? GradientDrawable)?.setColor(classe.corFundo)
        val sufixo = if (totalNaTela > 1) "   (melhor de $totalNaTela)" else ""
        tvTitulo.text = "${classe.rotulo}  •  ${moeda(o.valor)}$sufixo"
        tvPrincipal.text = if (o.minutos > 0) "${moeda(o.reaisPorKm)}/km    ${moeda(o.reaisPorHora)}/h"
        else "${moeda(o.reaisPorKm)}/km"

        val partes = mutableListOf(String.format(ptBr, "%.1f km", o.km))
        if (o.minutos > 0) {
            partes += "${o.minutos} min"
            partes += "${moeda(o.reaisPorMinuto)}/min"
        }
        if (o.paradas > 1) partes += "${o.paradas} entregas • ${moeda(o.valor / o.paradas)} cada"
        if (custoKmCentavos > 0) partes += "lucro ≈ ${moeda(o.lucroEstimado(custoKmCentavos))}"
        if (o.devolucao) partes += "⚠ pode ter devolução"
        tvDetalhe.text = partes.joinToString("  •  ")

        ofertaAtual = o
        btRegistrar.text = "✓ Registrar corrida"
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
        handler.postDelayed(esconderRunnable, duracaoMs)
    }

    /** Feedback visual depois que a corrida foi gravada. */
    fun marcarRegistrada() {
        if (!::btRegistrar.isInitialized) return
        btRegistrar.text = "Registrada ✓"
        btRegistrar.isEnabled = false
        handler.removeCallbacks(esconderRunnable)
        handler.postDelayed(esconderRunnable, 2_500)
    }

    /** Remove a janela da tela (seguro chamar várias vezes). */
    fun esconder() {
        handler.removeCallbacks(esconderRunnable)
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

    // ------------------------------------------------------------------ view

    /** View montada em código: não depende de layouts XML nem de tema. */
    @SuppressLint("ClickableViewAccessibility")
    private fun criarView(): LinearLayout {
        tvTitulo = texto(15f, negrito = true)
        tvPrincipal = texto(24f, negrito = true)
        tvDetalhe = texto(14f, negrito = false)
        btRegistrar = botao("✓ Registrar corrida") { ofertaAtual?.let { aoRegistrar?.invoke(it) } }
        btLocal = botao("📍 Ver embarque") {
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
            addView(linhaBotoes)

            // Arrastar na vertical reposiciona; toque simples fecha.
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
                        if (abs(dy) > dp(6)) moveu = true
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
    }
}
