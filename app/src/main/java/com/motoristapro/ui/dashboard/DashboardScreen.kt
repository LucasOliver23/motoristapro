package com.motoristapro.ui.dashboard

import com.motoristapro.ui.componentes.Tecla
import com.motoristapro.ui.componentes.SeloEstado
import com.motoristapro.ui.mais.lerCopiloto
import com.motoristapro.ui.mais.EstadoCopiloto
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.mutableIntStateOf
import com.motoristapro.ui.theme.VerdeLucro
import com.motoristapro.ui.manutencao.DadosKm
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.ui.manutencao.KmDoPainelDialog
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import com.motoristapro.ui.componentes.LogoPlataforma
import com.motoristapro.pacote.PacoteTaxa
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.Rotulo
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
import com.motoristapro.ui.componentes.CardPro
import com.motoristapro.ui.NavegacaoRapida
import com.motoristapro.ui.mais.SubTela
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.theme.EstiloValor
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.TextoPrincipal
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val manutencao by vm.manutencaoChegando.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // O selo do cabeçalho (5.1): relido a cada volta ao app, porque o motorista
    // libera as autorizações fora dele, nas Configurações do Android.
    var releitura by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        releitura++
        onPauseOrDispose { }
    }
    val copiloto = remember(releitura, leitorOk) { contexto.lerCopiloto(leitorOk) }


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

    // O km do painel, perguntado ao abrir e ao fechar a jornada: é o que
    // mantém a manutenção certa mesmo com o uso da moto fora do app.
    val escopo = rememberCoroutineScope()
    var perguntaKm by remember { mutableStateOf<Pair<Boolean, DadosKm>?>(null) }
    fun perguntarKm(encerrando: Boolean) {
        escopo.launch {
            val dados = vm.dadosKm(encerrando)
            // Bike não tem painel de km: segue direto. Com mais de um veículo a
            // pergunta do início ainda aparece, para escolher com qual rodar.
            if (dados.tipo == TipoVeiculo.BIKE && dados.veiculos.size <= 1) {
                if (encerrando) vm.encerrarJornada() else pedirPermissoes.launch(permissoes)
            } else {
                perguntaKm = encerrando to dados
            }
        }
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
    // único aviso que importa estar sempre visível — se o Copiloto está pronto.
    val primeiroNome = estado.config.nomeMotorista?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
    TelaAba(
        titulo = if (primeiroNome != null) "Olá, $primeiroNome" else "Olá, motorista",
        subtitulo = estado.dia.format(FORMATO_DIA).replaceFirstChar { it.uppercase() },
        snackbar = snackbar,
        acoes = {
            SeloCopiloto(copiloto, Modifier.padding(end = 10.dp)) { irParaAjuste(SubTela.COPILOTO, irPara) }
        }
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
                MedidorDoDia(estado, onVerFinancas = { irPara(Aba.FINANCAS) })

                RitmoEFalta(estado, agora, onDefinirMeta = { irParaAjuste(SubTela.METAS, irPara) })

                PacoteValeuAPena(estado)

                manutencao?.let { (item, faltam) ->
                    ManutencaoChegando(item.nome, faltam, item.odometroDoVencimento) {
                        irParaAjuste(SubTela.MANUTENCAO, irPara)
                    }
                }

                QuatroNumeros(estado, agora)

                // Os três ajustes que decidem tudo o que o app calcula, na tela
                // de abrir — e não a dois ou três toques dentro do Menu. São os
                // únicos que o motorista mexe de verdade depois de configurar.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BlocoAtalho(Icones.Semaforo, "Semáforo", AmareloAlerta, Modifier.weight(1f)) {
                        irParaAjuste(SubTela.SEMAFORO, irPara)
                    }
                    BlocoAtalho(Icones.Veiculo, "Veículo", TextoPrincipal, Modifier.weight(1f)) {
                        irParaAjuste(SubTela.VEICULO, irPara)
                    }
                    BlocoAtalho(
                        Icones.Copiloto, "Copiloto",
                        when {
                            !copiloto.leitor -> VermelhoPrejuizo
                            copiloto.tudoPronto -> VerdeLucro
                            else -> AmareloAlerta
                        },
                        Modifier.weight(1f)
                    ) {
                        irParaAjuste(SubTela.COPILOTO, irPara)
                    }
                }

                // Sem "+ Corrida / + Despesa" aqui: eram os mesmos botões das
                // abas Corridas e Finanças, e o Início é para olhar o dia.
                JornadaCard(
                    estado = estado,
                    agora = agora,
                    gpsRodando = gpsOk,
                    onIniciar = { perguntarKm(encerrando = false) },
                    onPausar = vm::alternarPausa,
                    onEncerrar = { perguntarKm(encerrando = true) },
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
            onEncerrar = { comemorar = false; perguntarKm(encerrando = true) }
        )
    }

    perguntaKm?.let { (encerrando, dados) ->
        val seguir = {
            perguntaKm = null
            if (encerrando) vm.encerrarJornada() else pedirPermissoes.launch(permissoes)
        }
        KmDoPainelDialog(
            encerrando = encerrando,
            dados = dados,
            onConfirmar = { km -> vm.registrarKm(km, encerrando); seguir() },
            onPular = { seguir() },
            onDismiss = { perguntaKm = null },
            onTrocarVeiculo = { id ->
                escopo.launch {
                    vm.trocarVeiculo(id)
                    perguntaKm = false to vm.dadosKm(false)
                }
            }
        )
    }
}

/**
 * O número do dia (5.1.2, o medidor aprovado): o lucro líquido no meio de um
 * arco que enche com a meta. Amarelo enquanto falta, verde quando bate.
 *
 * Lucro e não faturamento, de propósito. Faturamento é o número que a
 * plataforma mostra; lucro é o que fica depois do combustível e do desgaste —
 * é essa a conta que o app existe para fazer.
 */
@Composable
private fun MedidorDoDia(e: DashboardUiState, onVerFinancas: () -> Unit) {
    val lucro = e.resumo.lucroLiquidoCentavos
    val meta = e.config.metaLucroDiarioCentavos
    val fracao = if (meta > 0) lucro.coerceAtLeast(0).toFloat() / meta else 0f
    val cor = if (fracao >= 1f) VerdeLucro else AmareloAlerta
    val trilho = SuperficieMaisAlta
    val marca = Contorno
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onVerFinancas() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val traco = 16.dp.toPx()
                val margem = traco / 2 + 10.dp.toPx()
                val area = Size(size.width - margem * 2, size.height - margem * 2)
                val canto = Offset(margem, margem)
                // 270° abertos embaixo, como o ponteiro de um painel.
                drawArc(trilho, 135f, 270f, false, canto, area, style = Stroke(traco, cap = StrokeCap.Butt))
                if (fracao > 0f) {
                    drawArc(cor, 135f, 270f * fracao.coerceAtMost(1f), false, canto, area, style = Stroke(traco, cap = StrokeCap.Butt))
                }
                // Cinco marcas por fora: 0, 25, 50, 75 e 100% da meta.
                val raio = size.width / 2
                for (k in 0..4) {
                    val ang = Math.toRadians(135.0 + 270.0 * k / 4)
                    val de = raio - 4.dp.toPx()
                    val ate = raio - 1.dp.toPx()
                    drawLine(
                        marca,
                        Offset(center.x + (de * Math.cos(ang)).toFloat(), center.y + (de * Math.sin(ang)).toFloat()),
                        Offset(center.x + (ate * Math.cos(ang)).toFloat(), center.y + (ate * Math.sin(ang)).toFloat()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Rotulo("Lucro hoje")
                Text(
                    lucro.emReais().replace("R$ ", ""),
                    style = EstiloValor, fontSize = 40.sp,
                    color = if (lucro >= 0) TextoPrincipal else VermelhoPrejuizo
                )
                if (meta > 0) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = cor, fontWeight = FontWeight.Bold)) {
                                append("${(fracao * 100).toInt()}%")
                            }
                            append(" de ${meta.emReais().removeSuffix(",00")}")
                        },
                        fontSize = 12.sp, color = TextoSecundario
                    )
                } else {
                    Text("sem meta do dia", fontSize = 12.sp, color = TextoSecundario)
                }
            }
        }
        // Com o pacote da 99 ligado, a conta aparece aberta: o motorista vê
        // que a corrida entrou cheia e onde o pacote foi descontado.
        if (e.pacote.ativo) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Superficie)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
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
}

@Composable
private fun LinhaDaConta(rotulo: String, valor: String, cor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = cor)
    }
}

/** Cartão amarelo (ou vermelho, se venceu) do item de manutenção que está chegando. */
@Composable
private fun ManutencaoChegando(nome: String, faltam: Long, vence: Long, onClick: () -> Unit) {
    val vencido = faltam <= 0
    val cor = if (vencido) VermelhoPrejuizo else AmareloAlerta
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Superficie)
            .border(1.5.dp, cor, RoundedCornerShape(20.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { com.motoristapro.ui.componentes.IconeTexto(com.motoristapro.ui.componentes.Icones.Chave, cor) }
        Column(Modifier.weight(1f)) {
            Text(
                if (vencido) "$nome venceu" else "$nome chegando",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
            )
            Text(
                (if (vencido) "passou ${-faltam} km" else "faltam $faltam km") +
                    " · " + String.format(java.util.Locale("pt", "BR"), "%,d km", vence),
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        Text(
            if (vencido) "ATRASADA" else "ATENÇÃO",
            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = cor,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(cor.copy(alpha = 0.14f))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        )
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
                    color = if (compensou) VerdeLucro else TextoSecundario
                )
            }
            Text(
                (if (compensou) "+ " else "− ") + kotlin.math.abs(economia).emReais(),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (compensou) VerdeLucro else VermelhoPrejuizo
            )
        }
        Text(
            "Conta de comparação com a taxa média de 20% da 99.",
            style = MaterialTheme.typography.labelSmall, color = TextoSecundario
        )
    }
}

/**
 * RITMO e FALTA, lado a lado (5.1.2): quanto o dia está rendendo por hora de
 * turno e quanto falta para a meta.
 *
 * O ritmo usa o tempo de turno SEM as pausas: almoço de uma hora não pode
 * derrubar o R$/hora e empurrar a previsão para a madrugada. Verde quando o
 * ritmo bate a meta por hora, amarelo quando não bate.
 */
@Composable
private fun RitmoEFalta(e: DashboardUiState, agora: Long, onDefinirMeta: () -> Unit) {
    val jornada = e.jornada
    val segundosRodados = e.jornadasEncerradas.segundos + (jornada?.segundosRodando(agora) ?: 0)
    val previsao = PrevisaoMeta.calcular(
        metaCentavos = e.config.metaLucroDiarioCentavos,
        lucroCentavos = e.resumo.lucroLiquidoCentavos,
        segundosRodados = segundosRodados,
        turnoAndando = jornada != null && !jornada.pausada,
        agoraMillis = agora
    )
    if (previsao == null) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Superficie)
                .clickable { onDefinirMeta() }.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Rotulo("Meta do dia")
            Text("Defina sua meta para ver o ritmo e quanto falta.", fontSize = 13.sp, color = TextoSecundario)
        }
        return
    }
    val metaHora = e.metaPorHoraCentavos
    val ritmo = (previsao as? PrevisaoMeta.Falta)?.ritmoCentavosHora
        ?: if (segundosRodados > 0) e.resumo.lucroLiquidoCentavos * 3600 / segundosRodados else null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PainelNumero(
            rotulo = "Ritmo",
            valor = ritmo?.let { (it / 100).toString() } ?: "—",
            unidade = if (ritmo != null) "/h" else null,
            cor = when {
                ritmo == null || metaHora <= 0 -> TextoPrincipal
                ritmo >= metaHora -> VerdeLucro
                else -> AmareloAlerta
            },
            legenda = if (metaHora > 0) "meta ${metaHora.emReais().removeSuffix(",00")}/h" else "por hora de turno",
            modifier = Modifier.weight(1f)
        )
        when (previsao) {
            PrevisaoMeta.Batida -> PainelNumero(
                rotulo = "Falta", valor = "0", unidade = null, cor = VerdeLucro,
                legenda = "meta batida", modifier = Modifier.weight(1f)
            )
            is PrevisaoMeta.Falta -> PainelNumero(
                rotulo = "Falta",
                valor = previsao.faltaCentavos.emReais().replace("R$ ", ""),
                unidade = null,
                cor = TextoPrincipal,
                legenda = previsao.chegaEmMillis?.let {
                    "termina ~" + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(FORMATO_PREVISAO)
                } ?: previsao.ritmoCentavosHora?.takeIf { it > 0 }?.let {
                    val min = previsao.faltaCentavos * 60 / it
                    "~${min / 60}h${"%02d".format(min % 60)} no ritmo"
                } ?: "comece o turno",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Um mostrador do painel: rótulo pequeno, número grande em JetBrains Mono, legenda. */
@Composable
private fun PainelNumero(
    rotulo: String,
    valor: String,
    unidade: String?,
    cor: Color,
    legenda: String,
    modifier: Modifier
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Superficie).padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Rotulo(rotulo)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, style = EstiloValor, fontSize = 24.sp, color = cor, maxLines = 1)
            if (unidade != null) Text(unidade, fontSize = 13.sp, color = TextoSecundario, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(legenda, fontSize = 11.5.sp, color = TextoSecundario, maxLines = 1)
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
private fun QuatroNumeros(e: DashboardUiState, agora: Long) {
    val r = e.resumo
    val segundos = e.segundosTrabalhados(agora)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumeroPequeno("${r.qtdCorridas}", "corridas", Modifier.weight(1f))
        NumeroPequeno(if (r.metrosRodados > 0) "%.0f".format(r.kmRodados) else "0", "km", Modifier.weight(1f))
        NumeroPequeno("%d:%02d".format(segundos / 3600, segundos % 3600 / 60), "rodando", Modifier.weight(1f))
        NumeroPequeno(e.taxaDeAceite?.let { "$it%" } ?: "—", "aceite", Modifier.weight(1f))
    }
}

@Composable
private fun NumeroPequeno(valor: String, rotulo: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Superficie).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(valor, style = EstiloValor, fontSize = 17.sp, color = TextoPrincipal, maxLines = 1)
        Text(rotulo, fontSize = 11.sp, color = TextoSecundario)
    }
}

/**
 * O selo do cabeçalho (5.1, no lugar do "LEITOR ON"): verde "COPILOTO OK" com
 * tudo liberado, amarelo com quantas autorizações estão prontas, vermelho sem o
 * leitor. Toque abre o Copiloto, onde se libera o que falta.
 */
@Composable
private fun SeloCopiloto(estado: EstadoCopiloto, modifier: Modifier, onClick: () -> Unit) {
    val (texto, cor) = when {
        !estado.leitor -> "LEITOR DESLIGADO" to VermelhoPrejuizo
        estado.tudoPronto -> "COPILOTO OK" to VerdeLucro
        else -> "COPILOTO ${estado.prontos}/${estado.total}" to AmareloAlerta
    }
    SeloEstado(texto, cor, modifier, onClick)
}

/**
 * O turno (5.1.2): luz, estado e tempo à esquerda; a tecla de pausar à direita.
 * Encerrar fica embaixo, menor e em vermelho: é o que menos se toca e o que
 * mais custa tocar sem querer.
 */
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
    val segundos = jornada?.segundosRodando(agora) ?: estado.segundosTrabalhados(agora)
    val tempo = "%d:%02d".format(segundos / 3600, segundos % 3600 / 60)
    val veiculo = estado.config.veiculoNome?.takeIf { it.isNotBlank() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Superficie)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    when {
                        jornada == null -> SuperficieMaisAlta
                        jornada.pausada -> AmareloAlerta
                        else -> VerdeLucro
                    }
                )
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    when {
                        jornada == null -> "Turno parado"
                        jornada.pausada -> "Turno pausado · $tempo"
                        else -> "Turno rodando · $tempo"
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextoPrincipal
                )
                Text(
                    listOfNotNull(
                        when {
                            jornada == null -> if (segundos > 0) "hoje: $tempo rodando" else "comece quando sair"
                            gpsRodando -> String.format(Locale("pt", "BR"), "GPS ligado · %.1f km", jornada.metrosGps / 1000.0)
                            else -> "GPS pausado"
                        },
                        veiculo
                    ).joinToString(" · "),
                    fontSize = 11.5.sp,
                    color = if (jornada != null && !gpsRodando) AmareloAlerta else TextoSecundario
                )
            }
            when {
                jornada == null -> Tecla(
                    "Iniciar", fundo = Color.White, corTexto = Color.Black, borda = TextoSecundario,
                    altura = 48.dp, onClick = onIniciar
                )
                jornada.pausada -> Tecla(
                    "Retomar", fundo = VerdeLucro, corTexto = Color.Black, borda = Color(0xFF00A152),
                    altura = 48.dp, onClick = onPausar
                )
                else -> Tecla(
                    "Pausar", fundo = Color.White, corTexto = Color.Black, borda = TextoSecundario,
                    altura = 48.dp, onClick = onPausar
                )
            }
        }
        if (jornada != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val gasto = estado.config.custoCombustivelKmCentavos
                    ?.let { Math.round(jornada.metrosGps / 1000.0 * it) }
                Text(
                    gasto?.let { "${it.emReais()} de combustível" } ?: "",
                    fontSize = 11.5.sp, color = TextoSecundario, modifier = Modifier.weight(1f)
                )
                if (!gpsRodando) {
                    TextButton(onClick = onRetomarGps) { Text("Retomar GPS", color = AmareloAlerta) }
                }
                TextButton(onClick = onEncerrar) {
                    Text("Encerrar turno", color = VermelhoPrejuizo, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
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
        Text(rotulo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextoPrincipal)
    }
}
