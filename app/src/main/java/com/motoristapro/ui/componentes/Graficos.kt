package com.motoristapro.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import kotlin.math.abs
import kotlin.math.max

/**
 * Gráfico de barras verticais desenhado com Canvas (sem biblioteca externa).
 * Aceita valores negativos (barra vermelha abaixo da linha zero).
 *
 * @param rotulos um por barra; use "" para esconder rótulos intermediários.
 * @param destaque índice da barra a pintar em cor cheia (as demais ficam mais apagadas); -1 = todas cheias.
 */
@Composable
fun GraficoBarras(
    valores: List<Float>,
    rotulos: List<String>,
    modifier: Modifier = Modifier,
    altura: Dp = 160.dp,
    corPositiva: Color = Lima,
    corNegativa: Color = VermelhoPrejuizo,
    destaque: Int = -1
) {
    if (valores.isEmpty()) {
        Vazio("Sem dados no período")
        return
    }
    val maxPos = max(valores.maxOrNull() ?: 0f, 0f)
    val maxNeg = max(-(valores.minOrNull() ?: 0f), 0f)
    val total = if (maxPos + maxNeg > 0f) maxPos + maxNeg else 1f

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(altura)) {
            val n = valores.size
            val larguraSlot = size.width / n
            val larguraBarra = larguraSlot * 0.68f
            val yZero = size.height * (maxPos / total)

            // linha do zero
            drawLine(Contorno, Offset(0f, yZero), Offset(size.width, yZero), strokeWidth = 2f)

            valores.forEachIndexed { i, v ->
                if (v == 0f) return@forEachIndexed
                val h = size.height * (abs(v) / total)
                val x = i * larguraSlot + (larguraSlot - larguraBarra) / 2
                val topo = if (v > 0) yZero - h else yZero
                val base = if (v > 0) corPositiva else corNegativa
                val cor = if (destaque >= 0 && i != destaque) base.copy(alpha = 0.45f) else base
                drawRoundRect(
                    color = cor,
                    topLeft = Offset(x, topo),
                    size = Size(larguraBarra, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            rotulos.forEach { r ->
                Text(
                    r,
                    modifier = Modifier.weight(1f),
                    fontSize = 9.sp,
                    color = TextoSecundario,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** Barra horizontal proporcional (rankings: plataformas, categorias). */
@Composable
fun BarraHorizontal(
    rotulo: String,
    valorTexto: String,
    fracao: Float,
    modifier: Modifier = Modifier,
    detalhe: String? = null,
    cor: Color = Lima
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(rotulo, style = MaterialTheme.typography.bodyMedium)
            Text(valorTexto, style = MaterialTheme.typography.bodyMedium, color = cor)
        }
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                Modifier.fillMaxWidth(fracao.coerceIn(0f, 1f)).height(10.dp)
                    .clip(RoundedCornerShape(5.dp)).background(cor)
            )
        }
        if (detalhe != null) {
            Text(detalhe, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        }
    }
}
