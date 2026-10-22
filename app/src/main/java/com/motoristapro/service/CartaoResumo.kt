package com.motoristapro.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.motoristapro.R
import android.widget.TextView
import java.util.Locale

/**
 * O cartão que aparece em cima da tela "Detalhes da corrida" da 99.
 *
 * Dois jeitos: a corrida não está no app (com Registrar / Agora não) ou já
 * está (só um "Já está registrada ✓" que some sozinho). Mesma janela de
 * acessibilidade do cartão da oferta, mas embaixo, onde a 99 não tem nada
 * importante nessa tela.
 */
class CartaoResumo(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val esconderRunnable = Runnable { esconder() }
    private val ptBr = Locale("pt", "BR")
    private var raiz: View? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = dp(56)
    }

    val visivel: Boolean get() = raiz?.isAttachedToWindow == true

    /** A corrida não está no app: oferece registrar. */
    fun mostrarNova(r: ResumoCorrida, hora: String, onRegistrar: () -> Unit, onAgoraNao: () -> Unit) {
        val km = r.metros / 1000.0
        val chips = buildList {
            if (r.metros > 0) add(String.format(ptBr, "%.1f km", km))
            if (r.segundos > 0) add("${(r.segundos + 30) / 60} min")
            if (r.metros > 0) add(String.format(ptBr, "R$ %.2f/km", r.valorCentavos / 100.0 / km))
        }.joinToString("  ·  ")

        lateinit var btRegistrar: TextView
        val cartao = cartao().apply {
            addView(linha(
                titulo = "Essa corrida não está no app",
                sub = "99${r.tipo?.let { " $it" } ?: ""} · $hora",
                valor = reais(r.valorCentavos)
            ))
            addView(texto(13f, false, AZUL).apply { text = chips; setPadding(0, dp(8), 0, dp(10)) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                btRegistrar = botao("Registrar", AZUL, ESCURO) {
                    btRegistrar.isEnabled = false
                    onRegistrar()
                }
                addView(btRegistrar)
                addView(botao("Agora não", CINZA_FUNDO, CINZA_TEXTO) {
                    esconder()
                    onAgoraNao()
                })
            })
        }
        exibir(cartao, duracaoMs = 45_000)
    }

    /** Depois de tocar em Registrar, ou quando ela já estava no app. */
    fun mostrarRegistrada(valorCentavos: Long, hora: String, agora: Boolean) {
        val cartao = cartao().apply {
            addView(linha(
                titulo = if (agora) "Corrida registrada" else "Já estava registrada",
                sub = "${reais(valorCentavos)} · $hora · conta no lucro do dia",
                valor = null,
                corSub = AZUL
            ))
        }
        exibir(cartao, duracaoMs = 4_000)
    }

    fun esconder() {
        handler.removeCallbacks(esconderRunnable)
        val v = raiz ?: return
        if (v.isAttachedToWindow) runCatching { wm.removeViewImmediate(v) }
            .onFailure { Log.w(TAG, "Falha ao remover cartão do resumo", it) }
        raiz = null
    }

    fun liberar() {
        esconder()
        handler.removeCallbacksAndMessages(null)
    }

    // ------------------------------------------------------------------ view

    private fun exibir(v: View, duracaoMs: Long) {
        esconder()
        // A janela é da largura da tela: o recuo dos lados fica num embrulho.
        val embrulho = FrameLayout(context).apply {
            setPadding(dp(12), 0, dp(12), 0)
            addView(v)
        }
        try {
            wm.addView(embrulho, params)
            raiz = embrulho
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao mostrar cartão do resumo", e)
            return
        }
        handler.postDelayed(esconderRunnable, duracaoMs)
    }

    private fun cartao() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(FUNDO)
            setStroke(dp(1), CONTORNO)
        }
        elevation = dp(8).toFloat()
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun linha(titulo: String, sub: String, valor: String?, corSub: Int = CINZA_TEXTO) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply {
                setImageResource(R.drawable.logo_99)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(AMARELO_99) }
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(texto(15f, true, BRANCO).apply { text = titulo })
                addView(texto(12f, false, corSub).apply { text = sub })
            })
            if (valor != null) addView(texto(22f, true, BRANCO).apply { text = valor })
        }

    private fun texto(sp: Float, negrito: Boolean, cor: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(cor)
        if (negrito) typeface = Typeface.DEFAULT_BOLD
    }

    private fun botao(rotulo: String, fundo: Int, cor: Int, onClick: () -> Unit) = TextView(context).apply {
        text = rotulo
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(cor)
        gravity = Gravity.CENTER
        setPadding(0, dp(13), 0, dp(13))
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(fundo) }
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(dp(4), 0, dp(4), 0) }
    }

    private fun reais(c: Long) = String.format(ptBr, "R$ %.2f", c / 100.0)

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "MotoristaPro"
        const val FUNDO = 0xFF121212.toInt()
        const val CONTORNO = 0xFF2B323B.toInt()
        const val AZUL = 0xFFFFFFFF.toInt()   // destaque branco (5.0; nome mantido)
        const val ESCURO = 0xFF000000.toInt()
        const val BRANCO = 0xFFFFFFFF.toInt()
        const val CINZA_FUNDO = 0xFF1C1C1E.toInt()
        const val CINZA_TEXTO = 0xFF8B949E.toInt()
        const val AMARELO_99 = 0xFFFFD400.toInt()
    }
}
