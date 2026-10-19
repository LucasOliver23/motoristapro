package com.motoristapro.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.dao.ResumoOfertas
import com.motoristapro.data.local.dao.ResumoPeriodo
import com.motoristapro.data.local.dao.TotaisJornada
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.local.entity.Plataforma
import com.motoristapro.data.local.model.CustoPorKm
import com.motoristapro.data.repository.Periodo
import com.motoristapro.data.repository.custoFixoDiario
import com.motoristapro.data.repository.emReais
import com.motoristapro.jornada.JornadaService
import com.motoristapro.pacote.ConfigPacote
import com.motoristapro.pacote.PacoteTaxa
import com.motoristapro.service.OfertaAccessibilityService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.ui.custo.paraDados
import com.motoristapro.ui.manutencao.DadosKm
import com.motoristapro.ui.manutencao.maisUrgente
import com.motoristapro.veiculo.OrigemOdometro
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Tudo que a aba Início precisa. Dinheiro em centavos. */
data class DashboardUiState(
    val carregando: Boolean = true,
    val erro: String? = null,
    val dia: LocalDate = LocalDate.now(),
    val resumo: ResumoPeriodo = ResumoPeriodo.VAZIO,
    val custoKmRealCentavos: Double? = null,
    val config: Configuracao = Configuracao(),
    val jornada: Jornada? = null,
    val jornadasEncerradas: TotaisJornada = TotaisJornada(0, 0),
    /** Soma mensal dos custos fixos ativos (centavos). */
    val custoFixoMensalCentavos: Long = 0,
    /** Ofertas que o leitor analisou hoje — base da taxa de aceite. */
    val ofertas: ResumoOfertas = ResumoOfertas.VAZIO,
    /** Pacote 0% de taxa da 99 escolhido no Menu. */
    val pacote: ConfigPacote = ConfigPacote(),
    /** Compras do pacote lançadas hoje (já estão dentro das despesas do resumo). */
    val pacoteHojeCentavos: Long = 0,
    /** Corridas da 99 de hoje — base do "o pacote valeu a pena". */
    val ganhos99HojeCentavos: Long = 0,
    /** Horas por dia de Menu ▸ Suas metas (ou do assistente, ou 8). */
    val horasPorDia: Int = 8
) {
    /** Taxa evitada menos a parte do pacote no dia; null sem pacote ou sem corrida da 99. */
    val economiaPacoteCentavos: Long?
        get() = if (pacote.ativo && ganhos99HojeCentavos > 0)
            PacoteTaxa.economiaDoDiaCentavos(pacote.plano, ganhos99HojeCentavos) else null

    /** Quantas das ofertas lidas hoje viraram corrida (0..100), null sem oferta. */
    val taxaDeAceite: Int?
        get() = if (ofertas.total > 0) ofertas.registradas * 100 / ofertas.total else null

    /** Parte dos custos fixos que cabe a um dia trabalhado. */
    val custoFixoDiaCentavos: Long get() = custoFixoDiario(custoFixoMensalCentavos, config.diasTrabalhoMes)

    /** Lucro depois de descontar a parte do dia dos custos fixos. */
    val lucroRealCentavos: Long get() = resumo.lucroLiquidoCentavos - custoFixoDiaCentavos

    /** Lucro por hora que bate a meta do dia; 0 sem meta. */
    val metaPorHoraCentavos: Long
        get() = if (config.metaLucroDiarioCentavos > 0 && horasPorDia > 0)
            Math.round(config.metaLucroDiarioCentavos.toDouble() / horasPorDia) else 0

    val progressoMeta: Float
        get() = if (config.metaLucroDiarioCentavos > 0)
            (resumo.lucroLiquidoCentavos.toFloat() / config.metaLucroDiarioCentavos).coerceIn(0f, 1f) else 0f

    /** Segundos trabalhados hoje (turnos encerrados + turno aberto até [agora]). */
    fun segundosTrabalhados(agora: Long): Long =
        jornadasEncerradas.segundos + (jornada?.let { ((agora - it.inicioEm) / 1000).coerceAtLeast(0) } ?: 0)

    /** Metros medidos pelo GPS hoje. */
    val metrosGpsHoje: Long get() = jornadasEncerradas.metros + (jornada?.metrosGps ?: 0)

    /** Gasto estimado de combustível com o km do GPS (centavos). null sem veículo configurado. */
    val gastoCombustivelGpsCentavos: Long?
        get() = config.custoCombustivelKmCentavos?.let { Math.round(metrosGpsHoje / 1000.0 * it) }

    /** R$ por hora TRABALHADA (turno), não só em corrida. */
    fun ganhoPorHoraTrabalhadaCentavos(agora: Long): Long {
        val s = segundosTrabalhados(agora)
        return if (s > 60) resumo.faturamentoCentavos * 3600 / s else 0
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val app: MotoristaApp) : ViewModel() {

    private val repo = app.repository

    val uiState: StateFlow<DashboardUiState> = Periodo.hoje()
        .flatMapLatest { dia ->
            val (inicio, fim) = Periodo.dia(dia)
            combine(
                repo.resumoDoPeriodo(inicio, fim),
                repo.custoPorKmDoMes(YearMonth.from(dia)),
                repo.configuracao(),
                repo.jornadaAtiva(),
                repo.totaisJornadasEncerradas(inicio, fim)
            ) { resumo: ResumoPeriodo, custo: CustoPorKm, cfg: Configuracao, jornada: Jornada?, totais: TotaisJornada ->
                DashboardUiState(
                    carregando = false,
                    dia = dia,
                    resumo = resumo,
                    custoKmRealCentavos = custo.custoKmCentavos?.takeIf { it.isFinite() },
                    config = cfg,
                    jornada = jornada,
                    jornadasEncerradas = totais
                )
            }.combine(repo.custoFixoMensal()) { estado, fixo -> estado.copy(custoFixoMensalCentavos = fixo) }
                .combine(repo.resumoOfertas(inicio, fim)) { estado, of -> estado.copy(ofertas = of) }
                .combine(app.preferencias.pacote) { estado, p -> estado.copy(pacote = p) }
                .combine(repo.pacoteDoPeriodo(inicio, fim)) { estado, v -> estado.copy(pacoteHojeCentavos = v) }
                .combine(repo.ganhos99DoPeriodo(inicio, fim)) { estado, v -> estado.copy(ganhos99HojeCentavos = v) }
                .combine(
                    combine(app.preferencias.horasPorDia, repo.perfilCusto()) { h, perfil -> h ?: perfil?.horasPorDia ?: 8 }
                ) { estado, h -> estado.copy(horasPorDia = h) }
        }
        .catch { e -> emit(DashboardUiState(carregando = false, erro = "Erro ao ler o banco: ${e.message}")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /**
     * Dias seguidos batendo a meta, olhando os últimos [DIAS_DE_HISTORICO] dias.
     * Só a tela de comemoração usa; se o banco falhar, ela aparece sem o cartão do fogo.
     */
    val sequencia: StateFlow<SequenciaMeta?> = Periodo.hoje()
        .flatMapLatest { dia ->
            val inicio = Periodo.dia(dia.minusDays(DIAS_DE_HISTORICO)).first
            val fim = Periodo.dia(dia).second
            combine(repo.lucroDiario(inicio, fim), repo.configuracao()) { dias, cfg ->
                SequenciaMeta.calcular(
                    dias.associate { LocalDate.parse(it.dia) to it.lucroLiquidoCentavos },
                    cfg.metaLucroDiarioCentavos,
                    dia
                ) as SequenciaMeta?
            }
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val leitorConectado: StateFlow<Boolean> = OfertaAccessibilityService.conectado
    val gpsRodando: StateFlow<Boolean> = JornadaService.rodando

    val plataformas: StateFlow<List<Plataforma>> = repo.plataformasAtivas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mensagens = Channel<String>(Channel.BUFFERED)
    val mensagens: Flow<String> = _mensagens.receiveAsFlow()

    // ================================================================== jornada

    /** Abre o turno no banco e liga o GPS (se [comGps], ou seja, permissão concedida). */
    fun iniciarJornada(comGps: Boolean) {
        viewModelScope.launch {
            runCatching { repo.iniciarJornada() }
                .onSuccess {
                    if (comGps) JornadaService.iniciar(app)
                    _mensagens.send(if (comGps) "Turno iniciado • GPS medindo os km" else "Turno iniciado (sem GPS)")
                }
                .onFailure { _mensagens.send("Erro ao iniciar turno: ${it.message}") }
        }
    }

    /** O que a pergunta do km do painel mostra (início ou fim da jornada). */
    suspend fun dadosKm(encerrando: Boolean): DadosKm {
        val perfil = runCatching { repo.obterPerfilCusto() }.getOrNull()?.paraDados()
        val cfg = runCatching { repo.obterConfiguracao() }.getOrNull()
        return DadosKm(
            atual = app.odometro.atual(),
            inicio = if (encerrando) app.odometro.leituraDoInicio() else null,
            urgente = maisUrgente(runCatching { repo.manutencoes().first() }.getOrDefault(emptyList())),
            custoKmCentavos = cfg?.custoKmCentavos ?: 0,
            tipo = perfil?.tipoVeiculo ?: TipoVeiculo.MOTO,
            nomeVeiculo = cfg?.veiculoNome?.takeIf { it.isNotBlank() } ?: perfil?.veiculoNome?.takeIf { it.isNotBlank() }
        )
    }

    fun registrarKm(km: Long, encerrando: Boolean) =
        app.odometro.registrar(km, if (encerrando) OrigemOdometro.FIM else OrigemOdometro.INICIO)

    /** Religa o GPS de um turno que ficou aberto (ex.: o sistema encerrou o serviço). */
    fun retomarGps() = JornadaService.iniciar(app)

    /** Pausar/retomar o turno sem encerrar: o almoço não conta como hora rodada. */
    fun alternarPausa() {
        viewModelScope.launch {
            runCatching {
                val j = repo.obterJornadaAtiva() ?: return@runCatching
                if (j.pausada) repo.retomarJornada(j.id) else repo.pausarJornada(j.id)
            }.onFailure { _mensagens.send("Erro ao pausar: ${it.message}") }
        }
    }

    fun encerrarJornada() {
        viewModelScope.launch {
            if (JornadaService.rodando.value) {
                JornadaService.parar(app)       // o serviço grava km + fim
            } else {
                val j = uiState.value.jornada ?: return@launch
                runCatching { repo.encerrarJornada(j.id) }
                    .onFailure { _mensagens.send("Erro ao encerrar: ${it.message}") }
            }
            _mensagens.send("Turno encerrado")
        }
    }

    // ================================================================== registros rápidos

    fun salvarDespesa(d: Despesa) {
        viewModelScope.launch {
            runCatching { repo.registrarDespesa(d) }
                .onSuccess { _mensagens.send("${d.categoria.rotulo} de ${d.valorCentavos.emReais()} salvo") }
                .onFailure { _mensagens.send("Erro ao salvar: ${it.message}") }
        }
    }

    fun salvarCorrida(c: Corrida) {
        viewModelScope.launch {
            runCatching { repo.registrarCorrida(c) }
                .onSuccess { _mensagens.send("Corrida de ${c.receitaCentavos.emReais()} registrada") }
                .onFailure { _mensagens.send("Erro ao salvar: ${it.message}") }
        }
    }

    fun testarLeitor() {
        val s = OfertaAccessibilityService.instancia
        if (s == null) viewModelScope.launch { _mensagens.send("Leitor de ofertas não está conectado") }
        else s.mostrarTeste()
    }

    companion object {
        private const val DIAS_DE_HISTORICO = 120L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MotoristaApp)
            }
        }
    }
}
