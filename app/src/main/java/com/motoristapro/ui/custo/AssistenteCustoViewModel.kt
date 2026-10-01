package com.motoristapro.ui.custo

import androidx.lifecycle.ViewModel
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

/** Os quatro passos do assistente, na ordem. */
enum class PassoCusto(val titulo: String) {
    FIXOS("Custos fixos"),
    MANUTENCAO("Manutenção"),
    COMBUSTIVEL("Combustível"),
    METAS("Metas")
}

data class EstadoAssistente(
    val carregando: Boolean = true,
    val passo: PassoCusto = PassoCusto.FIXOS,
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
class AssistenteCustoViewModel(private val repo: FinanceiroRepository) : ViewModel() {

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
        viewModelScope.launch {
            runCatching {
                val cfg = repo.obterConfiguracao()
                repo.salvarConfiguracao(
                    cfg.copy(
                        custoKmCentavos = r.custoPorKmCentavos,
                        metaLucroMensalCentavos = _estado.value.dados.metaLucroMensalCentavos
                            .takeIf { it > 0 } ?: cfg.metaLucroMensalCentavos,
                        diasTrabalhoMes = Math.round(_estado.value.dados.diasPorMes).toInt()
                            .coerceIn(1, 31)
                    )
                )
            }
            _estado.value = _estado.value.copy(aplicado = true)
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
                AssistenteCustoViewModel(app.repository)
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
