package com.motoristapro.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.ui.MainActivity
import kotlin.math.abs

/**
 * Bolha flutuante: lucro de hoje + cronômetro do turno, sempre à mão por cima da Uber/99.
 * Em volta, um anel que enche com a meta do dia: amarelo até bater, verde depois (5.1).
 * Toque abre o painel do turno; segurar lê a tela agora; arrastar muda de lugar
 * (e gruda na borda mais próxima).
 * Mesmo tipo de janela do overlay de ofertas (TYPE_ACCESSIBILITY_OVERLAY).
 */
class BolhaFlutuante(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var raiz: FrameLayout? = null
    private lateinit var anel: AnelMeta
    private lateinit var tvLucro: TextView
    private lateinit var tvTempo: TextView

    private var lucroCentavos: Long = 0
    private var metaCentavos: Long = 0
    private var jornada: Jornada? = null

    private val tick = object : Runnable {
        override fun run() {
            renderizar()
            handler.postDelayed(this, 30_000)
        }
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = dp(220)
    }

    val visivel: Boolean get() = raiz?.isAttachedToWindow == true

    fun mostrar() {
        val v = raiz ?: criarView().also { raiz = it }
        renderizar()
        if (v.isAttachedToWindow) return
        try {
            wm.addView(v, params)
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, 30_000)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao exibir bolha", e)
        }
    }

    fun esconder() {
        handler.removeCallbacks(tick)
        val v = raiz ?: return
        if (v.isAttachedToWindow) runCatching { wm.removeViewImmediate(v) }
    }

    fun atualizar(lucroCentavos: Long, metaCentavos: Long, jornada: Jornada?) {
        this.lucroCentavos = lucroCentavos
        this.metaCentavos = metaCentavos
        this.jornada = jornada
        renderizar()
    }

    fun liberar() {
        esconder()
        handler.removeCallbacksAndMessages(null)
        raiz = null
    }

    private fun renderizar() {
        if (raiz == null) return
        // Reais inteiros: a bolha é pequena e o centavo não muda a decisão.
        tvLucro.text = (lucroCentavos / 100).toString()
        tvLucro.setTextColor(if (lucroCentavos < 0) COR_VERMELHO else COR_BRANCO)
        val j = jornada
        tvTempo.text = when {
            j == null -> "parado"
            j.pausada -> "pausa"
            else -> {
                val min = j.segundosRodando(System.currentTimeMillis()) / 60
                "%d:%02d".format(min / 60, min % 60)
            }
        }
        tvTempo.setTextColor(if (j?.pausada == true) COR_AMARELO else COR_CINZA)
        val fracao = if (metaCentavos > 0) lucroCentavos.toFloat() / metaCentavos else 0f
        anel.definir(fracao.coerceIn(0f, 1f), if (fracao >= 1f) COR_VERDE else COR_AMARELO)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun criarView(): FrameLayout {
        tvLucro = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(COR_BRANCO)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        tvTempo = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(COR_CINZA)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        anel = AnelMeta(context)
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(dp(TAMANHO_DP), dp(TAMANHO_DP))
            minimumWidth = dp(TAMANHO_DP)
            minimumHeight = dp(TAMANHO_DP)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COR_FUNDO)
            }
            addView(anel, FrameLayout.LayoutParams(dp(TAMANHO_DP), dp(TAMANHO_DP)))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(tvLucro)
                    addView(tvTempo)
                },
                FrameLayout.LayoutParams(dp(TAMANHO_DP), dp(TAMANHO_DP))
            )

            var xInicial = 0
            var yInicial = 0
            var toqueX = 0f
            var toqueY = 0f
            var moveu = false
            // Segurar na bolha = "leia esta tela agora". Feito na mao porque o
            // setOnTouchListener devolve true e engole os eventos antes do
            // detector de toque longo do proprio Android.
            var jaLeu = false
            val segurou = Runnable {
                jaLeu = true
                aoSegurar?.invoke()
            }
            setOnTouchListener { view, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        xInicial = params.x; yInicial = params.y
                        toqueX = ev.rawX; toqueY = ev.rawY
                        moveu = false
                        jaLeu = false
                        handler.postDelayed(segurou, TEMPO_SEGURAR_MS)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (ev.rawX - toqueX).toInt()
                        val dy = (ev.rawY - toqueY).toInt()
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                            moveu = true
                            handler.removeCallbacks(segurou)
                        }
                        params.x = (xInicial + dx).coerceAtLeast(0)
                        params.y = (yInicial + dy).coerceAtLeast(0)
                        if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, params) }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(segurou)
                        if (moveu || jaLeu) {
                            // Gruda na borda mais próxima.
                            val largura = context.resources.displayMetrics.widthPixels
                            params.x = if (params.x + view.width / 2 < largura / 2) 0 else largura - view.width
                            if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, params) }
                        } else {
                            // Toque curto abre o MENU, não o app: as ações do
                            // meio do turno estão todas ali e nenhuma delas vale
                            // sair da tela da oferta.
                            val menu = aoTocar
                            if (menu != null) menu() else abrirApp()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(segurou)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /**
     * Chamado quando o motorista SEGURA o dedo na bolha.
     *
     * Serve de plano B do leitor: tem app (e tela) em que a oferta não chega
     * sozinha, e aí ele abre a corrida, segura a bolha e o app lê o que está
     * na tela naquele instante.
     */
    var aoSegurar: (() -> Unit)? = null

    /** Toque curto. Quando ninguém assume, a bolha volta a abrir o app direto. */
    var aoTocar: (() -> Unit)? = null

    fun abrirApp() {
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao abrir o app pela bolha", e)
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "MotoristaPro"
        /** Meio segundo de dedo parado na bolha já conta como "leia agora". */
        const val TEMPO_SEGURAR_MS = 500L
        /** A bolha inteira: 72 dp, o dedo acerta sem cobrir o botão da Uber. */
        const val TAMANHO_DP = 72
        // Regra estrita de cor (5.1): sólidas sobre preto absoluto.
        const val COR_FUNDO = 0xFF000000.toInt()
        const val COR_BRANCO = 0xFFFFFFFF.toInt()
        const val COR_CINZA = 0xFF8E8E93.toInt()
        const val COR_VAZIO = 0xFF2C2C2E.toInt()
        const val COR_VERDE = 0xFF00E676.toInt()
        const val COR_AMARELO = 0xFFFFD600.toInt()
        const val COR_VERMELHO = 0xFFFF1744.toInt()
    }

    /** O anel da meta: trilho cinza-escuro inteiro e, por cima, a parte já feita. */
    private class AnelMeta(context: Context) : View(context) {
        private val traco = 5f * context.resources.displayMetrics.density
        private val trilho = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = traco
            color = COR_VAZIO
        }
        private val feito = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = traco
            strokeCap = Paint.Cap.ROUND
        }
        private val caixa = RectF()
        private var fracao = 0f

        fun definir(fracao: Float, cor: Int) {
            this.fracao = fracao
            feito.color = cor
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val m = traco / 2 + 1
            caixa.set(m, m, width - m, height - m)
            canvas.drawArc(caixa, 0f, 360f, false, trilho)
            if (fracao > 0f) canvas.drawArc(caixa, -90f, 360f * fracao, false, feito)
        }
    }
}
