package com.motoristapro.ui.dashboard

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
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

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "BR"))

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
    val snackbar = remember { SnackbarHostState() }

    var dialogDespesa by rememberSaveable { mutableStateOf<CategoriaDespesa?>(null) }
    var dialogCorrida by rememberSaveable { mutableStateOf(false) }

    // Relógio para o cronômetro do turno (1 s).
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(estado.jornada?.id) {
        while (true) {
            agora = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) { vm.mensagens.collect { snackbar.showSnackbar(it) } }

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

    TelaAba(
        titulo = "MotoristaPro",
        subtitulo = estado.dia.format(FORMATO_DIA).replaceFirstChar { it.uppercase() },
        snackbar = snackbar
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
                StatusLeitor(leitorOk, onTestar = vm::testarLeitor)

                // A ORDEM da tela é a ordem da pergunta do motorista: quanto
                // sobrou hoje, de onde veio, registrar o que acabou de rolar e,
                // por último, o relógio — que é para olhar, não para tocar.
                LucroDoDia(estado, onVerFinancas = { irPara(Aba.FINANCAS) })

                QuatroNumeros(estado)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotaoRegistrar("+ Corrida", principal = true, modifier = Modifier.weight(1f)) {
                        dialogCorrida = true
                    }
                    BotaoRegistrar("+ Despesa", principal = false, modifier = Modifier.weight(1f)) {
                        dialogDespesa = CategoriaDespesa.COMBUSTIVEL
                    }
                }

                JornadaCard(
                    estado = estado,
                    agora = agora,
                    gpsRodando = gpsOk,
                    onIniciar = { pedirPermissoes.launch(permissoes) },
                    onEncerrar = vm::encerrarJornada,
                    onRetomarGps = vm::retomarGps
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    dialogDespesa?.let { categoria ->
        DespesaDialog(
            categoriaInicial = categoria,
            onDismiss = { dialogDespesa = null },
            onSalvar = { vm.salvarDespesa(it); dialogDespesa = null }
        )
    }
    if (dialogCorrida) {
        CorridaDialog(
            plataformas = plataformas,
            onDismiss = { dialogCorrida = false },
            onSalvar = { vm.salvarCorrida(it); dialogCorrida = false }
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
private fun LucroDoDia(e: DashboardUiState, onVerFinancas: () -> Unit) {
    val lucro = e.resumo.lucroLiquidoCentavos
    val cor = if (lucro >= 0) Lima else VermelhoPrejuizo
    CardSecao(titulo = "LUCRO LÍQUIDO DE HOJE", modifier = Modifier.clickable { onVerFinancas() }) {
        Text(
            lucro.emReais(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = cor
        )
        if (e.config.metaLucroDiarioCentavos > 0) {
            LinearProgressIndicator(
                progress = { e.progressoMeta },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                color = Lima,
                trackColor = Contorno,
                drawStopIndicator = {}
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Meta do dia: ${e.config.metaLucroDiarioCentavos.emReais()}",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                Box(Modifier.weight(1f))
                Text(
                    "${(e.progressoMeta * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold, color = Lima
                )
            }
        } else {
            Text(
                "Defina sua meta em Menu ▸ Meu veículo e custos.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
    }
}

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
            Metrica(
                "Faturamento", r.faturamentoCentavos.emReais(), Modifier.weight(1f),
                detalhe = "${r.qtdCorridas} corrida(s)"
            )
            Metrica(
                "Despesas", r.despesasCentavos.emReais(), Modifier.weight(1f),
                cor = VermelhoPrejuizo, detalhe = "lançadas hoje"
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metrica(
                "R$ por km",
                if (r.metrosRodados > 0) r.ganhoPorKmCentavos.emReais() else "—",
                Modifier.weight(1f),
                cor = if (r.metrosRodados > 0) Lima else TextoSecundario,
                detalhe = e.config.custoKmCentavos.takeIf { it > 0 }?.let { "custo ${it.emReais()}/km" }
            )
            Metrica(
                "Taxa de aceite",
                e.taxaDeAceite?.let { "$it%" } ?: "—",
                Modifier.weight(1f),
                detalhe = if (e.ofertas.total > 0)
                    "${e.ofertas.registradas} de ${e.ofertas.total} ofertas"
                else "o leitor ainda não viu oferta"
            )
        }
    }
}

/** Botão de registrar: o verde cheio é o que mais se toca, o outro é secundário. */
@Composable
private fun BotaoRegistrar(
    texto: String,
    principal: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (principal) {
        Button(
            onClick = onClick,
            modifier = modifier.height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0A0D0B))
        ) { Text(texto, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(texto, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun StatusLeitor(conectado: Boolean, onTestar: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = if (conectado) Lima else AmareloAlerta, modifier = Modifier.size(10.dp)) {}
            Text(
                if (conectado) "Leitor de ofertas ativo • Uber, 99 e iFood" else "Leitor desligado pelo Android • veja em Mais",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (conectado) TextButton(onClick = onTestar) { Text("Testar", color = Lima) }
        }
    }
}

@Composable
private fun JornadaCard(
    estado: DashboardUiState,
    agora: Long,
    gpsRodando: Boolean,
    onIniciar: () -> Unit,
    onEncerrar: () -> Unit,
    onRetomarGps: () -> Unit
) {
    val jornada = estado.jornada
    if (jornada == null) {
        CardSecao(titulo = "JORNADA") {
            val trabalhadoHoje = estado.segundosTrabalhados(agora)
            Text(
                if (trabalhadoHoje > 0) "Hoje: ${trabalhadoHoje.formatarDuracao()} trabalhadas" else "Turno parado",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = onIniciar,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0A0D0B))
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Iniciar jornada", fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val segundosTurno = ((agora - jornada.inicioEm) / 1000).coerceAtLeast(0)
    val inicio = Instant.ofEpochMilli(jornada.inicioEm).atZone(ZoneId.systemDefault())
        .format(FORMATO_INICIO)

    // O anel enche em relação a um turno de 8 h. É escala, não meta: o número
    // que vale é a hora no meio, e a cor avisa quando o dia está esticando.
    val referencia = TURNO_DE_REFERENCIA_SEG

    CartaoJornada(
        segundos = segundosTurno,
        segundosDeReferencia = referencia,
        segundosEmCorrida = estado.resumo.segundosEmCorrida,
        faturamentoCentavos = estado.resumo.faturamentoCentavos,
        inicioTexto = "começou $inicio",
        pausada = false
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Km (GPS)", style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
                Text(
                    String.format(Locale("pt", "BR"), "%.1f km", jornada.metrosGps / 1000.0),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Combustível", style = MaterialTheme.typography.labelSmall, color = TextoSecundario)
                val gasto = estado.config.custoCombustivelKmCentavos
                    ?.let { Math.round(jornada.metrosGps / 1000.0 * it) }
                Text(
                    gasto?.emReais() ?: "configure o veículo",
                    style = if (gasto != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (gasto != null) VermelhoPrejuizo else TextoSecundario
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
        OutlinedButton(onClick = onEncerrar, modifier = Modifier.fillMaxWidth()) {
            Text("Encerrar jornada", color = VermelhoPrejuizo)
        }
    }
}

/** Hora de início do turno, para a linha de baixo do anel. */
private val FORMATO_INICIO = DateTimeFormatter.ofPattern("HH:mm")

/**
 * O turno de referência do anel: 8 horas.
 *
 * O app ainda não pergunta quantas horas o motorista pretende rodar — só a meta
 * de dinheiro. Oito horas é a jornada comum e serve de escala; passando disso o
 * anel fica âmbar, que é o aviso de dia esticado.
 */
private const val TURNO_DE_REFERENCIA_SEG = 8L * 3600
