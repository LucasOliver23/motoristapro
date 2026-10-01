package com.motoristapro.ui.relatorios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.dao.ResumoOfertas
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

enum class PeriodoRelatorio(val rotulo: String) {
    SETE_DIAS("7 dias"), MES("Este mês"), TRINTA_DIAS("30 dias");

    /** Primeiro e último dia (inclusive) do período. */
    fun dias(hoje: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> = when (this) {
        SETE_DIAS -> hoje.minusDays(6) to hoje
        MES -> YearMonth.from(hoje).atDay(1) to hoje
        TRINTA_DIAS -> hoje.minusDays(29) to hoje
    }
}

data class RelatoriosUiState(
    val periodo: PeriodoRelatorio = PeriodoRelatorio.SETE_DIAS,
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
    val diasTrabalhoMes: Int = 26
) {
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

    private val periodo = MutableStateFlow(PeriodoRelatorio.SETE_DIAS)

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
                Triple(of, fixo, cfg.diasTrabalhoMes)
            }
            base.combine(consumo) { estado, c -> estado.copy(consumo = c) }
                .combine(extras) { estado, (of, fixo, dias) ->
                    estado.copy(ofertas = of, custoFixoMensal = fixo, diasTrabalhoMes = dias)
                }
                .combine(repo.faixasHorarias(ini, fim)) { estado, fx -> estado.copy(faixas = fx) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RelatoriosUiState())

    fun selecionar(p: PeriodoRelatorio) { periodo.value = p }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MotoristaApp
                RelatoriosViewModel(app.repository)
            }
        }
    }
}
