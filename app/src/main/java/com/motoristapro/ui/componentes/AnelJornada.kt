package com.motoristapro.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.ui.formatarCronometro
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa

/**
 * O relógio do turno, em anel.
 *
 * O anel NÃO é enfeite girando: ele enche conforme a jornada de hoje avança em
 * relação ao turno de referência do motorista (as metas já dizem quantas horas
 * ele pretende rodar). Passou disso, vira âmbar — é o aviso de que o dia está
 * esticando, que é a informação que um cronômetro sozinho nunca dá.
 *
 * Desenhado com Canvas em vez de CircularProgressIndicator porque o indicador
 * do Material não aceita ponta arredondada com cor em gradiente, e é justamente
 * isso que dá o aspecto de brilho.
 */
@Composable
fun AnelJornada(
    segundos: Long,
    segundosDeReferencia: Long,
    modifier: Modifier = Modifier,
    rotuloDeCima: String = "HOJE",
    detalhe: String? = null
) {
    val fracao = if (segundosDeReferencia > 0) {
        (segundos.toFloat() / segundosDeReferencia).coerceIn(0f, 1f)
    } else 0f
    val passouDoNormal = segundosDeReferencia > 0 && segundos > segundosDeReferencia
    val cor = if (passouDoNormal) AmareloAlerta else Lima

    Box(modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().size(168.dp)) {
            val traco = 11.dp.toPx()
            val canto = Offset(traco / 2, traco / 2)
            val tamanho = Size(size.width - traco, size.height - traco)

            // Trilho: o que falta do turno.
            drawArc(
                color = Contorno,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = canto,
                size = tamanho,
                style = Stroke(width = traco, cap = StrokeCap.Round)
            )
            if (fracao <= 0f) return@Canvas

            // Brilho: o mesmo arco, grosso e transparente, desenhado por baixo.
            drawArc(
                color = cor.copy(alpha = 0.22f),
                startAngle = -90f,
                sweepAngle = 360f * fracao,
                useCenter = false,
                topLeft = canto,
                size = tamanho,
                style = Stroke(width = traco * 2.1f, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.linearGradient(listOf(cor, if (passouDoNormal) cor else Turquesa)),
                startAngle = -90f,
                sweepAngle = 360f * fracao,
                useCenter = false,
                topLeft = canto,
                size = tamanho,
                style = Stroke(width = traco, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                rotuloDeCima,
                style = MaterialTheme.typography.labelSmall,
                color = TextoSecundario,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                segundos.formatarCronometro(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (detalhe != null) {
                Text(detalhe, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
            }
        }
    }
}

/**
 * O cartão inteiro da jornada: anel, a divisão do tempo e os botões.
 *
 * Os três números embaixo do anel são o ponto do cartão. "Parado" é o tempo que
 * nenhum aplicativo de corrida mostra, e "por hora" aqui conta o tempo parado
 * junto — é o que o motorista realmente ganha por hora de celular ligado, não
 * por hora dentro da corrida.
 */
@Composable
fun CartaoJornada(
    segundos: Long,
    segundosDeReferencia: Long,
    segundosEmCorrida: Long,
    faturamentoCentavos: Long,
    inicioTexto: String?,
    pausada: Boolean,
    modifier: Modifier = Modifier,
    conteudoFinal: @Composable () -> Unit = {}
) {
    val parado = (segundos - segundosEmCorrida).coerceAtLeast(0)
    val porHora = if (segundos > 0) faturamentoCentavos * 3600 / segundos else 0

    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, Contorno, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuperficieAlta)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CONTROLE DE JORNADA",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextoSecundario,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                )
                Box(Modifier.weight(1f))
                EtiquetaEstado(
                    texto = if (pausada) "PAUSADA" else "RODANDO",
                    cor = if (pausada) AmareloAlerta else Lima
                )
            }

            AnelJornada(
                segundos = segundos,
                segundosDeReferencia = segundosDeReferencia,
                detalhe = inicioTexto
            )

            Row(Modifier.fillMaxWidth()) {
                NumeroDoAnel("EM CORRIDA", segundosEmCorrida.formatarCronometro(), Lima, Modifier.weight(1f))
                NumeroDoAnel("PARADO", parado.formatarCronometro(), AmareloAlerta, Modifier.weight(1f))
                NumeroDoAnel("POR HORA", porHora.emReaisCurto(), null, Modifier.weight(1f))
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                conteudoFinal()
            }
        }
    }
}

/** Pastilha de estado no alto do cartão. */
@Composable
private fun EtiquetaEstado(texto: String, cor: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp))
            .background(cor.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            color = cor,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

/** Um dos três números embaixo do anel. */
@Composable
private fun NumeroDoAnel(rotulo: String, valor: String, cor: Color?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = cor ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = TextoSecundario,
            fontSize = 9.sp
        )
    }
}

/** R$ 24,37 — curto, para caber embaixo do anel. */
private fun Long.emReaisCurto(): String =
    "R$ " + String.format(java.util.Locale("pt", "BR"), "%,.2f", this / 100.0)
