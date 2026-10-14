package com.motoristapro.ui.financas

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import com.motoristapro.ui.componentes.BarraMeta
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.CartaoDestaque
import com.motoristapro.ui.componentes.DivisorLista
import com.motoristapro.ui.componentes.Pilula
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.componentes.ValorComCentavos
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.EstiloValor
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.vector.ImageVector
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.componentes.SeloIcone
import com.motoristapro.ui.theme.Laranja
import com.motoristapro.ui.theme.AzulInfo
import com.motoristapro.ui.theme.Roxo
import com.motoristapro.ui.theme.Turquesa
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.motoristapro.data.local.entity.CategoriaDespesa
import com.motoristapro.ui.componentes.LogoPlataforma
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta

/** O selo de cada categoria de despesa: ícone de traço na cor da categoria. */
private fun iconeDeTraco(c: CategoriaDespesa): Pair<ImageVector, Color> = when (c) {
    CategoriaDespesa.COMBUSTIVEL -> Icones.Combustivel to Laranja
    CategoriaDespesa.MANUTENCAO -> Icones.Chave to AzulInfo
    CategoriaDespesa.SEGURO -> Icones.Escudo to Turquesa
    CategoriaDespesa.OUTROS -> Icones.Alfinete to Roxo
}

private val PT = Locale("pt", "BR")
private val FORMATO_MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT)
private val FORMATO_SO_MES = DateTimeFormatter.ofPattern("MMMM", PT)
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
        snackbar = snackbar,
        acoes = { SeletorMes(mes.mes, vm::mesAnterior, vm::proximoMes) },
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
            item { CartaoDoMes(mes, metas.config.metaLucroMensalCentavos) }

            item { CartaoCategorias(mes) }

            item {
                // Entradas e saídas no MESMO extrato. A corrida registrada entra
                // sozinha (vem da aba Corridas) e a despesa entra na mão — mas
                // para quem olha é um extrato só, em ordem de tempo.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FiltroLancamento.entries.forEach { f ->
                        Pilula(f.rotulo, mes.filtro == f) { vm.filtrarLancamentos(f) }
                    }
                }
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
            itemsIndexed(lancamentos, key = { _, l -> chaveDoLancamento(l) }) { i, l ->
                // Cada linha é um pedaço do mesmo cartão: canto redondo só na
                // primeira e na última, linha fina entre elas.
                val forma = RoundedCornerShape(
                    topStart = if (i == 0) 20.dp else 0.dp, topEnd = if (i == 0) 20.dp else 0.dp,
                    bottomStart = if (i == lancamentos.lastIndex) 20.dp else 0.dp,
                    bottomEnd = if (i == lancamentos.lastIndex) 20.dp else 0.dp
                )
                Column(Modifier.fillMaxWidth().clip(forma).background(Superficie).padding(horizontal = 14.dp)) {
                    if (i > 0) DivisorLista()
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

            item {
                val cfg = metas.config
                Cartao(Modifier.padding(top = 8.dp), espaco = 10) {
                    Rotulo("Metas de lucro")
                    ProgressoMeta("Hoje", metas.hoje, cfg.metaLucroDiarioCentavos, metas.hoje.emReais(), cfg.metaLucroDiarioCentavos.emReais())
                    ProgressoMeta("Esta semana", metas.semana, cfg.metaLucroSemanalCentavos, metas.semana.emReais(), cfg.metaLucroSemanalCentavos.emReais())
                    ProgressoMeta("Este mês", metas.mes, cfg.metaLucroMensalCentavos, metas.mes.emReais(), cfg.metaLucroMensalCentavos.emReais())
                    // Só leitura, de propósito: a meta mensal é definida UMA vez no
                    // assistente de custos, e a diária e a semanal saem dela.
                    Text(
                        if (cfg.metaLucroMensalCentavos > 0)
                            "A meta do mês é definida em Menu ▸ Meu veículo e custos. A diária e a semanal saem dela."
                        else "Defina sua meta em Menu ▸ Meu veículo e custos.",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
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
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        // O logo do app, não um ícone genérico: é por ele que o motorista
        // reconhece a entrada sem ler o nome.
        LogoPlataforma(item.plataformaNome, tamanho = 36.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Corrida · ${item.plataformaNome}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOf(data, String.format(PT, "%.1f km", c.kmTotal)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        Text("+ " + c.receitaCentavos.emReais().removePrefix("R$ "), style = EstiloValor, fontSize = 16.sp, color = Lima)
    }
}

@Composable
private fun SeletorMes(mes: YearMonth, onAnterior: () -> Unit, onProximo: () -> Unit) {
    // Compacto, no canto da barra de cima ("‹ Outubro ›"), como no desenho.
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onAnterior) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior") }
        Text(
            (if (mes.year == YearMonth.now().year) mes.format(FORMATO_SO_MES) else mes.format(FORMATO_MES))
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
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
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        iconeDeTraco(d.categoria).let { (icone, cor) -> SeloIcone(icone, cor, tamanho = 36.dp) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(d.descricao?.takeIf { it.isNotBlank() } ?: d.categoria.rotulo,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val extra = buildList {
                add(data)
                d.litrosMl?.let { add(String.format(PT, "%.1f L", it / 1000.0)) }
                if (!d.descricao.isNullOrBlank()) add(d.categoria.rotulo)
            }.joinToString(" · ")
            Text(extra, style = MaterialTheme.typography.bodySmall, color = TextoSecundario, maxLines = 1)
        }
        Text("− " + d.valorCentavos.emReais().removePrefix("R$ "), style = EstiloValor, fontSize = 16.sp)
        IconButton(onClick = onExcluir) {
            Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = TextoSecundario, modifier = Modifier.size(18.dp))
        }
    }
}

// ------------------------------------------------------------------ visual novo (4.0)

/** O cartão de cima: lucro do mês, quanto entrou, quanto saiu e a meta do mês. */
@Composable
private fun CartaoDoMes(mes: MesUiState, metaMensal: Long) {
    val r = mes.resumo
    val temFixo = mes.custoFixoMensal > 0
    val lucro = if (temFixo) mes.lucroRealCentavos else r.lucroLiquidoCentavos
    CartaoDestaque {
        Rotulo(if (temFixo) "Lucro real do mês" else "Lucro do mês")
        ValorComCentavos(lucro.emReais(), 38.sp, cor = if (lucro >= 0) TextoPrincipal else VermelhoPrejuizo)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("↑ " + r.faturamentoCentavos.emReais(), style = MaterialTheme.typography.bodySmall, color = Lima)
            Text(
                "↓ " + (r.despesasCentavos + mes.custoFixoMensal).emReais(),
                style = MaterialTheme.typography.bodySmall, color = VermelhoPrejuizo
            )
        }
        if (metaMensal > 0) {
            BarraMeta(lucro.toFloat() / metaMensal, Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${(lucro.coerceAtLeast(0) * 100 / metaMensal)}% da meta do mês",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                Box(Modifier.weight(1f))
                Text(metaMensal.emReais(), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
        }
        val detalhe = buildList {
            add("${r.qtdCorridas} corridas")
            add(String.format(PT, "%.0f km", r.kmRodados))
            if (temFixo) add("custos fixos ${mes.custoFixoMensal.emReais()}")
        }.joinToString(" · ")
        Text(detalhe, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
    }
}

/** Para onde foi o dinheiro: uma barra dividida pelas cores e a legenda em duas colunas. */
@Composable
private fun CartaoCategorias(mes: MesUiState) {
    Cartao(espaco = 12) {
        Rotulo("Gastos por categoria")
        val cats = mes.porCategoria.filter { it.totalCentavos > 0 }
        if (cats.isEmpty()) {
            Text("Sem despesas neste mês.", style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            return@Cartao
        }
        Row(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            cats.forEach { c ->
                Box(
                    Modifier.weight(c.totalCentavos.toFloat()).fillMaxHeight()
                        .background(iconeDeTraco(c.categoria).second)
                )
            }
        }
        cats.chunked(2).forEach { par ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                par.forEach { c ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(iconeDeTraco(c.categoria).second))
                        Text(
                            c.categoria.rotulo, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 1
                        )
                        Text(
                            c.totalCentavos.emReais().removePrefix("R$ "),
                            style = EstiloValor, fontSize = 13.sp
                        )
                    }
                }
                if (par.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}
