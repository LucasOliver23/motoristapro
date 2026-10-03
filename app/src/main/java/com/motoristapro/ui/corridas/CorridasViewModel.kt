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
import com.motoristapro.ui.componentes.chaveDaMarca
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
enum class ModoLista(val rotulo: String) { OFERTAS("Histórico"), CORRIDAS("Registradas") }

/** Aceitas, recusadas ou tudo. "Aceita" = a oferta virou corrida registrada. */
enum class FiltroAceite(val rotulo: String) { TODAS("Todas"), ACEITAS("Aceitas"), RECUSADAS("Recusadas") }

data class OfertasUiState(
    /** Tudo que o leitor viu no período, sem filtro — a base das contagens. */
    val todas: List<OfertaRecebida> = emptyList(),
    val resumo: ResumoOfertas = ResumoOfertas.VAZIO,
    /** Custo/km do motorista, para o histórico mostrar o lucro de cada oferta. */
    val custoKmCentavos: Long = 0,
    /** null = todas. Guarda a MARCA ("uber", "99", "ifood", "indrive"), não o pacote. */
    val plataforma: String? = null,
    val aceite: FiltroAceite = FiltroAceite.TODAS
) {
    /**
     * As marcas que apareceram no período, para a fila de selos.
     *
     * Por marca e não por pacote: o mesmo aplicativo chega com grafias
     * diferentes conforme a tela que o leitor pegou, e por pacote a fila
     * mostrava dois selos do mesmo app.
     */
    val plataformasVistas: List<String>
        get() = todas.map { chaveDaMarca(it.plataforma) }.distinct().sorted()

    /** A lista já filtrada, que é o que a tela mostra. */
    val ofertas: List<OfertaRecebida>
        get() = todas
            .filter { plataforma == null || chaveDaMarca(it.plataforma) == plataforma }
            .filter {
                when (aceite) {
                    FiltroAceite.TODAS -> true
                    FiltroAceite.ACEITAS -> it.registrada
                    FiltroAceite.RECUSADAS -> !it.registrada
                }
            }

    val qtdAceitas: Int get() = todas.count { it.registrada }
    val qtdRecusadas: Int get() = todas.size - qtdAceitas

    /** Só o que não é leitura absurda entra nas médias do topo. */
    private val validas: List<OfertaRecebida> get() = ofertas.filterNot { it.leituraSuspeita }

    val totalAnalisadoCentavos: Long get() = validas.sumOf { it.valorCentavos }

    val mediaPorCorridaCentavos: Long
        get() = if (validas.isNotEmpty()) totalAnalisadoCentavos / validas.size else 0

    val mediaPorHoraCentavos: Long
        get() {
            val minutos = validas.sumOf { it.minutos }
            return if (minutos > 0) totalAnalisadoCentavos * 60 / minutos else 0
        }

    val mediaPorKmCentavos: Long
        get() {
            val metros = validas.sumOf { it.metros }
            return if (metros > 0) totalAnalisadoCentavos * 1000 / metros else 0
        }

    val qtdSuspeitas: Int get() = ofertas.count { it.leituraSuspeita }

    /** Quantas ofertas a lista está mostrando agora (depois dos filtros). */
    val qtdNaLista: Int get() = ofertas.size
}

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

    // Abre no HISTÓRICO: é a tela que o motorista olha o dia inteiro. A lista
    // de corridas registradas continua a um toque, no chip de cima.
    private val _modo = MutableStateFlow(ModoLista.OFERTAS)
    val modo: StateFlow<ModoLista> = _modo

    private val filtroPlataforma = MutableStateFlow<String?>(null)
    private val filtroAceite = MutableStateFlow(FiltroAceite.TODAS)

    fun filtrarPlataforma(pacote: String?) {
        // Tocar de novo no selo já escolhido volta para "todas".
        filtroPlataforma.value = if (filtroPlataforma.value == pacote) null else pacote
    }

    fun filtrarAceite(f: FiltroAceite) { filtroAceite.value = f }

    val ofertasState: StateFlow<OfertasUiState> = filtro
        .flatMapLatest { f ->
            val (ini, fim) = f.intervalo()
            combine(
                repo.ofertasDoPeriodo(ini, fim),
                repo.resumoOfertas(ini, fim),
                repo.configuracao()
            ) { lista, resumo, cfg ->
                OfertasUiState(lista, resumo, cfg.custoKmCentavos)
            }
        }
        .combine(filtroPlataforma) { estado, p -> estado.copy(plataforma = p) }
        .combine(filtroAceite) { estado, a -> estado.copy(aceite = a) }
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

    /** Apaga uma oferta lida: é assim que se tira uma leitura torta do relatório. */
    fun excluirOferta(id: Long) {
        viewModelScope.launch {
            runCatching { repo.excluirOferta(id) }
                .onSuccess { _mensagens.send("Leitura excluída") }
                .onFailure { _mensagens.send("Erro ao excluir: ${it.message}") }
        }
    }

    /** Apaga de uma vez todas as leituras com R$/km impossível do período filtrado. */
    fun limparSuspeitas() {
        viewModelScope.launch {
            val (ini, fim) = filtro.value.intervalo()
            runCatching { repo.limparOfertasSuspeitas(ini, fim) }
                .onSuccess { quantas ->
                    _mensagens.send(
                        if (quantas > 0) "$quantas leitura(s) com erro excluída(s)"
                        else "Nenhuma leitura com erro neste período"
                    )
                }
                .onFailure { _mensagens.send("Erro ao limpar: ${it.message}") }
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
