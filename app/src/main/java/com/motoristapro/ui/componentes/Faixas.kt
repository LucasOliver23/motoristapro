package com.motoristapro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

        BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp)) {
            val bolinha = 30.dp
            val corrida = maxWidth - bolinha
            val corridaPx = with(LocalDensity.current) { corrida.toPx() }.coerceAtLeast(1f)

            // A régua de bolinhas começa e termina no CENTRO dos polegares: assim
            // a cor embaixo do dedo é a cor daquele valor, e não um pedaço
            // deslocado meia bolinha para o lado.
            Row(
                Modifier.align(Alignment.Center).fillMaxWidth()
                    .padding(horizontal = bolinha / 2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(PONTOS) { i ->
                    val onde = (i + 0.5f) / PONTOS
                    Box(
                        Modifier.size(if (onde < fracaoInicio || onde >= fracaoFim) 8.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    onde < fracaoInicio -> VermelhoPrejuizo
                                    onde < fracaoFim -> AmareloAlerta
                                    else -> Lima
                                }
                            )
                    )
                }
            }

            Polegar(
                cor = VermelhoPrejuizo,
                tamanho = bolinha,
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

            Polegar(
                cor = Lima,
                tamanho = bolinha,
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

/** Encaixa o valor no degrau mais perto (degrau 0 = livre). */
private fun arredondar(v: Float, degrau: Float): Float =
    if (degrau <= 0f) v else Math.round(v / degrau) * degrau

/** A etiqueta do valor, no alto da régua: é ela que mostra o número. */
@Composable
private fun Etiqueta(texto: String, cor: Color) {
    Box(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp))
            .background(cor.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.bodySmall, color = cor, fontWeight = FontWeight.Bold)
    }
}

/**
 * O polegar: uma bolinha com anel na cor da sua ponta.
 *
 * Era uma pastilha larga com o valor escrito dentro, e isso quebrava justamente
 * no caso mais comum: com 1,40 e 1,80 numa régua que vai de 0,50 a 6,00, os dois
 * valores ficam a 8% um do outro, e duas pastilhas de 74dp se sobrepunham —
 * escondendo exatamente o número que elas existiam para mostrar. A bolinha ocupa
 * um terço disso, e os valores ficam nas etiquetas do alto, que já estavam lá.
 */
@Composable
private fun Polegar(cor: Color, tamanho: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Box(
        modifier.size(tamanho).clip(CircleShape).background(SuperficieAlta)
            .border(2.dp, cor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(tamanho / 2.6f).clip(CircleShape).background(cor))
    }
}

/** Quantas bolinhas formam a régua. Ímpar não ajuda em nada; 21 cabe no celular. */
private const val PONTOS = 21

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
