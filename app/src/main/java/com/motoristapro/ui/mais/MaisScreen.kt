package com.motoristapro.ui.mais

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motoristapro.MotoristaApp
import com.motoristapro.assinatura.SituacaoAcesso
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.repository.emReais
import com.motoristapro.jornada.Notificacoes
import com.motoristapro.service.AppNavegacao
import com.motoristapro.service.EstiloCartao
import com.motoristapro.ui.NavegacaoRapida
import com.motoristapro.ui.abrirConfigAcessibilidade
import com.motoristapro.ui.abrirDetalhesDoApp
import com.motoristapro.ui.abrirInicializacaoAutomatica
import com.motoristapro.ui.assinatura.AssinaturaScreen
import com.motoristapro.ui.componentes.CamadaTelaCheia
import com.motoristapro.ui.componentes.CardPreparacao
import com.motoristapro.ui.componentes.ConfirmarExclusao
import com.motoristapro.ui.componentes.DialogoConfirmacao
import com.motoristapro.ui.componentes.Liberacao
import com.motoristapro.ui.componentes.SubTelaHost
import com.motoristapro.ui.componentes.TelaAba
import com.motoristapro.ui.componentes.bateriaLiberada
import com.motoristapro.ui.componentes.pedirBateriaLivre
import com.motoristapro.ui.custo.AssistenteCustoScreen
import com.motoristapro.ui.manutencao.ManutencaoConteudo
import com.motoristapro.ui.manutencao.VeiculoResumo
import com.motoristapro.ui.manutencao.maisUrgente
import com.motoristapro.ui.manutencao.textoDaManutencao
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.VermelhoPrejuizo
import java.time.LocalDate
import java.util.Locale

private val PT = Locale("pt", "BR")

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
    val odometroLido by vm.odometro.collectAsStateWithLifecycle()
    val avisoManutencao by appPrefs.avisoManutencaoKm.collectAsStateWithLifecycle()
    val repoApp = (LocalContext.current.applicationContext as com.motoristapro.MotoristaApp).repository
    val listaVeiculos by remember { repoApp.veiculos() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val idAtivo by appPrefs.veiculoAtivo.collectAsStateWithLifecycle()
    val veiculoAtivoObj = listaVeiculos.firstOrNull { it.id == idAtivo }
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
                        titulo = "Melhores horários",
                        icone = "⏰",
                        estado = "quando suas corridas pagam mais",
                        onClick = { sub = SubTela.HORARIOS }
                    ),
                    Ferramenta(
                        titulo = "Radar de eventos",
                        icone = "📡",
                        estado = "jogos, shows e festas com a hora de saída",
                        onClick = { sub = SubTela.RADAR }
                    ),
                    Ferramenta(
                        titulo = "Comparar apps",
                        icone = "⚖",
                        estado = "qual plataforma paga mais para você",
                        onClick = { sub = SubTela.COMPARAR }
                    ),
                    Ferramenta(
                        titulo = "Relatório do mês",
                        icone = "📄",
                        estado = "PDF para imposto, banco ou financiamento",
                        onClick = { sub = SubTela.RELATORIO }
                    ),
                    Ferramenta(
                        titulo = "Meus veículos",
                        icone = "🚗",
                        estado = veiculoAtivoObj?.let { a ->
                            if (listaVeiculos.size > 1) "${a.nome} ativo · ${listaVeiculos.size} veículos" else a.nome
                        } ?: "moto e carro, cada um com seu km",
                        onClick = { sub = SubTela.VEICULOS }
                    ),
                    Ferramenta(
                        titulo = "Manutenção",
                        icone = "🔧",
                        estado = when {
                            manutencoes.isEmpty() -> "óleo, pneus e revisão por km"
                            odometro <= 0L -> "informe o km do painel"
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
        DialogoConfirmacao(
            titulo = "Baixar da nuvem?",
            texto = "Os dados deste celular serão SUBSTITUÍDOS pelo backup da sua conta na nuvem. " +
                "Use isto ao trocar de celular ou reinstalar o app.",
            rotuloConfirmar = "Baixar e substituir",
            corConfirmar = VermelhoPrejuizo,
            onDismiss = { confirmarBaixarNuvem = false },
            onConfirmar = { confirmarBaixarNuvem = false; vm.baixarDaNuvem() }
        )
    }
    if (confirmarZerar) {
        DialogoConfirmacao(
            titulo = "Começar do zero?",
            texto = "Apaga deste celular as corridas, despesas, turnos, ofertas, custos fixos, " +
                "o custo por km, as metas e as faixas do semáforo — como se o app tivesse " +
                "acabado de ser instalado.\n\n" +
                "O backup na sua conta na nuvem NÃO é apagado: dá para trazer tudo de volta " +
                "em \"Baixar da nuvem\".",
            rotuloConfirmar = "Apagar e começar do zero",
            corConfirmar = VermelhoPrejuizo,
            onDismiss = { confirmarZerar = false },
            onConfirmar = { confirmarZerar = false; vm.zerarCelular() }
        )
    }
    if (confirmarSair) {
        DialogoConfirmacao(
            titulo = "Sair da conta?",
            texto = "Seus dados continuam neste celular e também na nuvem. Você pode entrar de novo quando quiser.",
            rotuloConfirmar = "Sair",
            onDismiss = { confirmarSair = false },
            onConfirmar = { confirmarSair = false; vm.sairDaConta() }
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
                    SubTela.LEITOR -> LeitorConteudo(
                        leitorOk = leitorOk,
                        bolha = bolha,
                        ocr = ocr,
                        diagnostico = diagnostico,
                        onTestar = vm::testarLeitor,
                        onTestarPrint = { sub = SubTela.TESTE_PRINT },
                        onAcessibilidade = { context.abrirConfigAcessibilidade() },
                        onBolha = vm::definirBolha,
                        onOcr = vm::definirOcr,
                        onDetalhesDoApp = { context.abrirDetalhesDoApp() }
                    )
                    SubTela.TESTE_PRINT -> TestarPrintScreen(
                        custoKmCentavos = cfg.custoKmCentavos,
                        tarifaMinimaCentavos = cfg.tarifaMinimaCentavos
                    )
                    SubTela.NUVEM -> {
                    if (vm.loginDisponivel && usuario != null) {
                        NuvemConteudo(
                            nuvem = nuvem,
                            onEnviar = vm::enviarParaNuvem,
                            onBaixar = { confirmarBaixarNuvem = true },
                            onSair = { confirmarSair = true },
                            onZerar = { confirmarZerar = true }
                        )
                    }
                    }
                    SubTela.BACKUP -> BackupConteudo(
                        ocupado = ocupado,
                        onBackup = { criarBackup.launch("motoristapro-backup-$hoje.db") },
                        onRestaurar = { confirmarRestauracao = true },
                        onCsvCorridas = { criarCsvCorridas.launch("corridas-$hoje.csv") },
                        onCsvDespesas = { criarCsvDespesas.launch("despesas-$hoje.csv") },
                        onCsvOfertas = { criarCsvOfertas.launch("ofertas-$hoje.csv") }
                    )
                    // Tratada antes, fora desta moldura (ela rola por conta propria).
                    SubTela.ASSINATURA -> Unit
                    SubTela.NAVEGACAO -> AppNavegacaoScreen()
                    SubTela.PACOTE -> PacoteTaxaScreen(onSalvo = { sub = null })
                    SubTela.HORARIOS -> MelhoresHorariosScreen()
                    SubTela.RADAR -> RadarScreen(cidade = cfg.cidade)
                    SubTela.COMPARAR -> CompararAppsScreen()
                    SubTela.RELATORIO -> RelatorioMesScreen()
                    SubTela.VEICULOS -> VeiculosScreen()
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
                        odometro = odometroLido,
                        veiculo = VeiculoResumo(
                            id = veiculoAtivoObj?.id ?: 1L,
                            tipo = veiculoAtivoObj?.tipo?.let { t -> runCatching { TipoVeiculo.valueOf(t) }.getOrNull() }
                                ?: dadosCusto?.tipoVeiculo ?: TipoVeiculo.MOTO,
                            nome = veiculoAtivoObj?.nome ?: cfg.veiculoNome?.takeIf { it.isNotBlank() }
                                ?: dadosCusto?.veiculoNome?.takeIf { it.isNotBlank() },
                            detalhe = listOfNotNull(
                                (veiculoAtivoObj?.tipo?.let { t -> runCatching { TipoVeiculo.valueOf(t) }.getOrNull() }
                                    ?: dadosCusto?.tipoVeiculo ?: TipoVeiculo.MOTO).rotulo,
                                dadosCusto?.combustivel?.rotulo?.lowercase(PT),
                                cfg.consumoKmLx100.takeIf { it > 0 }?.let { String.format(PT, "%.1f km/L", it / 100.0) }
                            ).joinToString(" · ")
                        ),
                        onSalvar = vm::salvarManutencao,
                        onExcluir = vm::excluirManutencao,
                        onCorrigirKm = vm::corrigirOdometro,
                        onEditarVeiculo = { assistenteAberto = true },
                        avisoKm = avisoManutencao,
                        onAvisoKm = appPrefs::definirAvisoManutencaoKm
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
        DialogoConfirmacao(
            titulo = "Restaurar backup?",
            texto = "Todos os dados atuais deste celular serão SUBSTITUÍDOS pelos do backup escolhido. " +
                "Se tiver dúvida, faça um backup antes.",
            rotuloConfirmar = "Escolher arquivo",
            corConfirmar = VermelhoPrejuizo,
            onDismiss = { confirmarRestauracao = false },
            onConfirmar = {
                confirmarRestauracao = false
                abrirBackup.launch(arrayOf("*/*"))
            }
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
