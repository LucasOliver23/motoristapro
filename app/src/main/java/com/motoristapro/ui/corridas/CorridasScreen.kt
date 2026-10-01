package com.motoristapro.ui.corridas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.OfertaRecebida
import com.motoristapro.data.local.model.CorridaComPlataforma
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.ChipsSelecao
import com.motoristapro.ui.componentes.ConfirmarExclusao
import com.motoristapro.ui.componentes.CorridaDialog
import com.motoristapro.ui.componentes.Metrica
import com.motoristapro.ui.componentes.TelaAba
import com.motoristapro.ui.componentes.Vazio
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.VermelhoPrejuizo
import com.motoristapro.ui.theme.TextoSecundario
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val PT = Locale("pt", "BR")
private val FORMATO_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm", PT)

/** Cor da etiqueta por plataforma (usa a cor cadastrada; Uber preta ganha contorno claro). */
private fun corPlataforma(hex: String?): Color = try {
    if (hex.isNullOrBlank()) Lima else Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Lima
}

@Composable
fun CorridasRoute(vm: CorridasViewModel = viewModel(factory = CorridasViewModel.Factory)) {
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val modo by vm.modo.collectAsStateWithLifecycle()
    val ofertas by vm.ofertasState.collectAsStateWithLifecycle()
    val plataformas by vm.plataformas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var editando by remember { mutableStateOf<Corrida?>(null) }
    var nova by remember { mutableStateOf(false) }
    var excluir by remember { mutableStateOf<CorridaComPlataforma?>(null) }

    LaunchedEffect(Unit) { vm.mensagens.collect { snackbar.showSnackbar(it) } }

    val coresPorId = remember(plataformas) { plataformas.associate { it.id to corPlataforma(it.corHex) } }

    TelaAba(
        titulo = "Corridas",
        subtitulo = if (modo == ModoLista.CORRIDAS) "${estado.resumo.qtdCorridas} no período"
        else "${ofertas.resumo.total} ofertas no período",
        snackbar = snackbar,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nova = true },
                containerColor = Lima,
                contentColor = Color(0xFF0A0D0B),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Corrida", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ChipsSelecao(
                    opcoes = ModoLista.entries.toList(),
                    selecionado = modo,
                    rotulo = { it.rotulo },
                    onSelecionar = vm::selecionarModo
                )
            }
            item {
                ChipsSelecao(
                    opcoes = FiltroPeriodo.entries.toList(),
                    selecionado = estado.filtro,
                    rotulo = { it.rotulo },
                    onSelecionar = vm::selecionar
                )
            }
            if (modo == ModoLista.OFERTAS) {
                item { ResumoOfertasCard(ofertas) }
                if (ofertas.ofertas.isEmpty()) {
                    item { Vazio("Nenhuma oferta lida neste período.\nAs ofertas aparecem aqui assim que o leitor mostra a janela.") }
                }
                items(ofertas.ofertas, key = { "o" + it.id }) { OfertaItem(it) }
                return@LazyColumn
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metrica("Faturamento", estado.resumo.faturamentoCentavos.emReais(), Modifier.weight(1f))
                    Metrica(
                        "Km", String.format(PT, "%.1f", estado.resumo.kmRodados), Modifier.weight(1f),
                        detalhe = if (estado.resumo.metrosRodados > 0) "${estado.resumo.ganhoPorKmCentavos.emReais()}/km" else null
                    )
                }
            }
            if (!estado.carregando && estado.corridas.isEmpty()) {
                item { Vazio("Nenhuma corrida neste período.\nUse o botão \"Registrar\" na janela da oferta ou o + abaixo.") }
            }
            items(estado.corridas, key = { it.corrida.id }) { item ->
                CorridaItem(
                    item = item,
                    cor = coresPorId[item.corrida.plataformaId] ?: Lima,
                    onClick = { editando = item.corrida },
                    onExcluir = { excluir = item }
                )
            }
        }
    }

    if (nova || editando != null) {
        CorridaDialog(
            plataformas = plataformas,
            inicial = editando,
            onDismiss = { nova = false; editando = null },
            onSalvar = { vm.salvar(it); nova = false; editando = null }
        )
    }
    excluir?.let { alvo ->
        ConfirmarExclusao(
            texto = "Excluir a corrida de ${alvo.corrida.receitaCentavos.emReais()} (${alvo.plataformaNome})?",
            onDismiss = { excluir = null },
            onConfirmar = { vm.excluir(alvo.corrida.id); excluir = null }
        )
    }
}

@Composable
private fun CorridaItem(item: CorridaComPlataforma, cor: Color, onClick: () -> Unit, onExcluir: () -> Unit) {
    val c = item.corrida
    val km = (c.distanciaMetros + c.deslocamentoMetros) / 1000.0
    val porKm = if (km > 0) c.receitaCentavos / km / 100.0 else 0.0
    val hora = Instant.ofEpochMilli(c.inicioEm).atZone(ZoneId.systemDefault()).format(FORMATO_HORA)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = cor.copy(alpha = 0.25f)) {
                        Text(
                            item.plataformaNome,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (cor == Color.Black) Color.White else cor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(hora, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
                Text(
                    String.format(PT, "%.1f km • %d min • R$ %.2f/km", km, c.duracaoSegundos / 60, porKm),
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                if (c.destino.isNotBlank() || c.origem != "Leitor de ofertas") {
                    Text(
                        listOf(c.origem, c.destino).filter { it.isNotBlank() && it != "—" }.joinToString(" → "),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1
                    )
                }
            }
            Text(
                c.receitaCentavos.emReais(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Lima
            )
            IconButton(onClick = onExcluir) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = TextoSecundario)
            }
        }
    }
}

@Composable
private fun ResumoOfertasCard(e: OfertasUiState) {
    val r = e.resumo
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metrica(
            "Recebidas", "${r.total}", Modifier.weight(1f),
            detalhe = "${r.boas} boas • ${r.medias} médias • ${r.ruins} ruins"
        )
        Metrica(
            "Registradas", "${r.registradas}", Modifier.weight(1f),
            detalhe = if (r.total > 0) "${r.registradas * 100 / r.total}% das ofertas" else null
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metrica(
            "R$/km registradas", if (r.metrosRegistradas > 0) r.reaisKmRegistradas.emReais() else "—",
            Modifier.weight(1f), cor = Lima
        )
        Metrica(
            "R$/km das outras", if (r.metrosNaoRegistradas > 0) r.reaisKmNaoRegistradas.emReais() else "—",
            Modifier.weight(1f), detalhe = "recusadas/não registradas"
        )
    }
    }
}

@Composable
private fun OfertaItem(o: OfertaRecebida) {
    val hora = Instant.ofEpochMilli(o.recebidaEm).atZone(ZoneId.systemDefault()).format(FORMATO_HORA)
    val cor = when (o.classificacao) {
        "BOA" -> Lima
        "MEDIA" -> AmareloAlerta
        else -> VermelhoPrejuizo
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = cor, modifier = Modifier.padding(end = 10.dp)) {
                Text(
                    when (o.classificacao) { "BOA" -> "BOA"; "MEDIA" -> "MÉDIA"; else -> "RUIM" },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF0A0D0B),
                    fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${o.plataforma} • $hora", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                val partes = mutableListOf(String.format(PT, "%.1f km", o.km))
                if (o.minutos > 0) partes += "${o.minutos} min"
                partes += "${o.reaisPorKmCentavos.emReais()}/km"
                if (o.paradas > 1) partes += "${o.paradas} entregas"
                Text(partes.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(o.valorCentavos.emReais(), fontWeight = FontWeight.Bold)
                if (o.registrada) Text("registrada ✓", style = MaterialTheme.typography.labelSmall, color = Lima)
            }
        }
    }
}
