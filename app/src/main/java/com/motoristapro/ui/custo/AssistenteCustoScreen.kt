package com.motoristapro.ui.custo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motoristapro.custo.DadosCusto
import com.motoristapro.custo.FormaAquisicao
import com.motoristapro.custo.ItemCusto
import com.motoristapro.custo.ResultadoCusto
import com.motoristapro.custo.TipoCombustivel
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.paraCentavosOuZero
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.util.Locale

private val PT = Locale("pt", "BR")

/**
 * Assistente de custo: quatro passos até o custo real por km e a tarifa mínima.
 *
 * A ideia é não pedir nada que o motorista não saiba de cabeça. Tudo que dá para
 * derivar (IPVA em reais, desvalorização mensal, km do mês) é calculado e mostrado,
 * nunca perguntado.
 */
@Composable
fun AssistenteCustoScreen(
    onFechar: () -> Unit,
    vm: AssistenteCustoViewModel = viewModel(factory = AssistenteCustoViewModel.Factory)
) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val r = e.resultado

    Column(
        Modifier.fillMaxSize().background(Fundo).safeDrawingPadding()
    ) {
        // -------------------------------------------------------------- topo
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onFechar) { Text("Fechar", color = TextoSecundario) }
            Spacer(Modifier.weight(1f))
            Text(
                e.dados.forma.rotulo.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextoSecundario
            )
        }

        if (r != null && !e.avisosPendentes) {
            ResultadoCard(r, e.aplicado, vm::aplicarAoSemaforo, vm::voltarAoFormulario, Modifier.weight(1f))
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                e.passo.titulo,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Passo ${e.indicePasso + 1} de ${PassoCusto.entries.size}",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )
        }

        // barra de progresso dos 4 passos
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PassoCusto.entries.forEachIndexed { i, _ ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= e.indicePasso) Lima else SuperficieAlta)
                )
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (e.passo) {
                PassoCusto.FIXOS -> PassoFixos(e.dados, vm::editar)
                PassoCusto.MANUTENCAO -> PassoManutencao(e.dados, vm::editar)
                PassoCusto.COMBUSTIVEL -> PassoCombustivel(e.dados, vm::editar)
                PassoCusto.METAS -> PassoMetas(e.dados, vm::editar)
            }
            Spacer(Modifier.height(8.dp))
        }

        // ------------------------------------------------------------ rodapé
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (e.indicePasso > 0) {
                OutlinedButton(onClick = vm::voltar) { Text("Voltar") }
            }
            Button(
                onClick = { if (e.ultimoPasso) vm.calcular() else vm.avancar() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Fundo)
            ) {
                Text(
                    if (e.ultimoPasso) "Calcular meu custo" else "Continuar",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ---------------------------------------------------- aviso de valor estranho
    if (e.avisosPendentes && r != null && r.avisos.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::revisar,
            title = { Text("Confira os valores") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alguns valores parecem fora do comum:", color = TextoSecundario)
                    r.avisos.forEach { Text("• ${it.texto}") }
                    Text(
                        "Confira se digitou certo. Calcular assim mesmo?",
                        color = TextoSecundario
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = vm::calcularMesmoAssim,
                    colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Fundo)
                ) { Text("Calcular assim mesmo") }
            },
            dismissButton = {
                TextButton(onClick = vm::revisar) { Text("Revisar", color = AmareloAlerta) }
            }
        )
    }
}

// ====================================================================== passos

@Composable
private fun PassoFixos(d: DadosCusto, editar: ((DadosCusto) -> DadosCusto) -> Unit) {
    CardSecao(titulo = "Como é o seu veículo") {
        EscolhaEmLinha(
            opcoes = TipoVeiculo.entries.map { it.rotulo },
            selecionado = d.tipoVeiculo.rotulo,
            onEscolher = { r -> editar { it.copy(tipoVeiculo = TipoVeiculo.entries.first { t -> t.rotulo == r }) } }
        )
        Spacer(Modifier.height(4.dp))
        EscolhaEmLinha(
            opcoes = FormaAquisicao.entries.map { it.rotulo },
            selecionado = d.forma.rotulo,
            onEscolher = { r -> editar { it.copy(forma = FormaAquisicao.entries.first { f -> f.rotulo == r }) } }
        )
        Text(
            when (d.forma) {
                FormaAquisicao.QUITADO -> "Veículo seu, sem parcela. Entra a depreciação: ele vale menos a cada mês."
                FormaAquisicao.FINANCIADO -> "Entram a parcela E a depreciação — o veículo é seu e desvaloriza no seu bolso."
                FormaAquisicao.ALUGADO -> "Entra só o aluguel. Depreciação e IPVA são de quem alugou, não seus."
            },
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }

    if (d.forma == FormaAquisicao.ALUGADO) {
        CardSecao(titulo = "Aluguel") {
            CampoDinheiro("Aluguel mensal", d.aluguelMensalCentavos) { v -> editar { it.copy(aluguelMensalCentavos = v) } }
        }
    } else {
        CardSecao(titulo = "O veículo") {
            CampoDinheiro(
                "Valor do veículo hoje", d.valorVeiculoCentavos,
                ajuda = "Quanto vale na tabela FIPE. Usado para depreciação e IPVA."
            ) { v -> editar { it.copy(valorVeiculoCentavos = v) } }

            if (d.forma == FormaAquisicao.FINANCIADO) {
                CampoDinheiro("Parcela mensal", d.parcelaMensalCentavos) { v -> editar { it.copy(parcelaMensalCentavos = v) } }
            }

            CampoPercentual(
                "Desvalorização por ano", d.desvalorizacaoAnualX100,
                ajuda = "Quanto o veículo perde de valor por ano. 10% a 15% é o comum."
            ) { v -> editar { it.copy(desvalorizacaoAnualX100 = v) } }
            LinhaCalculada(
                "Depreciação por mês",
                (d.valorVeiculoCentavos * (d.desvalorizacaoAnualX100 / 10_000.0) / 12.0).toLong()
            )

            CampoPercentual("IPVA por ano", d.ipvaPercentX100) { v -> editar { it.copy(ipvaPercentX100 = v) } }
            LinhaCalculada(
                "IPVA por mês",
                (d.valorVeiculoCentavos * (d.ipvaPercentX100 / 10_000.0) / 12.0).toLong()
            )
        }
    }

    CardSecao(titulo = "Outros custos do mês") {
        CampoDinheiro("Seguro mensal", d.seguroMensalCentavos, ajuda = "Paga por ano? Divida por 12.") { v ->
            editar { it.copy(seguroMensalCentavos = v) }
        }
        CampoDinheiro(
            "Outros custos fixos", d.outrosMensaisCentavos,
            ajuda = "Aluguel de garagem, app de rastreamento, lavagem — o que você paga todo mês."
        ) { v -> editar { it.copy(outrosMensaisCentavos = v) } }
    }
}

@Composable
private fun PassoManutencao(d: DadosCusto, editar: ((DadosCusto) -> DadosCusto) -> Unit) {
    CardSecao(titulo = "Troca de óleo") {
        CampoDinheiro("Custo da troca", d.trocaOleoCentavos) { v -> editar { it.copy(trocaOleoCentavos = v) } }
        CampoKm("A cada quantos km", d.intervaloOleoKm) { v -> editar { it.copy(intervaloOleoKm = v) } }
        LinhaPorKm("Dá por km", d.trocaOleoCentavos, d.intervaloOleoKm)
    }
    CardSecao(titulo = "Pneus") {
        CampoDinheiro("Jogo de pneus", d.jogoPneusCentavos) { v -> editar { it.copy(jogoPneusCentavos = v) } }
        CampoKm("Duram quantos km", d.duracaoPneusKm) { v -> editar { it.copy(duracaoPneusKm = v) } }
        LinhaPorKm("Dá por km", d.jogoPneusCentavos, d.duracaoPneusKm)
    }
    CardSecao(titulo = "Revisão") {
        CampoDinheiro("Custo da revisão", d.revisaoCentavos) { v -> editar { it.copy(revisaoCentavos = v) } }
        CampoKm("A cada quantos km", d.intervaloRevisaoKm) { v -> editar { it.copy(intervaloRevisaoKm = v) } }
        LinhaPorKm("Dá por km", d.revisaoCentavos, d.intervaloRevisaoKm)
    }
    CardSecao(titulo = "Outro desgaste") {
        Text(
            "Relação e coroa, pastilha de freio, embreagem — qualquer peça que você troca " +
                "de tantos em tantos km.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
        CampoDinheiro("Custo", d.outrosDesgasteCentavos) { v -> editar { it.copy(outrosDesgasteCentavos = v) } }
        CampoKm("A cada quantos km", d.outrosDesgasteKm) { v -> editar { it.copy(outrosDesgasteKm = v) } }
        LinhaPorKm("Dá por km", d.outrosDesgasteCentavos, d.outrosDesgasteKm)
    }
}

@Composable
private fun PassoCombustivel(d: DadosCusto, editar: ((DadosCusto) -> DadosCusto) -> Unit) {
    CardSecao(titulo = "Combustível") {
        EscolhaEmGrade(
            opcoes = TipoCombustivel.entries.map { it.rotulo },
            selecionado = d.combustivel.rotulo,
            onEscolher = { r ->
                editar { it.copy(combustivel = TipoCombustivel.entries.first { c -> c.rotulo == r }) }
            }
        )
        Spacer(Modifier.height(4.dp))
        CampoDinheiro(
            when (d.combustivel) {
                TipoCombustivel.ELETRICO -> "Preço do kWh"
                TipoCombustivel.GNV -> "Preço do m³"
                else -> "Preço do litro"
            },
            d.precoLitroCentavos
        ) { v -> editar { it.copy(precoLitroCentavos = v) } }

        CampoDecimal(
            "Consumo médio (${d.combustivel.unidade})", d.consumoX100,
            ajuda = "Quanto o veículo faz de verdade no seu uso, não o do fabricante."
        ) { v -> editar { it.copy(consumoX100 = v) } }

        val porKm = if (d.consumoX100 > 0) d.precoLitroCentavos * 100 / d.consumoX100 else 0
        if (porKm > 0) {
            LinhaCalculada("Combustível por km", porKm, destaque = true)
        }
    }
}

@Composable
private fun PassoMetas(d: DadosCusto, editar: ((DadosCusto) -> DadosCusto) -> Unit) {
    CardSecao(titulo = "Quanto você roda") {
        CampoKm("Km por dia", d.kmPorDia) { v -> editar { it.copy(kmPorDia = v) } }

        Text("Dias por semana", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { n ->
                val ativo = d.diasPorSemana == n
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (ativo) Lima else SuperficieAlta)
                        .clickable { editar { it.copy(diasPorSemana = n) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$n",
                        color = if (ativo) Fundo else TextoSecundario,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            "Horas por dia: ${d.horasPorDia}h",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = d.horasPorDia.toFloat(),
            onValueChange = { v -> editar { it.copy(horasPorDia = v.toInt().coerceIn(1, 18)) } },
            valueRange = 1f..18f,
            steps = 16
        )

        HorizontalDivider(color = Contorno)
        LinhaTexto("Dias no mês", String.format(PT, "%.0f dias", d.diasPorMes))
        LinhaTexto("Km no mês", String.format(PT, "%,.0f km", d.kmPorMes))
        LinhaTexto("Horas no mês", String.format(PT, "%.0f h", d.diasPorMes * d.horasPorDia))
    }

    CardSecao(titulo = "Sua meta") {
        CampoDinheiro(
            "Lucro que quer levar por mês", d.metaLucroMensalCentavos,
            ajuda = "O que sobra pra você, já pagos todos os custos acima."
        ) { v -> editar { it.copy(metaLucroMensalCentavos = v) } }
    }
}

// ================================================================== resultado

@Composable
private fun ResultadoCard(
    r: ResultadoCusto,
    aplicado: Boolean,
    onAplicar: () -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Seu custo real", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        CardSecao(titulo = "Quanto custa rodar") {
            Text(
                "${r.custoPorKmCentavos.emReais()} / km",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, Lima.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "Aceite corridas acima de ${r.tarifaMinimaPorKmCentavos.emReais()}/km",
                    color = Lima, fontWeight = FontWeight.Bold
                )
            }
            Text(
                "Abaixo disso você roda de graça. Esse é o número que vai alimentar o semáforo.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }

        CardSecao(titulo = "Projeção") {
            r.projecoes.forEach { p ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.rotulo, fontWeight = FontWeight.SemiBold)
                        Text(String.format(PT, "%,d km", p.km), color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Receita ${p.receitaCentavos.emReais()}", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        Text("Custos ${p.custoCentavos.emReais()}", style = MaterialTheme.typography.bodySmall, color = VermelhoPrejuizo)
                        Text(p.lucroCentavos.emReais(), style = MaterialTheme.typography.bodySmall, color = Lima, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = Contorno)
            }
        }

        CardSecao(titulo = "De onde vem o custo") {
            Text(
                "Custo mensal: ${r.custoMensalCentavos.emReais()}",
                fontWeight = FontWeight.Bold
            )
            r.itens.forEach { LinhaItemCusto(it) }
            if (r.itens.isEmpty()) {
                Text("Nenhum custo informado.", color = TextoSecundario)
            }
        }

        Button(
            onClick = onAplicar,
            enabled = r.valido && !aplicado,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Fundo)
        ) {
            Text(if (aplicado) "Aplicado ao semáforo ✓" else "Aplicar ao semáforo", fontWeight = FontWeight.Bold)
        }
        if (aplicado) {
            Text(
                "Pronto: o cartão da oferta já usa ${r.custoPorKmCentavos.emReais()}/km " +
                    "para calcular o lucro de cada corrida.",
                style = MaterialTheme.typography.bodySmall, color = Turquesa
            )
        }
        OutlinedButton(onClick = onVoltar, modifier = Modifier.fillMaxWidth()) {
            Text("Mudar os valores")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LinhaItemCusto(item: ItemCusto) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.nome, style = MaterialTheme.typography.bodyMedium)
            Text(item.mensalCentavos.emReais(), fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(SuperficieAlta)
        ) {
            Box(
                Modifier.fillMaxWidth(item.percentual / 100f).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Lima)
            )
        }
        Text(
            "${item.porKmCentavos.emReais()}/km · ${item.percentual}% do custo",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }
}

// ================================================================== auxiliares

@Composable
private fun CampoDinheiro(
    rotulo: String,
    centavos: Long,
    ajuda: String? = null,
    onValor: (Long) -> Unit
) {
    var texto by rememberSaveable(rotulo) {
        mutableStateOf(if (centavos > 0) centavos.campoReais() else "")
    }
    CampoFormulario(
        rotulo = "$rotulo (R$)",
        valor = texto,
        onValor = { novo ->
            texto = novo
            onValor(novo.paraCentavosOuZero() ?: 0L)
        },
        erro = texto.isNotBlank() && texto.paraCentavosOuZero() == null,
        ajuda = ajuda
    )
}

@Composable
private fun CampoKm(rotulo: String, km: Long, onValor: (Long) -> Unit) {
    var texto by rememberSaveable(rotulo) {
        mutableStateOf(if (km > 0) km.toString() else "")
    }
    CampoFormulario(
        rotulo = "$rotulo (km)",
        valor = texto,
        onValor = { novo ->
            val limpo = novo.filter(Char::isDigit)
            texto = limpo
            onValor(limpo.toLongOrNull() ?: 0L)
        }
    )
}

@Composable
private fun CampoPercentual(rotulo: String, x100: Long, ajuda: String? = null, onValor: (Long) -> Unit) {
    var texto by rememberSaveable(rotulo) {
        mutableStateOf(if (x100 > 0) (x100 / 100.0).let { String.format(PT, "%.0f", it) } else "")
    }
    CampoFormulario(
        rotulo = "$rotulo (%)",
        valor = texto,
        onValor = { novo ->
            texto = novo
            onValor(((novo.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong().coerceIn(0, 10_000))
        },
        ajuda = ajuda
    )
}

@Composable
private fun CampoDecimal(rotulo: String, x100: Long, ajuda: String? = null, onValor: (Long) -> Unit) {
    var texto by rememberSaveable(rotulo) {
        mutableStateOf(if (x100 > 0) String.format(PT, "%.1f", x100 / 100.0) else "")
    }
    CampoFormulario(
        rotulo = rotulo,
        valor = texto,
        onValor = { novo ->
            texto = novo
            onValor(((novo.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0))
        },
        ajuda = ajuda
    )
}

@Composable
private fun LinhaCalculada(rotulo: String, centavos: Long, destaque: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        Text(
            centavos.emReais(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (destaque) Lima else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LinhaPorKm(rotulo: String, valorCentavos: Long, duracaoKm: Long) {
    if (valorCentavos <= 0 || duracaoKm <= 0) return
    LinhaCalculada(rotulo, valorCentavos / duracaoKm)
}

@Composable
private fun LinhaTexto(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EscolhaEmLinha(opcoes: List<String>, selecionado: String, onEscolher: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEach { op -> Opcao(op, op == selecionado, Modifier.weight(1f)) { onEscolher(op) } }
    }
}

@Composable
private fun EscolhaEmGrade(opcoes: List<String>, selecionado: String, onEscolher: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.chunked(3).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { op -> Opcao(op, op == selecionado, Modifier.weight(1f)) { onEscolher(op) } }
                repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Opcao(rotulo: String, ativo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (ativo) MaterialTheme.colorScheme.primaryContainer else Superficie)
            .border(1.dp, if (ativo) Lima else Contorno, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            rotulo,
            color = if (ativo) Lima else TextoSecundario,
            fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** 1234 centavos -> "12,34", do jeito que o motorista digita. */
private fun Long.campoReais(): String = String.format(PT, "%.2f", this / 100.0)
