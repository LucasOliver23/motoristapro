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
import com.motoristapro.service.OfertaAccessibilityService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    val ofertas: ResumoOfertas = ResumoOfertas.VAZIO
) {
    /** Quantas das ofertas lidas hoje viraram corrida (0..100), null sem oferta. */
    val taxaDeAceite: Int?
        get() = if (ofertas.total > 0) ofertas.registradas * 100 / ofertas.total else null

    /** Parte dos custos fixos que cabe a um dia trabalhado. */
    val custoFixoDiaCentavos: Long get() = custoFixoDiario(custoFixoMensalCentavos, config.diasTrabalhoMes)

    /** Lucro depois de descontar a parte do dia dos custos fixos. */
    val lucroRealCentavos: Long get() = resumo.lucroLiquidoCentavos - custoFixoDiaCentavos

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
        }
        .catch { e -> emit(DashboardUiState(carregando = false, erro = "Erro ao ler o banco: ${e.message}")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

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

    /** Religa o GPS de um turno que ficou aberto (ex.: o sistema encerrou o serviço). */
    fun retomarGps() = JornadaService.iniciar(app)

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
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MotoristaApp)
            }
        }
    }
}
