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
import com.motoristapro.ui.mais.MelhoresHorariosScreen
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.util.Locale
import androidx.compose.runtime.getValue

private val PT = Locale("pt", "BR")

/**
 * O quadro do período: tempo, corridas e dinheiro no mesmo lugar.
 *
 * O número que não existe em nenhum app de corrida é o R$ POR HORA CONECTADA.
 * "R$/h em corrida" ignora o tempo parado esperando oferta, então sempre parece
 * melhor do que a vida real.
 */
@Composable
private fun QuadroDoPeriodo(e: RelatoriosUiState) {
    val r = e.resumo
    CardSecao(titulo = "Resumo ${e.periodo.detalhe}") {
        if (e.segundosConectado > 0) {
            LinhaValor("Tempo conectado", e.segundosConectado.formatarDuracao(), negrito = true)
            LinhaValor(
                "Tempo em corrida",
                r.segundosEmCorrida.formatarDuracao() + "  •  ${e.percentualEmCorrida}%"
            )
            LinhaValor(
                "Tempo parado",
                e.segundosParado.formatarDuracao() + "  •  ${100 - e.percentualEmCorrida}%",
                cor = if (e.percentualEmCorrida < 40) VermelhoPrejuizo else TextoSecundario
            )
        } else {
            Text(
                "Sem jornada no período. Use \"Iniciar jornada\" na tela inicial para " +
                    "o app contar seu tempo conectado.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }

        Spacer(Modifier.height(6.dp))
        if (e.ofertas.total > 0) {
            LinhaValor(
                "Corridas aceitas",
                "${e.corridasAceitas}" + (e.taxaDeAceite?.let { "  •  $it% de aceite" } ?: "")
            )
            LinhaValor("Corridas recusadas", "${e.corridasRecusadas}")
        } else {
            LinhaValor("Corridas", "${r.qtdCorridas}")
        }
        LinhaValor("Km rodados", String.format(PT, "%.1f km", r.kmRodados))

        Spacer(Modifier.height(6.dp))
        LinhaValor("Faturamento", r.faturamentoCentavos.emReais())
        LinhaValor("Despesas lançadas", "- " + r.despesasCentavos.emReais(), cor = VermelhoPrejuizo)
        LinhaValor(
            "Lucro", r.lucroLiquidoCentavos.emReais(), negrito = true,
            cor = if (r.lucroLiquidoCentavos >= 0) Lima else VermelhoPrejuizo
        )

        Spacer(Modifier.height(6.dp))
        LinhaValor(
            "R$ por hora conectada",
            if (e.segundosConectado > 0) e.ganhoPorHoraConectadoCentavos.emReais() + "/h" else "—",
            negrito = true, cor = Lima
        )
        LinhaValor("R$ por km", if (r.metrosRodados > 0) r.ganhoPorKmCentavos.emReais() + "/km" else "—")
        LinhaValor(
            "Custo real por km",
            if (e.custoRealKmCentavos > 0) e.custoRealKmCentavos.centavosEmReais() + "/km" else "—"
        )
    }
}

@Composable
fun RelatoriosRoute(vm: RelatoriosViewModel = viewModel(factory = RelatoriosViewModel.Factory)) {
    val e by vm.uiState.collectAsStateWithLifecycle()

    TelaAba(titulo = "Relatórios", subtitulo = "Desempenho ${e.periodo.detalhe}") { padding ->
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

            // ---------------- o quadro do período (tempo, corridas, dinheiro)
            QuadroDoPeriodo(e)

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
                    LinhaValor("Aceitas", "${o.registradas} (${o.registradas * 100 / o.total}%)")
                    LinhaValor("Recusadas", "${o.naoRegistradas}")
                    BarraHorizontal("Boas", "${o.boas}", o.boas.toFloat() / o.total)
                    BarraHorizontal("Médias", "${o.medias}", o.medias.toFloat() / o.total, cor = com.motoristapro.ui.theme.AmareloAlerta)
                    BarraHorizontal("Ruins", "${o.ruins}", o.ruins.toFloat() / o.total, cor = VermelhoPrejuizo)
                    LinhaValor("R$/km das aceitas", if (o.metrosRegistradas > 0) o.reaisKmRegistradas.emReais() else "—")
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
            CardSecao(titulo = "Melhores horários (corridas registradas)") {
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

            }

            // A tela completa de Melhores Horários, que antes vivia em Mais. É a
            // mesma informação, então ficar nos dois lugares só dava a chance de
            // um mostrar um número e o outro mostrar outro. Aqui ela tem o filtro
            // por dia da semana e o "paga melhor × mais movimento", que o card
            // resumido não tinha.
            Text(
                "Melhores horários (o que apareceu na sua tela)",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            MelhoresHorariosScreen()

            // ---------------- custos
            CardSecao(titulo = "Custo real medido (no período)") {
                if (e.custoPorCategoria.isEmpty()) {
                    Text(
                        "Sem despesas lançadas no período. Este card compara o que você " +
                            "GASTOU de verdade com o custo que o assistente previu.",
                        color = TextoSecundario, style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val totalKm = e.custoRealKmCentavos
                    LinhaValor("Total", if (totalKm > 0) totalKm.centavosEmReais() + "/km" else "—", negrito = true)
                    if (e.custoPlanejadoKmCentavos > 0 && totalKm > 0) {
                        val planejado = e.custoPlanejadoKmCentavos
                        val diferenca = ((totalKm - planejado) * 100 / planejado).toInt()
                        LinhaValor("Planejado (assistente de custos)", planejado.emReais() + "/km")
                        Text(
                            when {
                                diferenca > 5 -> "Você está gastando $diferenca% MAIS que o planejado — " +
                                    "vale refazer o assistente de custos."
                                diferenca < -5 -> "Você está gastando ${-diferenca}% menos que o planejado."
                                else -> "O planejado está batendo com a realidade."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (diferenca > 5) VermelhoPrejuizo else Lima
                        )
                    }
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
