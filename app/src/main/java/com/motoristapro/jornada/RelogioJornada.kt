package com.motoristapro.jornada

import android.content.Context
import android.util.Log
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.repository.FinanceiroRepository
import com.motoristapro.service.AppDeCorrida
import com.motoristapro.service.StatusApp
import com.motoristapro.service.StatusApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Em que pé está o turno agora. */
enum class EstadoJornada(val rotulo: String) {
    OFFLINE("Offline"),
    AGUARDANDO("Aguardando viagens"),
    BUSCANDO("Buscando passageiro"),
    ESPERANDO("Aguardando passageiro"),
    EM_VIAGEM("Em viagem")
}

/**
 * O relógio do turno: quanto tempo em cada estado e quanto tempo cada app ficou online.
 *
 * Funciona por FECHAMENTO DE TRECHO, não por tique-taque. A cada mudança (um app
 * ficou online, a jornada pausou, o turno acabou) o tempo decorrido desde a
 * última mudança é somado no estado em que se estava, e o relógio reinicia dali.
 * Assim o número fica certo mesmo com o Android matando o processo no meio —
 * nada depende de um timer ficar vivo.
 *
 * BUSCANDO, ESPERANDO e EM_VIAGEM existem no banco mas ainda não são detectados:
 * dependem de reconhecer as telas de dentro da corrida de cada app, que ainda
 * não foram mapeadas. Por enquanto o turno se divide entre OFFLINE e AGUARDANDO.
 */
class RelogioJornada(
    context: Context,
    private val repo: FinanceiroRepository
) {

    private val statusApps = StatusApps(context)
    private val prefs = context.applicationContext
        .getSharedPreferences("relogio_jornada", Context.MODE_PRIVATE)
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trava = Mutex()

    /** Começar sozinha quando algum app ficar online. Ligado de fábrica. */
    var inicioAutomatico: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO, v).apply()

    private var marcoMs: Long
        get() = prefs.getLong(KEY_MARCO, 0)
        set(v) = prefs.edit().putLong(KEY_MARCO, v).apply()

    private var estadoSalvo: EstadoJornada
        get() = runCatching { EstadoJornada.valueOf(prefs.getString(KEY_ESTADO, "") ?: "") }
            .getOrDefault(EstadoJornada.OFFLINE)
        set(v) = prefs.edit().putString(KEY_ESTADO, v.name).apply()

    /** O estado que os apps indicam agora. */
    fun estadoAtual(): EstadoJornada =
        if (statusApps.algumOnline()) EstadoJornada.AGUARDANDO else EstadoJornada.OFFLINE

    /**
     * Chamado pelo leitor de ofertas sempre que ele vê a tela de um app de corrida.
     * Fecha o trecho anterior e, se for o caso, começa a jornada sozinha.
     */
    fun aoVerTelaDeApp(pacote: String, textos: List<String>) {
        val antes = statusApps.algumOnline()
        statusApps.lerDaTela(pacote, textos) ?: return
        val agora = statusApps.algumOnline()
        if (antes == agora) {
            // Mesmo sem mudar de estado, vale fechar o trecho de vez em quando:
            // o app pode ficar horas online e um crash levaria tudo junto.
            if (System.currentTimeMillis() - marcoMs > INTERVALO_GRAVACAO_MS) fecharTrecho()
            return
        }
        if (agora && !antes && inicioAutomatico) {
            escopo.launch { runCatching { garantirJornada() } }
        }
        fecharTrecho()
    }

    /**
     * Fecha o trecho aberto AGORA.
     *
     * Chamado pela tela da Jornada de poucos em poucos segundos: sem isso os
     * números só mexiam quando o motorista voltava para um app de corrida, e a
     * tela parecia travada enquanto ele a olhava.
     */
    fun atualizarAgora() {
        if (System.currentTimeMillis() - marcoMs < INTERVALO_TELA_MS) return
        fecharTrecho()
    }

    /** Botão "Iniciar jornada": vale mesmo com todos os apps offline. */
    fun iniciarNaMao() {
        escopo.launch {
            runCatching {
                garantirJornada()
                marcoMs = System.currentTimeMillis()
                estadoSalvo = estadoAtual()
            }.onFailure { Log.w(TAG, "Falha ao iniciar jornada", it) }
        }
    }

    /** Pausar: o relógio para, o turno continua aberto. */
    fun pausar() {
        escopo.launch {
            runCatching {
                somarTrechoAtual()
                val j = repo.obterJornadaAtiva() ?: return@runCatching
                repo.pausarJornada(j.id, System.currentTimeMillis())
            }.onFailure { Log.w(TAG, "Falha ao pausar", it) }
        }
    }

    fun retomar() {
        escopo.launch {
            runCatching {
                val j = repo.obterJornadaAtiva() ?: return@runCatching
                repo.pausarJornada(j.id, 0)
                marcoMs = System.currentTimeMillis()
                estadoSalvo = estadoAtual()
            }.onFailure { Log.w(TAG, "Falha ao retomar", it) }
        }
    }

    fun encerrar() {
        escopo.launch {
            runCatching {
                somarTrechoAtual()
                val j = repo.obterJornadaAtiva() ?: return@runCatching
                repo.encerrarJornada(j.id, System.currentTimeMillis())
                statusApps.zerarTudo()
                marcoMs = 0
            }.onFailure { Log.w(TAG, "Falha ao encerrar", it) }
        }
    }

    /** O toque do motorista no quadradinho de um app. */
    fun corrigirApp(app: AppDeCorrida) {
        val antes = statusApps.algumOnline()
        statusApps.alternar(app)
        if (statusApps.algumOnline() != antes) fecharTrecho()
    }

    /**
     * O trecho que está aberto agora, em segundos — o tempo que o banco ainda
     * não recebeu.
     *
     * A tela soma isso ao que está gravado para o cronômetro andar de segundo em
     * segundo. Sem isso os números pulavam de 5 em 5 s (o intervalo em que o
     * trecho é fechado), e o motorista via o relógio "demorando a atualizar".
     */
    fun segundosDoTrechoAberto(): Long {
        val marco = marcoMs
        if (marco <= 0L) return 0L
        val segundos = (System.currentTimeMillis() - marco) / 1000
        return if (segundos < 0 || segundos > TRECHO_MAX_SEG) 0L else segundos
    }

    /** Em que estado esse trecho aberto está correndo. */
    fun estadoDoTrechoAberto(): EstadoJornada = estadoSalvo

    fun status(app: AppDeCorrida): StatusApp = statusApps.status(app)
    fun vistoEm(app: AppDeCorrida): Long = statusApps.vistoEm(app)
    fun corrigidoNaMao(app: AppDeCorrida): Boolean = statusApps.corrigidoNaMao(app)

    // ------------------------------------------------------------------ interno

    private fun fecharTrecho() {
        escopo.launch { runCatching { somarTrechoAtual() } }
    }

    /**
     * Soma o tempo decorrido desde a última marca no estado em que se estava, e
     * reinicia a marca. Não faz nada com a jornada pausada ou encerrada.
     */
    private suspend fun somarTrechoAtual() = trava.withLock {
        val jornada = repo.obterJornadaAtiva() ?: return@withLock
        val agora = System.currentTimeMillis()
        val inicio = marcoMs.takeIf { it > 0 } ?: jornada.inicioEm
        val segundos = ((agora - inicio) / 1000).coerceAtLeast(0)

        marcoMs = agora
        val estado = estadoSalvo
        estadoSalvo = estadoAtual()

        if (jornada.pausada || segundos <= 0 || segundos > TRECHO_MAX_SEG) {
            // Trecho absurdo (celular dormiu dias, relógio mexido) não entra.
            return@withLock
        }
        repo.somarTempoJornada(
            id = jornada.id,
            estado = estado,
            segundos = segundos,
            appsOnline = statusApps.online()
        )
    }

    private suspend fun garantirJornada(): Jornada {
        val j = repo.iniciarJornada()
        if (marcoMs == 0L) {
            marcoMs = System.currentTimeMillis()
            estadoSalvo = estadoAtual()
        }
        return j
    }

    private companion object {
        const val TAG = "MotoristaPro"
        const val KEY_AUTO = "inicio_automatico"
        const val KEY_MARCO = "marco_ms"
        const val KEY_ESTADO = "estado"
        const val INTERVALO_GRAVACAO_MS = 60_000L
        /** Com a tela da Jornada aberta, fecha o trecho a cada 5 s. */
        const val INTERVALO_TELA_MS = 5_000L
        /** 12 h num trecho só é erro de relógio, não turno. */
        const val TRECHO_MAX_SEG = 12 * 3600L
    }
}
