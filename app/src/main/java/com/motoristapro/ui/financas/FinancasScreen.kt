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
import com.motoristapro.data.local.model.CorridaComPlataforma
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.BarraHorizontal
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.ChipsSelecao
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
        subtitulo = "Entradas, saídas e metas",
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
                CardSecao(titulo = "RESULTADO DO MÊS", centralizado = true) {
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
                CardSecao(titulo = "METAS DE LUCRO") {
                    ProgressoMeta("Hoje", metas.hoje, cfg.metaLucroDiarioCentavos, metas.hoje.emReais(), cfg.metaLucroDiarioCentavos.emReais())
                    ProgressoMeta("Esta semana", metas.semana, cfg.metaLucroSemanalCentavos, metas.semana.emReais(), cfg.metaLucroSemanalCentavos.emReais())
                    ProgressoMeta("Este mês", metas.mes, cfg.metaLucroMensalCentavos, metas.mes.emReais(), cfg.metaLucroMensalCentavos.emReais())
                    // Só leitura, de propósito: a meta mensal é definida UMA vez no
                    // assistente de custos, e a diária e a semanal saem dela. Poder
                    // editar aqui também deixaria dois números discordando.
                    Text(
                        if (cfg.metaLucroMensalCentavos > 0)
                            "Meta do mês: ${cfg.metaLucroMensalCentavos.emReais()} — definida em " +
                                "Mais ▸ Meu veículo e custos. A diária e a semanal saem dela."
                        else "Defina sua meta em Mais ▸ Meu veículo e custos.",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                }
            }

            item {
                CardSecao(titulo = "DESPESAS POR CATEGORIA") {
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
                // Entradas e saídas no MESMO extrato. A corrida registrada entra
                // sozinha (vem da aba Corridas) e a despesa entra na mão — mas
                // para quem olha é um extrato só, em ordem de tempo.
                Column(Modifier.padding(top = 4.dp)) {
                    Text(
                        "Lançamentos",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextoSecundario
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "+ ${mes.totalEntradasCentavos.emReais()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Lima, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "- ${mes.totalSaidasCentavos.emReais()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = VermelhoPrejuizo, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            item {
                ChipsSelecao(
                    opcoes = FiltroLancamento.entries.toList(),
                    selecionado = mes.filtro,
                    rotulo = { it.rotulo },
                    onSelecionar = vm::filtrarLancamentos
                )
            }
            val lancamentos = mes.lancamentos
            if (lancamentos.isEmpty()) {
                item {
                    Vazio(
                        when (mes.filtro) {
                            FiltroLancamento.ENTRADAS -> "Nenhuma corrida registrada neste mês."
                            FiltroLancamento.SAIDAS -> "Nenhuma despesa neste mês."
                            FiltroLancamento.TUDO -> "Nenhum lançamento neste mês."
                        }
                    )
                }
            }
            items(lancamentos, key = { chaveDoLancamento(it) }) { l ->
                when (l) {
                    is Lancamento.Entrada -> EntradaItem(l.corrida)
                    is Lancamento.Saida -> DespesaItem(
                        l.despesa,
                        onClick = { editando = l.despesa },
                        onExcluir = { excluir = l.despesa }
                    )
                }
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

/** Chave estável da lista: corrida e despesa podem ter o mesmo id. */
private fun chaveDoLancamento(l: Lancamento): String = when (l) {
    is Lancamento.Entrada -> "c" + l.corrida.corrida.id
    is Lancamento.Saida -> "d" + l.despesa.id
}

/**
 * Uma entrada do extrato: a corrida como ela foi registrada.
 *
 * Sem lixeira e sem editar de propósito — isso se faz na aba Corridas, onde a
 * corrida tem plataforma, km e tempo para conferir antes de mexer.
 */
@Composable
private fun EntradaItem(item: CorridaComPlataforma) {
    val c = item.corrida
    val data = Instant.ofEpochMilli(c.inicioEm).atZone(ZoneId.systemDefault()).format(FORMATO_DATA)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.plataformaNome, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(data, String.format(PT, "%.1f km", c.kmTotal)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
            }
            Text(
                "+ " + c.receitaCentavos.emReais(),
                fontWeight = FontWeight.Bold, color = Lima
            )
        }
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
