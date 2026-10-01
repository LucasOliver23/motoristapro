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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.motoristapro.ui.centavosEmReais
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.CorridaDialog
import com.motoristapro.ui.componentes.DespesaDialog
import com.motoristapro.ui.componentes.LinhaValor
import com.motoristapro.ui.componentes.Metrica
import com.motoristapro.ui.componentes.TelaAba
import com.motoristapro.ui.formatarCronometro
import com.motoristapro.ui.formatarDuracao
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

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

                JornadaCard(
                    estado = estado,
                    agora = agora,
                    gpsRodando = gpsOk,
                    onIniciar = { pedirPermissoes.launch(permissoes) },
                    onEncerrar = vm::encerrarJornada,
                    onRetomarGps = vm::retomarGps
                )

                LucroCard(estado, onVerFinancas = { irPara(Aba.FINANCAS) })

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metrica(
                        "Faturamento", estado.resumo.faturamentoCentavos.emReais(), Modifier.weight(1f),
                        detalhe = "${estado.resumo.qtdCorridas} corridas"
                    )
                    Metrica(
                        "Despesas", estado.resumo.despesasCentavos.emReais(), Modifier.weight(1f),
                        detalhe = "lançadas hoje", cor = VermelhoPrejuizo
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val porHora = estado.ganhoPorHoraTrabalhadaCentavos(agora)
                    Metrica(
                        "R$ / hora", if (porHora > 0) porHora.emReais() else "—", Modifier.weight(1f),
                        detalhe = if (porHora > 0) "por hora trabalhada"
                        else if (estado.resumo.ganhoPorHoraCentavos > 0) "${estado.resumo.ganhoPorHoraCentavos.emReais()} em corrida"
                        else "inicie o turno"
                    )
                    Metrica(
                        "R$ / km", if (estado.resumo.metrosRodados > 0) estado.resumo.ganhoPorKmCentavos.emReais() else "—",
                        Modifier.weight(1f),
                        detalhe = String.format(Locale("pt", "BR"), "%.1f km em corridas", estado.resumo.kmRodados)
                    )
                }

                CardSecao(titulo = "Custo por km") {
                    LinhaValor("Real do mês", estado.custoKmRealCentavos?.centavosEmReais() ?: "—", negrito = true)
                    LinhaValor("Configurado", estado.config.custoKmCentavos.emReais())
                    estado.config.custoCombustivelKmCentavos?.let {
                        LinhaValor("Só combustível", it.centavosEmReais())
                    }
                }

                CardSecao(titulo = "Registrar") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AtalhoBotao("Abastecer", Modifier.weight(1f)) { dialogDespesa = CategoriaDespesa.COMBUSTIVEL }
                        AtalhoBotao("Corrida", Modifier.weight(1f)) { dialogCorrida = true }
                        AtalhoBotao("Despesa", Modifier.weight(1f)) { dialogDespesa = CategoriaDespesa.MANUTENCAO }
                    }
                }
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
    CardSecao(titulo = "Jornada") {
        if (jornada == null) {
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
        } else {
            val segundosTurno = ((agora - jornada.inicioEm) / 1000).coerceAtLeast(0)
            Text(
                segundosTurno.formatarCronometro(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Lima
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Km (GPS)", style = MaterialTheme.typography.labelMedium, color = TextoSecundario)
                    Text(
                        String.format(Locale("pt", "BR"), "%.1f km", jornada.metrosGps / 1000.0),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Combustível", style = MaterialTheme.typography.labelMedium, color = TextoSecundario)
                    val gasto = estado.config.custoCombustivelKmCentavos
                        ?.let { Math.round(jornada.metrosGps / 1000.0 * it) }
                    Text(
                        gasto?.emReais() ?: "configure o veículo",
                        style = if (gasto != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodySmall,
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
}

@Composable
private fun LucroCard(e: DashboardUiState, onVerFinancas: () -> Unit) {
    val cor = if (e.resumo.lucroLiquidoCentavos >= 0) Lima else VermelhoPrejuizo
    CardSecao(titulo = "Lucro líquido de hoje", destaque = true) {
        Text(
            e.resumo.lucroLiquidoCentavos.emReais(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = cor
        )
        if (e.config.metaLucroDiarioCentavos > 0) {
            LinearProgressIndicator(
                progress = { e.progressoMeta },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = cor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Meta ${e.config.metaLucroDiarioCentavos.emReais()} • ${(e.progressoMeta * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                val falta = e.config.metaLucroDiarioCentavos - e.resumo.lucroLiquidoCentavos
                Text(
                    if (falta > 0) "faltam ${falta.emReais()}" else "meta batida!",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (falta > 0) TextoSecundario else Lima,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (e.custoFixoDiaCentavos > 0) {
            Text(
                "Custos fixos do dia: −${e.custoFixoDiaCentavos.emReais()}  →  lucro real ${e.lucroRealCentavos.emReais()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (e.lucroRealCentavos >= 0) TextoSecundario else VermelhoPrejuizo
            )
        }
        TextButton(onClick = onVerFinancas) { Text("Ver finanças e metas", color = Lima) }
    }
}

@Composable
private fun AtalhoBotao(rotulo: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(4.dp))
        Text(rotulo, maxLines = 1)
    }
}
