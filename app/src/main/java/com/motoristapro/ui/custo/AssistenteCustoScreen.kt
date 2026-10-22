package com.motoristapro.ui.custo

import com.motoristapro.ui.theme.VerdeLucro
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motoristapro.custo.CalculadoraCusto
import com.motoristapro.custo.CustoAoVivo
import com.motoristapro.custo.DadosCusto
import com.motoristapro.custo.FatiasCusto
import com.motoristapro.custo.FormaAquisicao
import com.motoristapro.custo.ResultadoCusto
import com.motoristapro.custo.TipoCombustivel
import com.motoristapro.custo.TipoTrabalho
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.CorSobreDestaque
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.componentes.Pilula
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.paraCentavosOuZero
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.AzulInfo
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Laranja
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.Roxo
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.SuperficieMaisAlta
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.Turquesa
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val PT = Locale("pt", "BR")

/** As cores das três fatias do custo, iguais no resumo, no assistente e no resultado. */
val CorCombustivel get() = Laranja
val CorManutencao get() = AzulInfo
val CorFixos get() = Roxo

/** O ícone de cada passo, na fila de cima. */
private fun iconeDo(p: PassoCusto): ImageVector = when (p) {
    PassoCusto.VEICULO -> Icones.Moto
    PassoCusto.FIXOS -> Icones.Escudo
    PassoCusto.MANUTENCAO -> Icones.Chave
    PassoCusto.COMBUSTIVEL -> Icones.Combustivel
    PassoCusto.METAS -> Icones.Alvo
}

/**
 * Assistente de custo: cinco passos até o custo real por km e a tarifa mínima.
 *
 * A ideia é não pedir nada que o motorista não saiba de cabeça. Tudo que dá para
 * derivar (IPVA em reais, desvalorização mensal, km do mês) é calculado e mostrado,
 * nunca perguntado. E o custo "até aqui" fica sempre à vista, mudando enquanto
 * ele digita — é o que faz cada campo valer a pena preencher.
 */
@Composable
fun AssistenteCustoScreen(
    onFechar: () -> Unit,
    vm: AssistenteCustoViewModel = viewModel(factory = AssistenteCustoViewModel.Factory)
) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val r = e.resultado

    Column(
        // Esta tela e desenhada dentro da janela do app (nao numa Dialog), entao
        // os recuos vem direitos: barra de status em cima, teclado embaixo.
        Modifier.fillMaxSize().background(Fundo).statusBarsPadding().imePadding()
    ) {
        if (r != null && !e.avisosPendentes) {
            ResultadoFinal(e.dados, r, e.aplicado, vm::aplicarAoSemaforo, vm::voltarAoFormulario, onFechar)
            return@Column
        }

        // -------------------------------------------------------------- topo
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onFechar) {
                Icon(Icones.Fechar, contentDescription = "Fechar", tint = TextoSecundario)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Passo ${e.indicePasso + 1} de ${PassoCusto.entries.size}",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )
        }
        FilaDePassos(e.passo, vm::irPara)

        val (aoVivo, estimado) = remember(e.dados) { CustoAoVivo.calcular(e.dados) }
        val fatias = remember(aoVivo) { FatiasCusto.de(aoVivo) }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(e.passo.titulo, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextoPrincipal)
            when (e.passo) {
                PassoCusto.VEICULO -> PassoVeiculo(e.dados, vm::editar)
                PassoCusto.FIXOS -> PassoFixos(e.dados, fatias, vm::editar)
                PassoCusto.MANUTENCAO -> PassoManutencao(e.dados, fatias, vm::editar)
                PassoCusto.COMBUSTIVEL -> PassoCombustivel(e.dados, vm::editar)
                PassoCusto.METAS -> PassoMetas(e.dados, vm::editar)
            }
            Spacer(Modifier.height(8.dp))
        }

        // ------------------------------------------------------------ rodapé
        FaixaAoVivo(aoVivo, fatias, estimado)
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (e.indicePasso > 0) {
                Button(
                    onClick = vm::voltar,
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuperficieAlta, contentColor = TextoPrincipal)
                ) { Text("Voltar", fontWeight = FontWeight.SemiBold) }
            }
            Button(
                onClick = { if (e.ultimoPasso) vm.calcular() else vm.avancar() },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = CorSobreDestaque)
            ) {
                Text(
                    if (e.ultimoPasso) "Calcular meu custo" else "Continuar",
                    fontWeight = FontWeight.ExtraBold
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
                    colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = CorSobreDestaque)
                ) { Text("Calcular assim mesmo") }
            },
            dismissButton = {
                TextButton(onClick = vm::revisar) { Text("Revisar", color = AmareloAlerta) }
            }
        )
    }
}

// =============================================================== fila e faixa

/** Os cinco passos com ícone e nome; os já vistos ganham ✓ e um toque leva até eles. */
@Composable
private fun FilaDePassos(atual: PassoCusto, onIr: (PassoCusto) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        PassoCusto.entries.forEach { p ->
            val feito = p.ordinal < atual.ordinal
            val agora = p == atual
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onIr(p) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(
                        when {
                            agora -> Lima
                            feito -> Lima.copy(alpha = 0.16f)
                            else -> SuperficieAlta
                        }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (feito) Icones.Certo else iconeDo(p),
                        contentDescription = null,
                        tint = when {
                            agora -> CorSobreDestaque
                            feito -> Lima
                            else -> TextoSecundario
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    p.rotulo,
                    fontSize = 10.sp,
                    color = if (agora) TextoPrincipal else TextoSecundario,
                    fontWeight = if (agora) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1
                )
            }
        }
    }
}

/** "Seu custo até aqui": muda a cada tecla, com as três fatias numa barrinha. */
@Composable
private fun FaixaAoVivo(r: ResultadoCusto, f: FatiasCusto, estimado: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
            .background(Lima.copy(alpha = 0.08f)).border(1.dp, Lima.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Rotulo("Seu custo até aqui")
            Text(
                if (r.custoPorKmCentavos > 0) "${r.custoPorKmCentavos.emReais()} /km" else "—",
                style = EstiloValor, fontSize = 20.sp, color = Lima
            )
            if (estimado && r.custoPorKmCentavos > 0) {
                Text(
                    "estimando ${CustoAoVivo.KM_DIA_ESTIMADO} km/dia até o último passo",
                    fontSize = 10.sp, color = TextoSecundario
                )
            }
        }
        if (f.total > 0) BarraFatias(f, Modifier.width(110.dp).height(8.dp))
    }
}

/** Barra dividida nas três cores (combustível, manutenção, fixos). */
@Composable
fun BarraFatias(f: FatiasCusto, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(99.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(f.combustivel to CorCombustivel, f.manutencao to CorManutencao, f.fixos to CorFixos)
            .filter { it.first > 0 }
            .forEach { (v, cor) -> Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(cor)) }
    }
}

/** O cartão do topo de cada passo: quanto ESTE passo soma no custo por km. */
@Composable
private fun SomaDoPasso(rotulo: String, porKmCentavos: Double, icone: ImageVector, cor: Color, conta: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Superficie)
            .background(Brush.linearGradient(listOf(Lima.copy(alpha = 0.10f), Color.Transparent)))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(cor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Rotulo(rotulo)
            Text(centavosPorKm(porKmCentavos), style = EstiloValor, fontSize = 26.sp, color = TextoPrincipal)
        }
        if (conta != null) Text(conta, fontSize = 11.sp, color = TextoSecundario)
    }
}

/** R$ por km com 2 casas; abaixo de 1 centavo mostra 3, senão vira "R$ 0,00". */
fun centavosPorKm(c: Double): String =
    if (c > 0 && c < 1) String.format(PT, "R$ %.3f", c / 100.0) else String.format(PT, "R$ %.2f", c / 100.0)

// ====================================================================== passos

@Composable
private fun PassoVeiculo(d: DadosCusto, editar: ((DadosCusto) -> DadosCusto) -> Unit) {
    Text(
        "Isso muda o desgaste, a depreciação e quais custos entram na conta.",
        style = MaterialTheme.typography.bodyMedium, color = TextoSecundario
    )
    EscolhaDoVeiculo(d, editar)
    Rotulo("O que você faz", Modifier.padding(top = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TipoTrabalho.entries.forEach { t ->
            Pilula(if (t == TipoTrabalho.AMBOS) "Os dois" else t.rotulo, d.tipoTrabalho == t) {
                editar { it.copy(tipoTrabalho = t) }
            }
        }
    }
    Rotulo("Como conseguiu", Modifier.padding(top = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormaAquisicao.entries.forEach { f ->
            Pilula(f.rotulo, d.forma == f) { editar { it.copy(forma = f) } }
        }
    }
    Text(
        when (d.forma) {
            FormaAquisicao.QUITADO -> "Veículo seu, sem parcela. Entra a depreciação: ele vale menos a cada mês."
            FormaAquisicao.FINANCIADO -> "Entram a parcela E a depreciação — o veículo é seu e desvaloriza no seu bolso."
            FormaAquisicao.ALUGADO -> "Entra só o aluguel. Depreciação e IPVA são de quem alugou, não seus."
        },
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )
    CampoTexto(
        "Nome do veículo", d.veiculoNome,
        ajuda = "Como você chama ele. Ex.: Fan 160 2021."
    ) { v -> editar { it.copy(veiculoNome = v) } }
    if (d.forma != FormaAquisicao.ALUGADO) {
        CampoDinheiro(
            if (d.tipoVeiculo == TipoVeiculo.BIKE) "Quanto vale a bike" else "Valor na tabela FIPE",
            d.valorVeiculoCentavos,
            ajuda = "Quanto ele vale hoje. Usado para a depreciação e o IPVA."
        ) { v -> editar { it.copy(valorVeiculoCentavos = v) } }
    }
}

@Composable
private fun PassoFixos(d: DadosCusto, f: FatiasCusto, editar: ((DadosCusto) -> DadosCusto) -> Unit) {
    SomaDoPasso("Fixos por km", f.fixos, Icones.Escudo, CorFixos)
    Text(
        "O que você paga rode ou não. Vira custo por km dividido pelo quanto você roda.",
        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
    )
    if (d.forma == FormaAquisicao.ALUGADO) {
        Cartao {
            Rotulo("Aluguel")
            CampoDinheiro("Aluguel mensal", d.aluguelMensalCentavos) { v -> editar { it.copy(aluguelMensalCentavos = v) } }
        }
    } else {
        Cartao {
            Rotulo("O veículo")
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
        }
        if (d.tipoVeiculo != TipoVeiculo.BIKE) Cartao {
            Rotulo("IPVA")
            // Dois jeitos de informar o IPVA porque quase todo motorista sabe o
            // valor em reais do carnê, e quase nenhum sabe a aliquota do estado.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pilula("Em % do valor", !d.ipvaEmReais) { editar { it.copy(ipvaEmReais = false) } }
                Pilula("Em R$ por ano", d.ipvaEmReais) { editar { it.copy(ipvaEmReais = true) } }
            }
            if (d.ipvaEmReais) {
                CampoDinheiro(
                    "IPVA por ano", d.ipvaAnualCentavos,
                    ajuda = "O valor do carnê do ano inteiro, sem desconto à vista."
                ) { v -> editar { it.copy(ipvaAnualCentavos = v) } }
            } else {
                CampoPercentual(
                    "IPVA por ano", d.ipvaPercentX100,
                    ajuda = "Alíquota do seu estado sobre o valor do veículo. Moto costuma ser 2%, carro 4%."
                ) { v -> editar { it.copy(ipvaPercentX100 = v) } }
                LinhaCalculada("IPVA por ano", d.ipvaAnualEmCentavos.toLong())
            }
            LinhaCalculada("IPVA por mês", d.ipvaMensalEmCentavos.toLong())
        }
    }
    Cartao {
        Rotulo("Outros custos do mês")
        CampoDinheiro("Seguro mensal", d.seguroMensalCentavos, ajuda = "Paga por ano? Divida por 12.") { v ->
            editar { it.copy(seguroMensalCentavos = v) }
        }
        CampoDinheiro(
            "Outros custos fixos", d.outrosMensaisCentavos,
            ajuda = "Garagem, app de rastreamento, lavagem — o que você paga todo mês."
        ) { v -> editar { it.copy(outrosMensaisCentavos = v) } }
    }
}

@Composable
private fun PassoManutencao(d: DadosCusto, f: FatiasCusto, editar: ((DadosCusto) -> DadosCusto) -> Unit) {
    SomaDoPasso("Manutenção por km", f.manutencao, Icones.Chave, CorManutencao)
    Cartao {
        Rotulo("Troca de óleo")
        CampoDinheiro("Custo da troca", d.trocaOleoCentavos) { v -> editar { it.copy(trocaOleoCentavos = v) } }
        CampoKm("A cada quantos km", d.intervaloOleoKm) { v -> editar { it.copy(intervaloOleoKm = v) } }
        LinhaPorKm("Dá por km", d.trocaOleoCentavos, d.intervaloOleoKm)
    }
    Cartao {
        Rotulo("Pneus")
        CampoDinheiro("Jogo de pneus", d.jogoPneusCentavos) { v -> editar { it.copy(jogoPneusCentavos = v) } }
        CampoKm("Duram quantos km", d.duracaoPneusKm) { v -> editar { it.copy(duracaoPneusKm = v) } }
        LinhaPorKm("Dá por km", d.jogoPneusCentavos, d.duracaoPneusKm)
    }
    Cartao {
        Rotulo("Revisão")
        CampoDinheiro("Custo da revisão", d.revisaoCentavos) { v -> editar { it.copy(revisaoCentavos = v) } }
        CampoKm("A cada quantos km", d.intervaloRevisaoKm) { v -> editar { it.copy(intervaloRevisaoKm = v) } }
        LinhaPorKm("Dá por km", d.revisaoCentavos, d.intervaloRevisaoKm)
    }
    Cartao {
        Rotulo("Outro desgaste")
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
    if (!d.tipoVeiculo.usaCombustivel) {
        Text(
            "Bike não gasta combustível: este passo fica de fora da conta. Toque em Continuar.",
            style = MaterialTheme.typography.bodyMedium, color = TextoSecundario
        )
        return
    }
    val porKm = if (d.consumoX100 > 0) d.precoLitroCentavos * 100.0 / d.consumoX100 else 0.0
    SomaDoPasso(
        "Combustível por km", porKm, Icones.Combustivel, CorCombustivel,
        conta = if (porKm > 0) String.format(PT, "%.2f ÷ %.0f", d.precoLitroCentavos / 100.0, d.consumoX100 / 100.0) else null
    )
    Rotulo("Tipo")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TipoCombustivel.entries.chunked(3).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { c ->
                    Pilula(c.rotulo, d.combustivel == c, Modifier.weight(1f)) { editar { it.copy(combustivel = c) } }
                }
            }
        }
    }
    CampoDinheiro(
        when (d.combustivel) {
            TipoCombustivel.ELETRICO -> "Preço do kWh"
            TipoCombustivel.GNV -> "Preço do m³"
            else -> "Preço do litro"
        },
        d.precoLitroCentavos
    ) { v -> editar { it.copy(precoLitroCentavos = v) } }

    Cartao {
        val unidade = d.combustivel.unidade
        Rotulo("Quantos ${unidade.substringBefore('/')} faz com 1 ${unidade.substringAfter('/')}")
        // A barrinha resolve o caso comum num deslize; o campo fica para quem
        // quer o número exato (consumo com vírgula).
        val (min, max) = if (d.tipoVeiculo == TipoVeiculo.MOTO) 15f to 60f else 5f to 25f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (d.consumoX100 > 0) String.format(PT, "%.1f", d.consumoX100 / 100.0) else "—",
                style = EstiloValor, fontSize = 22.sp, modifier = Modifier.width(64.dp)
            )
            Slider(
                value = (d.consumoX100 / 100f).coerceIn(min, max),
                onValueChange = { v -> editar { it.copy(consumoX100 = Math.round(v * 2) * 50L) } },
                valueRange = min..max,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = TextoPrincipal, activeTrackColor = Lima, inactiveTrackColor = SuperficieMaisAlta)
            )
        }
        CampoDecimal(
            "Consumo médio ($unidade)", d.consumoX100,
            ajuda = if (d.tipoVeiculo == TipoVeiculo.MOTO) "Moto de 150–160 cc costuma fazer de 30 a 40 km/L no dia a dia."
            else "Carro popular costuma fazer de 10 a 14 km/L na cidade."
        ) { v -> editar { it.copy(consumoX100 = v) } }
    }
}

@Composable
private fun PassoMetas(d: DadosCusto, editar: ((DadosCusto) -> DadosCusto) -> Unit) {
    Cartao(espaco = 10) {
        Rotulo("Quanto você roda")
        CampoKm("Km por dia", d.kmPorDia) { v -> editar { it.copy(kmPorDia = v) } }

        Text("Dias por semana", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { n ->
                val ativo = d.diasPorSemana == n
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (ativo) Lima else SuperficieAlta)
                        .clickable { editar { it.copy(diasPorSemana = n) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$n", color = if (ativo) CorSobreDestaque else TextoSecundario, fontWeight = FontWeight.Bold)
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
            steps = 16,
            colors = SliderDefaults.colors(thumbColor = TextoPrincipal, activeTrackColor = Lima, inactiveTrackColor = SuperficieMaisAlta)
        )

        HorizontalDivider(color = Contorno)
        LinhaTexto("Dias no mês", String.format(PT, "%.0f dias", d.diasPorMes))
        LinhaTexto("Km no mês", String.format(PT, "%,.0f km", d.kmPorMes))
        LinhaTexto("Horas no mês", String.format(PT, "%.0f h", d.diasPorMes * d.horasPorDia))
    }

    Cartao(espaco = 10) {
        Rotulo("Sua meta")
        CampoDinheiro(
            "Lucro que quer levar por mês", d.metaLucroMensalCentavos,
            ajuda = "O que sobra pra você, já pagos todos os custos."
        ) { v -> editar { it.copy(metaLucroMensalCentavos = v) } }

        if (d.metaLucroMensalCentavos > 0) {
            // A meta do dia e a da semana saem desta: uma meta so, sem se contradizer.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuadroMeta("Por dia", d.metaLucroDiarioCentavos, Lima, Modifier.weight(1f))
                QuadroMeta("Semana", d.metaLucroSemanalCentavos, TextoPrincipal, Modifier.weight(1f))
                QuadroMeta("Mês", d.metaLucroMensalCentavos, TextoPrincipal, Modifier.weight(1f))
            }
            Text(
                "A meta do dia é a que aparece no Início, na barra do lucro de hoje.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
    }
}

/** Quadradinho "POR DIA 200" das metas (reais inteiros: centavo de meta não ajuda ninguém). */
@Composable
fun QuadroMeta(rotulo: String, centavos: Long, cor: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(SuperficieAlta).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(rotulo.uppercase(), fontSize = 9.sp, letterSpacing = 1.sp, color = TextoSecundario, fontWeight = FontWeight.SemiBold)
        Text(String.format(PT, "%,d", centavos / 100), style = EstiloValor, fontSize = 17.sp, color = cor)
    }
}

/** Carro, moto ou bike, em blocos grandes. Ao escolher bike, o tipo de trabalho vira entrega. */
@Composable
private fun EscolhaDoVeiculo(d: DadosCusto, editar: ((DadosCusto) -> DadosCusto) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TipoVeiculo.entries.forEach { t ->
            BlocoEscolha(Icones.doVeiculo(t), t.rotulo, d.tipoVeiculo == t, Modifier.weight(1f)) {
                editar {
                    it.copy(
                        tipoVeiculo = t,
                        // Bike não leva passageiro.
                        tipoTrabalho = if (t == TipoVeiculo.BIKE) TipoTrabalho.ENTREGAS else it.tipoTrabalho
                    )
                }
            }
        }
    }
}

/** Bloco grande de escolha com ícone (Carro / Moto / Bike). */
@Composable
private fun BlocoEscolha(icone: ImageVector, rotulo: String, ativo: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (ativo) Lima.copy(alpha = 0.12f) else SuperficieAlta)
            .border(2.dp, if (ativo) Lima else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icone, contentDescription = null, tint = if (ativo) Lima else TextoSecundario, modifier = Modifier.size(30.dp))
        Text(rotulo, fontWeight = FontWeight.SemiBold, color = if (ativo) TextoPrincipal else TextoSecundario)
    }
}

// ================================================================== resultado

@Composable
private fun ResultadoFinal(
    d: DadosCusto,
    r: ResultadoCusto,
    aplicado: Boolean,
    onAplicar: () -> Unit,
    onVoltar: () -> Unit,
    onFechar: () -> Unit
) {
    val f = FatiasCusto.de(r)
    Column(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Lima.copy(alpha = 0.16f), Color.Transparent)))
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)) {
            IconButton(onClick = onFechar) { Icon(Icones.Fechar, contentDescription = "Fechar", tint = TextoSecundario) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Lima.copy(alpha = 0.14f))
                        .border(3.dp, Lima, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icones.Certo, contentDescription = null, tint = Lima, modifier = Modifier.size(36.dp)) }
                Rotulo("Seu custo real", Modifier.padding(top = 14.dp))
                Text(
                    buildAnnotatedString {
                        append(r.custoPorKmCentavos.emReais())
                        withStyle(SpanStyle(fontSize = 22.sp, color = TextoSecundario)) { append("/km") }
                    },
                    style = EstiloValor, fontSize = 52.sp, color = Lima
                )
                Text(
                    "${r.custoMensalCentavos.emReais()} por mês para rodar " + String.format(PT, "%,d km", r.kmPorMes),
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario, textAlign = TextAlign.Center
                )
            }

            Cartao(espaco = 8) {
                Rotulo("De onde vem")
                LinhaFatia("Combustível", f.combustivel, f.total, CorCombustivel)
                LinhaFatia("Manutenção e pneus", f.manutencao, f.total, CorManutencao)
                LinhaFatia("IPVA, seguro e fixos", f.fixos, f.total, CorFixos)
                if (f.total <= 0) Text("Nenhum custo informado.", color = TextoSecundario)
            }

            if (r.tarifaMinimaPorKmCentavos > 0) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lima.copy(alpha = 0.08f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        buildAnnotatedString {
                            if (d.metaLucroDiarioCentavos > 0 && d.kmPorDia > 0) {
                                append("Para levar ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${d.metaLucroDiarioCentavos.emReais()} por dia")
                                }
                                append(" rodando ${d.kmPorDia} km, aceite corridas a partir de ")
                            } else {
                                append("Aceite corridas a partir de ")
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Lima)) {
                                append("${r.tarifaMinimaPorKmCentavos.emReais()}/km")
                            }
                            append(".")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${r.custoPorKmCentavos.emReais()} de custo + " +
                            "${(r.tarifaMinimaPorKmCentavos - r.custoPorKmCentavos).coerceAtLeast(0).emReais()} de lucro por km. " +
                            "Abaixo do custo você roda de graça.",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                }
            }

            Cartao(espaco = 6) {
                Rotulo("Projeção com essa tarifa")
                r.projecoes.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.rotulo, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                        Text(String.format(PT, "%,d km", p.km), style = MaterialTheme.typography.bodySmall, color = TextoSecundario, modifier = Modifier.weight(1f))
                        Text(p.lucroCentavos.emReais(), style = EstiloValor, fontSize = 15.sp, color = VerdeLucro)
                    }
                }
                Text("lucro já descontados todos os custos", fontSize = 11.sp, color = TextoSecundario)
            }
            if (aplicado) {
                Text(
                    "Pronto: o cartão da oferta já usa ${r.custoPorKmCentavos.emReais()}/km de custo e " +
                        "${r.tarifaMinimaPorKmCentavos.emReais()}/km como mínimo.",
                    style = MaterialTheme.typography.bodySmall, color = Turquesa
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAplicar,
                enabled = r.valido && !aplicado,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = CorSobreDestaque)
            ) {
                Text(
                    if (aplicado) "Aplicado ao semáforo"
                    else "Usar ${r.tarifaMinimaPorKmCentavos.emReais()}/km no semáforo",
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Button(
                onClick = onVoltar,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuperficieAlta, contentColor = TextoPrincipal)
            ) { Text("Revisar os valores", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun LinhaFatia(nome: String, porKm: Double, total: Double, cor: Color) {
    if (porKm <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(cor))
        Text(nome, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Text(centavosPorKm(porKm).removePrefix("R$ "), style = EstiloValor, fontSize = 15.sp)
        Text(
            "${Math.round(porKm * 100 / total)}%",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario,
            textAlign = TextAlign.End, modifier = Modifier.width(44.dp)
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
        rotulo = rotulo,
        valor = texto,
        onValor = { novo ->
            texto = novo
            onValor(novo.paraCentavosOuZero() ?: 0L)
        },
        erro = texto.isNotBlank() && texto.paraCentavosOuZero() == null,
        ajuda = ajuda,
        prefixo = "R$",
        // Ao sair do campo, "15000" vira "15.000,00" — o motorista confere de relance
        // se não faltou nem sobrou um zero.
        aoPerderFoco = { t -> t.paraCentavosOuZero()?.campoReais() ?: t }
    )
}

/** Campo de texto comum (sem teclado numérico), para nome do veículo e afins. */
@Composable
private fun CampoTexto(rotulo: String, valor: String, ajuda: String? = null, onValor: (String) -> Unit) {
    var texto by rememberSaveable(rotulo) { mutableStateOf(valor) }
    CampoFormulario(
        rotulo = rotulo,
        valor = texto,
        onValor = { novo -> texto = novo; onValor(novo) },
        numerico = false,
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
            // Sem teto: cortar em 100% escondia o erro de quem digita reais aqui.
            // Valor estranho vira AVISO na hora de calcular, nao um numero trocado.
            onValor(((novo.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0))
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
private fun Long.campoReais(): String = String.format(PT, "%,.2f", this / 100.0)

private const val POR_PORCENTO = "%"
private const val POR_REAIS = "R$"

// ============================================================ assistente rápido

/**
 * Assistente rápido (4.17): a camada OBRIGATÓRIA, numa tela só, antes de usar o
 * app. Só o que o motorista sabe de cabeça e que mais pesa no custo: o veículo,
 * o combustível, o que paga pelo veículo, a rotina e a meta limpa por hora.
 *
 * Óleo, pneus, IPVA, seguro e depreciação ficam para o assistente completo
 * (Menu ▸ Meu veículo e custos). Até lá o custo é o "básico" e o cartão avisa.
 */
@Composable
fun AssistenteRapidoScreen(
    onPronto: () -> Unit,
    vm: AssistenteCustoViewModel = viewModel(factory = AssistenteCustoViewModel.Factory)
) {
    val e by vm.estado.collectAsStateWithLifecycle()
    if (e.carregando) return
    val d = e.dados
    var metaHora by rememberSaveable { mutableStateOf(d.metaLimpaHoraCentavos) }
    var salvando by remember { mutableStateOf(false) }
    val comMeta = d.comMetaPorHora(metaHora)
    val falta = comMeta.faltaNoBasico()
    val r = remember(comMeta) { CalculadoraCusto.calcular(comMeta) }

    Column(Modifier.fillMaxSize().background(Fundo).statusBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Antes de começar", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextoPrincipal)
            Text(
                "Uma conta rápida para o cartão saber se a corrida dá lucro para VOCÊ. Leva um minuto.",
                style = MaterialTheme.typography.bodyMedium, color = TextoSecundario
            )

            Cartao(espaco = 10) {
                Rotulo("Seu veículo")
                EscolhaDoVeiculo(d, vm::editar)
                if (d.tipoVeiculo != TipoVeiculo.BIKE) {
                    Rotulo("O que você faz", Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TipoTrabalho.entries.forEach { t ->
                            Pilula(if (t == TipoTrabalho.AMBOS) "Os dois" else t.rotulo, d.tipoTrabalho == t) {
                                vm.editar { it.copy(tipoTrabalho = t) }
                            }
                        }
                    }
                }
            }

            if (d.tipoVeiculo.usaCombustivel) {
                Cartao(espaco = 10) {
                    Rotulo("Combustível")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TipoCombustivel.entries.chunked(3).forEach { linha ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                linha.forEach { c ->
                                    Pilula(c.rotulo, d.combustivel == c, Modifier.weight(1f)) {
                                        vm.editar { it.copy(combustivel = c) }
                                    }
                                }
                            }
                        }
                    }
                    CampoDinheiro(
                        when (d.combustivel) {
                            TipoCombustivel.ELETRICO -> "Preço do kWh"
                            TipoCombustivel.GNV -> "Preço do m³"
                            else -> "Preço do litro"
                        },
                        d.precoLitroCentavos
                    ) { v -> vm.editar { it.copy(precoLitroCentavos = v) } }
                    CampoDecimal(
                        "Consumo médio (${d.combustivel.unidade})", d.consumoX100,
                        ajuda = "Quanto ele faz no dia a dia, de cabeça mesmo."
                    ) { v -> vm.editar { it.copy(consumoX100 = v) } }
                }
            }

            Cartao(espaco = 10) {
                Rotulo("Quanto paga pelo veículo")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormaAquisicao.entries.forEach { f ->
                        Pilula(f.rotulo, d.forma == f) { vm.editar { it.copy(forma = f) } }
                    }
                }
                when (d.forma) {
                    FormaAquisicao.QUITADO -> Text(
                        "Seu, sem parcela. A depreciação entra depois, no assistente completo.",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                    FormaAquisicao.FINANCIADO -> CampoDinheiro("Parcela do mês", d.parcelaMensalCentavos) { v ->
                        vm.editar { it.copy(parcelaMensalCentavos = v) }
                    }
                    FormaAquisicao.ALUGADO -> CampoDinheiro(
                        "Aluguel do mês", d.aluguelMensalCentavos,
                        ajuda = "Paga por semana? Multiplique por 4,3."
                    ) { v -> vm.editar { it.copy(aluguelMensalCentavos = v) } }
                }
            }

            Cartao(espaco = 10) {
                Rotulo("Sua rotina e meta")
                CampoKm("Km por dia", d.kmPorDia) { v -> vm.editar { it.copy(kmPorDia = v) } }
                Text(
                    "Horas por dia: ${d.horasPorDia}h",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = d.horasPorDia.toFloat().coerceIn(1f, 18f),
                    onValueChange = { v -> vm.editar { it.copy(horasPorDia = v.toInt().coerceIn(1, 18)) } },
                    valueRange = 1f..18f,
                    steps = 16,
                    colors = SliderDefaults.colors(thumbColor = TextoPrincipal, activeTrackColor = Lima, inactiveTrackColor = SuperficieMaisAlta)
                )
                Text("Dias por semana", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { n ->
                        val ativo = d.diasPorSemana == n
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (ativo) Lima else SuperficieAlta)
                                .clickable { vm.editar { it.copy(diasPorSemana = n) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$n", color = if (ativo) CorSobreDestaque else TextoSecundario, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                CampoDinheiro(
                    "Quanto quer ganhar limpo por hora", metaHora,
                    ajuda = "O que sobra pra você por hora rodando, já pagos combustível e veículo."
                ) { v -> metaHora = v }
            }

            if (falta.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lima.copy(alpha = 0.08f))
                        .border(1.dp, Lima.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Rotulo("Seu custo (básico)")
                    Text("${r.custoPorKmCentavos.emReais()} /km", style = EstiloValor, fontSize = 22.sp, color = Lima)
                    Text(
                        "Meta: ${comMeta.metaLucroDiarioCentavos.emReais()} limpos por dia " +
                            "(${metaHora.emReais()} × ${d.horasPorDia}h).",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                }
            }
            r.avisos.forEach { Text("• ${it.texto}", style = MaterialTheme.typography.bodySmall, color = AmareloAlerta) }
            Text(
                "Óleo, pneus, IPVA e seguro ficam para depois, em Menu ▸ Meu veículo e custos. " +
                    "Até lá o cartão mostra \"custo básico\": um pouco abaixo do real.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (falta.isNotEmpty()) {
                Text(
                    "Falta: " + falta.joinToString(", ") + ".",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
            }
            Button(
                onClick = {
                    salvando = true
                    vm.concluirRapido(metaHora) { onPronto() }
                },
                enabled = falta.isEmpty() && !salvando,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = CorSobreDestaque)
            ) { Text("Pronto, começar", fontWeight = FontWeight.ExtraBold) }
        }
    }
}
