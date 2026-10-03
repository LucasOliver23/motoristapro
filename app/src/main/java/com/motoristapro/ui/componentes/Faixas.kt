package com.motoristapro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import kotlin.math.roundToInt

/**
 * Faixa de três cores com dois polegares: RUIM — ATENÇÃO — BOA.
 *
 * Em vez de digitar dois números soltos, o motorista arrasta e VÊ a faixa se
 * formar. O trecho vermelho vai do início ao primeiro polegar, o amarelo fica
 * entre os dois, e o verde vai do segundo até o fim.
 *
 * Os dois polegares nunca se cruzam: cada um é travado no valor do outro.
 *
 * Desenhado à mão e não com o RangeSlider do Material porque o polegar do
 * Material é um risquinho sem valor nenhum escrito — e o número é justamente o
 * que o motorista precisa ver enquanto arrasta, com o dedo cobrindo a barra.
 */
@Composable
fun FaixaTresCores(
    titulo: String,
    unidade: String,
    inicio: Float,
    fim: Float,
    minimo: Float,
    maximo: Float,
    /** De quanto em quanto o valor anda (0.05 = cinco centavos). */
    degrau: Float = 0f,
    formatar: (Float) -> String,
    onMudar: (Float, Float) -> Unit,
    ajuda: String? = null
) {
    val amplitude = (maximo - minimo).takeIf { it > 0f } ?: 1f
    val fracaoInicio = ((inicio - minimo) / amplitude).coerceIn(0f, 1f)
    val fracaoFim = ((fim - minimo) / amplitude).coerceIn(0f, 1f)

    // O arrasto lê o valor de AGORA, não o da composição em que começou: sem
    // isto, segurar o polegar e arrastar devagar faz o valor voltar sozinho.
    val valores by rememberUpdatedState(inicio to fim)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Etiqueta(formatar(inicio), VermelhoPrejuizo)
                Etiqueta(formatar(fim), Lima)
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(46.dp)) {
            val larguraPastilha = 74.dp
            val corrida = maxWidth - larguraPastilha
            val corridaPx = with(LocalDensity.current) { corrida.toPx() }.coerceAtLeast(1f)

            // A trilha começa e termina no CENTRO das pastilhas — é o que faz a
            // cor embaixo do polegar ser a cor daquele valor, e não um pedaço
            // deslocado meia pastilha para o lado.
            Row(
                Modifier.align(Alignment.Center).fillMaxWidth()
                    .padding(horizontal = larguraPastilha / 2)
                    .height(18.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .border(1.dp, Contorno, RoundedCornerShape(99.dp))
            ) {
                Pedaco(fracaoInicio, VermelhoPrejuizo)
                Pedaco(fracaoFim - fracaoInicio, AmareloAlerta)
                Pedaco(1f - fracaoFim, Lima)
            }

            Pastilha(
                texto = formatar(inicio),
                cor = VermelhoPrejuizo,
                largura = larguraPastilha,
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset((fracaoInicio * corridaPx).roundToInt(), 0) }
                    .pointerInput(minimo, maximo, degrau, corridaPx) {
                        detectHorizontalDragGestures { _, dx ->
                            val (iAtual, fAtual) = valores
                            val novo = (iAtual + dx / corridaPx * amplitude)
                                .coerceIn(minimo, maxOf(minimo, fAtual))
                            onMudar(arredondar(novo, degrau), fAtual)
                        }
                    }
            )

            Pastilha(
                texto = formatar(fim),
                cor = Lima,
                largura = larguraPastilha,
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset((fracaoFim * corridaPx).roundToInt(), 0) }
                    .pointerInput(minimo, maximo, degrau, corridaPx) {
                        detectHorizontalDragGestures { _, dx ->
                            val (iAtual, fAtual) = valores
                            val novo = (fAtual + dx / corridaPx * amplitude)
                                .coerceIn(minOf(iAtual, maximo), maximo)
                            onMudar(iAtual, arredondar(novo, degrau))
                        }
                    }
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ruim", style = MaterialTheme.typography.bodySmall, color = VermelhoPrejuizo)
            Text(unidade, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            Text("Boa", style = MaterialTheme.typography.bodySmall, color = Lima)
        }
        if (ajuda != null) {
            Text(ajuda, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        }
    }
}

/**
 * Um pedaço colorido da trilha.
 *
 * Peso mínimo de 0,004 em vez de zero: peso zero some do layout, e aí a faixa
 * inteira some quando o polegar encosta na ponta.
 */
@Composable
private fun RowScope.Pedaco(fracao: Float, cor: Color) {
    Box(
        Modifier.weight(fracao.coerceAtLeast(0.004f))
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(cor.copy(alpha = 0.75f), cor.copy(alpha = 0.35f))
                )
            )
    )
}

/**
 * O polegar: uma pastilha com o próprio valor escrito dentro.
 *
 * Ler o número na bolinha que se está arrastando é melhor do que procurá-lo em
 * outro canto da tela — ainda mais com o polegar cobrindo metade do slider.
 */
@Composable
private fun Pastilha(texto: String, cor: Color, largura: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Box(
        modifier.width(largura).height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(SuperficieAlta)
            .border(1.5.dp, cor.copy(alpha = 0.8f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            color = cor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/** Encaixa o valor no degrau mais perto (degrau 0 = livre). */
private fun arredondar(v: Float, degrau: Float): Float =
    if (degrau <= 0f) v else Math.round(v / degrau) * degrau

@Composable
private fun Etiqueta(texto: String, cor: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp))
            .background(cor.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.bodySmall, color = cor, fontWeight = FontWeight.Bold)
    }
}

/** Uma linha da tabela "Suas faixas". */
data class LinhaFaixa(
    val metrica: String,
    val ruim: String,
    val atencao: String,
    val boa: String
)

/**
 * Tabela de leitura rápida: o que é ruim, o que é atenção e o que é bom,
 * em cada critério. É o resumo das faixas que o motorista configurou.
 */
@Composable
fun TabelaFaixas(linhas: List<LinhaFaixa>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("", Modifier.weight(1.1f))
            Cabecalho("Ruim", VermelhoPrejuizo, Modifier.weight(1f))
            Cabecalho("Atenção", AmareloAlerta, Modifier.weight(1f))
            Cabecalho("Boa", Lima, Modifier.weight(1f))
        }
        linhas.forEach { l ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    l.metrica, Modifier.weight(1.1f),
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                Celula(l.ruim, VermelhoPrejuizo, Modifier.weight(1f))
                Celula(l.atencao, AmareloAlerta, Modifier.weight(1f))
                Celula(l.boa, Lima, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Cabecalho(texto: String, cor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(cor))
        Text(
            " $texto",
            style = MaterialTheme.typography.bodySmall,
            color = cor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Celula(texto: String, cor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(
        modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(8.dp))
            .background(cor.copy(alpha = 0.12f)).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, style = MaterialTheme.typography.bodySmall, color = cor, fontWeight = FontWeight.SemiBold)
    }
}
