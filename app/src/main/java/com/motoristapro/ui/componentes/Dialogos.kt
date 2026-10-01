package com.motoristapro.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motoristapro.data.local.entity.CategoriaDespesa
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.local.entity.Plataforma
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.litrosParaMl
import com.motoristapro.ui.paraCentavos
import com.motoristapro.ui.theme.TextoSecundario
import java.util.Locale

private val PT = Locale("pt", "BR")

/** Centavos -> "45,90" (para preencher campos de edição). */
private fun Long.campo(): String = emReais().removePrefix("R$ ").removePrefix("-R$ ")

/** Metros -> "12,4" km. */
private fun Long.campoKm(): String = String.format(PT, "%.1f", this / 1000.0)

/**
 * Nova despesa / edição. Com categoria COMBUSTIVEL aparecem litros e hodômetro
 * (habilitam o km/L real nos relatórios).
 */
@Composable
fun DespesaDialog(
    inicial: Despesa? = null,
    categoriaInicial: CategoriaDespesa = CategoriaDespesa.COMBUSTIVEL,
    onDismiss: () -> Unit,
    onSalvar: (Despesa) -> Unit
) {
    var categoria by rememberSaveable { mutableStateOf(inicial?.categoria ?: categoriaInicial) }
    var valor by rememberSaveable { mutableStateOf(inicial?.valorCentavos?.campo() ?: "") }
    var descricao by rememberSaveable { mutableStateOf(inicial?.descricao ?: "") }
    var litros by rememberSaveable {
        mutableStateOf(inicial?.litrosMl?.let { String.format(PT, "%.2f", it / 1000.0) } ?: "")
    }
    var odometro by rememberSaveable { mutableStateOf(inicial?.odometroKm?.toString() ?: "") }
    var tentou by rememberSaveable { mutableStateOf(false) }

    val valorC = valor.paraCentavos()
    val litrosMl = litros.litrosParaMl()
    val odo = odometro.trim().toLongOrNull()?.takeIf { it > 0 }
    val combustivel = categoria == CategoriaDespesa.COMBUSTIVEL
    val erroLitros = combustivel && litros.isNotBlank() && litrosMl == null
    val erroOdo = combustivel && odometro.isNotBlank() && odo == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (inicial == null) "Nova despesa" else "Editar despesa") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChipsSelecao(
                    opcoes = CategoriaDespesa.entries.toList(),
                    selecionado = categoria,
                    rotulo = { it.rotulo },
                    onSelecionar = { categoria = it }
                )
                CampoFormulario("Valor (R$) *", valor, { valor = it }, erro = tentou && valorC == null)
                if (combustivel) {
                    CampoFormulario("Litros (opcional)", litros, { litros = it }, erro = erroLitros)
                    CampoFormulario(
                        "Hodômetro km (opcional)", odometro, { odometro = it.filter(Char::isDigit) },
                        erro = erroOdo, ajuda = "Com litros + hodômetro o app calcula seu km/L real"
                    )
                    if (valorC != null && litrosMl != null) {
                        Text(
                            "Preço por litro: " + String.format(PT, "R$ %.3f", valorC * 10.0 / litrosMl),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextoSecundario
                        )
                    }
                }
                CampoFormulario("Descrição (opcional)", descricao, { descricao = it }, numerico = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tentou = true
                if (valorC != null && !erroLitros && !erroOdo) {
                    val base = inicial ?: Despesa(
                        categoria = categoria, valorCentavos = valorC, dataEm = System.currentTimeMillis()
                    )
                    onSalvar(
                        base.copy(
                            categoria = categoria,
                            valorCentavos = valorC,
                            descricao = descricao.trim().ifEmpty { null },
                            litrosMl = if (combustivel) litrosMl else null,
                            odometroKm = if (combustivel) odo else null
                        )
                    )
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Nova corrida (manual) / edição. */
@Composable
fun CorridaDialog(
    plataformas: List<Plataforma>,
    inicial: Corrida? = null,
    onDismiss: () -> Unit,
    onSalvar: (Corrida) -> Unit
) {
    if (plataformas.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sem plataformas") },
            text = { Text("Nenhuma plataforma ativa cadastrada.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        return
    }
    var plataformaId by rememberSaveable { mutableStateOf(inicial?.plataformaId ?: plataformas.first().id) }
    var valor by rememberSaveable { mutableStateOf(inicial?.valorCentavos?.campo() ?: "") }
    var gorjeta by rememberSaveable {
        mutableStateOf(inicial?.gorjetaCentavos?.takeIf { it > 0 }?.campo() ?: "")
    }
    var km by rememberSaveable {
        mutableStateOf(inicial?.let { (it.distanciaMetros + it.deslocamentoMetros).campoKm() } ?: "")
    }
    var minutos by rememberSaveable { mutableStateOf(inicial?.let { (it.duracaoSegundos / 60).toString() } ?: "") }
    var origem by rememberSaveable { mutableStateOf(inicial?.origem ?: "") }
    var destino by rememberSaveable { mutableStateOf(inicial?.destino ?: "") }
    var tentou by rememberSaveable { mutableStateOf(false) }

    val valorC = valor.paraCentavos()
    val gorjetaC = if (gorjeta.isBlank()) 0L else gorjeta.paraCentavos()
    val metros = km.litrosParaMl()          // mesma conversão: "12,4" -> 12400
    val min = minutos.trim().toLongOrNull()?.takeIf { it > 0 }
    val plataformaAtual = plataformas.firstOrNull { it.id == plataformaId } ?: plataformas.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (inicial == null) "Nova corrida" else "Editar corrida") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChipsSelecao(
                    opcoes = plataformas,
                    selecionado = plataformaAtual,
                    rotulo = { it.nome },
                    onSelecionar = { plataformaId = it.id }
                )
                CampoFormulario("Valor recebido (R$) *", valor, { valor = it }, erro = tentou && valorC == null)
                CampoFormulario("Gorjeta (R$)", gorjeta, { gorjeta = it }, erro = gorjeta.isNotBlank() && gorjetaC == null)
                CampoFormulario("Distância total (km) *", km, { km = it }, erro = tentou && metros == null)
                CampoFormulario(
                    "Duração (min) *", minutos, { minutos = it.filter(Char::isDigit) },
                    erro = tentou && min == null
                )
                CampoFormulario("Origem", origem, { origem = it }, numerico = false)
                CampoFormulario("Destino", destino, { destino = it }, numerico = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tentou = true
                if (valorC != null && gorjetaC != null && metros != null && min != null) {
                    val base = inicial ?: Corrida(
                        plataformaId = plataformaAtual.id, origem = "", destino = "",
                        valorCentavos = valorC, distanciaMetros = metros, duracaoSegundos = min * 60,
                        inicioEm = System.currentTimeMillis()
                    )
                    onSalvar(
                        base.copy(
                            plataformaId = plataformaAtual.id,
                            valorCentavos = valorC,
                            gorjetaCentavos = gorjetaC,
                            distanciaMetros = metros,
                            deslocamentoMetros = 0,
                            duracaoSegundos = min * 60,
                            origem = origem.trim().ifEmpty { "—" },
                            destino = destino.trim()
                        )
                    )
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Confirmação genérica de exclusão. */
@Composable
fun ConfirmarExclusao(texto: String, onDismiss: () -> Unit, onConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excluir?") },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
