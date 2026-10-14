package com.motoristapro.ui.relatorios

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.motoristapro.ui.componentes.BarraMeta
import com.motoristapro.ui.theme.SuperficieMaisAlta
import java.time.LocalDate
import androidx.compose.foundation.layout.fillMaxHeight
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.CartaoDestaque
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.componentes.NumeroCentral
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.componentes.Segmentado
import com.motoristapro.ui.componentes.SeloTexto
import com.motoristapro.ui.componentes.ValorComCentavos
import com.motoristapro.ui.theme.TextoPrincipal
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
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
    Cartao(espaco = 8) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Rotulo("Quando rende mais", Modifier.weight(1f))
            Text("R$/km", style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
        }
        if (e.mapa.isEmpty()) {
            Text(
                "Com o leitor ligado, aqui aparece em que dia e hora o mercado paga " +
                    "melhor por km — contando até as corridas que você recusou.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            return@Cartao
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
                    color = TextoSecundario,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Segunda primeiro, como o motorista pensa a semana.
        listOf(1, 2, 3, 4, 5, 6, 0).forEach { indice ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(DIAS_DA_SEMANA[indice], color = TextoSecundario, fontSize = 9.sp, modifier = Modifier.width(30.dp))
                colunas.forEach { f ->
                    val casa = porCasa[indice to f]
                    val forca = if (casa == null || teto == piso) 0f
                    else ((casa.reaisKmCentavos - piso).toFloat() / (teto - piso)).coerceIn(0f, 1f)
                    Box(
                        Modifier.weight(1f).padding(1.5.dp).height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (casa == null) SuperficieAlta else Lima.copy(alpha = 0.2f + 0.8f * forca))
                    )
                }
            }
        }
        e.melhorCasa?.let { melhor ->
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(10.dp))
                    .background(Lima.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icones.Raio, contentDescription = null, tint = Lima, modifier = Modifier.size(16.dp))
                Text(
                    "Melhor horário: ${DIAS_DA_SEMANA[melhor.diaSemana].lowercase(PT)} às ${melhor.faixa * 2}h · " +
                        "${melhor.reaisKmCentavos.emReais()}/km",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * O cartão de cima: o lucro do período JUNTO com a meta daquele período (a
 * semana com a meta da semana, o ano com a meta do mês × 12) e o gráfico com a
 * linha tracejada da meta de cada barra. Era o que antes ficava espalhado entre
 * Finanças ("metas de lucro") e aqui.
 */
@Composable
private fun LucroDoPeriodo(e: RelatoriosUiState) {
    val r = e.resumo
    val meta = e.metaDoPeriodo
    val lucro = r.lucroLiquidoCentavos
    CartaoDestaque {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rotulo(
                when (e.periodo) {
                    PeriodoRelatorio.DIARIO -> "Lucro hoje"
                    PeriodoRelatorio.SEMANAL -> "Lucro na semana"
                    PeriodoRelatorio.MENSAL -> "Lucro no mês"
                    PeriodoRelatorio.ANUAL -> "Lucro no ano"
                },
                Modifier.weight(1f)
            )
            SeloTexto("${r.qtdCorridas} corridas", Lima)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ValorComCentavos(lucro.emReais(), 32.sp, cor = if (lucro >= 0) TextoPrincipal else VermelhoPrejuizo)
            if (meta > 0) {
                Text(
                    "de ${meta.emReaisInteiros()}",
                    style = MaterialTheme.typography.bodyMedium, color = TextoSecundario,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
        if (meta > 0) {
            BarraMeta(lucro.toFloat() / meta, Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Lima, fontWeight = FontWeight.Bold)) {
                            append("${(lucro.coerceAtLeast(0) * 100 / meta)}%")
                        }
                        append(
                            " da meta " + when (e.periodo) {
                                PeriodoRelatorio.DIARIO -> "do dia"
                                PeriodoRelatorio.SEMANAL -> "da semana"
                                PeriodoRelatorio.MENSAL -> "do mês"
                                PeriodoRelatorio.ANUAL -> "do ano"
                            }
                        )
                    },
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (lucro >= meta) "meta batida 🎉" else "faltam ${(meta - lucro).emReais()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lucro >= meta) Lima else TextoSecundario
                )
            }
        } else {
            Text(
                "${r.faturamentoCentavos.emReais()} faturado · " + String.format(PT, "%.0f km", r.kmRodados),
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }

        if (e.periodo != PeriodoRelatorio.DIARIO) {
            val serie = SerieDoGrafico.montar(e.periodo, e.lucroPorDia, e.metaDiaria, e.metaMensal, LocalDate.now())
            if (serie.vazia) {
                SemCorrida(
                    when (e.periodo) {
                        PeriodoRelatorio.ANUAL -> "Nenhuma corrida registrada no ano"
                        PeriodoRelatorio.MENSAL -> "Nenhuma corrida registrada no mês"
                        else -> "Nenhuma corrida registrada na semana"
                    }
                )
            } else {
                GraficoComMeta(serie, Modifier.padding(top = 10.dp))
                Text(
                    if (serie.metaPorBarra > 0) {
                        if (e.periodo == PeriodoRelatorio.ANUAL) "■ mês que bateu a meta · o mês atual ainda está em andamento"
                        else "■ dia que bateu a meta do dia"
                    } else "Defina a meta em Menu ▸ Meu veículo e custos para ver a linha da meta.",
                    fontSize = 11.sp, color = TextoSecundario
                )
            }
        }
    }
}

/**
 * Barras com a linha tracejada da meta: a barra que passou da meta fica acesa,
 * a que não passou fica apagada, e a que ainda está correndo (hoje, mês atual)
 * fica pontilhada. Negativo desce em vermelho.
 */
@Composable
private fun GraficoComMeta(s: SerieDoGrafico, modifier: Modifier = Modifier) {
    val acesa = Lima
    val apagada = Lima.copy(alpha = 0.35f)
    val vazia = SuperficieMaisAlta
    val vermelho = VermelhoPrejuizo
    val corMeta = AmareloAlerta
    val medidor = rememberTextMeasurer()
    val estiloMeta = TextStyle(color = corMeta, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val maior = maxOf(s.valores.maxOrNull() ?: 0L, s.metaPorBarra, 1L).toFloat()
            val n = s.valores.size
            val vao = 5.dp.toPx()
            val largura = (size.width - vao * (n - 1)) / n
            val alturaUtil = size.height - 14.dp.toPx()     // espaço para o texto da meta
            s.valores.forEachIndexed { i, v ->
                val x = i * (largura + vao)
                val h = (kotlin.math.abs(v) / maior * alturaUtil).coerceAtLeast(3.dp.toPx())
                val cor = when {
                    v == 0L -> vazia
                    v < 0 -> vermelho
                    s.metaPorBarra > 0 && v >= s.metaPorBarra -> acesa
                    s.metaPorBarra > 0 -> apagada
                    else -> acesa
                }
                val topo = Offset(x, size.height - h)
                if (i == s.emAndamento && v > 0 && (s.metaPorBarra == 0L || v < s.metaPorBarra)) {
                    // Ainda correndo: contorno pontilhado em vez de barra cheia.
                    drawRoundRect(acesa.copy(alpha = 0.15f), topo, Size(largura, h), CornerRadius(5.dp.toPx()))
                    drawRoundRect(
                        acesa, topo, Size(largura, h), CornerRadius(5.dp.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    )
                } else {
                    drawRoundRect(cor, topo, Size(largura, h), CornerRadius(5.dp.toPx()))
                }
            }
            if (s.metaPorBarra > 0) {
                val y = size.height - s.metaPorBarra / maior * alturaUtil
                drawLine(
                    corMeta.copy(alpha = 0.8f), Offset(0f, y), Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
                val texto = medidor.measure("meta " + s.metaPorBarra.emReaisInteiros(), estiloMeta)
                drawText(texto, topLeft = Offset(size.width - texto.size.width, (y - texto.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            s.rotulos.forEach { r ->
                Text(r, fontSize = 10.sp, color = TextoSecundario, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

/** No lugar do gráfico vazio: barrinhas apagadas e o aviso por cima. */
@Composable
private fun SemCorrida(texto: String) {
    Box(
        Modifier.fillMaxWidth().padding(top = 10.dp).height(90.dp).clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(12) { Box(Modifier.weight(1f).fillMaxHeight().background(SuperficieAlta.copy(alpha = 0.6f))) }
        }
        Text(texto, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
    }
}

/** "R$ 1.200" — meta em reais inteiros: centavo de meta não ajuda ninguém. */
private fun Long.emReaisInteiros(): String = "R$ " + String.format(PT, "%,d", this / 100)

/** Segunda = S, terça = T... (java.time: segunda é 1). */
private val LETRA_DO_DIA = listOf("S", "T", "Q", "Q", "S", "S", "D")

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
        // Ganho por km menos custo por km: o número que sobra de verdade a cada
        // quilômetro. É o que separa rodar muito de ganhar muito.
        if (r.metrosRodados > 0 && e.custoRealKmCentavos > 0) {
            val lucroKm = r.ganhoPorKmCentavos - e.custoRealKmCentavos
            LinhaValor(
                "Lucro por km", lucroKm.centavosEmReais() + "/km",
                negrito = true, cor = if (lucroKm >= 0) Lima else VermelhoPrejuizo
            )
        }
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
            Segmentado(
                opcoes = listOf("Dia", "Semana", "Mês", "Ano"),
                selecionado = e.periodo.ordinal
            ) { vm.selecionar(PeriodoRelatorio.entries[it]) }

            // A ordem é a da pergunta: quanto rendeu, quando vale sair e como
            // foi o tempo ligado. O detalhamento vem depois disso.
            val r = e.resumo
            LucroDoPeriodo(e)

            MapaDeCalor(e)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumeroCentral(
                    "Aceitas", if (e.ofertas.total > 0) "${e.corridasAceitas}" else "${r.qtdCorridas}",
                    Modifier.weight(1f), cor = Lima
                )
                NumeroCentral(
                    "Recusadas", if (e.ofertas.total > 0) "${e.corridasRecusadas}" else "—",
                    Modifier.weight(1f), cor = VermelhoPrejuizo
                )
                NumeroCentral(
                    "Online",
                    if (e.segundosConectado > 0) e.segundosConectado.formatarDuracao() else "—",
                    Modifier.weight(1f)
                )
            }

            ResumoConectado(e)

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
