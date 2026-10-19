package com.motoristapro.ui.manutencao

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.data.local.entity.ItemManutencao
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.veiculo.CalculoOdometro
import com.motoristapro.veiculo.LeituraOdometro
import com.motoristapro.veiculo.OdometroAtual
import java.util.Locale

private val PT_BR = Locale("pt", "BR")

/** "da moto" / "do carro". */
fun TipoVeiculo.comArtigo(): String = if (this == TipoVeiculo.MOTO) "da moto" else "do carro"

/** "83.544" — o km do painel como o motorista lê. */
fun Long.kmPainel(): String = String.format(PT_BR, "%,d", this)

/** Tudo que a pergunta do km precisa mostrar, já lido do banco. */
data class DadosKm(
    val atual: OdometroAtual?,
    /** A leitura do início da jornada (só no encerrar). */
    val inicio: LeituraOdometro?,
    val urgente: ItemManutencao?,
    val custoKmCentavos: Long,
    val tipo: TipoVeiculo,
    val nomeVeiculo: String?
)

/**
 * "Km atual da moto": aparece ao iniciar e ao encerrar a jornada.
 *
 * Vem preenchido com o que o app acha (última leitura + GPS); o motorista só
 * confere com o painel. É o que pega o km rodado fora do app, que nenhum GPS vê.
 */
@Composable
fun KmDoPainelDialog(
    encerrando: Boolean,
    dados: DadosKm,
    onConfirmar: (Long) -> Unit,
    onPular: () -> Unit,
    onDismiss: () -> Unit
) {
    var texto by rememberSaveable { mutableStateOf(dados.atual?.km?.takeIf { it > 0 }?.toString().orEmpty()) }
    val km = texto.toLongOrNull() ?: 0L
    val aviso = if (texto.isBlank()) null else CalculoOdometro.aviso(km, dados.atual)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (dados.tipo == TipoVeiculo.MOTO) Icones.Moto else Icones.Carro,
                contentDescription = null, tint = Lima, modifier = Modifier.size(34.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(if (encerrando) "Km ao encerrar" else "Km atual ${dados.tipo.comArtigo()}")
                dados.nomeVeiculo?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        encerrando && dados.atual?.estimado == true ->
                            "Pelo GPS você rodou uns ${dados.atual.kmGps} km. Confira no painel."
                        encerrando -> "Confira o km no painel antes de encerrar."
                        else -> "Olhe o painel e confira. Se usou ${if (dados.tipo == TipoVeiculo.MOTO) "a moto" else "o carro"} " +
                            "fora do app, o número muda aqui."
                    },
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                CampoFormulario(
                    rotulo = "Hodômetro (km)",
                    valor = texto,
                    onValor = { texto = it.filter(Char::isDigit).take(7) },
                    erro = aviso != null && km <= 0,
                    ajuda = aviso
                )
                if (km > 0) Detalhes(encerrando, dados, km)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(km) }, enabled = km > 0) {
                Text(if (encerrando) "Confirmar e encerrar" else "Confirmar e iniciar", color = Lima, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onPular) {
                Text(if (encerrando) "Encerrar sem km" else "Pular hoje", color = TextoSecundario)
            }
        }
    )
}

@Composable
private fun Detalhes(encerrando: Boolean, dados: DadosKm, km: Long) {
    if (encerrando) {
        val inicio = dados.inicio
        if (inicio != null && km >= inicio.km) {
            val rodou = km - inicio.km
            Linha("Começou o dia em", "${inicio.km.kmPainel()} km")
            Linha("Rodou hoje", "$rodou km", Lima)
            if (dados.custoKmCentavos > 0) {
                Linha(
                    "Custo ${dados.tipo.comArtigo()} no dia",
                    (rodou * dados.custoKmCentavos).emReais()
                )
            }
        }
    } else {
        val anterior = dados.atual
        if (anterior != null) {
            Linha("Último km do app", "${anterior.km.kmPainel()} km")
            val fora = CalculoOdometro.foraDoApp(anterior, km)
            if (fora > 0) Linha("Uso fora do app", "+ $fora km", AmareloAlerta)
        }
    }
    dados.urgente?.let { item ->
        val faltam = item.faltamKm(km)
        Text(
            if (faltam > 0) "🔧 ${item.nome} em $faltam km (${item.odometroDoVencimento.kmPainel()} km)"
            else "🔧 ${item.nome} venceu há ${-faltam} km",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(corDaManutencao(faltam).copy(alpha = 0.12f)).padding(10.dp),
            style = MaterialTheme.typography.bodySmall, color = corDaManutencao(faltam)
        )
    }
}

@Composable
private fun Linha(rotulo: String, valor: String, cor: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(rotulo, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        Text(
            valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            color = cor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
