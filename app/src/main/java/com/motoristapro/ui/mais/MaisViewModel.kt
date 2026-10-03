package com.motoristapro.ui.mais

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.net.Uri
import com.motoristapro.MotoristaApp
import com.motoristapro.atualizacao.EstadoAtualizacao
import com.motoristapro.atualizacao.VersaoPublicada
import com.motoristapro.auth.Usuario
import com.motoristapro.data.backup.BackupManager
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.ItemManutencao
import com.motoristapro.service.Diagnostico
import com.motoristapro.service.AvisoVoz
import com.motoristapro.service.EnderecosDeRisco
import com.motoristapro.service.LimitesOferta
import com.motoristapro.nuvem.EstadoNuvem
import com.motoristapro.service.OfertaAccessibilityService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Valores digitados no formulário da aba Mais (já convertidos). */
/** Fotografia das faixas do semáforo para a tela desenhar. */
data class EstadoFaixas(
    val kmRuim: Float,
    val kmBoa: Float,
    val horaRuim: Float,
    val horaBoa: Float,
    val notaRuim: Float,
    val notaBoa: Float,
    val usarNota: Boolean,
    val lucroMinimo: Float,
    val lucroPercent: Float
)

data class FormConfig(
    val veiculoNome: String?,
    val consumoKmLx100: Long,
    val precoLitroCentavos: Long,
    val custoKmCentavos: Long,
    val metaDiaria: Long,
    val metaSemanal: Long,
    val metaMensal: Long,
    val tarifaMinima: Long,
    val diasTrabalhoMes: Int
)

class MaisViewModel(private val app: MotoristaApp) : ViewModel() {

    private val repo = app.repository
    private val prefs = app.preferencias
    val limites = LimitesOferta(app)
    private val voz = AvisoVoz(app)
    private val risco = EnderecosDeRisco(app)

    /** null enquanto carrega (o formulário só é montado com a configuração real). */
    val config: StateFlow<Configuracao?> = repo.configuracao()
        .map<Configuracao, Configuracao?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val leitorConectado: StateFlow<Boolean> = OfertaAccessibilityService.conectado

    /** Última tela lida da Uber/99/iFood (para calibrar o leitor). */
    val diagnostico: StateFlow<Diagnostico?> = OfertaAccessibilityService.diagnostico

    val custosFixos: StateFlow<List<CustoFixo>> = repo.custosFixos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ================================================================== manutenção

    val manutencoes: StateFlow<List<ItemManutencao>> = repo.manutencoes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * O hodômetro de hoje, tirado do último abastecimento com km informado.
     *
     * É o número que o motorista já digita quando abastece — pedir de novo só
     * para a manutenção seria obrigá-lo a manter dois hodômetros na cabeça.
     * Zero = ainda não informou km em nenhum abastecimento.
     */
    private val _odometroAtual = MutableStateFlow(0L)
    val odometroAtual: StateFlow<Long> = _odometroAtual.asStateFlow()

    fun conferirOdometro() {
        viewModelScope.launch {
            _odometroAtual.value = runCatching { repo.ultimoOdometro() }.getOrNull() ?: 0L
        }
    }

    fun salvarManutencao(item: ItemManutencao) {
        viewModelScope.launch {
            runCatching { repo.salvarManutencao(item) }
                .onSuccess { _mensagens.send("Manutenção salva"); conferirOdometro() }
                .onFailure { _mensagens.send(it.message ?: "Erro ao salvar") }
        }
    }

    fun excluirManutencao(id: Long) {
        viewModelScope.launch {
            runCatching { repo.excluirManutencao(id) }
                .onFailure { _mensagens.send("Erro ao excluir: ${it.message}") }
        }
    }

    private val backup = BackupManager(app)

    /** Conta logada (null = Firebase não configurado ou ninguém logado). */
    val usuario: StateFlow<Usuario?> = app.autenticacao.usuario
    val loginDisponivel: Boolean get() = app.autenticacao.disponivel
    val estadoNuvem: StateFlow<EstadoNuvem> = app.nuvem.estado

    // ================================================================== atualização do app
    val estadoAtualizacao: StateFlow<EstadoAtualizacao> = app.atualizacao.estado
    val versaoInstalada: String get() = app.atualizacao.versaoInstalada
    val codigoInstalado: Int get() = app.atualizacao.codigoInstalado

    fun procurarAtualizacao() {
        viewModelScope.launch {
            val nova = app.atualizacao.verificar(avisarSeAtualizado = true)
            if (nova == null) {
                app.atualizacao.estado.value.mensagem?.let { _mensagens.send(it) }
                app.atualizacao.limparMensagem()
            }
        }
    }

    fun instalarAtualizacao(versao: VersaoPublicada) {
        if (!app.atualizacao.podeInstalar()) {
            app.atualizacao.abrirPermissaoDeInstalacao()
            viewModelScope.launch {
                _mensagens.send("Libere \"Instalar apps desconhecidos\" e toque em atualizar de novo")
            }
            return
        }
        viewModelScope.launch {
            runCatching { app.atualizacao.baixarEInstalar(versao) }
                .onFailure { _mensagens.send("Erro ao atualizar: ${it.message}") }
        }
    }

    fun dispensarAtualizacao() = app.atualizacao.dispensar()

    // ================================================================== perfil

    fun salvarPerfil(nome: String, telefone: String, cidade: String) {
        val atual = config.value ?: return
        viewModelScope.launch {
            runCatching {
                repo.salvarConfiguracao(
                    atual.copy(
                        nomeMotorista = nome.trim().ifEmpty { null },
                        telefone = telefone.trim().ifEmpty { null },
                        cidade = cidade.trim().ifEmpty { null }
                    )
                )
            }
                .onSuccess { _mensagens.send("Perfil salvo") }
                .onFailure { _mensagens.send("Erro ao salvar: ${it.message}") }
        }
    }

    /** true enquanto um backup/exportação está em andamento. */
    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado.asStateFlow()

    /** Vira true depois de restaurar: a tela pede para reiniciar o app. */
    private val _restaurado = MutableStateFlow(false)
    val restaurado: StateFlow<Boolean> = _restaurado.asStateFlow()

    private val _bolha = MutableStateFlow(prefs.bolhaAtiva)
    val bolhaAtiva: StateFlow<Boolean> = _bolha.asStateFlow()

    private val _ocr = MutableStateFlow(prefs.ocrAtivo)
    val ocrAtivo: StateFlow<Boolean> = _ocr.asStateFlow()

    private val _resumo = MutableStateFlow(prefs.resumoDiarioAtivo)
    val resumoAtivo: StateFlow<Boolean> = _resumo.asStateFlow()

    private val _voz = MutableStateFlow(voz.ativo)
    val vozAtiva: StateFlow<Boolean> = _voz.asStateFlow()

    private val _vozResumida = MutableStateFlow(voz.resumido)
    val vozResumida: StateFlow<Boolean> = _vozResumida.asStateFlow()

    private val _riscoAtivo = MutableStateFlow(risco.ativo)
    val riscoAtivo: StateFlow<Boolean> = _riscoAtivo.asStateFlow()

    private val _riscoMercados = MutableStateFlow(risco.alertarMercados)
    val riscoMercados: StateFlow<Boolean> = _riscoMercados.asStateFlow()

    private val _riscoPalavras = MutableStateFlow(risco.palavras)
    val riscoPalavras: StateFlow<List<String>> = _riscoPalavras.asStateFlow()

    /** Faixas do semáforo. Salvam na hora: arrastou, valeu. */
    private val _faixas = MutableStateFlow(lerFaixas())
    val faixas: StateFlow<EstadoFaixas> = _faixas.asStateFlow()

    private fun lerFaixas() = EstadoFaixas(
        kmRuim = limites.kmRuimAbaixo, kmBoa = limites.kmBoaAcima,
        horaRuim = limites.horaRuimAbaixo, horaBoa = limites.horaBoaAcima,
        notaRuim = limites.notaRuimAbaixo, notaBoa = limites.notaBoaAcima,
        usarNota = limites.usarNota,
        lucroMinimo = limites.minLucroReais, lucroPercent = limites.minLucroPercent
    )

    private val _mensagens = Channel<String>(Channel.BUFFERED)
    val mensagens: Flow<String> = _mensagens.receiveAsFlow()

    fun salvar(f: FormConfig) {
        val atual = config.value ?: return
        viewModelScope.launch {
            runCatching {
                repo.salvarConfiguracao(
                    atual.copy(
                        veiculoNome = f.veiculoNome,
                        consumoKmLx100 = f.consumoKmLx100,
                        precoLitroCentavos = f.precoLitroCentavos,
                        custoKmCentavos = f.custoKmCentavos,
                        metaLucroDiarioCentavos = f.metaDiaria,
                        metaLucroSemanalCentavos = f.metaSemanal,
                        metaLucroMensalCentavos = f.metaMensal,
                        tarifaMinimaCentavos = f.tarifaMinima,
                        diasTrabalhoMes = f.diasTrabalhoMes
                    )
                )
            }
                .onSuccess { _mensagens.send("Configurações salvas") }
                .onFailure { _mensagens.send("Erro ao salvar: ${it.message}") }
        }
    }

    // ================================================================== custos fixos

    fun salvarCustoFixo(nome: String, valorCentavos: Long) {
        viewModelScope.launch {
            runCatching { repo.salvarCustoFixo(CustoFixo(nome = nome.trim(), valorMensalCentavos = valorCentavos)) }
                .onSuccess { _mensagens.send("Custo fixo \"${nome.trim()}\" adicionado") }
                .onFailure { _mensagens.send("Erro: ${it.message}") }
        }
    }

    fun excluirCustoFixo(id: Long) {
        viewModelScope.launch {
            runCatching { repo.excluirCustoFixo(id) }
                .onFailure { _mensagens.send("Erro ao excluir: ${it.message}") }
        }
    }

    // ================================================================== backup e exportação

    private fun executar(sucesso: (Int) -> String, bloco: suspend () -> Int) {
        if (_ocupado.value) return
        _ocupado.value = true
        viewModelScope.launch {
            runCatching { bloco() }
                .onSuccess { _mensagens.send(sucesso(it)) }
                .onFailure { _mensagens.send("Erro: ${it.message}") }
            _ocupado.value = false
        }
    }

    fun fazerBackup(destino: Uri) = executar({ "Backup salvo com sucesso" }) { backup.fazerBackup(destino); 0 }

    fun exportarCorridas(destino: Uri) = executar({ "$it corridas exportadas" }) { backup.exportarCorridas(destino) }

    fun exportarDespesas(destino: Uri) = executar({ "$it despesas exportadas" }) { backup.exportarDespesas(destino) }

    fun exportarOfertas(destino: Uri) = executar({ "$it ofertas exportadas" }) { backup.exportarOfertas(destino) }

    fun restaurar(origem: Uri) {
        if (_ocupado.value) return
        _ocupado.value = true
        viewModelScope.launch {
            runCatching { backup.restaurar(origem) }
                .onSuccess { _restaurado.value = true }
                .onFailure { _mensagens.send("Não foi possível restaurar: ${it.message}") }
            _ocupado.value = false
        }
    }

    fun reiniciarApp() = backup.reiniciarApp()

    // ================================================================== nuvem

    fun enviarParaNuvem() {
        viewModelScope.launch {
            runCatching { app.nuvem.enviar() }
                .onSuccess { _mensagens.send("Enviado para a nuvem ($it corridas)") }
                .onFailure { _mensagens.send("Erro ao enviar: ${it.message}") }
        }
    }

    fun baixarDaNuvem() {
        viewModelScope.launch {
            runCatching { app.nuvem.baixar() }
                .onSuccess { _mensagens.send("Dados da nuvem restaurados ($it corridas)") }
                .onFailure { _mensagens.send("Erro ao baixar: ${it.message}") }
        }
    }

    fun consultarNuvem() {
        viewModelScope.launch { runCatching { app.nuvem.consultar() } }
    }

    /**
     * Apaga tudo deste celular e volta o app ao estado de recem-instalado.
     * A nuvem nao e tocada: "Baixar da nuvem" ainda traz tudo de volta.
     */
    fun zerarCelular() {
        viewModelScope.launch {
            runCatching { app.nuvem.zerarCelular() }
                .onSuccess { _mensagens.send("Celular zerado — o app comecou do zero") }
                .onFailure { _mensagens.send("Erro ao zerar: ${it.message}") }
        }
    }

    fun sairDaConta() {
        app.autenticacao.sair()
        viewModelScope.launch { _mensagens.send("Você saiu da conta") }
    }

    // ================================================================== preferências

    fun definirBolha(ativa: Boolean) {
        prefs.bolhaAtiva = ativa
        _bolha.value = ativa
        OfertaAccessibilityService.instancia?.aplicarPreferencias()
    }

    fun definirOcr(ativo: Boolean) {
        prefs.ocrAtivo = ativo
        _ocr.value = ativo
    }

    // ------------------------------------------------------------ faixas

    fun definirFaixaKm(ruim: Float, boa: Float) {
        limites.definirFaixaKm(ruim, boa)
        _faixas.value = lerFaixas()
    }

    fun definirFaixaHora(ruim: Float, boa: Float) {
        limites.definirFaixaHora(ruim, boa)
        _faixas.value = lerFaixas()
    }

    fun definirFaixaNota(ruim: Float, boa: Float) {
        limites.definirFaixaNota(ruim, boa)
        _faixas.value = lerFaixas()
    }

    fun definirUsarNota(usar: Boolean) {
        limites.usarNota = usar
        _faixas.value = lerFaixas()
    }

    fun definirLucroMinimo(reais: Float) {
        limites.minLucroReais = reais.coerceAtLeast(0f)
        _faixas.value = lerFaixas()
    }

    fun definirLucroPercent(pct: Float) {
        limites.minLucroPercent = pct.coerceIn(0f, 99f)
        _faixas.value = lerFaixas()
    }

    /** Valor mínimo da viagem mora na Configuração (Room), não nas preferências. */
    fun definirValorMinimoViagem(centavos: Long) {
        viewModelScope.launch { runCatching { repo.definirTarifaMinima(centavos.coerceAtLeast(0)) } }
    }

    fun definirResumo(ativo: Boolean) {
        prefs.resumoDiarioAtivo = ativo
        _resumo.value = ativo
    }

    // ------------------------------------------------------------------ voz

    fun definirVoz(ativa: Boolean) {
        voz.ativo = ativa          // o próprio AvisoVoz cria/libera o motor de fala
        _voz.value = ativa
        OfertaAccessibilityService.instancia?.aplicarPreferencias()
    }

    fun definirVozResumida(resumida: Boolean) {
        voz.resumido = resumida
        _vozResumida.value = resumida
    }

    // ------------------------------------------------------------- endereços de risco

    fun definirRisco(ativo: Boolean) {
        risco.ativo = ativo
        _riscoAtivo.value = ativo
    }

    fun definirRiscoMercados(ativo: Boolean) {
        risco.alertarMercados = ativo
        _riscoMercados.value = ativo
    }

    fun adicionarPalavraRisco(palavra: String) {
        risco.adicionar(palavra)
        _riscoPalavras.value = risco.palavras
    }

    fun removerPalavraRisco(palavra: String) {
        risco.remover(palavra)
        _riscoPalavras.value = risco.palavras
    }

    fun testarLeitor() {
        val s = OfertaAccessibilityService.instancia
        if (s == null) viewModelScope.launch { _mensagens.send("Leitor de ofertas não está conectado") }
        else s.mostrarTeste()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MaisViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MotoristaApp)
            }
        }
    }
}
