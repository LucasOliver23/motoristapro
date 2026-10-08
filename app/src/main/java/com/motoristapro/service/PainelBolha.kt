package com.motoristapro.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * O menu que abre ao tocar na bolha.
 *
 * Existe porque a bolha fazia uma coisa só — abrir o app — e as ações que o
 * motorista precisa no meio do turno são justamente as que não compensam abrir
 * o app para fazer: conferir se o leitor está lendo, mandar ler a tela agora,
 * calar a voz no meio de uma corrida com passageiro. O menu nasce colado na
 * bolha e some ao tocar fora, igual a um menu de sistema.
 *
 * Views programáticas, sem XML: é uma janela de overlay do serviço de
 * acessibilidade, e inflar layout aqui dentro obrigaria a carregar um tema do
 * app que o serviço não tem.
 */
class PainelBolha(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var raiz: LinearLayout? = null

    /** Estado consultado na hora de desenhar — o serviço é quem sabe. */
    var leitorLendo: () -> Boolean = { true }
    var jornadaRodando: () -> Boolean = { false }
    var vozLigada: () -> Boolean = { false }

    var aoLerAgora: (() -> Unit)? = null
    var aoAlternarVoz: (() -> Unit)? = null
    var aoAbrirAba: ((Int) -> Unit)? = null

    val visivel: Boolean get() = raiz?.isAttachedToWindow == true

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // NOT_FOCUSABLE continua recebendo toque e não rouba o teclado do app de
        // baixo; WATCH_OUTSIDE_TOUCH é o que faz o menu fechar ao tocar fora.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP
        y = dp(90)
    }

    fun alternar() {
        if (visivel) esconder() else mostrar()
    }

    fun mostrar() {
        esconder()
        val v = montar()
        raiz = v
        try {
            wm.addView(v, params)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao abrir o menu da bolha", e)
            raiz = null
        }
    }

    fun esconder() {
        val v = raiz ?: return
        if (v.isAttachedToWindow) runCatching { wm.removeViewImmediate(v) }
        raiz = null
    }

    fun liberar() = esconder()

    // ------------------------------------------------------------------ montagem

    private fun montar(): LinearLayout {
        val lendo = leitorLendo()

        val cabecalho = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        texto("MotoristaPro", 15f, COR_TEXTO, negrito = true)
                    )
                    addView(
                        texto(
                            if (lendo) "● lendo as ofertas" else "● leitor desligado",
                            11f,
                            if (lendo) COR_LIMA else COR_VERMELHO
                        )
                    )
                },
                LinearLayout.LayoutParams(0, WRAP, 1f)
            )
            addView(
                botaoRedondo("✕") { esconder() },
                LinearLayout.LayoutParams(dp(34), dp(34))
            )
        }

        val estados = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                pastilha("Leitor", if (lendo) "Ligado" else "Desligado", lendo) {},
                pesado()
            )
            addView(
                pastilha(
                    "Jornada",
                    if (jornadaRodando()) "Rodando" else "Parada",
                    jornadaRodando()
                ) { abrir(0) },
                pesado()
            )
            addView(
                pastilha("Voz", if (vozLigada()) "Ligada" else "Muda", vozLigada()) {
                    aoAlternarVoz?.invoke()
                    mostrar()   // redesenha com o estado novo
                },
                pesado()
            )
        }

        val acoes = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(acao("Ler agora") { aoLerAgora?.invoke(); esconder() }, pesado())
            addView(acao("Corridas") { abrir(1) }, pesado())
            addView(acao("Finanças") { abrir(2) }, pesado())
            addView(acao("Abrir app") { abrir(0) }, pesado())
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(COR_FUNDO)
                setStroke(dp(1), COR_CONTORNO)
            }
            addView(cabecalho, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(estados, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
            addView(acoes, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })

            // Toque fora fecha. Sem isto o menu ficaria por cima da Uber até o
            // motorista achar o ✕ — justamente quando ele tem 12 segundos para
            // decidir a corrida.
            setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_OUTSIDE) {
                    esconder()
                    true
                } else false
            }
        }
    }

    private fun abrir(aba: Int) {
        esconder()
        aoAbrirAba?.invoke(aba)
    }

    /** Pastilha de estado: rótulo em cima, situação embaixo, verde quando ligado. */
    private fun pastilha(rotulo: String, situacao: String, aceso: Boolean, onClick: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(10), dp(6), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(COR_CAIXA)
                setStroke(dp(1), if (aceso) COR_LIMA else COR_CONTORNO)
            }
            addView(texto(rotulo, 12f, COR_TEXTO, negrito = true))
            addView(texto(situacao, 10f, if (aceso) COR_LIMA else COR_APAGADO))
            setOnClickListener { onClick() }
        }

    /** Botão de ação: um retângulo com o nome do que ele faz. */
    private fun acao(rotulo: String, onClick: () -> Unit): View =
        TextView(context).apply {
            text = rotulo
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COR_TEXTO)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(12), dp(4), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(COR_CAIXA)
            }
            setOnClickListener { onClick() }
        }

    private fun botaoRedondo(rotulo: String, onClick: () -> Unit): View =
        TextView(context).apply {
            text = rotulo
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COR_APAGADO)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COR_CAIXA)
            }
            setOnClickListener { onClick() }
        }

    private fun texto(valor: String, tamanho: Float, cor: Int, negrito: Boolean = false): TextView =
        TextView(context).apply {
            text = valor
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanho)
            setTextColor(cor)
            gravity = Gravity.CENTER_HORIZONTAL
            if (negrito) typeface = Typeface.DEFAULT_BOLD
        }

    /** Cada filho ocupa a mesma fatia da linha, com uma folga entre eles. */
    private fun pesado() = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
        leftMargin = dp(3)
        rightMargin = dp(3)
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "MotoristaPro"
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        val COR_FUNDO = 0xF50D1117.toInt()
        val COR_CAIXA = 0xFF21262D.toInt()
        val COR_CONTORNO = 0xFF2C333D.toInt()
        val COR_TEXTO = 0xFFFFFFFF.toInt()
        val COR_APAGADO = 0xFF8B949E.toInt()
        val COR_LIMA = 0xFF39FF14.toInt()
        val COR_VERMELHO = 0xFFFF5252.toInt()
    }
}
