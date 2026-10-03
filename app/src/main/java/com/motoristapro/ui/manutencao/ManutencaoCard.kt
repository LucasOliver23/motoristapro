package com.motoristapro.ui.manutencao

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motoristapro.data.local.entity.ItemManutencao
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.BotaoPro
import com.motoristapro.ui.componentes.BotaoProEscuro
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo

/**
 * Como o item aparece: vencido, perto ou tranquilo.
 *
 * O corte de 500 km não é redondo por acaso — é mais ou menos dois dias de
 * trabalho de quem roda de aplicativo. Avisar com menos que isso é avisar
 * quando já não dá tempo de marcar a oficina.
 */
private const val AVISO_KM = 500L

fun corDaManutencao(faltam: Long): Color = when {
    faltam <= 0 -> VermelhoPrejuizo
    faltam <= AVISO_KM -> AmareloAlerta
    else -> Lima
}

fun textoDaManutencao(item: ItemManutencao, odometroAtual: Long): String {
    if (odometroAtual <= 0) return "informe o km num abastecimento"
    val faltam = item.faltamKm(odometroAtual)
    return when {
        faltam <= 0 -> "venceu há ${-faltam} km"
        else -> "vence em $faltam km"
    }
}

/** O item mais urgente, que é o que o Menu mostra na linha. */
fun maisUrgente(itens: List<ItemManutencao>): ItemManutencao? = itens.minByOrNull { it.odometroDoVencimento }

/**
 * A tela de manutenção: a lista do que vence, e o formulário de cadastro.
 *
 * Tudo em km e nada em data, de propósito: o motorista sabe de cabeça quantos
 * km roda por dia, e é o km que estraga a peça.
 */
@Composable
fun ManutencaoConteudo(
    itens: List<ItemManutencao>,
    odometroAtual: Long,
    onSalvar: (ItemManutencao) -> Unit,
    onExcluir: (Long) -> Unit
) {
    var editando by rememberSaveable { mutableStateOf<Long?>(null) }
    var novo by rememberSaveable { mutableStateOf(false) }
    var excluir by rememberSaveable { mutableStateOf<Long?>(null) }

    CardSecao(titulo = "HODÔMETRO DE HOJE") {
        Text(
            if (odometroAtual > 0) "$odometroAtual km" else "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (odometroAtual > 0) Lima else TextoSecundario
        )
        Text(
            if (odometroAtual > 0)
                "Vem do último abastecimento em que você informou o km. Lance o km " +
                    "sempre que abastecer e a manutenção se mantém sozinha."
            else "Lance um abastecimento com o km do painel em Finanças. Sem ele o app " +
                "não tem como saber quanto falta para cada troca.",
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario
        )
    }

    if (itens.isEmpty()) {
        CardSecao {
            Text(
                "Nenhum item cadastrado. Cadastre o que você controla — óleo, " +
                    "relação, pneu, revisão — com o intervalo em km e o hodômetro da " +
                    "última troca.",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )
        }
    }

    itens.forEach { item ->
        val faltam = item.faltamKm(odometroAtual)
        val cor = corDaManutencao(faltam)
        CardSecao {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "a cada ${item.intervaloKm} km • última em ${item.odometroUltimaKm} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextoSecundario
                    )
                }
                Text(
                    textoDaManutencao(item, odometroAtual),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (odometroAtual > 0) cor else TextoSecundario
                )
                IconButton(onClick = { excluir = item.id }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = TextoSecundario)
                }
            }
            if (odometroAtual > 0) {
                LinearProgressIndicator(
                    progress = { item.fracaoUsada(odometroAtual) },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                    color = cor,
                    trackColor = Contorno,
                    drawStopIndicator = {}
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotaoProEscuro("Editar", Modifier.weight(1f)) { editando = item.id }
                BotaoPro("Fiz agora", Modifier.weight(1f), habilitado = odometroAtual > 0) {
                    onSalvar(
                        item.copy(
                            odometroUltimaKm = odometroAtual,
                            atualizadoEm = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    BotaoPro("+ Novo item", Modifier.fillMaxWidth()) { novo = true }

    val alvo = itens.firstOrNull { it.id == editando }
    if (novo || alvo != null) {
        ManutencaoDialog(
            inicial = alvo,
            odometroAtual = odometroAtual,
            onDismiss = { novo = false; editando = null },
            onSalvar = { onSalvar(it); novo = false; editando = null }
        )
    }
    excluir?.let { id ->
        AlertDialog(
            onDismissRequest = { excluir = null },
            title = { Text("Excluir item") },
            text = { Text("O item sai da lista e o aviso para de aparecer.") },
            confirmButton = {
                TextButton(onClick = { onExcluir(id); excluir = null }) {
                    Text("Excluir", color = VermelhoPrejuizo)
                }
            },
            dismissButton = { TextButton(onClick = { excluir = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ManutencaoDialog(
    inicial: ItemManutencao?,
    odometroAtual: Long,
    onDismiss: () -> Unit,
    onSalvar: (ItemManutencao) -> Unit
) {
    var nome by rememberSaveable { mutableStateOf(inicial?.nome ?: "") }
    var intervalo by rememberSaveable { mutableStateOf(inicial?.intervaloKm?.toString() ?: "") }
    var ultima by rememberSaveable {
        mutableStateOf(
            inicial?.odometroUltimaKm?.toString()
                ?: odometroAtual.takeIf { it > 0 }?.toString().orEmpty()
        )
    }
    val km = intervalo.trim().toLongOrNull()
    val odo = ultima.trim().toLongOrNull()
    val valido = nome.isNotBlank() && km != null && km > 0 && odo != null && odo >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (inicial == null) "Novo item" else "Editar item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Sugestões: é o que quase todo mundo controla, e poupa digitação
                // na tela pequena com a mão suja de graxa.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Óleo", "Relação", "Pneu", "Revisão").forEach { s ->
                        Box(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(SuperficieAlta)
                                .clickable { nome = s }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(s, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
                        }
                    }
                }
                CampoFormulario("O que é", nome, { nome = it }, numerico = false)
                CampoFormulario(
                    "A cada quantos km", intervalo, { intervalo = it.filter(Char::isDigit) },
                    erro = intervalo.isNotBlank() && (km == null || km <= 0),
                    ajuda = "óleo de moto costuma ser 3.000 km"
                )
                CampoFormulario(
                    "Hodômetro da última vez", ultima, { ultima = it.filter(Char::isDigit) },
                    erro = ultima.isNotBlank() && odo == null,
                    ajuda = if (odometroAtual > 0) "hoje o app está em $odometroAtual km" else null
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido,
                onClick = {
                    onSalvar(
                        ItemManutencao(
                            id = inicial?.id ?: 0L,
                            nome = nome.trim(),
                            intervaloKm = km ?: 0L,
                            odometroUltimaKm = odo ?: 0L,
                            atualizadoEm = System.currentTimeMillis()
                        )
                    )
                }
            ) { Text("Salvar", color = if (valido) Lima else TextoSecundario) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
