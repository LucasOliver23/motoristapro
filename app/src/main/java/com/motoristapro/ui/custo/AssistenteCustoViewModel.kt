package com.motoristapro.ui.custo

import androidx.lifecycle.ViewModel
import com.motoristapro.data.prefs.PreferenciasApp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.motoristapro.MotoristaApp
import com.motoristapro.custo.CalculadoraCusto
import com.motoristapro.custo.DadosCusto
import com.motoristapro.custo.FormaAquisicao
import com.motoristapro.custo.ResultadoCusto
import com.motoristapro.custo.TipoCombustivel
import com.motoristapro.custo.TipoTrabalho
import com.motoristapro.custo.TipoVeiculo
import com.motoristapro.data.local.entity.PerfilCusto
import com.motoristapro.data.repository.FinanceiroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Os cinco passos do assistente, na ordem. [titulo] é a pergunta grande do
 * passo; [rotulo] é o nome curto que vai embaixo do ícone na fila de passos.
 */
enum class PassoCusto(val titulo: String, val rotulo: String) {
    VEICULO("Qual é o seu veículo?", "Veículo"),
    FIXOS("Custos fixos", "Fixos"),
    MANUTENCAO("Manutenção", "Manutenção"),
    COMBUSTIVEL("Combustível", "Combustível"),
    METAS("Quanto você roda", "Metas")
}

data class EstadoAssistente(
    val carregando: Boolean = true,
    val passo: PassoCusto = PassoCusto.VEICULO,
    val dados: DadosCusto = DadosCusto(),
    /** Preenchido ao tocar em "Calcular custo". null = ainda no formulário. */
    val resultado: ResultadoCusto? = null,
    /** Avisos que o motorista ainda não confirmou (valor que parece digitado errado). */
    val avisosPendentes: Boolean = false,
    val aplicado: Boolean = false
) {
    val indicePasso: Int get() = PassoCusto.entries.indexOf(passo)
    val ultimoPasso: Boolean get() = passo == PassoCusto.entries.last()
}

/**
 * Guarda as respostas do assistente e devolve o custo real por km.
 *
 * A conta em si vive em [CalculadoraCusto] — Kotlin puro e testado. Aqui só
 * tem estado de tela e a gravação no banco.
 */
class AssistenteCustoViewModel(
    private val repo: FinanceiroRepository,
    private val prefs: PreferenciasApp? = null,
    /** Depois de aplicar: o veículo ativo guarda o custo novo (v14). */
    private val aoAplicar: suspend () -> Unit = {}
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoAssistente())
    val estado: StateFlow<EstadoAssistente> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val salvo = runCatching { repo.obterPerfilCusto() }.getOrNull()
            _estado.value = _estado.value.copy(
                carregando = false,
                dados = salvo?.paraDados() ?: DadosCusto()
            )
        }
    }

    fun editar(bloco: (DadosCusto) -> DadosCusto) {
        _estado.value = _estado.value.copy(
            dados = bloco(_estado.value.dados),
            resultado = null,           // qualquer mudança invalida o cálculo anterior
            avisosPendentes = false
        )
    }

    fun irPara(p: PassoCusto) {
        _estado.value = _estado.value.copy(passo = p)
    }

    fun avancar() {
        val proximo = PassoCusto.entries.getOrNull(_estado.value.indicePasso + 1) ?: return
        irPara(proximo)
    }

    fun voltar() {
        val anterior = PassoCusto.entries.getOrNull(_estado.value.indicePasso - 1) ?: return
        irPara(anterior)
    }

    /**
     * Calcula. Se algum valor parecer digitado errado, primeiro mostra o aviso;
     * só no segundo toque (ou em [calcularMesmoAssim]) é que o resultado aparece.
     */
    fun calcular() {
        val e = _estado.value
        val r = CalculadoraCusto.calcular(e.dados)
        if (r.avisos.isNotEmpty() && !e.avisosPendentes) {
            _estado.value = e.copy(resultado = r, avisosPendentes = true)
            return
        }
        _estado.value = e.copy(resultado = r, avisosPendentes = false)
        salvar(e.dados)
    }

    fun calcularMesmoAssim() {
        _estado.value = _estado.value.copy(avisosPendentes = false)
        salvar(_estado.value.dados)
    }

    /** "Revisar": fecha o aviso e volta para o passo do primeiro campo suspeito. */
    fun revisar() {
        val avisos = _estado.value.resultado?.avisos.orEmpty()
        val passo = when (avisos.firstOrNull()?.campo) {
            "Intervalo do óleo", "Duração dos pneus", "Intervalo da revisão" -> PassoCusto.MANUTENCAO
            "Consumo médio", "Preço por litro" -> PassoCusto.COMBUSTIVEL
            "Km por dia", "Horas por dia" -> PassoCusto.METAS
            "Valor do veículo" -> PassoCusto.VEICULO
            else -> PassoCusto.FIXOS
        }
        _estado.value = _estado.value.copy(resultado = null, avisosPendentes = false, passo = passo)
    }

    fun voltarAoFormulario() {
        _estado.value = _estado.value.copy(resultado = null, avisosPendentes = false, aplicado = false)
    }

    /**
     * "Aplicar ao Semáforo": o custo por km e a tarifa mínima calculados passam a
     * valer para o leitor de ofertas. É o que liga esta tela ao cartão da corrida.
     */
    fun aplicarAoSemaforo() {
        val r = _estado.value.resultado ?: return
        val d = _estado.value.dados
        viewModelScope.launch {
            aplicar(d, r)
            _estado.value = _estado.value.copy(aplicado = true)
        }
    }

    /**
     * Assistente rápido (a camada obrigatória, 4.17): grava a meta por hora,
     * calcula e já aplica — sem a tela de resultado no meio. Os avisos de valor
     * estranho a tela rápida mostra antes, enquanto o motorista digita.
     */
    fun concluirRapido(metaHoraCentavos: Long, aoTerminar: () -> Unit) {
        val d = _estado.value.dados.comMetaPorHora(metaHoraCentavos)
        val r = CalculadoraCusto.calcular(d)
        _estado.value = _estado.value.copy(dados = d)
        viewModelScope.launch {
            runCatching { repo.salvarPerfilCusto(d.paraEntidade()) }
            aplicar(d, r)
            aoTerminar()
        }
    }

    private suspend fun aplicar(d: DadosCusto, r: ResultadoCusto) {
        runCatching {
            val cfg = repo.obterConfiguracao()
            repo.salvarConfiguracao(
                cfg.copy(
                    custoKmCentavos = r.custoPorKmCentavos,
                    tarifaMinimaCentavos = r.tarifaMinimaPorKmCentavos,
                    // O assistente e a UNICA porta de entrada destes dados:
                    // a tela "Meu veiculo e custos" so mostra o que sai daqui.
                    veiculoNome = d.veiculoNome.takeIf { it.isNotBlank() } ?: cfg.veiculoNome,
                    consumoKmLx100 = d.consumoX100.takeIf { it > 0 } ?: cfg.consumoKmLx100,
                    precoLitroCentavos = d.precoLitroCentavos.takeIf { it > 0 }
                        ?: cfg.precoLitroCentavos,
                    metaLucroMensalCentavos = d.metaLucroMensalCentavos
                        .takeIf { it > 0 } ?: cfg.metaLucroMensalCentavos,
                    // Diaria e semanal saem da mensal: uma meta so, sem se contradizer.
                    metaLucroDiarioCentavos = d.metaLucroDiarioCentavos
                        .takeIf { it > 0 } ?: cfg.metaLucroDiarioCentavos,
                    metaLucroSemanalCentavos = d.metaLucroSemanalCentavos
                        .takeIf { it > 0 } ?: cfg.metaLucroSemanalCentavos,
                    diasTrabalhoMes = Math.round(d.diasPorMes).toInt().coerceIn(1, 31)
                )
            )
            // Seguro, IPVA, parcela e depreciacao viram linhas da lista de
            // custos fixos, que e o que o Inicio desconta do lucro do dia.
            repo.substituirCustosFixosDoAssistente(d.custosFixosDoMes())
            // As horas do assistente passam a valer na meta por hora.
            prefs?.definirHorasPorDia(d.horasPorDia)
            aoAplicar()
        }
    }

    private fun salvar(d: DadosCusto) {
        viewModelScope.launch {
            runCatching { repo.salvarPerfilCusto(d.paraEntidade()) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MotoristaApp
                AssistenteCustoViewModel(app.repository, app.preferencias) { app.veiculos.sincronizarAtivo() }
            }
        }
    }
}

// ------------------------------------------------------------------ conversões

/** Entidade do banco -> dados da calculadora. Enum desconhecido cai no padrão. */
fun PerfilCusto.paraDados() = DadosCusto(
    forma = runCatching { FormaAquisicao.valueOf(forma) }.getOrDefault(FormaAquisicao.QUITADO),
    tipoVeiculo = runCatching { TipoVeiculo.valueOf(tipoVeiculo) }.getOrDefault(TipoVeiculo.MOTO),
    tipoTrabalho = runCatching { TipoTrabalho.valueOf(tipoTrabalho) }
        .getOrDefault(TipoTrabalho.PASSAGEIROS),
    veiculoNome = veiculoNome.orEmpty(),
    valorVeiculoCentavos = valorVeiculoCentavos,
    parcelaMensalCentavos = parcelaMensalCentavos,
    aluguelMensalCentavos = aluguelMensalCentavos,
    seguroMensalCentavos = seguroMensalCentavos,
    ipvaPercentX100 = ipvaPercentX100,
    ipvaEmReais = ipvaEmReais,
    ipvaAnualCentavos = ipvaAnualCentavos,
    desvalorizacaoAnualX100 = desvalorizacaoAnualX100,
    outrosMensaisCentavos = outrosMensaisCentavos,
    revisaoCentavos = revisaoCentavos,
    intervaloRevisaoKm = intervaloRevisaoKm,
    trocaOleoCentavos = trocaOleoCentavos,
    intervaloOleoKm = intervaloOleoKm,
    jogoPneusCentavos = jogoPneusCentavos,
    duracaoPneusKm = duracaoPneusKm,
    outrosDesgasteCentavos = outrosDesgasteCentavos,
    outrosDesgasteKm = outrosDesgasteKm,
    combustivel = runCatching { TipoCombustivel.valueOf(combustivel) }
        .getOrDefault(TipoCombustivel.GASOLINA),
    precoLitroCentavos = precoLitroCentavos,
    consumoX100 = consumoX100,
    kmPorDia = kmPorDia,
    diasPorSemana = diasPorSemana,
    horasPorDia = horasPorDia,
    metaLucroMensalCentavos = metaLucroMensalCentavos
)

fun DadosCusto.paraEntidade() = PerfilCusto(
    forma = forma.name,
    tipoVeiculo = tipoVeiculo.name,
    tipoTrabalho = tipoTrabalho.name,
    veiculoNome = veiculoNome.takeIf { it.isNotBlank() },
    valorVeiculoCentavos = valorVeiculoCentavos,
    parcelaMensalCentavos = parcelaMensalCentavos,
    aluguelMensalCentavos = aluguelMensalCentavos,
    seguroMensalCentavos = seguroMensalCentavos,
    ipvaPercentX100 = ipvaPercentX100,
    ipvaEmReais = ipvaEmReais,
    ipvaAnualCentavos = ipvaAnualCentavos,
    desvalorizacaoAnualX100 = desvalorizacaoAnualX100,
    outrosMensaisCentavos = outrosMensaisCentavos,
    revisaoCentavos = revisaoCentavos,
    intervaloRevisaoKm = intervaloRevisaoKm,
    trocaOleoCentavos = trocaOleoCentavos,
    intervaloOleoKm = intervaloOleoKm,
    jogoPneusCentavos = jogoPneusCentavos,
    duracaoPneusKm = duracaoPneusKm,
    outrosDesgasteCentavos = outrosDesgasteCentavos,
    outrosDesgasteKm = outrosDesgasteKm,
    combustivel = combustivel.name,
    precoLitroCentavos = precoLitroCentavos,
    consumoX100 = consumoX100,
    kmPorDia = kmPorDia,
    diasPorSemana = diasPorSemana,
    horasPorDia = horasPorDia,
    metaLucroMensalCentavos = metaLucroMensalCentavos,
    calculadoEm = System.currentTimeMillis()
)
