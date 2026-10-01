package com.motoristapro.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.MainActivity
import kotlin.math.abs

/**
 * Bolha flutuante: lucro de hoje + cronômetro do turno, sempre à mão por cima da Uber/99.
 * Arrastar move (e gruda na borda mais próxima); toque abre o MotoristaPro.
 * Mesmo tipo de janela do overlay de ofertas (TYPE_ACCESSIBILITY_OVERLAY).
 */
class BolhaFlutuante(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var raiz: LinearLayout? = null
    private lateinit var tvLucro: TextView
    private lateinit var tvTempo: TextView

    private var lucroCentavos: Long = 0
    private var inicioJornada: Long? = null

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

    fun atualizar(lucroCentavos: Long, inicioJornada: Long?) {
        this.lucroCentavos = lucroCentavos
        this.inicioJornada = inicioJornada
        renderizar()
    }

    fun liberar() {
        esconder()
        handler.removeCallbacksAndMessages(null)
        raiz = null
    }

    private fun renderizar() {
        val v = raiz ?: return
        tvLucro.text = lucroCentavos.emReais().replace(",00", "")
        val inicio = inicioJornada
        if (inicio != null) {
            val min = ((System.currentTimeMillis() - inicio) / 60_000).coerceAtLeast(0)
            tvTempo.text = "⏱ %dh%02d".format(min / 60, min % 60)
        } else {
            tvTempo.text = "turno parado"
        }
        (v.background as? GradientDrawable)?.setColor(
            if (lucroCentavos >= 0) COR_FUNDO else COR_FUNDO_NEGATIVO
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun criarView(): LinearLayout {
        tvLucro = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(COR_LIMA)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        tvTempo = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0xFFE8F0E9.toInt())
            gravity = Gravity.CENTER
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(COR_FUNDO)
                setStroke(dp(2), COR_LIMA)
            }
            addView(tvLucro)
            addView(tvTempo)

            var xInicial = 0
            var yInicial = 0
            var toqueX = 0f
            var toqueY = 0f
            var moveu = false
            setOnTouchListener { view, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        xInicial = params.x; yInicial = params.y
                        toqueX = ev.rawX; toqueY = ev.rawY
                        moveu = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (ev.rawX - toqueX).toInt()
                        val dy = (ev.rawY - toqueY).toInt()
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) moveu = true
                        params.x = (xInicial + dx).coerceAtLeast(0)
                        params.y = (yInicial + dy).coerceAtLeast(0)
                        if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, params) }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moveu) {
                            // Gruda na borda mais próxima.
                            val largura = context.resources.displayMetrics.widthPixels
                            params.x = if (params.x + view.width / 2 < largura / 2) 0 else largura - view.width
                            if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, params) }
                        } else {
                            abrirApp()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun abrirApp() {
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
        val COR_LIMA = 0xFFA3E635.toInt()
        val COR_FUNDO = 0xEE0F1410.toInt()
        val COR_FUNDO_NEGATIVO = 0xEE3A1212.toInt()
    }
}
