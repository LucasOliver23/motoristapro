package com.motoristapro.ui.corridas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.motoristapro.MotoristaApp
import com.motoristapro.data.local.dao.ResumoOfertas
import com.motoristapro.data.local.dao.ResumoPeriodo
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.OfertaRecebida
import com.motoristapro.data.local.entity.Plataforma
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
import java.time.LocalDate
import java.time.YearMonth

enum class FiltroPeriodo(val rotulo: String) {
    HOJE("Hoje"), SEMANA("Semana"), MES("Mês"), TRINTA_DIAS("30 dias");

    fun intervalo(hoje: LocalDate = LocalDate.now()): Pair<Long, Long> = when (this) {
        HOJE -> Periodo.dia(hoje)
        SEMANA -> Periodo.semana(hoje)
        MES -> Periodo.mes(YearMonth.from(hoje))
        TRINTA_DIAS -> Periodo.ultimosDias(30, hoje)
    }
}

/** O que a aba mostra: corridas registradas ou todas as ofertas recebidas. */
enum class ModoLista(val rotulo: String) { CORRIDAS("Corridas"), OFERTAS("Ofertas recebidas") }

data class OfertasUiState(
    val ofertas: List<OfertaRecebida> = emptyList(),
    val resumo: ResumoOfertas = ResumoOfertas.VAZIO
)

data class CorridasUiState(
    val filtro: FiltroPeriodo = FiltroPeriodo.HOJE,
    val corridas: List<CorridaComPlataforma> = emptyList(),
    val resumo: ResumoPeriodo = ResumoPeriodo.VAZIO,
    val carregando: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class CorridasViewModel(private val repo: FinanceiroRepository) : ViewModel() {

    private val filtro = MutableStateFlow(FiltroPeriodo.HOJE)

    val uiState: StateFlow<CorridasUiState> = filtro
        .flatMapLatest { f ->
            val (ini, fim) = f.intervalo()
            combine(repo.corridasDoPeriodo(ini, fim), repo.resumoDoPeriodo(ini, fim)) { lista, resumo ->
                CorridasUiState(filtro = f, corridas = lista, resumo = resumo, carregando = false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CorridasUiState())

    val plataformas: StateFlow<List<Plataforma>> = repo.plataformasAtivas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mensagens = Channel<String>(Channel.BUFFERED)
    val mensagens: Flow<String> = _mensagens.receiveAsFlow()

    private val _modo = MutableStateFlow(ModoLista.CORRIDAS)
    val modo: StateFlow<ModoLista> = _modo

    val ofertasState: StateFlow<OfertasUiState> = filtro
        .flatMapLatest { f ->
            val (ini, fim) = f.intervalo()
            combine(repo.ofertasDoPeriodo(ini, fim), repo.resumoOfertas(ini, fim)) { lista, resumo ->
                OfertasUiState(lista, resumo)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfertasUiState())

    fun selecionar(f: FiltroPeriodo) { filtro.value = f }

    fun selecionarModo(m: ModoLista) { _modo.value = m }

    fun salvar(c: Corrida) {
        viewModelScope.launch {
            runCatching { if (c.id == 0L) repo.registrarCorrida(c) else repo.atualizarCorrida(c) }
                .onSuccess { _mensagens.send("Corrida de ${c.receitaCentavos.emReais()} salva") }
                .onFailure { _mensagens.send("Erro ao salvar: ${it.message}") }
        }
    }

    fun excluir(id: Long) {
        viewModelScope.launch {
            runCatching { repo.excluirCorrida(id) }
                .onSuccess { _mensagens.send("Corrida excluída") }
                .onFailure { _mensagens.send("Erro ao excluir: ${it.message}") }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MotoristaApp
                CorridasViewModel(app.repository)
            }
        }
    }
}
