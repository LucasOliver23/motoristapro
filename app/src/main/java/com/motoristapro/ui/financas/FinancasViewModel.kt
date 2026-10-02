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
import com.motoristapro.data.local.model.CorridaComPlataforma
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

/**
 * Uma linha do extrato: entrada (corrida) ou saída (despesa).
 *
 * As duas viram o mesmo tipo porque a pergunta do motorista é uma só — "o que
 * entrou e o que saiu neste mês, em ordem" — e até agora a aba só mostrava as
 * saídas, o que dava a impressão de que a corrida registrada não tinha chegado lá.
 */
sealed interface Lancamento {
    val quandoMs: Long
    /** Positivo entra, negativo sai. */
    val valorCentavos: Long

    data class Entrada(val corrida: CorridaComPlataforma) : Lancamento {
        override val quandoMs: Long get() = corrida.corrida.inicioEm
        override val valorCentavos: Long get() = corrida.corrida.receitaCentavos
    }

    data class Saida(val despesa: Despesa) : Lancamento {
        override val quandoMs: Long get() = despesa.dataEm
        override val valorCentavos: Long get() = -despesa.valorCentavos
    }
}

/** O que a lista de lançamentos está mostrando. */
enum class FiltroLancamento(val rotulo: String) { TUDO("Tudo"), ENTRADAS("Entradas"), SAIDAS("Saídas") }

/** Dados do mês escolhido. */
data class MesUiState(
    val mes: YearMonth = YearMonth.now(),
    val resumo: ResumoPeriodo = ResumoPeriodo.VAZIO,
    val porCategoria: List<TotalPorCategoria> = emptyList(),
    val despesas: List<Despesa> = emptyList(),
    val corridas: List<CorridaComPlataforma> = emptyList(),
    /** Custos fixos mensais ativos (seguro, parcela...) — descontados do resultado do mês. */
    val custoFixoMensal: Long = 0,
    val filtro: FiltroLancamento = FiltroLancamento.TUDO
) {
    val lucroRealCentavos: Long get() = resumo.lucroLiquidoCentavos - custoFixoMensal

    /** Entradas e saídas numa linha do tempo só, da mais recente para a mais antiga. */
    val lancamentos: List<Lancamento>
        get() = when (filtro) {
            FiltroLancamento.ENTRADAS -> corridas.map { Lancamento.Entrada(it) }
            FiltroLancamento.SAIDAS -> despesas.map { Lancamento.Saida(it) }
            FiltroLancamento.TUDO ->
                corridas.map { Lancamento.Entrada(it) } + despesas.map { Lancamento.Saida(it) }
        }.sortedByDescending { it.quandoMs }

    val totalEntradasCentavos: Long get() = resumo.faturamentoCentavos
    val totalSaidasCentavos: Long get() = resumo.despesasCentavos
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

    // Declarado aqui de propósito: mesState usa este fluxo no inicializador, e
    // propriedade declarada depois ainda vale null nessa hora.
    private val filtroLancamento = MutableStateFlow(FiltroLancamento.TUDO)

    val mesState: StateFlow<MesUiState> = mesSelecionado
        .flatMapLatest { mes ->
            val (ini, fim) = Periodo.mes(mes)
            combine(
                repo.resumoDoPeriodo(ini, fim),
                repo.despesasPorCategoria(ini, fim),
                repo.despesasDoPeriodo(ini, fim),
                repo.corridasDoPeriodo(ini, fim),
                repo.custoFixoMensal()
            ) { resumo, categorias, despesas, corridas, fixo ->
                MesUiState(mes, resumo, categorias, despesas, corridas, fixo)
            }
        }
        .combine(filtroLancamento) { estado, f -> estado.copy(filtro = f) }
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

    fun filtrarLancamentos(f: FiltroLancamento) { filtroLancamento.value = f }

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
