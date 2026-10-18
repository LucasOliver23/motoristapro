package com.motoristapro.ui.mais

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border
import com.motoristapro.custo.CalculadoraCusto
import com.motoristapro.custo.DadosCusto
import com.motoristapro.custo.FatiasCusto
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.ui.custo.BarraFatias
import com.motoristapro.ui.custo.CorCombustivel
import com.motoristapro.ui.custo.CorManutencao
import com.motoristapro.ui.custo.CorFixos
import com.motoristapro.ui.custo.QuadroMeta
import com.motoristapro.ui.custo.centavosPorKm
import com.motoristapro.ui.componentes.Cartao
import com.motoristapro.ui.componentes.Rotulo
import com.motoristapro.ui.theme.TextoPrincipal
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.horizontalScroll
import com.motoristapro.ui.componentes.CartaoDestaque
import com.motoristapro.ui.componentes.CorSobreDestaque
import com.motoristapro.ui.componentes.SeloTexto
import com.motoristapro.ui.theme.EstiloValor
import androidx.compose.ui.graphics.vector.ImageVector
import com.motoristapro.ui.componentes.Icones
import com.motoristapro.ui.componentes.SeloIcone
import com.motoristapro.ui.theme.Laranja
import com.motoristapro.ui.theme.AzulInfo
import com.motoristapro.ui.theme.Roxo
import com.motoristapro.ui.theme.Turquesa
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.motoristapro.ui.abrirInicializacaoAutomatica
import com.motoristapro.ui.NavegacaoRapida
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
import com.motoristapro.MotoristaApp
import com.motoristapro.assinatura.SituacaoAcesso
import com.motoristapro.ui.assinatura.AssinaturaScreen
import com.motoristapro.service.AppNavegacao
import com.motoristapro.service.EstiloCartao
import com.motoristapro.ui.theme.PreferenciaTema
import java.util.Locale
import com.motoristapro.ui.componentes.CardPreparacao
import com.motoristapro.ui.componentes.Liberacao
import com.motoristapro.ui.componentes.bateriaLiberada
import com.motoristapro.ui.componentes.pedirBateriaLivre
import com.motoristapro.ui.componentes.FaixaTresCores
import com.motoristapro.ui.componentes.LinhaFaixa
import com.motoristapro.ui.componentes.TabelaFaixas
import com.motoristapro.ui.custo.AssistenteCustoScreen
import com.motoristapro.ui.theme.AmareloAlerta
import com.motoristapro.ui.theme.Contorno
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import com.motoristapro.ui.theme.SuperficieAlta
import com.motoristapro.ui.theme.Superficie
import com.motoristapro.ui.theme.ModoTema
import com.motoristapro.ui.manutencao.ManutencaoConteudo
import com.motoristapro.ui.manutencao.maisUrgente
import com.motoristapro.ui.manutencao.textoDaManutencao

private val PT = Locale("pt", "BR")

private fun Long.campo(): String = emReais().removePrefix("R$ ")

@Composable
fun MaisRoute(vm: MaisViewModel = viewModel(factory = MaisViewModel.Factory)) {
    val config by vm.config.collectAsStateWithLifecycle()
    val leitorOk by vm.leitorConectado.collectAsStateWithLifecycle()
    val bolha by vm.bolhaAtiva.collectAsStateWithLifecycle()
    val resumo by vm.resumoAtivo.collectAsStateWithLifecycle()
    val custos by vm.custosFixos.collectAsStateWithLifecycle()
    val dadosCusto by vm.dadosCusto.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val restaurado by vm.restaurado.collectAsStateWithLifecycle()
    val diagnostico by vm.diagnostico.collectAsStateWithLifecycle()
    val usuario by vm.usuario.collectAsStateWithLifecycle()
    val nuvem by vm.estadoNuvem.collectAsStateWithLifecycle()
    val atualizacao by vm.estadoAtualizacao.collectAsStateWithLifecycle()
    var confirmarBaixarNuvem by remember { mutableStateOf(false) }
    var confirmarSair by remember { mutableStateOf(false) }
    var confirmarZerar by remember { mutableStateOf(false) }
    LaunchedEffect(usuario?.uid) { if (usuario != null) vm.consultarNuvem() }
    val ocr by vm.ocrAtivo.collectAsStateWithLifecycle()
    val appPrefs = (LocalContext.current.applicationContext as com.motoristapro.MotoristaApp).preferencias
    val pacote99 by appPrefs.pacote.collectAsStateWithLifecycle()
    val notificacaoOferta by appPrefs.notificacaoOferta.collectAsStateWithLifecycle()
    val voz by vm.vozAtiva.collectAsStateWithLifecycle()
    val vozResumida by vm.vozResumida.collectAsStateWithLifecycle()
    val riscoLigado by vm.riscoAtivo.collectAsStateWithLifecycle()
    val riscoMercados by vm.riscoMercados.collectAsStateWithLifecycle()
    val palavrasRisco by vm.riscoPalavras.collectAsStateWithLifecycle()
    val faixas by vm.faixas.collectAsStateWithLifecycle()
    val horasPorDia by vm.horasPorDia.collectAsStateWithLifecycle()
    val metaPorHora by vm.metaPorHora.collectAsStateWithLifecycle()
    val manutencoes by vm.manutencoes.collectAsStateWithLifecycle()
    val odometro by vm.odometroAtual.collectAsStateWithLifecycle()
    var assistenteAberto by remember { mutableStateOf(false) }
    var sub by remember { mutableStateOf<SubTela?>(null) }
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

    // Estado do cartao da oferta, so para o quadradinho da grade mostrar o resumo.
    // Relido quando a sub-tela fecha: e la que ele muda.
    val assinaturaManager = remember { (context.applicationContext as MotoristaApp).assinatura }
    val acesso by assinaturaManager.acesso.collectAsStateWithLifecycle()
    val precos by assinaturaManager.precos.collectAsStateWithLifecycle()

    val estiloCartao = remember { EstiloCartao(context) }
    var estilo by remember { mutableStateOf(estiloCartao.ler()) }
    var navegacao by remember { mutableStateOf(AppNavegacao.lida(context)) }
    // Atalho do Início: "abra o Menu já dentro desta tela".
    LaunchedEffect(Unit) {
        NavegacaoRapida.subTelaPedida.collect { nome ->
            if (nome != null) {
                sub = runCatching { SubTela.valueOf(nome) }.getOrNull()
                NavegacaoRapida.subTelaPedida.value = null
            }
        }
    }

    LaunchedEffect(sub) {
        if (sub == null) {
            estilo = estiloCartao.ler()
            navegacao = AppNavegacao.lida(context)
        }
        // O hodômetro vem de um abastecimento, que pode ter sido lançado em
        // Finanças desde a última vez que esta aba foi aberta.
        vm.conferirOdometro()
    }

    var notificacoesOk by remember { mutableStateOf(Notificacoes.podeNotificar(context)) }
    var bateriaOk by remember { mutableStateOf(context.bateriaLiberada()) }
    LifecycleResumeEffect(Unit) {
        // Relido toda vez que a tela volta ao primeiro plano: é assim que o card
        // de preparação percebe o que o motorista acabou de liberar nas Configurações.
        notificacoesOk = Notificacoes.podeNotificar(context)
        bateriaOk = context.bateriaLiberada()
        onPauseOrDispose { }
    }
    val pedirNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notificacoesOk = ok
    }
    LaunchedEffect(Unit) { vm.mensagens.collect { snackbar.showSnackbar(it) } }

    TelaAba(titulo = "Menu", snackbar = snackbar) { padding ->
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
            CardPreparacao(
                listOf(
                    Liberacao(
                        titulo = "Leitor de ofertas",
                        porque = "Sem ele o app não enxerga a corrida que aparece na tela.",
                        concedida = leitorOk,
                        abrir = { context.abrirConfigAcessibilidade() }
                    ),
                    Liberacao(
                        titulo = "Bateria sem restrições",
                        porque = "É o que impede o Android de desligar o leitor no meio do turno.",
                        concedida = bateriaOk,
                        abrir = { context.pedirBateriaLivre() }
                    ),
                    Liberacao(
                        titulo = "Notificações",
                        porque = "Resumo do dia e avisos de atualização.",
                        concedida = notificacoesOk,
                        abrir = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.abrirDetalhesDoApp()
                            }
                        }
                    )
                )
            )

            PerfilLinha(
                cfg = cfg,
                email = usuario?.email ?: usuario?.nome,
                nomeDaConta = usuario?.nome,
                fotoDaConta = usuario?.fotoUrl,
                selo = when (acesso.situacao) {
                    SituacaoAcesso.ASSINATURA_ATIVA -> "PRO · ${acesso.diasDeAssinatura} dias"
                    SituacaoAcesso.TESTE_ATIVO -> "TESTE · ${acesso.diasDeTeste} dias"
                    SituacaoAcesso.TESTE_ACABOU -> "TESTE ACABOU"
                    SituacaoAcesso.ASSINATURA_VENCIDA -> "VENCIDA"
                    else -> null
                },
                seloAceso = acesso.situacao == SituacaoAcesso.ASSINATURA_ATIVA ||
                    acesso.situacao == SituacaoAcesso.TESTE_ATIVO
            ) { sub = SubTela.PERFIL }

            ListaFerramentas(
                titulo = "LEITOR DE OFERTAS",
                itens = listOf(
                    Ferramenta(
                        titulo = "Ler ofertas na tela",
                        icone = "👁",
                        estado = if (leitorOk) "lendo Uber · 99 · iFood · inDrive" else "desconectado — religue",
                        aceso = leitorOk,
                        alerta = !leitorOk,
                        onClick = { sub = SubTela.LEITOR }
                    ),
                    Ferramenta(
                        titulo = "Pacote 0% de taxa",
                        icone = "%",
                        estado = if (pacote99.ativo) "99 · ${pacote99.plano.rotulo} · ${pacote99.plano.precoCentavos.emReais()}"
                        else "não uso pacote",
                        aceso = pacote99.ativo,
                        onClick = { sub = SubTela.PACOTE }
                    ),
                    Ferramenta(
                        titulo = "Aparência do cartão",
                        icone = "🎨",
                        estado = "cores, números, tamanho e voz",
                        onClick = { sub = SubTela.ESTILO }
                    ),
                    Ferramenta(
                        titulo = "Notificação da corrida",
                        icone = "🔔",
                        estado = "endereços e atalhos do mapa na barra",
                        ligado = notificacaoOferta,
                        onClick = { appPrefs.definirNotificacaoOferta(!notificacaoOferta) }
                    ),
                    Ferramenta(
                        titulo = "Aviso por voz",
                        icone = "🔊",
                        estado = "fala a decisão e o R$/km",
                        ligado = voz,
                        onClick = { vm.definirVoz(!voz) }
                    ),
                    Ferramenta(
                        titulo = "Bolha flutuante",
                        icone = "⚪",
                        estado = "lucro do dia sempre à vista",
                        ligado = bolha,
                        onClick = { vm.definirBolha(!bolha) }
                    ),
                    Ferramenta(
                        titulo = "Endereços de risco",
                        icone = "⚠",
                        estado = if (palavrasRisco.isEmpty()) "nenhuma palavra"
                        else "${palavrasRisco.size} palavra(s)",
                        aceso = palavrasRisco.isNotEmpty() && riscoLigado,
                        onClick = { sub = SubTela.RISCO }
                    ),
                    Ferramenta(
                        titulo = "App de navegação",
                        icone = "🧭",
                        estado = navegacao.rotulo.lowercase(PT),
                        onClick = { sub = SubTela.NAVEGACAO }
                    ),
                )
            )

            ListaFerramentas(
                titulo = "MEU TRABALHO",
                itens = listOf(
                    Ferramenta(
                        titulo = "Suas metas",
                        icone = "🎯",
                        estado = metaPorHora?.let {
                            "${config?.metaLucroDiarioCentavos?.emReais()}/dia · " +
                                "${it.lucroPorHoraCentavos.emReais()}/h em ${it.horasPorDia}h"
                        } ?: "sem meta cadastrada",
                        aceso = metaPorHora != null,
                        onClick = { sub = SubTela.METAS }
                    ),
                    Ferramenta(
                        titulo = "Manutenção",
                        icone = "🔧",
                        estado = when {
                            manutencoes.isEmpty() -> "óleo, pneus e revisão por km"
                            odometro <= 0L -> "informe o km ao abastecer"
                            else -> maisUrgente(manutencoes)!!.let { item ->
                                "${item.nome.lowercase(PT)} ${textoDaManutencao(item, odometro)}"
                            }
                        },
                        aceso = manutencoes.isNotEmpty() && odometro > 0 &&
                            maisUrgente(manutencoes)!!.faltamKm(odometro) > 500,
                        alerta = manutencoes.isNotEmpty() && odometro > 0 &&
                            maisUrgente(manutencoes)!!.faltamKm(odometro) <= 500,
                        onClick = { sub = SubTela.MANUTENCAO }
                    ),
                    Ferramenta(
                        titulo = "Resumo do dia às 22h",
                        icone = "🔔",
                        estado = if (resumo) "notificação com o lucro do dia" else "desligado",
                        ligado = resumo,
                        onClick = { vm.definirResumo(!resumo) }
                    ),
                )
            )

            ListaFerramentas(
                titulo = "O APP",
                itens = listOf(
                    Ferramenta(
                        titulo = "Minha assinatura",
                        icone = "⭐",
                        estado = when (acesso.situacao) {
                            SituacaoAcesso.TESTE_ATIVO -> "teste: ${acesso.diasDeTeste} dia(s)"
                            SituacaoAcesso.ASSINATURA_ATIVA ->
                                "${acesso.plano?.rotulo?.lowercase(PT) ?: "ativa"} · ${acesso.diasDeAssinatura} dia(s)"
                            SituacaoAcesso.TESTE_ACABOU -> "teste acabou"
                            SituacaoAcesso.ASSINATURA_VENCIDA -> "vencida"
                            else -> "ver planos"
                        },
                        aceso = acesso.situacao == SituacaoAcesso.ASSINATURA_ATIVA,
                        alerta = acesso.situacao == SituacaoAcesso.TESTE_ACABOU ||
                            acesso.situacao == SituacaoAcesso.ASSINATURA_VENCIDA,
                        onClick = { sub = SubTela.ASSINATURA }
                    ),
                    Ferramenta(
                        titulo = "Conta e nuvem",
                        icone = "☁",
                        estado = if (!vm.loginDisponivel) "indisponível"
                        else usuario?.email ?: "entrar para salvar",
                        onClick = { if (vm.loginDisponivel) sub = SubTela.NUVEM }
                    ),
                    Ferramenta(
                        titulo = "Backup e planilhas",
                        icone = "📦",
                        estado = "arquivo de backup e CSV",
                        onClick = { sub = SubTela.BACKUP }
                    ),
                    Ferramenta(
                        titulo = "Versão do app",
                        icone = "⚙",
                        estado = "${vm.versaoInstalada} (build ${vm.codigoInstalado})",
                        onClick = { sub = SubTela.VERSAO }
                    ),
                )
            )

            // Escuro / Claro / Do sistema direto aqui, sem entrar em tela nenhuma.
            SeletorDeTema(context)

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
    if (confirmarZerar) {
        AlertDialog(
            onDismissRequest = { confirmarZerar = false },
            title = { Text("Começar do zero?") },
            text = {
                Text(
                    "Apaga deste celular as corridas, despesas, turnos, ofertas, custos fixos, " +
                        "o custo por km, as metas e as faixas do semáforo — como se o app tivesse " +
                        "acabado de ser instalado.\n\n" +
                        "O backup na sua conta na nuvem NÃO é apagado: dá para trazer tudo de volta " +
                        "em \"Baixar da nuvem\"."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarZerar = false; vm.zerarCelular() }) {
                    Text("Apagar e começar do zero", color = VermelhoPrejuizo)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarZerar = false }) { Text("Cancelar") } }
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

    // ------------------------------------------------------------- sub-telas
    // Cada linha da lista abre o seu proprio conteudo em tela cheia. Os cartoes
    // sao os mesmos de antes - so pararam de disputar espaco numa pagina so.
    sub?.let { aberta ->
        // 'cfg' so existe dentro do TelaAba; aqui fora temos a versao anulavel.
        val cfg = config ?: return@let
        // Camada por cima da aba, e NAO uma Dialog: a janela da Dialog se estende
        // para fora da tela quando pede o inset do teclado, e era por isso que o
        // botao do rodape caia abaixo da borda do celular.
        BackHandler { sub = null }

        // A tela de assinatura rola sozinha e ocupa a tela inteira. Dentro do
        // SubTelaHost — que tambem rola — ela recebia altura infinita e o app
        // FECHAVA na hora de abrir. Por isso ela vem antes, sem a moldura.
        if (aberta == SubTela.ASSINATURA) {
            CamadaTelaCheia {
                AssinaturaScreen(
                    acesso = acesso,
                    precos = precos,
                    gerente = assinaturaManager,
                    onFechar = { sub = null },
                    onSair = { confirmarSair = true }
                )
            }
            return@let
        }

        CamadaTelaCheia {
            SubTelaHost(titulo = aberta.titulo, onFechar = { sub = null }) {
                when (aberta) {
                    SubTela.PERFIL ->
                    PerfilCard(
                        cfg = cfg,
                        emailConta = usuario?.email ?: usuario?.nome,
                        onSalvar = vm::salvarPerfil
                    )
                    SubTela.VEICULO -> {
                    // Tela de LEITURA. Quem preenche e o assistente de custo: os
                    // mesmos numeros digitados em dois lugares viviam divergindo
                    // (meta de R$ 200/dia convivendo com meta mensal de R$ 5.000).
                    CustoPorKmCard(cfg, dadosCusto, onAbrir = { assistenteAberto = true })
                    VeiculoCard(cfg, dadosCusto, onAbrir = { assistenteAberto = true })
                    MetasCard(cfg, onEditar = { assistenteAberto = true })
                    CustosFixosCard(
                        custos = custos,
                        diasTrabalhoMes = cfg.diasTrabalhoMes,
                        onExcluir = { excluirCusto = it },
                        onAdicionar = { novoCusto = true }
                    )
                    }
                    SubTela.SEMAFORO -> {
                        // As faixas decidem a COR; o estilo decide o que o cartão
                        // escreve dentro dela. Separados eram dois menus para
                        // ajustar a mesma coisa.
                        CardSuasFaixas(
                            faixas = faixas,
                            temCusto = cfg.custoKmCentavos > 0,
                            valorMinimoViagem = cfg.tarifaMinimaCentavos,
                            onFaixaKm = vm::definirFaixaKm,
                            onFaixaHora = vm::definirFaixaHora,
                            onFaixaNota = vm::definirFaixaNota,
                            onUsarNota = vm::definirUsarNota,
                            onLucroMinimo = vm::definirLucroMinimo,
                            onLucroPercent = vm::definirLucroPercent,
                            onValorMinimo = vm::definirValorMinimoViagem,
                            onAbrirAssistente = { assistenteAberto = true },
                            metaHora = metaPorHora?.ofertaPorHoraReais
                        )
                        EstiloCartaoScreen(custoKmCentavos = cfg.custoKmCentavos)
                    }
                    SubTela.CHECKUP -> CheckupConteudo(
                        leitorOk = leitorOk,
                        ocrLigado = ocr,
                        notificacoesOk = notificacoesOk,
                        bateriaOk = bateriaOk,
                        diagnostico = diagnostico,
                        onAcessibilidade = { context.abrirConfigAcessibilidade() },
                        onBateria = { context.pedirBateriaLivre() },
                        onNotificacoes = { context.abrirDetalhesDoApp() },
                        onInicializacao = { context.abrirInicializacaoAutomatica() },
                        onOcr = { vm.definirOcr(!ocr) },
                        onTestar = vm::testarLeitor
                    )
                    SubTela.RISCO ->
                    CardSecaoEnderecosRisco(
                        ativo = riscoLigado,
                        mercados = riscoMercados,
                        palavras = palavrasRisco,
                        onAtivo = vm::definirRisco,
                        onMercados = vm::definirRiscoMercados,
                        onAdicionar = vm::adicionarPalavraRisco,
                        onRemover = vm::removerPalavraRisco
                    )
                    SubTela.LEITOR -> {
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
                    DiagnosticoCard(diagnostico, leitorOk, ocr)
                    CardSecao(titulo = "Permissões") {
                        OutlinedButton(onClick = { context.abrirDetalhesDoApp() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Informações do app (localização, bateria)")
                        }
                        Text(
                            "Dica: tire o MotoristaPro da otimização de bateria para o leitor e o GPS não serem desligados.",
                            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                        )
                    }
                    }
                    SubTela.NUVEM -> {
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
                            HorizontalDivider(color = Contorno)
                            Text(
                                "Entrou com outra conta neste celular e os dados do motorista anterior " +
                                    "ficaram? A partir de agora o app zera sozinho quando a conta muda — " +
                                    "e aqui você limpa o que já estava.",
                                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                            )
                            OutlinedButton(
                                onClick = { confirmarZerar = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Começar do zero neste celular", color = VermelhoPrejuizo) }
                        }
                    }
                    }
                    SubTela.BACKUP ->
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
                    // Tratada antes, fora desta moldura (ela rola por conta propria).
                    SubTela.ASSINATURA -> Unit
                    SubTela.NAVEGACAO -> AppNavegacaoScreen()
                    SubTela.PACOTE -> PacoteTaxaScreen(onSalvo = { sub = null })
                    SubTela.METAS -> MetaPorHoraConteudo(
                        cfg = cfg,
                        horasPorDia = horasPorDia,
                        conta = metaPorHora,
                        horaBoaAtual = faixas.horaBoa,
                        veiculo = (dadosCusto?.tipoVeiculo ?: TipoVeiculo.CARRO).rotulo.lowercase(PT),
                        onHoras = vm::definirHorasPorDia,
                        onUsarNoSemaforo = vm::usarMetaNoSemaforo,
                        onEditarMeta = { assistenteAberto = true }
                    )
                    SubTela.ESTILO -> {
                        EstiloCartaoScreen(custoKmCentavos = cfg.custoKmCentavos)
                        VozOfertasCard()
                    }
                    SubTela.MANUTENCAO -> ManutencaoConteudo(
                        itens = manutencoes,
                        odometroAtual = odometro,
                        onSalvar = vm::salvarManutencao,
                        onExcluir = vm::excluirManutencao
                    )
                    SubTela.APARENCIA -> AparenciaScreen()
                    SubTela.VERSAO ->
                    AtualizacaoCard(
                        estado = atualizacao,
                        versao = vm.versaoInstalada,
                        codigo = vm.codigoInstalado,
                        onProcurar = vm::procurarAtualizacao,
                        onInstalar = vm::instalarAtualizacao
                    )
                }
            }
        }
    }

    if (assistenteAberto) {
        BackHandler { assistenteAberto = false }
        CamadaTelaCheia {
            AssistenteCustoScreen(onFechar = { assistenteAberto = false })
        }
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
internal fun DiagnosticoCard(d: Diagnostico?, leitorOk: Boolean, ocrLigado: Boolean) {
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
        d.motivo?.takeIf { d.reconhecida == null }?.let { motivo ->
            Text(
                "Por quê: $motivo",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )
        }
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
                    appendLine(
                        "Leitura por imagem: " + (if (ocrLigado) "ligada" else "DESLIGADA") +
                            "  Android: ${android.os.Build.VERSION.SDK_INT}  Aparelho: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                    )
                    d.motivo?.let { appendLine("Por quê: $it") }
                    appendLine("Textos:")
                    d.textos.forEach { appendLine(it) }
                }
                clipboard.setText(AnnotatedString(texto))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copiar textos (para enviar ao suporte)") }
    }
}

private val AmareloAlertaDiag: Color get() = com.motoristapro.ui.theme.AmareloAlerta

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

    // Salva sozinho ao sair da tela. Antes, quem digitava o nome e tocava em
    // voltar perdia tudo: o botão "Salvar perfil" nasce embaixo dos campos e
    // nem sempre está à vista quando o teclado está aberto.
    val pendente by rememberUpdatedState(Triple(nome, telefone, cidade) to mudou)
    DisposableEffect(Unit) {
        onDispose {
            val (dados, precisa) = pendente
            if (precisa) onSalvar(dados.first, dados.second, dados.third)
        }
    }

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


// ====================================================== custo real e faixas

/** Atalho para o assistente de custo, mostrando o que já está valendo. */
@Composable
private fun CardCustoReal(custoKmCentavos: Long, onAbrir: () -> Unit) {
    CardSecao(titulo = "Custo real por km") {
        if (custoKmCentavos > 0) {
            Text(
                "${custoKmCentavos.emReais()} / km",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Lima
            )
            Text(
                "É o que o app desconta de cada corrida para mostrar o lucro de verdade.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        } else {
            Text(
                "Você ainda não calculou seu custo. Sem ele, o app mostra o valor bruto da " +
                    "corrida — não o que sobra pra você.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        Button(
            onClick = onAbrir,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0B0F14))
        ) {
            Text(
                if (custoKmCentavos > 0) "Recalcular meu custo" else "Calcular meu custo",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** O veículo, como o assistente deixou. Só leitura: editar é refazer o cálculo. */
@Composable
private fun ResumoVeiculo(cfg: Configuracao) {
    CardSecao(titulo = "Veículo") {
        val nome = cfg.veiculoNome?.takeIf { it.isNotBlank() }
        if (nome == null && cfg.consumoKmLx100 <= 0) {
            Text(
                "Nada preenchido ainda. Tudo isto vem do assistente de custo, ali em cima.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            return@CardSecao
        }
        if (nome != null) LinhaValor("Modelo", nome)
        if (cfg.consumoKmLx100 > 0) {
            LinhaValor("Consumo", String.format(PT, "%.1f km/L", cfg.consumoKmLx100 / 100.0))
        }
        if (cfg.precoLitroCentavos > 0) LinhaValor("Preço do litro", cfg.precoLitroCentavos.emReais())
        cfg.custoCombustivelKmCentavos?.let {
            LinhaValor("Combustível por km", it.centavosEmReais(), cor = AmareloAlerta)
        }
    }
}

/** As metas, todas saindo da mensal que o assistente perguntou. */
@Composable
private fun ResumoMetas(cfg: Configuracao) {
    CardSecao(titulo = "Metas de lucro") {
        if (cfg.metaLucroMensalCentavos <= 0) {
            Text(
                "Sem meta cadastrada. O assistente pergunta quanto você quer levar por mês " +
                    "e divide pelos dias trabalhados.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            return@CardSecao
        }
        LinhaValor("Por dia trabalhado", cfg.metaLucroDiarioCentavos.emReais(), negrito = true, cor = Lima)
        LinhaValor("Por semana", cfg.metaLucroSemanalCentavos.emReais())
        LinhaValor("Por mês", cfg.metaLucroMensalCentavos.emReais())
        LinhaValor("Dias trabalhados por mês", "${cfg.diasTrabalhoMes}")
        Text(
            "A meta do dia é a barra do lucro de hoje, no Início. As três saem da meta mensal.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }
}

/**
 * As faixas do semáforo. Tudo aqui salva na hora — arrastou, valeu na próxima oferta.
 *
 * Lucro mínimo em R$ e em % ficam BLOQUEADOS enquanto não existir custo por km:
 * sem o custo não há lucro a calcular, e deixar o motorista configurar algo que
 * nunca vai valer é pior do que não oferecer.
 */
@Composable
private fun CardSuasFaixas(
    faixas: EstadoFaixas,
    temCusto: Boolean,
    valorMinimoViagem: Long,
    onFaixaKm: (Float, Float) -> Unit,
    onFaixaHora: (Float, Float) -> Unit,
    onFaixaNota: (Float, Float) -> Unit,
    onUsarNota: (Boolean) -> Unit,
    onLucroMinimo: (Float) -> Unit,
    onLucroPercent: (Float) -> Unit,
    onValorMinimo: (Long) -> Unit,
    onAbrirAssistente: () -> Unit,
    /** O R$/hora que bate a meta (Menu ▸ Suas metas); vira um risco na régua. */
    metaHora: Int? = null
) {
    CardSecao(titulo = "Suas faixas") {
        TabelaFaixas(
            listOf(
                LinhaFaixa(
                    "R$/km",
                    String.format(PT, "< %.2f", faixas.kmRuim),
                    String.format(PT, "%.2f–%.2f", faixas.kmRuim, faixas.kmBoa),
                    String.format(PT, "≥ %.2f", faixas.kmBoa)
                ),
                LinhaFaixa(
                    "R$/hora",
                    String.format(PT, "< %.0f", faixas.horaRuim),
                    String.format(PT, "%.0f–%.0f", faixas.horaRuim, faixas.horaBoa),
                    String.format(PT, "≥ %.0f", faixas.horaBoa)
                )
            ) + if (faixas.usarNota) listOf(
                LinhaFaixa(
                    "Nota",
                    String.format(PT, "< %.2f", faixas.notaRuim),
                    String.format(PT, "%.2f–%.2f", faixas.notaRuim, faixas.notaBoa),
                    String.format(PT, "≥ %.2f", faixas.notaBoa)
                )
            ) else emptyList()
        )

        HorizontalDivider(color = Contorno, modifier = Modifier.padding(vertical = 4.dp))

        FaixaTresCores(
            titulo = "Ganho por km",
            unidade = "R$/km",
            inicio = faixas.kmRuim, fim = faixas.kmBoa,
            // Régua até R$ 4/km: até 6 os valores de verdade (0,50 a 2,50) ficavam
            // espremidos no primeiro terço e cada milímetro do dedo pulava 10 centavos.
            minimo = 0.3f, maximo = 4f, degrau = 0.05f,
            formatar = { String.format(PT, "R$ %.2f", it) },
            onMudar = onFaixaKm
        )

        FaixaTresCores(
            titulo = "Ganho por hora",
            unidade = "R$/hora",
            inicio = faixas.horaRuim, fim = faixas.horaBoa,
            // Meta alta (poucas horas, custo alto) passa de R$ 80: a régua cresce
            // junto para o verde dela caber.
            minimo = 5f, maximo = maxOf(80f, faixas.horaBoa + 10f, (metaHora ?: 0) + 10f), degrau = 1f,
            formatar = { String.format(PT, "R$ %.0f", it) },
            onMudar = onFaixaHora,
            marca = metaHora?.toFloat(),
            ajuda = metaHora?.let { "Risco branco: sua meta, R$ $it/h (Menu ▸ Suas metas)." }
        )

        LinhaSwitch(
            "Usar a nota do passageiro",
            "Só vale nas telas que mostram a nota. Uber mostra; a 99 nem sempre.",
            faixas.usarNota, onUsarNota
        )
        if (faixas.usarNota) {
            FaixaTresCores(
                titulo = "Nota do passageiro",
                unidade = "estrelas",
                inicio = faixas.notaRuim, fim = faixas.notaBoa,
                minimo = 3f, maximo = 5f, degrau = 0.05f,
                formatar = { String.format(PT, "★ %.2f", it) },
                onMudar = onFaixaNota
            )
        }

        HorizontalDivider(color = Contorno, modifier = Modifier.padding(vertical = 4.dp))
        Text("Condições mínimas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Violou qualquer uma, a oferta fica vermelha na hora — não importa o resto.",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )

        CampoMinimo(
            rotulo = "Valor mínimo da viagem (R$)",
            valorInicial = if (valorMinimoViagem > 0) valorMinimoViagem.campo() else "",
            habilitado = true,
            onValor = { onValorMinimo(it.paraCentavosOuZero() ?: 0L) }
        )
        CampoMinimo(
            rotulo = "Lucro líquido mínimo (R$)",
            valorInicial = if (faixas.lucroMinimo > 0f) String.format(PT, "%.2f", faixas.lucroMinimo) else "",
            habilitado = temCusto,
            onValor = { onLucroMinimo(((it.paraCentavosOuZero() ?: 0L) / 100f)) }
        )
        CampoMinimo(
            rotulo = "Porcentagem de lucro (%)",
            valorInicial = if (faixas.lucroPercent > 0f) faixas.lucroPercent.toInt().toString() else "",
            habilitado = temCusto,
            onValor = { onLucroPercent(it.filter(Char::isDigit).toFloatOrNull() ?: 0f) }
        )

        if (!temCusto) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Requer custo por km",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmareloAlerta,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onAbrirAssistente) { Text("Calcular agora", color = Lima) }
            }
        }
    }
}

/** Campo de mínimo: quando bloqueado, mostra "Requer custo" em vez de aceitar digitação. */
@Composable
private fun CampoMinimo(
    rotulo: String,
    valorInicial: String,
    habilitado: Boolean,
    onValor: (String) -> Unit
) {
    var texto by rememberSaveable(rotulo) { mutableStateOf(valorInicial) }
    if (!habilitado) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            Text("Requer custo", style = MaterialTheme.typography.bodySmall, color = AmareloAlerta)
        }
        return
    }
    CampoFormulario(
        rotulo = rotulo,
        valor = texto,
        onValor = {
            texto = it
            onValor(it)
        }
    )
}


// ====================================================== lista e sub-telas

/**
 * As telas que a lista do "Mais" abre.
 *
 * A regra do corte: o que o motorista mexe TODO DIA fica como interruptor na
 * lista; o que ele configura UMA VEZ some para dentro de uma destas.
 */
enum class SubTela(val titulo: String) {
    PERFIL("Meu perfil"),
    VEICULO("Meu veículo e custos"),
    // Faixas e estilo do cartão viraram uma tela só: os dois são o semáforo —
    // um decide a cor, o outro decide o que o cartão mostra dentro dela.
    SEMAFORO("Semáforo de valores"),
    CHECKUP("Checkup do app"),
    RISCO("Endereços de risco"),
    LEITOR("Leitor de ofertas"),
    NUVEM("Conta e nuvem"),
    BACKUP("Backup e exportação"),
    VERSAO("Versão do app"),
    APARENCIA("Aparência do app"),
    ASSINATURA("Minha assinatura"),
    NAVEGACAO("App de navegação"),
    PACOTE("Pacote 0% de taxa"),
    ESTILO("Aparência do cartão"),
    METAS("Suas metas"),
    MANUTENCAO("Manutenção")
}

/**
 * Uma tela inteira desenhada por cima da aba.
 *
 * Antes isto era uma Dialog, e a janela dela media mais que a tela do celular:
 * o rodape (o botao "Continuar") ficava fora da borda de baixo. Aqui dentro da
 * propria janela do app, os recuos de status bar e teclado chegam certos.
 *
 * O clickable sem efeito visual existe só para a camada engolir os toques —
 * sem ele o motorista acertaria os botoes da tela que ficou atras.
 */
@Composable
private fun CamadaTelaCheia(conteudo: @Composable () -> Unit) {
    val semRipple = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = semRipple, indication = null) { }
    ) {
        conteudo()
    }
}

/** Moldura das sub-telas: barra com título e voltar, conteúdo rolável embaixo. */
@Composable
private fun SubTelaHost(
    titulo: String,
    onFechar: () -> Unit,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Column(
        // statusBarsPadding (e nao safeDrawingPadding): a barra de abas do app ja
        // fica embaixo desta camada, entao o recuo de baixo quem da e o teclado.
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onFechar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                titulo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = conteudo
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Escuro, Claro ou Do sistema, em três botões.
 *
 * Fica no alto do Menu, antes de qualquer ajuste: foi a opção que mais custou
 * para o motorista achar, e a que mais muda a cara do app.
 */
@Composable
private fun SeletorDeTema(context: android.content.Context) {
    var modo by remember { mutableStateOf(PreferenciaTema.modo) }
    CardSecao(titulo = "APARÊNCIA") {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(SuperficieAlta).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ModoTema.entries.forEach { m ->
                val ativo = m == modo
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (ativo) MaterialTheme.colorScheme.background else Color.Transparent)
                        .clickable { PreferenciaTema.definir(context, m); modo = m }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        m.rotulo,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal,
                        color = if (ativo) Lima else TextoSecundario
                    )
                }
            }
        }
    }
}

/** Cabeçalho do "Mais": avatar, nome e um resumo de uma linha. */
@Composable
private fun PerfilLinha(
    cfg: Configuracao,
    email: String?,
    nomeDaConta: String? = null,
    fotoDaConta: String? = null,
    selo: String? = null,
    seloAceso: Boolean = false,
    onClick: () -> Unit
) {
    // O nome digitado no app manda; sem ele, vale o da conta Google.
    val nome = cfg.nomeMotorista?.takeIf { it.isNotBlank() }
        ?: nomeDaConta?.takeIf { it.isNotBlank() } ?: "Motorista"
    CartaoDestaque(Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar aceso, com a inicial: é a primeira coisa do Menu, como no desenho.
            FotoDaConta(fotoDaConta, nome.first().uppercase())
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                // O e-mail identifica a conta, o veículo identifica a conta de
                // custo. São coisas diferentes, por isso as duas linhas.
                if (!email.isNullOrBlank()) {
                    Text(email, style = MaterialTheme.typography.bodySmall, color = TextoSecundario, maxLines = 1)
                }
                Text(
                    cfg.veiculoNome?.takeIf { it.isNotBlank() }
                        ?: if (email.isNullOrBlank()) "Toque para completar seus dados" else "veículo não informado",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario,
                    maxLines = 1
                )
            }
            if (selo != null) SeloTexto(selo, if (seloAceso) Lima else AmareloAlerta)
        }
    }
}

// ------------------------------------------------------------------ FERRAMENTAS

/**
 * Uma linha da lista de ajustes.
 *
 * Duas naturezas na mesma linha: quem tem [ligado] é interruptor (o toque liga
 * e desliga ali mesmo, sem abrir tela); quem não tem abre a sua tela. O subtítulo
 * sempre diz como a função está AGORA — é o que faz a lista valer mais que um menu.
 */
class Ferramenta(
    val titulo: String,
    val estado: String,
    val onClick: () -> Unit,
    /** O selo da esquerda. Um caractere, para o olho achar a linha sem ler. */
    val icone: String = "•",
    /** Interruptor: true/false. null = abre uma tela. */
    val ligado: Boolean? = null,
    /** Pinta o estado de verde (configurado / funcionando). */
    val aceso: Boolean = false,
    /** Pinta o estado de vermelho (precisa de atenção). */
    val alerta: Boolean = false,
    /** Mostra as três bolinhas do semáforo no canto. */
    val semaforo: Boolean = false
)

/**
 * A lista de ajustes de um grupo, dentro de um cartão só.
 *
 * Lista e não grade: o nome e o estado de cada função cabem inteiros na linha,
 * o interruptor fica onde o polegar já procura (na direita) e o olho desce a
 * coluna de selos em vez de pular em zigue-zague entre quadrados.
 */
@Composable
private fun ListaFerramentas(titulo: String = "AJUSTES", itens: List<Ferramenta>) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            titulo,
            style = MaterialTheme.typography.labelSmall,
            color = TextoSecundario,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = Superficie)
        ) {
            Column(Modifier.fillMaxWidth()) {
                itens.forEachIndexed { indice, f ->
                    if (indice > 0) {
                        HorizontalDivider(
                            Modifier.padding(start = 60.dp),
                            thickness = 1.dp,
                            color = Contorno
                        )
                    }
                    LinhaFerramenta(f)
                }
            }
        }
    }
}

@Composable
private fun LinhaFerramenta(f: Ferramenta) {
    val ligadoAgora = f.ligado == true
    Row(
        Modifier.fillMaxWidth().clickable { f.onClick() }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val traco = iconeDeTraco(f.icone)
        if (traco != null) {
            SeloIcone(traco.first, traco.second)
        } else {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(SuperficieAlta),
                contentAlignment = Alignment.Center
            ) {
                Text(f.icone, fontSize = 15.sp)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 10.dp)) {
            Text(
                f.titulo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                f.estado,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    f.alerta -> VermelhoPrejuizo
                    f.aceso || ligadoAgora -> Lima
                    else -> TextoSecundario
                }
            )
        }
        when {
            f.ligado != null -> Switch(
                checked = ligadoAgora,
                onCheckedChange = { f.onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF0D1117),
                    checkedTrackColor = Lima,
                    checkedBorderColor = Lima,
                    uncheckedThumbColor = TextoSecundario,
                    uncheckedTrackColor = SuperficieAlta,
                    uncheckedBorderColor = Contorno
                )
            )
            f.semaforo -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(VermelhoPrejuizo, AmareloAlerta, Lima).forEach { cor ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(cor))
                }
            }
            else -> Text("›", style = MaterialTheme.typography.titleMedium, color = TextoSecundario)
        }
    }
}

/**
 * O ícone de traço (e a cor do assunto) de cada ajuste, a partir do selo
 * antigo. Assim as linhas do Menu não mudam de lugar nem de texto — só o
 * desenho. Selo sem par aqui continua aparecendo como antes.
 */
private fun iconeDeTraco(selo: String): Pair<ImageVector, Color>? = when (selo) {
    "💰" -> Icones.Veiculo to AzulInfo
    "🚦" -> Icones.Semaforo to AmareloAlerta
    "🔧" -> Icones.Chave to Laranja
    "🔔" -> Icones.Sino to AmareloAlerta
    "👁" -> Icones.Olho to Lima
    "🩺" -> Icones.Checkup to Roxo
    "🔊" -> Icones.Som to Turquesa
    "⚪" -> Icones.Bolha to Roxo
    "⚠" -> Icones.Alerta to VermelhoPrejuizo
    "🧭" -> Icones.Bussola to AzulInfo
    "⭐" -> Icones.Estrela to AmareloAlerta
    "☁" -> Icones.Nuvem to Turquesa
    "📦" -> Icones.Caixa to Laranja
    "🌓" -> Icones.Contraste to Roxo
    "⚙" -> Icones.Engrenagem to TextoSecundario
    else -> null
}


// ====================================================== meu veículo e custos (4.1)

/** O custo por km grande, com a barra de onde ele vem (combustível, manutenção, fixos). */
@Composable
private fun CustoPorKmCard(cfg: Configuracao, dados: DadosCusto?, onAbrir: () -> Unit) {
    val fatias = dados?.let { FatiasCusto.de(CalculadoraCusto.calcular(it)) } ?: FatiasCusto.ZERO
    CartaoDestaque {
        Rotulo("Seu custo por km")
        if (cfg.custoKmCentavos > 0) {
            Text(
                buildAnnotatedString {
                    append(cfg.custoKmCentavos.emReais())
                    withStyle(SpanStyle(fontSize = 20.sp, color = TextoSecundario)) { append(" /km") }
                },
                style = EstiloValor, fontSize = 42.sp
            )
            Text(
                "É o que o app desconta de cada corrida para mostrar o lucro.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            if (fatias.total > 0) {
                BarraFatias(fatias, Modifier.fillMaxWidth().height(12.dp).padding(top = 4.dp))
                Row(Modifier.fillMaxWidth()) {
                    LegendaFatia("Combustível", fatias.combustivel, CorCombustivel, Modifier.weight(1f))
                    LegendaFatia("Manutenção", fatias.manutencao, CorManutencao, Modifier.weight(1f))
                    LegendaFatia("Fixos", fatias.fixos, CorFixos, Modifier.weight(1f))
                }
            }
        } else {
            Text(
                "Você ainda não calculou seu custo. Sem ele, o app mostra o valor bruto da " +
                    "corrida — não o que sobra pra você.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
        }
        Button(
            onClick = onAbrir,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = CorSobreDestaque)
        ) {
            Text(if (cfg.custoKmCentavos > 0) "Recalcular meu custo" else "Calcular meu custo", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LegendaFatia(nome: String, porKm: Double, cor: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(cor))
            Text(nome, fontSize = 11.sp, color = TextoSecundario, modifier = Modifier.padding(start = 4.dp))
        }
        Text(centavosPorKm(porKm).removePrefix("R$ "), style = EstiloValor, fontSize = 15.sp)
    }
}

/** O veículo num cartão só: ícone, nome e etiquetas (moto, gasolina, 35 km/L, quitada). */
@Composable
private fun VeiculoCard(cfg: Configuracao, dados: DadosCusto?, onAbrir: () -> Unit) {
    val nome = cfg.veiculoNome?.takeIf { it.isNotBlank() } ?: dados?.veiculoNome?.takeIf { it.isNotBlank() }
    val moto = dados?.tipoVeiculo != TipoVeiculo.CARRO
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Superficie)
            .clickable { onAbrir() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeloIcone(if (moto) Icones.Moto else Icones.Carro, AzulInfo, tamanho = 46.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(nome ?: "Veículo não informado", fontWeight = FontWeight.Bold)
            val etiquetas = buildList {
                dados?.let { add(it.tipoVeiculo.rotulo) }
                dados?.let { add(it.combustivel.rotulo) }
                if (cfg.consumoKmLx100 > 0) {
                    add(String.format(PT, "%.0f %s", cfg.consumoKmLx100 / 100.0, dados?.combustivel?.unidade ?: "km/L"))
                }
                dados?.let { add(if (moto) it.forma.rotulo.replace("do", "da") else it.forma.rotulo) }
            }
            if (etiquetas.isEmpty()) {
                Text("Toque para preencher no assistente", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    etiquetas.forEach { t ->
                        Text(
                            t, fontSize = 12.sp, color = TextoSecundario, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(SuperficieAlta)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = TextoSecundario)
    }
}

/** Metas em três quadradinhos (dia, semana, mês). Todas saem da mensal, no assistente. */
@Composable
private fun MetasCard(cfg: Configuracao, onEditar: () -> Unit) {
    Cartao(espaco = 10) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rotulo("Suas metas de lucro", Modifier.weight(1f))
            Text(
                "Editar", color = Lima, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onEditar() }.padding(4.dp)
            )
        }
        if (cfg.metaLucroMensalCentavos <= 0) {
            Text(
                "Sem meta cadastrada. O assistente pergunta quanto você quer levar por mês " +
                    "e divide pelos dias trabalhados.",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario
            )
            return@Cartao
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuadroMeta("Por dia", cfg.metaLucroDiarioCentavos, Lima, Modifier.weight(1f))
            QuadroMeta("Semana", cfg.metaLucroSemanalCentavos, TextoPrincipal, Modifier.weight(1f))
            QuadroMeta("Mês", cfg.metaLucroMensalCentavos, TextoPrincipal, Modifier.weight(1f))
        }
        Text(
            "${cfg.diasTrabalhoMes} dias por mês · a meta do dia é a barra do Início",
            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
        )
    }
}

/**
 * Os custos fixos do mês numa lista com ícone. Os que vêm do assistente não têm
 * lixeira: apagar não adiantaria, eles voltam no próximo cálculo.
 */
@Composable
private fun CustosFixosCard(
    custos: List<CustoFixo>,
    diasTrabalhoMes: Int,
    onExcluir: (CustoFixo) -> Unit,
    onAdicionar: () -> Unit
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Superficie).padding(horizontal = 14.dp)) {
        Rotulo("Custos fixos do mês", Modifier.padding(top = 14.dp, bottom = 4.dp))
        custos.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SeloIcone(if (c.doAssistente) Icones.Escudo else Icones.Alfinete, if (c.doAssistente) Turquesa else Roxo)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(c.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (c.doAssistente) "do assistente de custo" else "adicionado por você",
                        style = MaterialTheme.typography.labelSmall, color = TextoSecundario
                    )
                }
                Text(
                    buildAnnotatedString {
                        append(c.valorMensalCentavos.emReais().removePrefix("R$ "))
                        withStyle(SpanStyle(fontSize = 11.sp, color = TextoSecundario)) { append("/mês") }
                    },
                    style = EstiloValor, fontSize = 15.sp
                )
                if (!c.doAssistente) {
                    IconButton(onClick = { onExcluir(c) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = TextoSecundario, modifier = Modifier.size(18.dp))
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = Contorno)
        }
        Row(
            Modifier.fillMaxWidth().clickable { onAdicionar() }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeloIcone(Icones.Mais, Roxo)
            Text(
                "Adicionar custo fixo", color = Lima, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        val total = custos.filter { it.ativo }.sumOf { it.valorMensalCentavos }
        if (total > 0) {
            Text(
                "Total ${total.emReais()}/mês · ${custoFixoDiario(total, diasTrabalhoMes).emReais()} por dia trabalhado",
                style = MaterialTheme.typography.bodySmall, color = TextoSecundario,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
    }
}


/**
 * A foto da conta Google, redonda e com borda azul. Baixada uma vez e guardada
 * em cache; sem foto (login por e-mail, sem internet) fica a inicial do nome.
 */
@Composable
private fun FotoDaConta(url: String?, inicial: String) {
    val contexto = LocalContext.current
    var foto by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        foto = withContext(Dispatchers.IO) { baixarFoto(contexto, url) }
    }
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(Lima).border(2.dp, Lima, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = foto
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(), contentDescription = "Foto da conta",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(inicial, color = CorSobreDestaque, style = EstiloValor, fontSize = 22.sp)
        }
    }
}

/** Baixa (ou lê do cache) a foto do perfil. Qualquer falha devolve null e fica a inicial. */
private fun baixarFoto(contexto: android.content.Context, url: String): android.graphics.Bitmap? = runCatching {
    val arquivo = java.io.File(contexto.cacheDir, "foto_conta_${url.hashCode()}.img")
    if (!arquivo.exists()) {
        val conexao = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conexao.responseCode !in 200..299) return null
            conexao.inputStream.use { entrada -> arquivo.outputStream().use { entrada.copyTo(it) } }
        } finally {
            conexao.disconnect()
        }
    }
    android.graphics.BitmapFactory.decodeFile(arquivo.path)
}.getOrNull()
