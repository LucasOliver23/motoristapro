package com.motoristapro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.SuperficieMaisAlta
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.VermelhoPrejuizo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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

/**
 * Nova corrida (manual) / edição: uma folha que sobe de baixo.
 *
 * O app é escolhido pelo LOGO (os chips de texto cortavam o nome no meio) e o
 * R$/km e R$/hora da corrida aparecem enquanto o motorista digita — é a conta
 * que ele faria de cabeça para saber se valeu.
 */
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
    var origem by rememberSaveable { mutableStateOf(inicial?.origem?.takeIf { it != "—" } ?: "") }
    var destino by rememberSaveable { mutableStateOf(inicial?.destino ?: "") }
    var tentou by rememberSaveable { mutableStateOf(false) }

    val valorC = valor.paraCentavos()
    val gorjetaC = if (gorjeta.isBlank()) 0L else gorjeta.paraCentavos()
    val metros = km.litrosParaMl()          // mesma conversão: "12,4" -> 12400
    val min = minutos.trim().toLongOrNull()?.takeIf { it > 0 }
    val plataformaAtual = plataformas.firstOrNull { it.id == plataformaId } ?: plataformas.first()

    val salvar = {
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
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Fundo escuro que fecha ao toque; a folha por cima segura os próprios toques.
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(Superficie)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(SuperficieMaisAlta)
                )
                Text(
                    if (inicial == null) "Nova corrida" else "Editar corrida",
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                )
                Rotulo("De qual app")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    plataformas.forEach { p ->
                        val sel = p.id == plataformaAtual.id
                        Column(
                            Modifier.width(62.dp).clip(RoundedCornerShape(14.dp)).clickable { plataformaId = p.id }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                Modifier.size(50.dp).clip(RoundedCornerShape(15.dp))
                                    .border(2.dp, if (sel) Lima else Color.Transparent, RoundedCornerShape(15.dp))
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (chaveDaMarca(p.nome) in MARCAS_COM_LOGO) {
                                    LogoPlataforma(p.nome, tamanho = 44.dp)
                                } else {
                                    SeloIcone(Icones.Menu, TextoSecundario, tamanho = 44.dp)
                                }
                            }
                            Text(
                                p.nome, fontSize = 11.sp, maxLines = 1,
                                color = if (sel) TextoPrincipal else TextoSecundario,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                CampoFormulario(
                    "Valor recebido *", valor, { valor = it },
                    erro = tentou && valorC == null, prefixo = "R$"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoFormulario(
                        "Distância (km) *", km, { km = it },
                        erro = tentou && metros == null, modifier = Modifier.weight(1f)
                    )
                    CampoFormulario(
                        "Duração (min) *", minutos, { minutos = it.filter(Char::isDigit) },
                        erro = tentou && min == null, modifier = Modifier.weight(1f)
                    )
                }
                // R$/km e R$/h desta corrida, ao vivo.
                if (valorC != null && valorC > 0 && metros != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lima.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Esta corrida:", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        Text("${(valorC * 1000 / metros).emReais()}/km", style = EstiloValor, fontSize = 15.sp, color = Lima)
                        if (min != null) {
                            Text("·", color = TextoSecundario)
                            Text("${(valorC * 60 / min).emReais()}/h", style = EstiloValor, fontSize = 15.sp)
                        }
                    }
                }
                CampoFormulario(
                    "Gorjeta (opcional)", gorjeta, { gorjeta = it },
                    erro = gorjeta.isNotBlank() && gorjetaC == null, prefixo = "R$"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoFormulario("Origem", origem, { origem = it }, numerico = false, modifier = Modifier.weight(1f))
                    CampoFormulario("Destino", destino, { destino = it }, numerico = false, modifier = Modifier.weight(1f))
                }
                if (tentou && (valorC == null || metros == null || min == null)) {
                    Text(
                        "Preencha valor, distância e duração.",
                        style = MaterialTheme.typography.bodySmall, color = VermelhoPrejuizo
                    )
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuperficieAlta, contentColor = TextoPrincipal)
                    ) { Text("Cancelar", fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { salvar() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = CorSobreDestaque)
                    ) { Text(if (inicial == null) "Salvar corrida" else "Salvar", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

/** As marcas que têm logo de verdade em res/drawable-nodpi; o resto ganha o ícone de pessoa. */
private val MARCAS_COM_LOGO = setOf("uber", "99", "indrive", "ifood")

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
