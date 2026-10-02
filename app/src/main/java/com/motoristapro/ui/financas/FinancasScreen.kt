package com.motoristapro.ui.financas

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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.BarraHorizontal
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.ConfirmarExclusao
import com.motoristapro.ui.componentes.DespesaDialog
import com.motoristapro.ui.componentes.LinhaValor
import com.motoristapro.ui.componentes.ProgressoMeta
import com.motoristapro.ui.componentes.TelaAba
import com.motoristapro.ui.componentes.Vazio
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val PT = Locale("pt", "BR")
private val FORMATO_MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT)
private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM HH:mm", PT)

@Composable
fun FinancasRoute(vm: FinancasViewModel = viewModel(factory = FinancasViewModel.Factory)) {
    val mes by vm.mesState.collectAsStateWithLifecycle()
    val metas by vm.metasState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var editando by remember { mutableStateOf<Despesa?>(null) }
    var nova by remember { mutableStateOf(false) }
    var excluir by remember { mutableStateOf<Despesa?>(null) }

    LaunchedEffect(Unit) { vm.mensagens.collect { snackbar.showSnackbar(it) } }

    TelaAba(
        titulo = "Finanças",
        subtitulo = "Ganhos, despesas e metas",
        snackbar = snackbar,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nova = true },
                containerColor = Lima,
                contentColor = Color(0xFF0A0D0B),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Despesa", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SeletorMes(mes.mes, vm::mesAnterior, vm::proximoMes) }

            item {
                val r = mes.resumo
                CardSecao(titulo = "Resultado do mês", destaque = true) {
                    Text(
                        r.lucroLiquidoCentavos.emReais(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (r.lucroLiquidoCentavos >= 0) Lima else VermelhoPrejuizo
                    )
                    LinhaValor("Faturamento", r.faturamentoCentavos.emReais())
                    LinhaValor("Despesas", "- " + r.despesasCentavos.emReais(), cor = VermelhoPrejuizo)
                    if (mes.custoFixoMensal > 0) {
                        LinhaValor("Custos fixos do mês", "- " + mes.custoFixoMensal.emReais(), cor = VermelhoPrejuizo)
                        LinhaValor(
                            "Lucro real", mes.lucroRealCentavos.emReais(),
                            cor = if (mes.lucroRealCentavos >= 0) Lima else VermelhoPrejuizo, negrito = true
                        )
                    }
                    LinhaValor("Corridas", "${r.qtdCorridas}")
                    LinhaValor("Km rodados", String.format(PT, "%.1f km", r.kmRodados))
                }
            }

            item {
                val cfg = metas.config
                CardSecao(titulo = "Metas de lucro") {
                    ProgressoMeta("Hoje", metas.hoje, cfg.metaLucroDiarioCentavos, metas.hoje.emReais(), cfg.metaLucroDiarioCentavos.emReais())
                    ProgressoMeta("Esta semana", metas.semana, cfg.metaLucroSemanalCentavos, metas.semana.emReais(), cfg.metaLucroSemanalCentavos.emReais())
                    ProgressoMeta("Este mês", metas.mes, cfg.metaLucroMensalCentavos, metas.mes.emReais(), cfg.metaLucroMensalCentavos.emReais())
                    Text("Altere as metas na aba Mais.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
            }

            item {
                CardSecao(titulo = "Despesas por categoria") {
                    if (mes.porCategoria.isEmpty()) {
                        Text("Sem despesas neste mês.", color = TextoSecundario)
                    } else {
                        val total = mes.porCategoria.sumOf { it.totalCentavos }.coerceAtLeast(1)
                        mes.porCategoria.forEach { c ->
                            BarraHorizontal(
                                rotulo = c.categoria.rotulo,
                                valorTexto = c.totalCentavos.emReais(),
                                fracao = c.totalCentavos.toFloat() / total,
                                detalhe = "${c.totalCentavos * 100 / total}% das despesas",
                                cor = VermelhoPrejuizo
                            )
                        }
                    }
                }
            }

            item {
                // As ENTRADAS entram sozinhas (cada corrida registrada) e por isso
                // não viram linha aqui — seriam centenas. O que se lança na mão é
                // a saída. A linha abaixo deixa claro que as duas estão contadas.
                Column(Modifier.padding(top = 4.dp)) {
                    Text(
                        "Lançamentos",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextoSecundario
                    )
                    val r = mes.resumo
                    Text(
                        "Entradas: ${r.qtdCorridas} corrida(s) • ${r.faturamentoCentavos.emReais()} " +
                            "(automático, da aba Corridas)",
                        style = MaterialTheme.typography.labelSmall, color = Lima
                    )
                    Text(
                        "Saídas: ${mes.despesas.size} lançamento(s) • ${r.despesasCentavos.emReais()}",
                        style = MaterialTheme.typography.labelSmall, color = VermelhoPrejuizo
                    )
                }
            }
            if (mes.despesas.isEmpty()) {
                item { Vazio("Nenhuma despesa neste mês.") }
            }
            items(mes.despesas, key = { it.id }) { d ->
                DespesaItem(d, onClick = { editando = d }, onExcluir = { excluir = d })
            }
        }
    }

    if (nova || editando != null) {
        DespesaDialog(
            inicial = editando,
            onDismiss = { nova = false; editando = null },
            onSalvar = { vm.salvar(it); nova = false; editando = null }
        )
    }
    excluir?.let { alvo ->
        ConfirmarExclusao(
            texto = "Excluir ${alvo.categoria.rotulo} de ${alvo.valorCentavos.emReais()}?",
            onDismiss = { excluir = null },
            onConfirmar = { vm.excluir(alvo.id); excluir = null }
        )
    }
}

@Composable
private fun SeletorMes(mes: YearMonth, onAnterior: () -> Unit, onProximo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onAnterior) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior") }
        Text(
            mes.format(FORMATO_MES).replaceFirstChar { it.uppercase() },
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onProximo, enabled = mes.isBefore(YearMonth.now())) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Próximo mês")
        }
    }
}

@Composable
private fun DespesaItem(d: Despesa, onClick: () -> Unit, onExcluir: () -> Unit) {
    val data = Instant.ofEpochMilli(d.dataEm).atZone(ZoneId.systemDefault()).format(FORMATO_DATA)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.categoria.rotulo, fontWeight = FontWeight.SemiBold)
                val extra = buildList {
                    add(data)
                    d.litrosMl?.let { add(String.format(PT, "%.1f L", it / 1000.0)) }
                    d.descricao?.let { add(it) }
                }.joinToString(" • ")
                Text(extra, style = MaterialTheme.typography.bodySmall, color = TextoSecundario, maxLines = 1)
            }
            Text(d.valorCentavos.emReais(), color = VermelhoPrejuizo, fontWeight = FontWeight.Bold)
            IconButton(onClick = onExcluir) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = TextoSecundario)
            }
        }
    }
}
