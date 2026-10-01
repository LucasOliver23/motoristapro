package com.motoristapro.ui.relatorios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.centavosEmReais
import com.motoristapro.ui.componentes.BarraHorizontal
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.ChipsSelecao
import com.motoristapro.ui.componentes.GraficoBarras
import com.motoristapro.ui.componentes.LinhaValor
import com.motoristapro.ui.componentes.Metrica
import com.motoristapro.ui.componentes.TelaAba
import com.motoristapro.ui.formatarDuracao
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.util.Locale
import androidx.compose.runtime.getValue

private val PT = Locale("pt", "BR")

@Composable
fun RelatoriosRoute(vm: RelatoriosViewModel = viewModel(factory = RelatoriosViewModel.Factory)) {
    val e by vm.uiState.collectAsStateWithLifecycle()

    TelaAba(titulo = "Relatórios", subtitulo = "Desempenho por período") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChipsSelecao(
                opcoes = PeriodoRelatorio.entries.toList(),
                selecionado = e.periodo,
                rotulo = { it.rotulo },
                onSelecionar = vm::selecionar
            )

            // ---------------- resumo
            val r = e.resumo
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metrica(
                    "Lucro", r.lucroLiquidoCentavos.emReais(), Modifier.weight(1f),
                    cor = if (r.lucroLiquidoCentavos >= 0) Lima else VermelhoPrejuizo,
                    detalhe = "${r.faturamentoCentavos.emReais()} faturado"
                )
                Metrica("Corridas", "${r.qtdCorridas}", Modifier.weight(1f), detalhe = String.format(PT, "%.0f km", r.kmRodados))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metrica(
                    "R$ / km", if (r.metrosRodados > 0) r.ganhoPorKmCentavos.emReais() else "—", Modifier.weight(1f)
                )
                Metrica(
                    "R$ / h em corrida", if (r.segundosEmCorrida > 0) r.ganhoPorHoraCentavos.emReais() else "—",
                    Modifier.weight(1f), detalhe = if (r.segundosEmCorrida > 0) r.segundosEmCorrida.formatarDuracao() else null
                )
            }

            if (e.custoFixoPeriodo > 0) {
                CardSecao(titulo = "Lucro real (com custos fixos)") {
                    LinhaValor("Lucro líquido", r.lucroLiquidoCentavos.emReais())
                    LinhaValor(
                        "Custos fixos (${e.diasTrabalhados} dias trabalhados)",
                        "- " + e.custoFixoPeriodo.emReais(), cor = VermelhoPrejuizo
                    )
                    LinhaValor(
                        "Lucro real", e.lucroRealCentavos.emReais(),
                        cor = if (e.lucroRealCentavos >= 0) Lima else VermelhoPrejuizo, negrito = true
                    )
                }
            }

            // ---------------- ofertas recebidas
            if (e.ofertas.total > 0) {
                val o = e.ofertas
                CardSecao(titulo = "Ofertas recebidas") {
                    LinhaValor("Recebidas", "${o.total}")
                    LinhaValor("Registradas", "${o.registradas} (${o.registradas * 100 / o.total}%)")
                    BarraHorizontal("Boas", "${o.boas}", o.boas.toFloat() / o.total)
                    BarraHorizontal("Médias", "${o.medias}", o.medias.toFloat() / o.total, cor = com.motoristapro.ui.theme.AmareloAlerta)
                    BarraHorizontal("Ruins", "${o.ruins}", o.ruins.toFloat() / o.total, cor = VermelhoPrejuizo)
                    LinhaValor("R$/km das registradas", if (o.metrosRegistradas > 0) o.reaisKmRegistradas.emReais() else "—")
                    LinhaValor("R$/km das recusadas", if (o.metrosNaoRegistradas > 0) o.reaisKmNaoRegistradas.emReais() else "—")
                }
            }

            // ---------------- lucro por dia
            CardSecao(titulo = "Lucro por dia") {
                val valores = e.lucroPorDia.map { it.second / 100f }
                val passo = when {
                    e.lucroPorDia.size <= 7 -> 1
                    e.lucroPorDia.size <= 15 -> 2
                    else -> 5
                }
                val rotulos = e.lucroPorDia.mapIndexed { i, (d, _) ->
                    if (i % passo == 0 || i == e.lucroPorDia.lastIndex) d.dayOfMonth.toString() else ""
                }
                GraficoBarras(valores = valores, rotulos = rotulos)
                val melhorDia = e.lucroPorDia.maxByOrNull { it.second }
                if (melhorDia != null && melhorDia.second > 0) {
                    Text(
                        "Melhor dia: ${melhorDia.first.dayOfMonth}/${melhorDia.first.monthValue} • ${melhorDia.second.emReais()}",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                }
            }

            // ---------------- plataformas
            CardSecao(titulo = "Por plataforma") {
                if (e.plataformas.isEmpty()) {
                    Text("Sem corridas no período.", color = TextoSecundario)
                } else {
                    val maior = e.plataformas.maxOf { it.receitaCentavos }.coerceAtLeast(1)
                    e.plataformas.forEach { p ->
                        BarraHorizontal(
                            rotulo = "${p.plataforma} • ${p.qtdCorridas} corridas",
                            valorTexto = p.receitaCentavos.emReais(),
                            fracao = p.receitaCentavos.toFloat() / maior,
                            detalhe = p.ganhoKmCentavos?.let { "${it.centavosEmReais()}/km" }
                        )
                    }
                }
            }

            // ---------------- melhores horários
            CardSecao(titulo = "Melhores horários") {
                val porHora = LongArray(24)
                e.porHora.forEach { if (it.hora in 0..23) porHora[it.hora] = it.ganhoPorHoraCentavos }
                val melhor = e.melhorHora
                GraficoBarras(
                    valores = porHora.map { it / 100f },
                    rotulos = (0..23).map { if (it % 3 == 0) "${it}h" else "" },
                    altura = 120.dp,
                    destaque = melhor?.hora ?: -1
                )
                if (melhor != null) {
                    Text(
                        "Melhor faixa: ${melhor.hora}h–${melhor.hora + 1}h • ${melhor.ganhoPorHoraCentavos.emReais()}/h em corrida (${melhor.qtdCorridas} corridas)",
                        style = MaterialTheme.typography.bodySmall, color = Lima
                    )
                } else {
                    Text("Registre corridas para descobrir seus melhores horários.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }

                // O que o MERCADO ofereceu, faixa a faixa. Conta toda oferta lida — aceita
                // ou não —, então enche de dados muito antes do histórico de corridas.
                if (e.faixas.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "O que apareceu na tela, por faixa de 2 h",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                    )
                    val teto = e.faixas.maxOf { it.reaisPorKmCentavos }.coerceAtLeast(1)
                    e.melhoresFaixas.forEach { f ->
                        BarraHorizontal(
                            rotulo = f.rotulo,
                            valorTexto = "${f.reaisPorKmCentavos.emReais()}/km",
                            fracao = f.reaisPorKmCentavos.toFloat() / teto,
                            detalhe = "${f.ofertas} ofertas",
                            cor = Lima
                        )
                    }
                    if (e.pioresFaixas.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Horários a evitar",
                            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                        )
                        e.pioresFaixas.forEach { f ->
                            BarraHorizontal(
                                rotulo = f.rotulo,
                                valorTexto = "${f.reaisPorKmCentavos.emReais()}/km",
                                fracao = f.reaisPorKmCentavos.toFloat() / teto,
                                detalhe = "${f.ofertas} ofertas",
                                cor = VermelhoPrejuizo
                            )
                        }
                    }
                } else {
                    Text(
                        "Com o leitor de ofertas ligado, aqui aparecem as faixas de horário " +
                            "que mais pagam por km — contando até as corridas que você recusou.",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                }
            }

            // ---------------- custos
            CardSecao(titulo = "Custo por km (real)") {
                if (e.custoPorCategoria.isEmpty()) {
                    Text("Sem despesas no período.", color = TextoSecundario)
                } else {
                    val totalKm = e.custoPorCategoria.sumOf { it.custoKmCentavos ?: 0.0 }
                    LinhaValor("Total", if (totalKm > 0) totalKm.centavosEmReais() + "/km" else "—", negrito = true)
                    e.custoPorCategoria.forEach { c ->
                        LinhaValor(
                            c.categoria.rotulo,
                            (c.custoKmCentavos?.let { it.centavosEmReais() + "/km" } ?: "—") + "  •  " + c.despesaCentavos.emReais()
                        )
                    }
                }
                val consumo = e.consumo
                if (consumo?.kmPorLitro != null) {
                    Text(
                        String.format(PT, "Consumo real: %.1f km/L (%d km medidos)", consumo.kmPorLitro, consumo.kmPercorridos ?: 0L),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Lima
                    )
                } else {
                    Text(
                        "Para ver o km/L real, informe litros e hodômetro em 2 abastecimentos (tanque cheio).",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
