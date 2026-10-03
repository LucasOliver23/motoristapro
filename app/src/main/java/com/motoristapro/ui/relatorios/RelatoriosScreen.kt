package com.motoristapro.ui.relatorios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.layout.Box
import com.motoristapro.ui.theme.Contorno
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta

private val PT = Locale("pt", "BR")

/**
 * O quadro do período: tempo, corridas e dinheiro no mesmo lugar.
 *
 * O número que não existe em nenhum app de corrida é o R$ POR HORA CONECTADA.
 * "R$/h em corrida" ignora o tempo parado esperando oferta, então sempre parece
 * melhor do que a vida real.
 */
/**
 * O mapa de calor: dia da semana nas linhas, faixa de 2 h nas colunas.
 *
 * Verde mais claro = mais R$/km naquele pedaço da semana. É a pergunta que o
 * motorista faz todo dia ("vale a pena sair agora?") respondida de uma olhada,
 * e sai de dado que o app já tem: toda oferta que o leitor viu, aceita ou não.
 *
 * Casa sem oferta nenhuma fica apagada, não verde-escuro: vazio não é "ruim",
 * é "não sei" — e misturar os dois mentiria para quem está começando.
 */
@Composable
private fun MapaDeCalor(e: RelatoriosUiState) {
    CardSecao {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Melhores horários",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Box(
                Modifier.padding(start = 8.dp).clip(RoundedCornerShape(99.dp))
                    .background(Lima.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("NOVO", style = MaterialTheme.typography.labelSmall, color = Lima, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
        }
        if (e.mapa.isEmpty()) {
            Text(
                "Com o leitor ligado, aqui aparece em que dia e hora o mercado paga " +
                    "melhor por km — contando até as corridas que você recusou.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            return@CardSecao
        }

        val porCasa = e.mapa.associateBy { it.diaSemana to it.faixa }
        val teto = e.mapa.maxOf { it.reaisKmCentavos }.coerceAtLeast(1)
        val piso = e.mapa.minOf { it.reaisKmCentavos }
        // Faixas de 3 em 3 (6h, 9h, 12h...) para caber na largura do celular.
        val colunas = listOf(3, 4, 6, 7, 9, 10, 0, 1)

        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(30.dp))
            colunas.forEach { f ->
                Text(
                    "${f * 2}h",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextoSecundario,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        DIAS_DA_SEMANA.forEachIndexed { indice, sigla ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sigla,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextoSecundario,
                    fontSize = 8.sp,
                    modifier = Modifier.width(30.dp)
                )
                colunas.forEach { f ->
                    val casa = porCasa[indice to f]
                    val forca = if (casa == null || teto == piso) 0f
                    else ((casa.reaisKmCentavos - piso).toFloat() / (teto - piso)).coerceIn(0f, 1f)
                    Box(
                        Modifier.weight(1f).padding(1.dp).height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (casa == null) Contorno
                                else Lima.copy(alpha = 0.25f + 0.75f * forca)
                            )
                    )
                }
            }
        }
        // Legenda: a escala de verde e, do outro lado, o melhor pedaço da semana.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("menos", style = MaterialTheme.typography.labelSmall, color = TextoSecundario, fontSize = 8.sp)
            listOf(0.25f, 0.45f, 0.65f, 0.85f, 1f).forEach { a ->
                Box(
                    Modifier.padding(horizontal = 1.dp).width(14.dp).height(8.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Lima.copy(alpha = a))
                )
            }
            Text(
                "mais R$/km",
                style = MaterialTheme.typography.labelSmall,
                color = TextoSecundario,
                fontSize = 8.sp,
                modifier = Modifier.padding(start = 3.dp)
            )
            Box(Modifier.weight(1f))
            e.melhorCasa?.let { melhor ->
                Text(
                    "${DIAS_DA_SEMANA[melhor.diaSemana]} ${melhor.faixa * 2}h",
                    style = MaterialTheme.typography.labelSmall,
                    color = Lima,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        e.melhorCasa?.let { melhor ->
            Text(
                "Melhor: ${DIAS_DA_SEMANA[melhor.diaSemana]} às ${melhor.faixa * 2}h • " +
                    "${melhor.reaisKmCentavos.emReais()}/km em ${melhor.ofertas} oferta(s)",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
    }
}

private val DIAS_DA_SEMANA = listOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")

/**
 * O cartão de cima da aba: o período em cinco linhas.
 *
 * São as cinco perguntas na ordem em que o motorista faz: quanto tempo fiquei
 * ligado, quanto disso foi rodando, quanto foi parado, quantas ofertas aceitei
 * e — a única que importa no fim — quanto saiu por hora de celular ligado.
 */
@Composable
private fun ResumoConectado(e: RelatoriosUiState) {
    CardSecao(titulo = e.periodo.detalhe.uppercase(PT)) {
        if (e.segundosConectado <= 0) {
            Text(
                "Sem jornada no período. Use \"Iniciar jornada\" na tela inicial para " +
                    "o app contar seu tempo conectado.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            return@CardSecao
        }
        LinhaDupla("Tempo conectado", null, e.segundosConectado.formatarDuracao())
        LinhaDupla(
            "Em corrida", "${e.percentualEmCorrida}% do conectado",
            e.resumo.segundosEmCorrida.formatarDuracao(), Lima
        )
        LinhaDupla(
            "Parado", "${100 - e.percentualEmCorrida}%",
            e.segundosParado.formatarDuracao(), AmareloAlerta
        )
        LinhaDupla(
            "Aceitas / recusadas",
            e.taxaDeAceite?.let { "$it% de aceite" },
            if (e.ofertas.total > 0) "${e.corridasAceitas} / ${e.corridasRecusadas}" else "—"
        )
        LinhaDupla(
            "R$ por hora conectada", null,
            e.ganhoPorHoraConectadoCentavos.emReais(), Lima
        )
    }
}

/** Linha "nome + explicação ..... número". O número é o que o olho procura. */
@Composable
private fun LinhaDupla(rotulo: String, detalhe: String?, valor: String, cor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = MaterialTheme.typography.bodyMedium)
            if (detalhe != null) {
                Text(detalhe, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
            }
        }
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = cor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun QuadroDoPeriodo(e: RelatoriosUiState) {
    val r = e.resumo
    CardSecao(titulo = "DETALHES DO PERÍODO") {
        LinhaValor("Corridas", "${r.qtdCorridas}")
        LinhaValor("Km rodados", String.format(PT, "%.1f km", r.kmRodados))

        Spacer(Modifier.height(6.dp))
        LinhaValor("Faturamento", r.faturamentoCentavos.emReais())
        LinhaValor("Despesas lançadas", "- " + r.despesasCentavos.emReais(), cor = VermelhoPrejuizo)
        LinhaValor(
            "Lucro", r.lucroLiquidoCentavos.emReais(), negrito = true,
            cor = if (r.lucroLiquidoCentavos >= 0) Lima else VermelhoPrejuizo
        )

        Spacer(Modifier.height(6.dp))
        LinhaValor("R$ por km", if (r.metrosRodados > 0) r.ganhoPorKmCentavos.emReais() + "/km" else "—")
        LinhaValor(
            "R$ por hora em corrida",
            if (r.segundosEmCorrida > 0) r.ganhoPorHoraCentavos.emReais() + "/h" else "—"
        )
        LinhaValor(
            "Custo real por km",
            if (e.custoRealKmCentavos > 0) e.custoRealKmCentavos.centavosEmReais() + "/km" else "—"
        )
    }
}

@Composable
fun RelatoriosRoute(vm: RelatoriosViewModel = viewModel(factory = RelatoriosViewModel.Factory)) {
    val e by vm.uiState.collectAsStateWithLifecycle()

    TelaAba(titulo = "Atividade", subtitulo = "Desempenho ${e.periodo.detalhe}") { padding ->
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

            // A ordem é a da pergunta: quanto tempo rendeu quanto, quando vale
            // sair, e como foi dia a dia. O detalhamento vem depois disso.
            val r = e.resumo
            ResumoConectado(e)

            MapaDeCalor(e)

            // ---------------- lucro por dia
            CardSecao(titulo = "LUCRO POR DIA") {
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

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metrica(
                    "Lucro", r.lucroLiquidoCentavos.emReais(), Modifier.weight(1f),
                    cor = if (r.lucroLiquidoCentavos >= 0) Lima else VermelhoPrejuizo,
                    detalhe = "${r.faturamentoCentavos.emReais()} faturado",
                    centralizado = true
                )
                Metrica(
                    "Corridas", "${r.qtdCorridas}", Modifier.weight(1f),
                    detalhe = String.format(PT, "%.0f km", r.kmRodados), centralizado = true
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
