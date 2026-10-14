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

    // ---- Menu e Finanças

    val Chave = icone(
        "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94" +
            "l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"
    )

    val Sino = icone("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")

    val Olho = icone("M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z", circulo(12f, 12f, 3f))

    val Som = icone("M11 5L6 9H2v6h4l5 4V5z", "M15.54 8.46a5 5 0 0 1 0 7.07", "M19.07 4.93a10 10 0 0 1 0 14.14")

    val Bolha = icone(circulo(12f, 12f, 9f), circulo(12f, 12f, 3f))

    val Alerta = icone(
        "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z",
        "M12 9v4", "M12 17h.01"
    )

    val Bussola = icone(circulo(12f, 12f, 10f), "M16.24 7.76l-2.12 6.36-6.36 2.12 2.12-6.36z")

    val Estrela = icone(
        "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z"
    )

    val Nuvem = icone("M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z")

    val Caixa = icone(
        "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z",
        "M3.27 6.96L12 12.01l8.73-5.05", "M12 22.08V12"
    )

    val Contraste = icone(circulo(12f, 12f, 10f), "M12 2a10 10 0 0 0 0 20z")

    val Engrenagem = icone(circulo(12f, 12f, 3f), circulo(12f, 12f, 8f), "M12 2v2", "M12 20v2",
        "M2 12h2", "M20 12h2", "M4.9 4.9l1.4 1.4", "M17.7 17.7l1.4 1.4", "M4.9 19.1l1.4-1.4", "M17.7 6.3l1.4-1.4")

    val Escudo = icone("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z")

    val Alfinete = icone("M12 17v5", "M9 3h6l-1 7 3 3v2H7v-2l3-3z")

    val Seta = icone("M12 19V5", "M5 12l7-7 7 7")

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
            // Um caminho mal escrito não pode derrubar o app inteiro na abertura
            // (as abas usam estes ícones): no pior caso aquele traço some.
            val nos = runCatching { addPathNodes(d) }.getOrElse { emptyList() }
            b.addPath(
                pathData = nos,
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
