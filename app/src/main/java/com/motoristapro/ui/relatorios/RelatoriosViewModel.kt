package com.motoristapro.ui.relatorios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.dao.CasaDoMapa
import com.motoristapro.data.local.dao.ResumoOfertas
import com.motoristapro.data.local.dao.TempoConectado
import com.motoristapro.data.local.dao.ResumoPeriodo
import com.motoristapro.data.local.model.ConsumoCombustivel
import com.motoristapro.data.local.model.CustoKmPorCategoria
import com.motoristapro.data.local.model.GanhoPorHora
import com.motoristapro.data.local.model.LucroDiario
import com.motoristapro.data.local.model.ResumoPlataforma
import com.motoristapro.data.local.dao.FaixaHoraria
import com.motoristapro.data.repository.FinanceiroRepository
import com.motoristapro.data.repository.Periodo
import com.motoristapro.data.repository.custoFixoDiario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/**
 * Os quatro períodos do relatório.
 *
 * "Semanal" são os últimos 7 dias, não a semana do calendário: na segunda-feira
 * a semana do calendário tem um dia só, e o relatório ficaria sempre vazio no
 * começo da semana.
 */
enum class PeriodoRelatorio(val rotulo: String, val detalhe: String) {
    DIARIO("Diário", "hoje"),
    SEMANAL("Semanal", "últimos 7 dias"),
    MENSAL("Mensal", "este mês"),
    ANUAL("Anual", "este ano");

    /** Primeiro e último dia (inclusive) do período. */
    fun dias(hoje: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> = when (this) {
        DIARIO -> hoje to hoje
        SEMANAL -> hoje.minusDays(6) to hoje
        MENSAL -> YearMonth.from(hoje).atDay(1) to hoje
        ANUAL -> hoje.withDayOfYear(1) to hoje
    }
}

data class RelatoriosUiState(
    val periodo: PeriodoRelatorio = PeriodoRelatorio.SEMANAL,
    val carregando: Boolean = true,
    val resumo: ResumoPeriodo = ResumoPeriodo.VAZIO,
    /** Um ponto por dia do período, na ordem (dias sem movimento = 0). */
    val lucroPorDia: List<Pair<LocalDate, Long>> = emptyList(),
    val plataformas: List<ResumoPlataforma> = emptyList(),
    val porHora: List<GanhoPorHora> = emptyList(),
    val custoPorCategoria: List<CustoKmPorCategoria> = emptyList(),
    val consumo: ConsumoCombustivel? = null,
    val ofertas: ResumoOfertas = ResumoOfertas.VAZIO,
    /** Faixas de 2 h do dia com R$/km médio das ofertas (as com movimento suficiente). */
    val faixas: List<FaixaHoraria> = emptyList(),
    /** Dias do período com pelo menos uma corrida. */
    val diasTrabalhados: Int = 0,
    val custoFixoMensal: Long = 0,
    val diasTrabalhoMes: Int = 26,
    /** Custo/km que o assistente de custos calculou (para comparar com o real). */
    val custoKmConfigurado: Long = 0,
    /** Tempo de app ligado no período (jornadas, já contando a que está aberta). */
    val conectado: TempoConectado = TempoConectado.VAZIO,
    /** Mapa de calor dia da semana x faixa de 2 h. */
    val mapa: List<CasaDoMapa> = emptyList()
) {
    /** A casa mais clara do mapa — "o seu melhor horário da semana". */
    val melhorCasa: CasaDoMapa? get() = mapa.filter { it.ofertas >= 2 }.maxByOrNull { it.reaisKmCentavos }
        ?: mapa.maxByOrNull { it.reaisKmCentavos }

    /** Tempo conectado em segundos. */
    val segundosConectado: Long get() = conectado.segundos

    /**
     * Tempo conectado SEM corrida: é o número que dói e o que o motorista não vê
     * em nenhum app. Nunca negativo — o tempo das corridas registradas na mão
     * pode cair fora da jornada.
     */
    val segundosParado: Long
        get() = (conectado.segundos - resumo.segundosEmCorrida).coerceAtLeast(0)

    /** Quanto por hora de celular ligado — não por hora em corrida. */
    val ganhoPorHoraConectadoCentavos: Long
        get() = if (conectado.segundos > 0) resumo.faturamentoCentavos * 3600 / conectado.segundos else 0

    /** Fatia do tempo conectado que virou corrida (0..100). */
    val percentualEmCorrida: Int
        get() = if (conectado.segundos > 0)
            (resumo.segundosEmCorrida * 100 / conectado.segundos).toInt().coerceIn(0, 100) else 0

    /** Ofertas que viraram corrida registrada. */
    val corridasAceitas: Int get() = ofertas.registradas

    /** Ofertas lidas que não viraram corrida. */
    val corridasRecusadas: Int get() = ofertas.naoRegistradas

    /** Taxa de aceite (0..100), null quando o leitor não viu oferta nenhuma. */
    val taxaDeAceite: Int?
        get() = if (ofertas.total > 0) ofertas.registradas * 100 / ofertas.total else null

    /** Custo real por km do período: o que foi gasto de verdade ÷ km rodados. */
    val custoRealKmCentavos: Double
        get() = custoPorCategoria.sumOf { it.custoKmCentavos ?: 0.0 }

    /** Custo por km que o assistente de custos previu, para comparar com o real. */
    val custoPlanejadoKmCentavos: Long get() = custoKmConfigurado

    /** Custos fixos rateados pelos dias trabalhados no período. */
    val custoFixoPeriodo: Long get() = custoFixoDiario(custoFixoMensal, diasTrabalhoMes) * diasTrabalhados

    val lucroRealCentavos: Long get() = resumo.lucroLiquidoCentavos - custoFixoPeriodo

    /** As 3 faixas de horário que mais pagam por km. */
    val melhoresFaixas: List<FaixaHoraria>
        get() = faixas.sortedByDescending { it.reaisPorKmCentavos }.take(3)

    /** As 3 piores — tão úteis quanto as melhores: são as horas de ficar em casa. */
    val pioresFaixas: List<FaixaHoraria>
        get() = faixas.sortedBy { it.reaisPorKmCentavos }
            .take(3)
            .filterNot { pior -> melhoresFaixas.any { it.faixa == pior.faixa } }

    /** Hora do dia com maior R$/h em corrida (mínimo 2 corridas para evitar ruído). */
    val melhorHora: GanhoPorHora?
        get() = porHora.filter { it.qtdCorridas >= 2 }.maxByOrNull { it.ganhoPorHoraCentavos }
            ?: porHora.maxByOrNull { it.ganhoPorHoraCentavos }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RelatoriosViewModel(private val repo: FinanceiroRepository) : ViewModel() {

    private val periodo = MutableStateFlow(PeriodoRelatorio.SEMANAL)

    val uiState: StateFlow<RelatoriosUiState> = periodo
        .flatMapLatest { p ->
            val (primeiro, ultimo) = p.dias()
            val ini = Periodo.dia(primeiro).first
            val fim = Periodo.dia(ultimo).second

            val base = combine(
                repo.resumoDoPeriodo(ini, fim),
                repo.lucroDiario(ini, fim),
                repo.resumoPorPlataforma(ini, fim),
                repo.ganhoPorHoraDoDia(ini, fim),
                repo.custoKmPorCategoria(ini, fim)
            ) { resumo: ResumoPeriodo, diario: List<LucroDiario>, plats: List<ResumoPlataforma>,
                horas: List<GanhoPorHora>, custos: List<CustoKmPorCategoria> ->
                val porDia = diario.associate { it.dia to it.lucroLiquidoCentavos }
                val serie = generateSequence(primeiro) { d -> d.plusDays(1).takeIf { !it.isAfter(ultimo) } }
                    .map { d -> d to (porDia[d.toString()] ?: 0L) }
                    .toList()
                RelatoriosUiState(
                    periodo = p, carregando = false, resumo = resumo, lucroPorDia = serie,
                    plataformas = plats, porHora = horas, custoPorCategoria = custos,
                    diasTrabalhados = diario.count { it.qtdCorridas > 0 }
                )
            }
            val consumo = flow<ConsumoCombustivel?> { emit(repo.consumoCombustivel(ini, fim)) }
                .catch { emit(null) }
            val extras = combine(repo.resumoOfertas(ini, fim), repo.custoFixoMensal(), repo.configuracao()) { of, fixo, cfg ->
                Extras(of, fixo, cfg.diasTrabalhoMes, cfg.custoKmCentavos)
            }
            base.combine(consumo) { estado, c -> estado.copy(consumo = c) }
                .combine(extras) { estado, x ->
                    estado.copy(
                        ofertas = x.ofertas, custoFixoMensal = x.custoFixoMensal,
                        diasTrabalhoMes = x.diasTrabalhoMes, custoKmConfigurado = x.custoKmCentavos
                    )
                }
                .combine(repo.faixasHorarias(ini, fim)) { estado, fx -> estado.copy(faixas = fx) }
                .combine(tempoConectadoAoVivo(ini, fim)) { estado, t -> estado.copy(conectado = t) }
                .combine(repo.mapaDeCalor(ini, fim)) { estado, m -> estado.copy(mapa = m) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RelatoriosUiState())

    /**
     * Tempo conectado se refazendo de minuto em minuto.
     *
     * A consulta recebe o "agora" como parâmetro para contar a jornada que ainda
     * está aberta — e um parâmetro fixo congelaria o número do dia de hoje até
     * algo mudar no banco. De minuto em minuto basta: ninguém olha o relatório
     * esperando o segundo virar.
     */
    private fun tempoConectadoAoVivo(ini: Long, fim: Long): Flow<TempoConectado> =
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(60_000)
            }
        }.flatMapLatest { agora -> repo.tempoConectado(ini, fim, agora) }

    fun selecionar(p: PeriodoRelatorio) { periodo.value = p }

    /** Só para não carregar uma Triple de quatro coisas. */
    private data class Extras(
        val ofertas: ResumoOfertas,
        val custoFixoMensal: Long,
        val diasTrabalhoMes: Int,
        val custoKmCentavos: Long
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MotoristaApp
                RelatoriosViewModel(app.repository)
            }
        }
    }
}
