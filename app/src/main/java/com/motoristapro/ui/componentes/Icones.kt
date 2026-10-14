package com.motoristapro.ui.componentes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Os ícones do visual novo: traço fino de 2 px numa grade de 24, no estilo
 * Lucide (licença ISC).
 *
 * Desenhados aqui, e não tirados do Material, porque o conjunto que vem no app
 * não tem bomba de combustível, semáforo nem gráfico — e o "extended" pesa
 * vários MB a mais no APK para usar meia dúzia. Cada ícone é só o caminho SVG.
 *
 * O traço sai preto e a cor de verdade vem do `tint` do Icon, como em qualquer
 * ícone do Material: aba aberta fica azul, fechada fica cinza.
 */
object Icones {

    val Inicio = icone("M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")

    val Corridas = icone("M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01")

    val Financas = icone(
        "M4 6h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
        "M2 10h20", "M16 14h.01"
    )

    val Atividade = icone("M3 3v18h18", "M7 15l4-4 3 3 5-6")

    val Menu = icone(
        circulo(12f, 8f, 4f),
        "M4 21c0-4 4-6 8-6s8 2 8 6"
    )

    /** Semáforo: caixa em pé com as três luzes. */
    val Semaforo = icone(
        "M10 2h4a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3h-4a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3z",
        circulo(12f, 7f, 1.5f), circulo(12f, 12f, 1.5f), circulo(12f, 17f, 1.5f)
    )

    val Veiculo = icone(
        "M5 17h14", "M6 17l1.5-6h9L18 17", "M7 17v2", "M17 17v2", "M4 11h16"
    )

    /** Linha de batimento: o "checkup" do app. */
    val Checkup = icone("M22 12h-4l-3 9L9 3l-3 9H2")

    val Relogio = icone(circulo(12f, 12f, 10f), "M12 6v6l4 2")

    val Combustivel = icone(
        "M3 22V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v17", "M2 22h14",
        "M15 9h2a2 2 0 0 1 2 2v6a1.5 1.5 0 0 0 3 0V8l-3-3", "M6 8h6"
    )

    val Raio = icone("M13 2L3 14h9l-1 8 10-12h-9z")

    val Mais = icone("M12 5v14", "M5 12h14")

    val Certo = icone("M20 6L9 17l-5-5")

    // -------------------------------------------------------------- montagem

    /** Círculo em caminho SVG (dois arcos), porque o ImageVector só entende path. */
    private fun circulo(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun icone(vararg caminhos: String): ImageVector {
        val b = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        caminhos.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }
}
