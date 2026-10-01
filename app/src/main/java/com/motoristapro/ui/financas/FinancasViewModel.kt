package com.motoristapro.ui.financas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.dao.ResumoPeriodo
import com.motoristapro.data.local.dao.TotalPorCategoria
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.repository.FinanceiroRepository
import com.motoristapro.data.repository.Periodo
import com.motoristapro.data.repository.emReais
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Dados do mês escolhido. */
data class MesUiState(
    val mes: YearMonth = YearMonth.now(),
    val resumo: ResumoPeriodo = ResumoPeriodo.VAZIO,
    val porCategoria: List<TotalPorCategoria> = emptyList(),
    val despesas: List<Despesa> = emptyList(),
    /** Custos fixos mensais ativos (seguro, parcela...) — descontados do resultado do mês. */
    val custoFixoMensal: Long = 0
) {
    val lucroRealCentavos: Long get() = resumo.lucroLiquidoCentavos - custoFixoMensal
}

/** Metas sempre em relação a hoje / esta semana / este mês. */
data class MetasUiState(
    val hoje: Long = 0,
    val semana: Long = 0,
    val mes: Long = 0,
    val config: Configuracao = Configuracao()
)

@OptIn(ExperimentalCoroutinesApi::class)
class FinancasViewModel(private val repo: FinanceiroRepository) : ViewModel() {

    private val mesSelecionado = MutableStateFlow(YearMonth.now())

    val mesState: StateFlow<MesUiState> = mesSelecionado
        .flatMapLatest { mes ->
            val (ini, fim) = Periodo.mes(mes)
            combine(
                repo.resumoDoPeriodo(ini, fim),
                repo.despesasPorCategoria(ini, fim),
                repo.despesasDoPeriodo(ini, fim),
                repo.custoFixoMensal()
            ) { resumo, categorias, despesas, fixo -> MesUiState(mes, resumo, categorias, despesas, fixo) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MesUiState())

    val metasState: StateFlow<MetasUiState> = Periodo.hoje()
        .flatMapLatest { dia ->
            val (iniDia, fimDia) = Periodo.dia(dia)
            combine(
                repo.resumoDoPeriodo(iniDia, fimDia),
                repo.resumoDaSemana(dia),
                repo.resumoDoMes(YearMonth.from(dia)),
                repo.configuracao()
            ) { h, s, m, cfg ->
                MetasUiState(h.lucroLiquidoCentavos, s.lucroLiquidoCentavos, m.lucroLiquidoCentavos, cfg)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetasUiState())

    private val _mensagens = Channel<String>(Channel.BUFFERED)
    val mensagens: Flow<String> = _mensagens.receiveAsFlow()

    fun mesAnterior() { mesSelecionado.value = mesSelecionado.value.minusMonths(1) }

    fun proximoMes() {
        val prox = mesSelecionado.value.plusMonths(1)
        if (!prox.isAfter(YearMonth.now())) mesSelecionado.value = prox
    }

    fun salvar(d: Despesa) {
        viewModelScope.launch {
            runCatching { if (d.id == 0L) repo.registrarDespesa(d) else repo.atualizarDespesa(d) }
                .onSuccess { _mensagens.send("${d.categoria.rotulo} de ${d.valorCentavos.emReais()} salvo") }
                .onFailure { _mensagens.send("Erro ao salvar: ${it.message}") }
        }
    }

    fun excluir(id: Long) {
        viewModelScope.launch {
            runCatching { repo.excluirDespesa(id) }
                .onSuccess { _mensagens.send("Despesa excluída") }
                .onFailure { _mensagens.send("Erro ao excluir: ${it.message}") }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MotoristaApp
                FinancasViewModel(app.repository)
            }
        }
    }
}
