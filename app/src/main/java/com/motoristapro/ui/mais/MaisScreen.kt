package com.motoristapro.ui.mais

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.motoristapro.atualizacao.EstadoAtualizacao
import com.motoristapro.service.Diagnostico
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.repository.custoFixoDiario
import com.motoristapro.ui.componentes.ConfirmarExclusao
import com.motoristapro.ui.componentes.LinhaValor
import com.motoristapro.ui.paraCentavos
import java.time.LocalDate
import com.motoristapro.data.repository.emReais
import com.motoristapro.jornada.Notificacoes
import com.motoristapro.ui.abrirConfigAcessibilidade
import com.motoristapro.ui.abrirDetalhesDoApp
import com.motoristapro.ui.centavosEmReais
import com.motoristapro.ui.componentes.CampoFormulario
import com.motoristapro.ui.componentes.CardSecao
import com.motoristapro.ui.componentes.TelaAba
import com.motoristapro.ui.litrosParaMl
import com.motoristapro.ui.paraCentavosOuZero
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.util.Locale

private val PT = Locale("pt", "BR")

private fun Long.campo(): String = emReais().removePrefix("R$ ")

@Composable
fun MaisRoute(vm: MaisViewModel = viewModel(factory = MaisViewModel.Factory)) {
    val config by vm.config.collectAsStateWithLifecycle()
    val leitorOk by vm.leitorConectado.collectAsStateWithLifecycle()
    val bolha by vm.bolhaAtiva.collectAsStateWithLifecycle()
    val resumo by vm.resumoAtivo.collectAsStateWithLifecycle()
    val custos by vm.custosFixos.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val restaurado by vm.restaurado.collectAsStateWithLifecycle()
    val diagnostico by vm.diagnostico.collectAsStateWithLifecycle()
    val usuario by vm.usuario.collectAsStateWithLifecycle()
    val nuvem by vm.estadoNuvem.collectAsStateWithLifecycle()
    val atualizacao by vm.estadoAtualizacao.collectAsStateWithLifecycle()
    var confirmarBaixarNuvem by remember { mutableStateOf(false) }
    var confirmarSair by remember { mutableStateOf(false) }
    LaunchedEffect(usuario?.uid) { if (usuario != null) vm.consultarNuvem() }
    val ocr by vm.ocrAtivo.collectAsStateWithLifecycle()
    val voz by vm.vozAtiva.collectAsStateWithLifecycle()
    val vozResumida by vm.vozResumida.collectAsStateWithLifecycle()
    val riscoLigado by vm.riscoAtivo.collectAsStateWithLifecycle()
    val riscoMercados by vm.riscoMercados.collectAsStateWithLifecycle()
    val palavrasRisco by vm.riscoPalavras.collectAsStateWithLifecycle()
    var novoCusto by remember { mutableStateOf(false) }
    var excluirCusto by remember { mutableStateOf<CustoFixo?>(null) }
    var confirmarRestauracao by remember { mutableStateOf(false) }

    // Seletores de arquivo do Android (permitem salvar em Downloads, Google Drive, etc.).
    val hoje = LocalDate.now().toString()
    val criarBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(vm::fazerBackup) }
    val criarCsvCorridas = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportarCorridas)
    }
    val criarCsvDespesas = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportarDespesas)
    }
    val criarCsvOfertas = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportarOfertas)
    }
    val abrirBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::restaurar)
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var notificacoesOk by remember { mutableStateOf(Notificacoes.podeNotificar(context)) }
    LifecycleResumeEffect(Unit) {
        notificacoesOk = Notificacoes.podeNotificar(context)
        onPauseOrDispose { }
    }
    val pedirNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notificacoesOk = ok
    }
    LaunchedEffect(Unit) { vm.mensagens.collect { snackbar.showSnackbar(it) } }

    TelaAba(titulo = "Mais", subtitulo = "Veículo, metas e leitor de ofertas", snackbar = snackbar) { padding ->
        val cfg = config
        if (cfg == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator(color = Lima) }
            return@TelaAba
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PerfilCard(
                cfg = cfg,
                emailConta = usuario?.email ?: usuario?.nome,
                onSalvar = vm::salvarPerfil
            )

            if (vm.loginDisponivel && usuario != null) {
                CardSecao(titulo = "Nuvem") {
                    LinhaValor(
                        "Último backup",
                        if (nuvem.atualizadoNaNuvemEm > 0) dataHora(nuvem.atualizadoNaNuvemEm) else "ainda não enviado"
                    )
                    if (nuvem.corridasNaNuvem >= 0) LinhaValor("Corridas na nuvem", "${nuvem.corridasNaNuvem}")
                    Text(
                        "Os dados sobem automaticamente ao entrar. Use \"Baixar da nuvem\" ao trocar de celular.",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = vm::enviarParaNuvem,
                            enabled = !nuvem.ocupado,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0A0D0B))
                        ) { Text(if (nuvem.ocupado) "Aguarde..." else "Enviar agora") }
                        OutlinedButton(
                            onClick = { confirmarBaixarNuvem = true },
                            enabled = !nuvem.ocupado,
                            modifier = Modifier.weight(1f)
                        ) { Text("Baixar da nuvem") }
                    }
                    OutlinedButton(onClick = { confirmarSair = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sair da conta", color = VermelhoPrejuizo)
                    }
                }
            }

            AtualizacaoCard(
                estado = atualizacao,
                versao = vm.versaoInstalada,
                codigo = vm.codigoInstalado,
                onProcurar = vm::procurarAtualizacao,
                onInstalar = vm::instalarAtualizacao
            )

            // Formulário recriado só quando a config é carregada pela primeira vez.
            key(cfg.id) {
                FormularioConfig(
                    cfg = cfg,
                    minKmInicial = vm.limites.minReaisPorKm,
                    minHoraInicial = vm.limites.minReaisPorHora,
                    minLucroInicial = vm.limites.minLucroReais,
                    minLucroPctInicial = vm.limites.minLucroPercent,
                    minNotaInicial = vm.limites.minNota,
                    onSalvar = vm::salvar
                )
            }

            CardSecao(titulo = "Custos fixos mensais") {
                Text(
                    "Seguro, parcela, IPVA, internet... Não lance como despesa: o app divide o total " +
                        "pelos dias trabalhados no mês e desconta do lucro de cada dia.",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                custos.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.nome, modifier = Modifier.weight(1f))
                        Text(c.valorMensalCentavos.emReais() + "/mês", fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { excluirCusto = c }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = TextoSecundario)
                        }
                    }
                }
                val total = custos.filter { it.ativo }.sumOf { it.valorMensalCentavos }
                if (total > 0) {
                    LinhaValor("Total por mês", total.emReais(), negrito = true)
                    LinhaValor(
                        "Por dia trabalhado (${cfg.diasTrabalhoMes} dias)",
                        custoFixoDiario(total, cfg.diasTrabalhoMes).emReais(), cor = VermelhoPrejuizo
                    )
                }
                OutlinedButton(onClick = { novoCusto = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Adicionar custo fixo")
                }
            }

            CardSecao(titulo = "Backup e exportação") {
                Text(
                    "Seus dados ficam só neste celular. Faça backup de vez em quando e salve no Google Drive " +
                        "(escolha \"Drive\" na tela que abrir).",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
                Button(
                    onClick = { criarBackup.launch("motoristapro-backup-$hoje.db") },
                    enabled = !ocupado,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0A0D0B))
                ) { Text(if (ocupado) "Aguarde..." else "Fazer backup completo", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { confirmarRestauracao = true }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) {
                    Text("Restaurar backup")
                }
                Text("Exportar para Excel (CSV)", style = MaterialTheme.typography.labelLarge, color = TextoSecundario)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { criarCsvCorridas.launch("corridas-$hoje.csv") }, enabled = !ocupado,
                        modifier = Modifier.weight(1f)
                    ) { Text("Corridas") }
                    OutlinedButton(
                        onClick = { criarCsvDespesas.launch("despesas-$hoje.csv") }, enabled = !ocupado,
                        modifier = Modifier.weight(1f)
                    ) { Text("Despesas") }
                    OutlinedButton(
                        onClick = { criarCsvOfertas.launch("ofertas-$hoje.csv") }, enabled = !ocupado,
                        modifier = Modifier.weight(1f)
                    ) { Text("Ofertas") }
                }
            }

            CardSecao(titulo = "Leitor de ofertas") {
                Text(
                    if (leitorOk) "Conectado e lendo Uber, 99 e iFood" else "Desconectado",
                    color = if (leitorOk) Lima else VermelhoPrejuizo,
                    fontWeight = FontWeight.SemiBold
                )
                if (!leitorOk) {
                    Text(
                        "O Android desligou o leitor (acontece ao fechar o app pela tela de recentes ou por economia " +
                            "de bateria). Toque em Acessibilidade, DESLIGUE e LIGUE de novo o \"MotoristaPro – Leitor de ofertas\". " +
                            "Para não repetir: libere \"Início automático\" e deixe a bateria \"Sem restrições\" em Informações do app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::testarLeitor, modifier = Modifier.weight(1f)) { Text("Testar janela") }
                    OutlinedButton(onClick = { context.abrirConfigAcessibilidade() }, modifier = Modifier.weight(1f)) {
                        Text("Acessibilidade")
                    }
                }
                LinhaSwitch(
                    "Bolha flutuante",
                    "Lucro do dia e cronômetro do turno por cima da Uber/99. Toque na bolha para abrir o app.",
                    bolha, vm::definirBolha
                )
                LinhaSwitch(
                    "Ler oferta pela imagem (OCR)",
                    "Para apps que escondem o texto do card (como a 99). Lê a tela no próprio celular, " +
                        "sem internet. Gasta um pouco mais de bateria. Android 11 ou mais novo.",
                    ocr, vm::definirOcr
                )
            }

            CardSecao(titulo = "Aviso por voz") {
                LinhaSwitch(
                    "Falar a oferta em voz alta",
                    "Diz \"Aceitar\", \"Analisar\" ou \"Recusar\" com o R$/km — para decidir sem " +
                        "tirar a mão do guidão. Usa a voz do próprio Android, sem internet.",
                    voz, vm::definirVoz
                )
                if (voz) {
                    LinhaSwitch(
                        "Modo curto",
                        "Só a decisão e o R$/km. Sem nota, paradas nem R$/hora.",
                        vozResumida, vm::definirVozResumida
                    )
                    Text(
                        "Se não ouvir nada, instale a voz em português: Configurações do Android > " +
                            "Idiomas > Conversão de texto em voz.",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                }
            }

            CardSecaoEnderecosRisco(
                ativo = riscoLigado,
                mercados = riscoMercados,
                palavras = palavrasRisco,
                onAtivo = vm::definirRisco,
                onMercados = vm::definirRiscoMercados,
                onAdicionar = vm::adicionarPalavraRisco,
                onRemover = vm::removerPalavraRisco
            )

            DiagnosticoCard(diagnostico, leitorOk)

            CardSecao(titulo = "Automação") {
                LinhaSwitch(
                    "Resumo diário às 22h",
                    "Notificação com faturamento, despesas e lucro do dia.",
                    resumo, vm::definirResumo
                )
                if (!notificacoesOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(
                        onClick = { pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Permitir notificações") }
                }
            }

            CardSecao(titulo = "Permissões") {
                OutlinedButton(onClick = { context.abrirDetalhesDoApp() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Informações do app (localização, bateria)")
                }
                Text(
                    "Dica: tire o MotoristaPro da otimização de bateria para o leitor e o GPS não serem desligados.",
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (novoCusto) {
        NovoCustoFixoDialog(
            onDismiss = { novoCusto = false },
            onSalvar = { nome, valor -> vm.salvarCustoFixo(nome, valor); novoCusto = false }
        )
    }
    excluirCusto?.let { alvo ->
        ConfirmarExclusao(
            texto = "Excluir o custo fixo \"${alvo.nome}\" (${alvo.valorMensalCentavos.emReais()}/mês)?",
            onDismiss = { excluirCusto = null },
            onConfirmar = { vm.excluirCustoFixo(alvo.id); excluirCusto = null }
        )
    }
    if (confirmarBaixarNuvem) {
        AlertDialog(
            onDismissRequest = { confirmarBaixarNuvem = false },
            title = { Text("Baixar da nuvem?") },
            text = {
                Text(
                    "Os dados deste celular serão SUBSTITUÍDOS pelo backup da sua conta na nuvem. " +
                        "Use isto ao trocar de celular ou reinstalar o app."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarBaixarNuvem = false; vm.baixarDaNuvem() }) {
                    Text("Baixar e substituir", color = VermelhoPrejuizo)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarBaixarNuvem = false }) { Text("Cancelar") } }
        )
    }
    if (confirmarSair) {
        AlertDialog(
            onDismissRequest = { confirmarSair = false },
            title = { Text("Sair da conta?") },
            text = { Text("Seus dados continuam neste celular e também na nuvem. Você pode entrar de novo quando quiser.") },
            confirmButton = { TextButton(onClick = { confirmarSair = false; vm.sairDaConta() }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirmarSair = false }) { Text("Cancelar") } }
        )
    }
    if (confirmarRestauracao) {
        AlertDialog(
            onDismissRequest = { confirmarRestauracao = false },
            title = { Text("Restaurar backup?") },
            text = {
                Text(
                    "Todos os dados atuais deste celular serão SUBSTITUÍDOS pelos do backup escolhido. " +
                        "Se tiver dúvida, faça um backup antes."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarRestauracao = false
                    abrirBackup.launch(arrayOf("*/*"))
                }) { Text("Escolher arquivo", color = VermelhoPrejuizo) }
            },
            dismissButton = { TextButton(onClick = { confirmarRestauracao = false }) { Text("Cancelar") } }
        )
    }
    if (restaurado) {
        // O banco antigo já foi fechado: reinicia sozinho em 3 s para não ler dados inválidos.
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3_000)
            vm.reiniciarApp()
        }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Backup restaurado") },
            text = {
                Text(
                    "O app vai reiniciar em instantes para carregar os dados. Depois, desligue e ligue o leitor em " +
                        "Acessibilidade se ele aparecer desconectado."
                )
            },
            confirmButton = { TextButton(onClick = vm::reiniciarApp) { Text("Reiniciar agora", color = Lima) } }
        )
    }
}

@Composable
private fun NovoCustoFixoDialog(onDismiss: () -> Unit, onSalvar: (String, Long) -> Unit) {
    var nome by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    val valorC = valor.paraCentavos()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo custo fixo mensal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoFormulario("Nome (ex.: Seguro, Parcela)", nome, { nome = it }, numerico = false)
                CampoFormulario("Valor por mês (R$)", valor, { valor = it }, erro = valor.isNotBlank() && valorC == null)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valorC != null && nome.isNotBlank()) onSalvar(nome, valorC) },
                enabled = valorC != null && nome.isNotBlank()
            ) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun FormularioConfig(
    cfg: Configuracao,
    minKmInicial: Float,
    minHoraInicial: Float,
    minLucroInicial: Float,
    minLucroPctInicial: Float,
    minNotaInicial: Float,
    onSalvar: (FormConfig) -> Unit
) {
    var veiculo by rememberSaveable { mutableStateOf(cfg.veiculoNome ?: "") }
    var consumo by rememberSaveable {
        mutableStateOf(if (cfg.consumoKmLx100 > 0) String.format(PT, "%.1f", cfg.consumoKmLx100 / 100.0) else "")
    }
    var preco by rememberSaveable { mutableStateOf(if (cfg.precoLitroCentavos > 0) cfg.precoLitroCentavos.campo() else "") }
    var custoKm by rememberSaveable { mutableStateOf(cfg.custoKmCentavos.campo()) }
    var metaDia by rememberSaveable { mutableStateOf(cfg.metaLucroDiarioCentavos.campo()) }
    var metaSemana by rememberSaveable { mutableStateOf(cfg.metaLucroSemanalCentavos.campo()) }
    var metaMes by rememberSaveable { mutableStateOf(cfg.metaLucroMensalCentavos.campo()) }
    var tarifa by rememberSaveable { mutableStateOf(cfg.tarifaMinimaCentavos.campo()) }
    var minKm by rememberSaveable { mutableStateOf(String.format(PT, "%.2f", minKmInicial)) }
    var minHora by rememberSaveable { mutableStateOf(String.format(PT, "%.2f", minHoraInicial)) }
    var minLucro by rememberSaveable { mutableStateOf(String.format(PT, "%.2f", minLucroInicial)) }
    var minLucroPct by rememberSaveable { mutableStateOf(minLucroPctInicial.toInt().toString()) }
    var minNota by rememberSaveable { mutableStateOf(String.format(PT, "%.1f", minNotaInicial)) }
    var dias by rememberSaveable { mutableStateOf(cfg.diasTrabalhoMes.toString()) }

    // "12,5" km/L -> 1250 (mesma conversão x1000/10)
    val consumoX100 = if (consumo.isBlank()) 0L else consumo.litrosParaMl()?.div(10)
    val precoC = if (preco.isBlank()) 0L else preco.paraCentavosOuZero()
    val custoC = custoKm.paraCentavosOuZero()
    val metaDiaC = metaDia.paraCentavosOuZero()
    val metaSemanaC = metaSemana.paraCentavosOuZero()
    val metaMesC = metaMes.paraCentavosOuZero()
    val tarifaC = tarifa.paraCentavosOuZero()
    val minKmC = minKm.paraCentavosOuZero()
    val minHoraC = minHora.paraCentavosOuZero()
    val minLucroC = minLucro.paraCentavosOuZero()
    val minNotaC = minNota.paraCentavosOuZero()?.takeIf { it == 0L || it in 100..500 }
    val minLucroPctC = minLucroPct.trim().ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..99 }
    val diasC = dias.trim().toIntOrNull()?.takeIf { it in 1..31 }

    val combustivelKm = if (consumoX100 != null && consumoX100 > 0 && precoC != null && precoC > 0)
        precoC * 100.0 / consumoX100 else null

    val valido = listOf(
        consumoX100, precoC, custoC, metaDiaC, metaSemanaC, metaMesC,
        tarifaC, minKmC, minHoraC, minLucroC, minNotaC
    ).all { it != null } && diasC != null && minLucroPctC != null

    CardSecao(titulo = "Veículo") {
        CampoFormulario("Modelo (ex.: Onix 1.0 2020)", veiculo, { veiculo = it }, numerico = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoFormulario("Consumo (km/L)", consumo, { consumo = it }, Modifier.weight(1f), erro = consumoX100 == null)
            CampoFormulario("Preço do litro (R$)", preco, { preco = it }, Modifier.weight(1f), erro = precoC == null)
        }
        if (combustivelKm != null) {
            Text(
                "Combustível por km: ${combustivelKm.centavosEmReais()}",
                color = Lima, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
            )
        }
        CampoFormulario(
            "Custo total por km (R$)", custoKm, { custoKm = it }, erro = custoC == null,
            ajuda = "Combustível + manutenção + seguro. Usado no lucro estimado da oferta."
        )
        if (combustivelKm != null) {
            OutlinedButton(onClick = {
                custoKm = Math.round(combustivelKm * 1.3).campo()
            }) { Text("Sugerir: combustível + 30% (manutenção/desgaste)") }
        }
    }

    CardSecao(titulo = "Metas de lucro") {
        CampoFormulario("Meta diária (R$)", metaDia, { metaDia = it }, erro = metaDiaC == null)
        CampoFormulario("Meta semanal (R$)", metaSemana, { metaSemana = it }, erro = metaSemanaC == null)
        CampoFormulario("Meta mensal (R$)", metaMes, { metaMes = it }, erro = metaMesC == null)
        CampoFormulario(
            "Dias trabalhados por mês", dias, { dias = it.filter(Char::isDigit) }, erro = diasC == null,
            ajuda = "Usado para dividir os custos fixos por dia (1 a 31)."
        )
    }

    CardSecao(titulo = "Critérios da oferta") {
        CampoFormulario(
            "Tarifa mínima por corrida (R$)", tarifa, { tarifa = it }, erro = tarifaC == null,
            ajuda = "Abaixo disso a oferta aparece em vermelho. 0 = desligado."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoFormulario("Mínimo R$/km", minKm, { minKm = it }, Modifier.weight(1f), erro = minKmC == null)
            CampoFormulario("Mínimo R$/hora", minHora, { minHora = it }, Modifier.weight(1f), erro = minHoraC == null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoFormulario("Lucro mínimo (R$)", minLucro, { minLucro = it }, Modifier.weight(1f), erro = minLucroC == null)
            CampoFormulario(
                "Lucro mínimo (%)", minLucroPct, { minLucroPct = it.filter(Char::isDigit) },
                Modifier.weight(1f), erro = minLucroPctC == null
            )
        }
        CampoFormulario(
            "Nota mínima do passageiro", minNota, { minNota = it }, erro = minNotaC == null,
            ajuda = "De 1,0 a 5,0. 0 = desligado. Vale só quando o app de corrida mostra a nota."
        )
        Text(
            "Qualquer mínimo acima derruba a oferta para vermelho na hora. Lucro e % " +
                "dependem do custo por km preenchido no Veículo.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }

    Button(
        onClick = {
            if (valido) {
                onSalvar(
                    FormConfig(
                        veiculoNome = veiculo.trim().ifEmpty { null },
                        consumoKmLx100 = consumoX100 ?: 0L,
                        precoLitroCentavos = precoC ?: 0L,
                        custoKmCentavos = custoC ?: 0L,
                        metaDiaria = metaDiaC ?: 0L,
                        metaSemanal = metaSemanaC ?: 0L,
                        metaMensal = metaMesC ?: 0L,
                        tarifaMinima = tarifaC ?: 0L,
                        minReaisKm = (minKmC ?: 0L) / 100f,
                        minReaisHora = (minHoraC ?: 0L) / 100f,
                        minLucroReais = (minLucroC ?: 0L) / 100f,
                        minLucroPercent = (minLucroPctC ?: 0).toFloat(),
                        minNota = (minNotaC ?: 0L) / 100f,
                        diasTrabalhoMes = diasC ?: 26
                    )
                )
            }
        },
        enabled = valido,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0A0D0B))
    ) { Text("Salvar configurações", fontWeight = FontWeight.Bold) }
}

/**
 * Lista de palavras que marcam uma corrida como arriscada. O motorista conhece a cidade
 * dele melhor que qualquer base de dados — então quem cadastra é ele.
 */
@Composable
private fun CardSecaoEnderecosRisco(
    ativo: Boolean,
    mercados: Boolean,
    palavras: List<String>,
    onAtivo: (Boolean) -> Unit,
    onMercados: (Boolean) -> Unit,
    onAdicionar: (String) -> Unit,
    onRemover: (String) -> Unit
) {
    var nova by rememberSaveable { mutableStateOf("") }

    CardSecao(titulo = "Endereços de risco") {
        LinhaSwitch(
            "Alertar antes de aceitar",
            "Se o embarque ou o destino tiver uma das suas palavras, o cartão acende o aviso vermelho.",
            ativo, onAtivo
        )
        LinhaSwitch(
            "Avisar em mercado e atacadão",
            "Embarque em supermercado costuma dar espera longa e cancelamento. Reconhece as redes conhecidas.",
            mercados, onMercados
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CampoFormulario(
                "Bairro, rua ou região", nova, { nova = it },
                Modifier.weight(1f), numerico = false
            )
            OutlinedButton(
                onClick = {
                    onAdicionar(nova)
                    nova = ""
                },
                enabled = nova.isNotBlank()
            ) { Text("Add") }
        }

        if (palavras.isEmpty()) {
            Text(
                "Nenhuma palavra cadastrada. Exemplos: um bairro que você evita à noite, " +
                    "uma rua sem saída, o nome de uma comunidade.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        } else {
            palavras.forEach { p ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠  $p", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onRemover(p) }) {
                        Text("Remover", color = VermelhoPrejuizo)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaSwitch(titulo: String, descricao: String, marcado: Boolean, onMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titulo, fontWeight = FontWeight.SemiBold)
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        }
        Switch(
            checked = marcado,
            onCheckedChange = onMudar,
            colors = SwitchDefaults.colors(checkedTrackColor = Lima, checkedThumbColor = Color(0xFF0A0D0B))
        )
    }
}

private val FORMATO_HORA_DIAG = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Mostra o que o leitor viu na última oferta (ou tela da Uber/99/iFood).
 * "Copiar" permite mandar os textos para ajustar o reconhecimento sem precisar de computador.
 */
@Composable
private fun DiagnosticoCard(d: Diagnostico?, leitorOk: Boolean) {
    val clipboard = LocalClipboardManager.current
    CardSecao(titulo = "Diagnóstico do leitor") {
        if (!leitorOk) {
            Text("Leitor desconectado: ligue-o em Acessibilidade.", color = VermelhoPrejuizo, style = MaterialTheme.typography.bodySmall)
            return@CardSecao
        }
        if (d == null) {
            Text(
                "Nenhuma oferta lida ainda. Abra a Uber, 99 ou iFood e espere uma oferta aparecer; depois volte aqui.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            return@CardSecao
        }
        val hora = Instant.ofEpochMilli(d.momento).atZone(ZoneId.systemDefault()).format(FORMATO_HORA_DIAG)
        LinhaValor("Última leitura", hora)
        LinhaValor("App", d.pacotes.joinToString { com.motoristapro.data.repository.nomePlataforma(it) })
        LinhaValor("Janelas lidas / textos", "${d.janelas} / ${d.textos.size}")
        LinhaValor("Leitura por", d.origem)
        Text(
            d.reconhecida?.let { "Reconhecida: $it" }
                ?: if (d.textos.isEmpty()) "O app não expôs nenhum texto. Ligue \"Ler oferta pela imagem (OCR)\" acima."
                else "Oferta NÃO reconhecida nesta leitura.",
            color = if (d.reconhecida != null) Lima else AmareloAlertaDiag,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (d.textos.isNotEmpty()) {
            Text(
                d.textos.take(40).joinToString(" | "),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextoSecundario,
                maxLines = 12
            )
        }
        OutlinedButton(
            onClick = {
                val texto = buildString {
                    appendLine("MotoristaPro - diagnóstico $hora")
                    appendLine("Apps: ${d.pacotes.joinToString()}  Janelas: ${d.janelas}  Leitura: ${d.origem}")
                    appendLine("Reconhecida: ${d.reconhecida ?: "não"}")
                    appendLine("Textos:")
                    d.textos.forEach { appendLine(it) }
                }
                clipboard.setText(AnnotatedString(texto))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copiar textos (para enviar ao suporte)") }
    }
}

private val AmareloAlertaDiag = com.motoristapro.ui.theme.AmareloAlerta

private val FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private fun dataHora(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(FORMATO_DATA_HORA)

/** Perfil do motorista: nome, telefone, cidade e a conta usada para entrar. */
@Composable
private fun PerfilCard(
    cfg: Configuracao,
    emailConta: String?,
    onSalvar: (String, String, String) -> Unit
) {
    var nome by rememberSaveable(cfg.id) { mutableStateOf(cfg.nomeMotorista.orEmpty()) }
    var telefone by rememberSaveable(cfg.id) { mutableStateOf(cfg.telefone.orEmpty()) }
    var cidade by rememberSaveable(cfg.id) { mutableStateOf(cfg.cidade.orEmpty()) }

    val mudou = nome != cfg.nomeMotorista.orEmpty() ||
        telefone != cfg.telefone.orEmpty() ||
        cidade != cfg.cidade.orEmpty()

    CardSecao(titulo = "Meu perfil", destaque = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Inicial do nome (ou do email) como avatar.
            val inicial = (nome.ifBlank { emailConta.orEmpty() }).trim().firstOrNull()?.uppercase() ?: "M"
            Surface(shape = CircleShape, color = Lima, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(inicial, color = Color(0xFF0A0D0B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    nome.ifBlank { "Motorista" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    emailConta ?: "sem conta conectada",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario
                )
            }
        }
        CampoFormulario("Nome", nome, { nome = it }, numerico = false)
        CampoFormulario("Telefone", telefone, { telefone = it }, numerico = false)
        CampoFormulario("Cidade", cidade, { cidade = it }, numerico = false)
        if (mudou) {
            Button(
                onClick = { onSalvar(nome, telefone, cidade) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0A0D0B))
            ) { Text("Salvar perfil", fontWeight = FontWeight.Bold) }
        }
    }
}

/** Versão instalada e atualização do app sem passar pelo Android Studio. */
@Composable
private fun AtualizacaoCard(
    estado: EstadoAtualizacao,
    versao: String,
    codigo: Int,
    onProcurar: () -> Unit,
    onInstalar: (com.motoristapro.atualizacao.VersaoPublicada) -> Unit
) {
    CardSecao(titulo = "Versão do app") {
        LinhaValor("Instalada", "$versao (build $codigo)")
        val nova = estado.disponivel
        if (nova != null) {
            Text(
                "Nova versão disponível: ${nova.versionName}",
                color = Lima, fontWeight = FontWeight.Bold
            )
            if (nova.notas.isNotBlank()) {
                Text(nova.notas, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
            if (estado.baixando) {
                LinearProgressIndicator(
                    progress = { estado.progresso / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Lima
                )
                Text("Baixando... ${estado.progresso}%", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            } else {
                Button(
                    onClick = { onInstalar(nova) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0A0D0B))
                ) { Text("Baixar e instalar", fontWeight = FontWeight.Bold) }
            }
        } else {
            OutlinedButton(
                onClick = onProcurar,
                enabled = !estado.verificando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.verificando) "Procurando..." else "Procurar atualização") }
        }
    }
}
