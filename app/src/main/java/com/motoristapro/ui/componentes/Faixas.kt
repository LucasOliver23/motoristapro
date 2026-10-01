package com.motoristapro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo

/**
 * Faixa de três cores com dois polegares: RUIM — ATENÇÃO — BOA.
 *
 * Em vez de digitar dois números soltos, o motorista arrasta e VÊ a faixa se
 * formar. O trecho vermelho vai do início ao primeiro polegar, o amarelo fica
 * entre os dois, e o verde vai do segundo até o fim.
 *
 * Os dois polegares nunca se cruzam: o RangeSlider do Material já garante isso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaixaTresCores(
    titulo: String,
    unidade: String,
    inicio: Float,
    fim: Float,
    minimo: Float,
    maximo: Float,
    passos: Int = 0,
    formatar: (Float) -> String,
    onMudar: (Float, Float) -> Unit,
    ajuda: String? = null
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Etiqueta(formatar(inicio), VermelhoPrejuizo)
                Etiqueta(formatar(fim), Lima)
            }
        }

        RangeSlider(
            value = inicio..fim,
            onValueChange = { faixa -> onMudar(faixa.start, faixa.endInclusive) },
            valueRange = minimo..maximo,
            steps = passos,
            colors = SliderDefaults.colors(
                thumbColor = Lima,
                activeTrackColor = AmareloAlerta,
                inactiveTrackColor = SuperficieAlta
            )
        )

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
