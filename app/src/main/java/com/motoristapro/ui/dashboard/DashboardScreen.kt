package com.motoristapro.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import com.motoristapro.ui.componentes.BarraMeta
import com.motoristapro.ui.componentes.LogoPlataforma
import com.motoristapro.pacote.PacoteTaxa
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.CartaoDestaque
import com.motoristapro.ui.componentes.MetricaCompacta
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.componentes.ValorComCentavos
import com.motoristapro.ui.theme.Fundo
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.SuperficieMaisAlta
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motoristapro.data.local.entity.CategoriaDespesa
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.Aba
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.CartaoJornada
import com.motoristapro.ui.componentes.CorridaDialog
import com.motoristapro.ui.componentes.DespesaDialog
import com.motoristapro.ui.componentes.TelaAba
import com.motoristapro.ui.formatarDuracao
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.motoristapro.ui.theme.Contorno
import com.motoristapro.ui.componentes.Metrica
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import com.motoristapro.ui.componentes.BotaoProEscuro
import com.motoristapro.ui.componentes.BotaoPro
import com.motoristapro.ui.componentes.CardPro
import com.motoristapro.ui.NavegacaoRapida
import com.motoristapro.ui.mais.AtalhoRedondo
import com.motoristapro.ui.mais.SubTela
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.theme.AzulInfo
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.Roxo
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoPrincipal
import com.motoristapro.ui.theme.Turquesa
import androidx.compose.foundation.background
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "BR"))

/**
 * Leva para a tela de ajuste dentro do Menu, em um toque.
 *
 * Troca a aba e já pede a sub-tela: sem isso o motorista cairia na lista do
 * Menu e teria que achar o item — que é exatamente o que o atalho existe para
 * evitar.
 */
private fun irParaAjuste(tela: SubTela, irPara: (Aba) -> Unit) {
    NavegacaoRapida.pedirSubTela(tela.name, Aba.MAIS.ordinal)
    irPara(Aba.MAIS)
}

/** Aba Início: turno, lucro do dia, eficiência e atalhos de registro. */
@Composable
fun DashboardRoute(
    irPara: (Aba) -> Unit,
    vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val leitorOk by vm.leitorConectado.collectAsStateWithLifecycle()
    val gpsOk by vm.gpsRodando.collectAsStateWithLifecycle()
    val plataformas by vm.plataformas.collectAsStateWithLifecycle()
    val sequencia by vm.sequencia.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val snackbar = remember { SnackbarHostState() }


    // Relógio para o cronômetro do turno (1 s).
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(estado.jornada?.id) {
        while (true) {
            agora = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) { vm.mensagens.collect { snackbar.showSnackbar(it) } }

    // Comemoração: uma vez por dia, na hora em que o lucro passa da meta.
    var comemorar by rememberSaveable { mutableStateOf(false) }
    val bateuMeta = !estado.carregando && estado.config.metaLucroDiarioCentavos > 0 &&
        estado.resumo.lucroLiquidoCentavos >= estado.config.metaLucroDiarioCentavos
    LaunchedEffect(bateuMeta, estado.dia) {
        if (bateuMeta && !ComemoracaoMetaPrefs.jaComemorou(contexto, estado.dia)) {
            ComemoracaoMetaPrefs.marcar(contexto, estado.dia)
            comemorar = true
        }
    }

    // Localização (+ notificações no Android 13+) antes de iniciar o turno.
    val permissoes = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val pedirPermissoes = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        vm.iniciarJornada(comGps = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true)
    }

    // Botão "Iniciar jornada" do widget: espera o Início carregar e segue o
    // mesmo caminho do botão da tela (pede a localização e abre o turno).
    val pedidoWidget by NavegacaoRapida.iniciarJornadaPedido.collectAsStateWithLifecycle()
    LaunchedEffect(pedidoWidget, estado.carregando) {
        if (pedidoWidget && !estado.carregando) {
            NavegacaoRapida.iniciarJornadaPedido.value = false
            if (estado.jornada == null) pedirPermissoes.launch(permissoes)
        }
    }

    // O cabeçalho fala com a pessoa, não com o app: "Olá, Lucas" e, do lado, o
    // único aviso que importa estar sempre visível — se o leitor está lendo.
    val primeiroNome = estado.config.nomeMotorista?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
    TelaAba(
        titulo = if (primeiroNome != null) "Olá, $primeiroNome" else "Olá, motorista",
        subtitulo = estado.dia.format(FORMATO_DIA).replaceFirstChar { it.uppercase() },
        snackbar = snackbar,
        acoes = { PastilhaLeitor(leitorOk, onTestar = vm::testarLeitor) }
    ) { padding ->
        when {
            estado.carregando -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = Lima)
            }
            estado.erro != null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text(estado.erro ?: "", color = VermelhoPrejuizo, textAlign = TextAlign.Center)
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // A ORDEM da tela é a ordem da pergunta do motorista: quanto
                // sobrou hoje, de onde veio, registrar o que acabou de rolar e,
                // por último, o relógio — que é para olhar, não para tocar.
                LucroDoDia(estado, agora, onVerFinancas = { irPara(Aba.FINANCAS) })

                PacoteValeuAPena(estado)

                QuatroNumeros(estado)

                // Os três ajustes que decidem tudo o que o app calcula, na tela
                // de abrir — e não a dois ou três toques dentro do Menu. São os
                // únicos que o motorista mexe de verdade depois de configurar.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BlocoAtalho(Icones.Semaforo, "Semáforo", AmareloAlerta, Modifier.weight(1f)) {
                        irParaAjuste(SubTela.SEMAFORO, irPara)
                    }
                    BlocoAtalho(Icones.Veiculo, "Veículo", AzulInfo, Modifier.weight(1f)) {
                        irParaAjuste(SubTela.VEICULO, irPara)
                    }
                    BlocoAtalho(
                        Icones.Checkup, "Checkup", if (leitorOk) Roxo else VermelhoPrejuizo, Modifier.weight(1f)
                    ) {
                        irParaAjuste(SubTela.CHECKUP, irPara)
                    }
                }

                // Sem "+ Corrida / + Despesa" aqui: eram os mesmos botões das
                // abas Corridas e Finanças, e o Início é para olhar o dia.
                JornadaCard(
                    estado = estado,
                    agora = agora,
                    gpsRodando = gpsOk,
                    onIniciar = { pedirPermissoes.launch(permissoes) },
                    onPausar = vm::alternarPausa,
                    onEncerrar = vm::encerrarJornada,
                    onRetomarGps = vm::retomarGps
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (comemorar) {
        ComemoracaoMeta(
            lucroCentavos = estado.resumo.lucroLiquidoCentavos,
            segundosRodados = estado.jornadasEncerradas.segundos + (estado.jornada?.segundosRodando(agora) ?: 0),
            sequencia = sequencia,
            turnoAberto = estado.jornada != null,
            onContinuar = { comemorar = false },
            onEncerrar = { comemorar = false; vm.encerrarJornada() }
        )
    }
}

/**
 * O número do dia: lucro líquido, com a meta logo abaixo.
 *
 * Lucro e não faturamento, de propósito. Faturamento é o número que a
 * plataforma mostra; lucro é o que fica depois do combustível e do desgaste —
 * é essa a conta que o app existe para fazer.
 */
@Composable
private fun LucroDoDia(e: DashboardUiState, agora: Long, onVerFinancas: () -> Unit) {
    val lucro = e.resumo.lucroLiquidoCentavos
    val meta = e.config.metaLucroDiarioCentavos
    CartaoDestaque(Modifier.clickable { onVerFinancas() }) {
        Rotulo("Lucro líquido de hoje")
        ValorComCentavos(lucro.emReais(), 46.sp, cor = if (lucro >= 0) TextoPrincipal else VermelhoPrejuizo)
        if (meta > 0) {
            BarraMeta(e.progressoMeta, Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${(lucro.coerceAtLeast(0) * 100 / meta)}% da meta",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                Box(Modifier.weight(1f))
                Text(meta.emReais(), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
            QuantoFalta(e, agora)
        } else {
            Text(
                "Defina sua meta em Menu ▸ Meu veículo e custos.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        // Com o pacote da 99 ligado, a conta aparece aberta: o motorista vê
        // que a corrida entrou cheia e onde o pacote foi descontado.
        if (e.pacote.ativo) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Contorno.copy(alpha = 0.4f)))
            LinhaDaConta("Corridas (100%)", e.resumo.faturamentoCentavos.emReais(), TextoPrincipal)
            LinhaDaConta(
                "Combustível e custos",
                "− " + (e.resumo.despesasCentavos - e.pacoteHojeCentavos).coerceAtLeast(0).emReais(),
                VermelhoPrejuizo
            )
            if (e.pacoteHojeCentavos > 0) {
                LinhaDaConta("Pacote 99 · ${e.pacote.plano.rotulo}", "− " + e.pacoteHojeCentavos.emReais(), VermelhoPrejuizo)
            }
        }
    }
}

@Composable
private fun LinhaDaConta(rotulo: String, valor: String, cor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = cor)
    }
}

/**
 * "O pacote valeu a pena hoje": a taxa que a 99 ficaria sem ele (pela taxa
 * média) menos a parte do pacote que cabe ao dia. É só comparação — o lucro
 * de cima já está certo com ou sem este cartão.
 */
@Composable
private fun PacoteValeuAPena(e: DashboardUiState) {
    val economia = e.economiaPacoteCentavos ?: return
    val compensou = economia >= 0
    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoPlataforma("99", tamanho = 34.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    if (compensou) "Pacote valeu a pena hoje" else "Pacote ainda não compensou hoje",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                )
                Text(
                    "Sem ele a 99 ficaria com ~ ${PacoteTaxa.taxaEvitadaCentavos(e.ganhos99HojeCentavos).emReais()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (compensou) Lima else TextoSecundario
                )
            }
            Text(
                (if (compensou) "+ " else "− ") + kotlin.math.abs(economia).emReais(),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (compensou) Lima else VermelhoPrejuizo
            )
        }
        Text(
            "Conta de comparação com a taxa média de 20% da 99.",
            style = MaterialTheme.typography.labelSmall, color = TextoSecundario
        )
    }
}

/**
 * A linha "faltam R$ X; no seu ritmo, termina por volta das HHhMM".
 *
 * O ritmo usa o tempo de turno SEM as pausas: almoço de uma hora não pode
 * derrubar o R$/hora e empurrar a previsão para a madrugada.
 */
@Composable
private fun QuantoFalta(e: DashboardUiState, agora: Long) {
    val jornada = e.jornada
    val segundosRodados = e.jornadasEncerradas.segundos + (jornada?.segundosRodando(agora) ?: 0)
    val previsao = PrevisaoMeta.calcular(
        metaCentavos = e.config.metaLucroDiarioCentavos,
        lucroCentavos = e.resumo.lucroLiquidoCentavos,
        segundosRodados = segundosRodados,
        turnoAndando = jornada != null && !jornada.pausada,
        agoraMillis = agora
    ) ?: return

    val negrito = SpanStyle(fontWeight = FontWeight.Bold, color = TextoPrincipal)
    val mensagem = buildAnnotatedString {
        when (previsao) {
            PrevisaoMeta.Batida -> withStyle(negrito) { append("Meta do dia batida! 🎉") }
            is PrevisaoMeta.Falta -> {
                append("Faltam ")
                withStyle(negrito) { append(previsao.faltaCentavos.emReais()) }
                append(" para a meta.")
                val ritmo = previsao.ritmoCentavosHora
                if (ritmo != null) {
                    append(" No seu ritmo de ")
                    withStyle(negrito) { append("${ritmo.emReais()}/h") }
                    // Comparado com a meta por hora: dá para saber no meio do
                    // dia se o ritmo fecha a conta, sem esperar a noite.
                    val metaHora = e.metaPorHoraCentavos
                    if (metaHora > 0) {
                        val bate = ritmo >= metaHora
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (bate) Lima else AmareloAlerta)) {
                            append(" (meta ${metaHora.emReais()}/h${if (bate) " ✓" else ""})")
                        }
                    }
                    val chega = previsao.chegaEmMillis
                    if (chega != null) {
                        val hora = Instant.ofEpochMilli(chega).atZone(ZoneId.systemDefault())
                        append(", termina por volta das ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Turquesa)) {
                            append(hora.format(FORMATO_PREVISAO))
                        }
                    }
                    append(".")
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(Fundo.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icones.Relogio, contentDescription = null,
            tint = if (previsao == PrevisaoMeta.Batida) Lima else Turquesa,
            modifier = Modifier.size(20.dp)
        )
        Text(mensagem, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
    }
}

/** "17h50": como o motorista fala a hora, sem os dois-pontos de relógio digital. */
private val FORMATO_PREVISAO = DateTimeFormatter.ofPattern("HH'h'mm")

/**
 * Os quatro números que respondem "como está o dia" sem rolar a tela.
 *
 * Taxa de aceite entra aqui porque é o único deles que o motorista não
 * consegue ver em lugar nenhum: as plataformas mostram a delas, não a dele.
 */
@Composable
private fun QuatroNumeros(e: DashboardUiState) {
    val r = e.resumo
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricaCompacta("Faturamento", r.faturamentoCentavos.emReais(), Modifier.weight(1f))
            MetricaCompacta("Despesas", r.despesasCentavos.emReais(), Modifier.weight(1f), cor = VermelhoPrejuizo)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricaCompacta(
                "R$ por km",
                if (r.metrosRodados > 0) r.ganhoPorKmCentavos.emReais() else "—",
                Modifier.weight(1f)
            )
            MetricaCompacta("Aceite", e.taxaDeAceite?.let { "$it%" } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PastilhaLeitor(conectado: Boolean, onTestar: () -> Unit) {
    val cor = if (conectado) Lima else AmareloAlerta
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = cor.copy(alpha = 0.16f),
        modifier = Modifier.padding(end = 10.dp).clickable { onTestar() }
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(shape = CircleShape, color = cor, modifier = Modifier.size(8.dp)) {}
            Text(
                if (conectado) "LEITOR ON" else "LEITOR OFF",
                style = MaterialTheme.typography.labelSmall,
                color = cor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun JornadaCard(
    estado: DashboardUiState,
    agora: Long,
    gpsRodando: Boolean,
    onIniciar: () -> Unit,
    onPausar: () -> Unit,
    onEncerrar: () -> Unit,
    onRetomarGps: () -> Unit
) {
    val jornada = estado.jornada
    Cartao(espaco = 10) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val segundos = jornada?.segundosRodando(agora) ?: estado.segundosTrabalhados(agora)
            AnelPequeno(
                fracao = segundos.toFloat() / estado.turnoDeReferenciaSeg,
                cor = when {
                    jornada?.pausada == true -> AmareloAlerta
                    segundos > estado.turnoDeReferenciaSeg -> AmareloAlerta
                    else -> Turquesa
                }
            )
            Column(Modifier.weight(1f)) {
                Rotulo(
                    when {
                        jornada == null -> "Jornada"
                        jornada.pausada -> "Jornada · pausada"
                        else -> "Jornada · desde " + Instant.ofEpochMilli(jornada.inicioEm)
                            .atZone(ZoneId.systemDefault()).format(FORMATO_INICIO)
                    }
                )
                Text(
                    if (jornada == null && segundos == 0L) "Parada" else segundos.formatarDuracao(),
                    style = EstiloValor, fontSize = 22.sp, color = TextoPrincipal
                )
            }
            if (jornada != null) {
                BotaoPequeno(if (jornada.pausada) "Retomar" else "Pausar", if (jornada.pausada) Lima else TextoPrincipal, onPausar)
                BotaoPequeno("Encerrar", VermelhoPrejuizo, onEncerrar)
            }
        }
        if (jornada == null) {
            BotaoPro("▶  Iniciar jornada", Modifier.fillMaxWidth(), onClick = onIniciar)
        } else {
            // Km e combustível numa linha fina, sem roubar o lugar do relógio.
            val gasto = estado.config.custoCombustivelKmCentavos
                ?.let { Math.round(jornada.metrosGps / 1000.0 * it) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(Locale("pt", "BR"), "%.1f km pelo GPS", jornada.metrosGps / 1000.0),
                    style = MaterialTheme.typography.labelSmall, color = TextoSecundario
                )
                Box(Modifier.weight(1f))
                if (gasto != null) {
                    Text(
                        "${gasto.emReais()} de combustível",
                        style = MaterialTheme.typography.labelSmall, color = TextoSecundario
                    )
                }
            }
            if (!gpsRodando) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = AmareloAlerta)
                    Text(
                        "GPS pausado",
                        color = AmareloAlerta,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    TextButton(onClick = onRetomarGps) { Text("Retomar GPS", color = Lima) }
                }
            }
        }
    }
}

/** O anel pequeno da jornada (54 dp): enche em relação a um turno de 8 h. */
@Composable
private fun AnelPequeno(fracao: Float, cor: Color) {
    val trilho = SuperficieMaisAlta
    Canvas(Modifier.size(54.dp)) {
        val traco = 6.dp.toPx()
        val area = Size(size.width - traco, size.height - traco)
        val canto = Offset(traco / 2, traco / 2)
        drawArc(trilho, 0f, 360f, false, canto, area, style = Stroke(traco))
        val f = fracao.coerceIn(0f, 1f)
        if (f > 0f) drawArc(cor, -90f, 360f * f, false, canto, area, style = Stroke(traco, cap = StrokeCap.Round))
    }
}

/** Botão baixinho do cartão da jornada (Pausar / Encerrar). */
@Composable
private fun BotaoPequeno(texto: String, cor: Color, onClick: () -> Unit) {
    Text(
        texto,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(SuperficieAlta)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 9.dp),
        color = cor,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge
    )
}

/** Um dos três atalhos do Início: ícone colorido em cima, nome embaixo, num bloco só dele. */
@Composable
private fun BlocoAtalho(icone: ImageVector, rotulo: String, cor: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Superficie).clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(24.dp))
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
    }
}

/** Hora de início do turno, para a linha de baixo do anel. */
private val FORMATO_INICIO = DateTimeFormatter.ofPattern("HH:mm")

/**
 * O turno de referência do anel: as horas por dia de Menu ▸ Suas metas (8 se
 * ninguém disse). Passando disso o anel fica âmbar, que é o aviso de dia esticado.
 */
private val DashboardUiState.turnoDeReferenciaSeg: Long get() = horasPorDia.coerceAtLeast(1) * 3600L
