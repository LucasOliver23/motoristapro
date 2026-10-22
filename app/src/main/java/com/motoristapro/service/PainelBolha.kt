package com.motoristapro.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.motoristapro.data.repository.emReais

/**
 * O que o painel mostra, lido do serviço na hora de abrir.
 *
 * @param turnoSegundos tempo de turno já sem as pausas; null = sem turno aberto.
 */
data class EstadoBolha(
    val lucroCentavos: Long = 0,
    val metaCentavos: Long = 0,
    val turnoSegundos: Long? = null,
    val turnoPausado: Boolean = false,
    val leitorLendo: Boolean = true,
    val vozLigada: Boolean = false
)

/**
 * O painel que abre ao tocar na bolha (5.1): o turno inteiro sem sair da Uber.
 *
 * Em cima, o lucro do dia com a meta em segmentos e o estado do leitor, do turno
 * e da voz. Embaixo, o botão grande de pausar (o que o motorista mais faz no
 * meio do dia: almoço, banheiro, abastecer), as quatro ações do turno e o
 * encerrar, em vermelho e com confirmação: um toque sem querer não fecha o dia.
 *
 * Cores sólidas sobre preto absoluto, sem degradê e sem sombra: verde é lucro e
 * meta batida, amarelo é atenção, vermelho é encerrar. Botões com borda mais
 * escura embaixo, para parecerem tecla.
 *
 * Views programáticas, sem XML: é uma janela de overlay do serviço de
 * acessibilidade, e inflar layout aqui dentro obrigaria a carregar um tema do
 * app que o serviço não tem.
 */
class PainelBolha(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var raiz: LinearLayout? = null

    /** Estado consultado na hora de desenhar — o serviço é quem sabe. */
    var estado: () -> EstadoBolha = { EstadoBolha() }

    var aoLerAgora: (() -> Unit)? = null
    var aoAlternarVoz: (() -> Unit)? = null
    var aoAbrirAba: ((Int) -> Unit)? = null
    var aoAlternarPausa: (() -> Unit)? = null
    var aoEncerrarTurno: (() -> Unit)? = null
    var aoIniciarTurno: (() -> Unit)? = null
    var aoRegistrarCorrida: (() -> Unit)? = null

    val visivel: Boolean get() = raiz?.isAttachedToWindow == true

    /** O primeiro toque em "Encerrar turno" só arma; o segundo, em até 4 s, encerra. */
    private var encerrarArmado = false
    private val desarmar = Runnable {
        encerrarArmado = false
        if (visivel) mostrar()
    }

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
        y = dp(60)
    }

    fun alternar() {
        if (visivel) esconder() else mostrar()
    }

    fun mostrar() {
        removerView()
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
        handler.removeCallbacks(desarmar)
        encerrarArmado = false
        removerView()
    }

    private fun removerView() {
        val v = raiz ?: return
        if (v.isAttachedToWindow) runCatching { wm.removeViewImmediate(v) }
        raiz = null
    }

    fun liberar() = esconder()

    // ------------------------------------------------------------------ montagem

    private fun montar(): LinearLayout {
        val e = estado()
        val temTurno = e.turnoSegundos != null
        val fracao = if (e.metaCentavos > 0) e.lucroCentavos.toFloat() / e.metaCentavos else 0f
        val bateu = e.metaCentavos > 0 && fracao >= 1f

        val cabecalho = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(rotulo("LUCRO HOJE"))
                    addView(
                        texto(e.lucroCentavos.emReais(), 30f, if (e.lucroCentavos < 0) VERMELHO else BRANCO, mono = true)
                    )
                    if (e.metaCentavos > 0) {
                        addView(
                            texto(
                                "${(fracao * 100).toInt()}% da meta de ${e.metaCentavos.emReais().replace(",00", "")}",
                                12f, if (bateu) VERDE else AMARELO
                            )
                        )
                    }
                },
                LinearLayout.LayoutParams(0, WRAP, 1f)
            )
            addView(
                TextView(context).apply {
                    text = "✕"
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(CINZA)
                    background = caixa(CARD, 12)
                    setOnClickListener { esconder() }
                },
                LinearLayout.LayoutParams(dp(44), dp(44))
            )
        }

        val estados = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(chip(if (e.leitorLendo) "LEITOR LENDO" else "LEITOR DESLIGADO", if (e.leitorLendo) VERDE else VERMELHO))
            addView(
                chip(
                    when {
                        !temTurno -> "SEM TURNO"
                        e.turnoPausado -> "PAUSADO ${relogio(e.turnoSegundos ?: 0)}"
                        else -> "TURNO ${relogio(e.turnoSegundos ?: 0)}"
                    },
                    when {
                        !temTurno -> CINZA
                        e.turnoPausado -> AMARELO
                        else -> VERDE
                    }
                ),
                LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6) }
            )
        }
        val voz = chip(if (e.vozLigada) "VOZ LIGADA" else "VOZ DESLIGADA", if (e.vozLigada) VERDE else CINZA)

        // O botão grande: pausar / retomar o turno, ou abrir um quando não há.
        val principal = when {
            !temTurno -> tecla("Iniciar turno", IconeOverlay.Caminho.RETOMAR, VERDE, VERDE_BORDA, PRETO, grande = true) {
                esconder(); aoIniciarTurno?.invoke()
            }
            e.turnoPausado -> tecla("Retomar turno", IconeOverlay.Caminho.RETOMAR, VERDE, VERDE_BORDA, PRETO, grande = true) {
                aoAlternarPausa?.invoke(); esconder()
            }
            else -> tecla("Pausar turno", IconeOverlay.Caminho.PAUSAR, AMARELO, AMARELO_BORDA, PRETO, grande = true) {
                aoAlternarPausa?.invoke(); esconder()
            }
        }

        val linha1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                tecla("Registrar corrida", IconeOverlay.Caminho.REGISTRAR, CARD_ALTO, PRETO, BRANCO) {
                    esconder(); aoRegistrarCorrida?.invoke()
                },
                metade(direita = false)
            )
            addView(
                tecla("Ler a tela agora", IconeOverlay.Caminho.LER_TELA, CARD_ALTO, PRETO, BRANCO) {
                    esconder(); aoLerAgora?.invoke()
                },
                metade(direita = true)
            )
        }
        val linha2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                tecla(
                    if (e.vozLigada) "Desligar a voz" else "Ligar a voz",
                    if (e.vozLigada) IconeOverlay.Caminho.VOZ_MUDA else IconeOverlay.Caminho.VOZ_LIGADA,
                    CARD_ALTO, PRETO, BRANCO
                ) {
                    aoAlternarVoz?.invoke()
                    mostrar()   // redesenha com o estado novo
                },
                metade(direita = false)
            )
            addView(
                tecla("Abrir o app", IconeOverlay.Caminho.ABRIR_APP, CARD_ALTO, PRETO, BRANCO) { abrir(0) },
                metade(direita = true)
            )
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(PRETO)
                setStroke(dp(1), CARD_ALTO)
            }
            addView(cabecalho, LinearLayout.LayoutParams(MATCH, WRAP))
            if (e.metaCentavos > 0) {
                addView(segmentos(fracao, if (bateu) VERDE else AMARELO), LinearLayout.LayoutParams(MATCH, dp(8)).apply { topMargin = dp(10) })
            }
            addView(estados, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
            addView(voz, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(6) })
            voz.setOnClickListener { aoAlternarVoz?.invoke(); mostrar() }
            addView(principal, LinearLayout.LayoutParams(MATCH, dp(60)).apply { topMargin = dp(14) })
            addView(linha1, LinearLayout.LayoutParams(MATCH, dp(68)).apply { topMargin = dp(10) })
            addView(linha2, LinearLayout.LayoutParams(MATCH, dp(68)).apply { topMargin = dp(10) })
            if (temTurno) {
                addView(botaoEncerrar(), LinearLayout.LayoutParams(MATCH, dp(52)).apply { topMargin = dp(10) })
            }

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

    /** "Encerrar turno": o primeiro toque pede confirmação, o segundo encerra. */
    private fun botaoEncerrar(): View {
        val armado = encerrarArmado
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = if (armado) caixa(VERMELHO, 12) else caixa(CARD, 12)
            val cor = if (armado) BRANCO else VERMELHO
            addView(icone(IconeOverlay.Caminho.ENCERRAR, cor), LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(
                texto(if (armado) "Toque de novo para encerrar" else "Encerrar turno", 15f, cor, negrito = true),
                LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(8) }
            )
            setOnClickListener {
                if (encerrarArmado) {
                    esconder()
                    aoEncerrarTurno?.invoke()
                } else {
                    encerrarArmado = true
                    handler.removeCallbacks(desarmar)
                    handler.postDelayed(desarmar, TEMPO_CONFIRMAR_MS)
                    mostrar()
                }
            }
        }
    }

    private fun abrir(aba: Int) {
        esconder()
        aoAbrirAba?.invoke(aba)
    }

    /**
     * Botão tátil: cor sólida com uma faixa mais escura embaixo, como uma tecla.
     * Sem sombra — a "profundidade" é só a borda de baixo.
     */
    private fun tecla(
        rotulo: String,
        caminho: String,
        fundo: Int,
        borda: Int,
        corTexto: Int,
        grande: Boolean = false,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = if (grande) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = LayerDrawable(arrayOf<Drawable>(caixa(borda, 12), caixa(fundo, 12))).apply {
            setLayerInset(1, 0, 0, 0, dp(4))
        }
        setPadding(dp(6), 0, dp(6), dp(4))
        val tam = if (grande) 22 else 20
        addView(icone(caminho, corTexto), LinearLayout.LayoutParams(dp(tam), dp(tam)))
        addView(
            texto(rotulo, if (grande) 17f else 13f, corTexto, negrito = true),
            LinearLayout.LayoutParams(WRAP, WRAP).apply {
                if (grande) leftMargin = dp(10) else topMargin = dp(6)
            }
        )
        setOnClickListener { onClick() }
    }

    /** Pastilha de estado: contorno e texto na cor do significado, fundo preto. */
    private fun chip(rotulo: String, cor: Int): TextView =
        TextView(context).apply {
            text = "●  $rotulo"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(BRANCO)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(99).toFloat()
                setColor(PRETO)
                setStroke(dp(1), cor)
            }
            // Só a bolinha leva a cor; o texto fica branco para ler no sol.
            text = android.text.SpannableString(text).apply {
                setSpan(android.text.style.ForegroundColorSpan(cor), 0, 1, 0)
            }
        }

    /** A meta em 20 segmentos: acesos até onde o lucro chegou, apagados no resto. */
    private fun segmentos(fracao: Float, cor: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val acesos = (fracao.coerceIn(0f, 1f) * SEGMENTOS).toInt()
        repeat(SEGMENTOS) { i ->
            addView(
                View(context).apply { background = caixa(if (i < acesos) cor else CARD_ALTO, 2) },
                LinearLayout.LayoutParams(0, MATCH, 1f).apply { if (i > 0) leftMargin = dp(3) }
            )
        }
    }

    private fun icone(caminho: String, cor: Int): ImageView =
        ImageView(context).apply { setImageDrawable(IconeOverlay(caminho, cor)) }

    private fun caixa(cor: Int, raio: Int) = GradientDrawable().apply {
        cornerRadius = dp(raio).toFloat()
        setColor(cor)
    }

    private fun rotulo(valor: String): TextView = texto(valor, 10f, CINZA, negrito = true).apply {
        letterSpacing = 0.1f
    }

    private fun texto(
        valor: String,
        tamanho: Float,
        cor: Int,
        negrito: Boolean = false,
        mono: Boolean = false
    ): TextView = TextView(context).apply {
        text = valor
        setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanho)
        setTextColor(cor)
        typeface = when {
            mono -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            negrito -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
    }

    private fun relogio(segundos: Long): String {
        val min = segundos / 60
        return "%d:%02d".format(min / 60, min % 60)
    }

    private fun metade(direita: Boolean) = LinearLayout.LayoutParams(0, MATCH, 1f).apply {
        if (direita) leftMargin = dp(5) else rightMargin = dp(5)
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "MotoristaPro"
        const val SEGMENTOS = 20
        const val TEMPO_CONFIRMAR_MS = 4_000L
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

        // A regra estrita de cor (5.1): sólidas, alto contraste, sobre preto absoluto.
        const val PRETO = 0xFF000000.toInt()
        const val CARD = 0xFF1C1C1E.toInt()
        const val CARD_ALTO = 0xFF2C2C2E.toInt()
        const val BRANCO = 0xFFFFFFFF.toInt()
        const val CINZA = 0xFF8E8E93.toInt()
        const val VERDE = 0xFF00E676.toInt()
        const val VERDE_BORDA = 0xFF00A152.toInt()
        const val AMARELO = 0xFFFFD600.toInt()
        const val AMARELO_BORDA = 0xFFA68B00.toInt()
        const val VERMELHO = 0xFFFF1744.toInt()
    }
}
