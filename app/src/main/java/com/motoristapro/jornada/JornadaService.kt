package com.motoristapro.jornada

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import com.motoristapro.diagnostico.RegistroErros
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.motoristapro.MotoristaApp
import com.motoristapro.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Serviço em primeiro plano do turno: mantém o cronômetro na notificação e soma
 * os km rodados pelo GPS (LocationManager, sem Google Play Services).
 *
 * A jornada em si vive no banco (tabela jornadas). Este serviço só mede o GPS:
 * se ele for encerrado pelo sistema, o turno continua aberto e o app oferece "Retomar GPS".
 */
class JornadaService : Service() {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main + RegistroErros.handler("jornada"))
    private var locationManager: LocationManager? = null

    private var jornadaId: Long = 0
    private var inicioEm: Long = 0
    private var metros: Double = 0.0
    private var ancora: Location? = null

    private var ultimoPersistido: Double = 0.0
    private var ultimaGravacaoEm: Long = 0
    private var ultimaNotificacaoEm: Long = 0

    // Parado há 30 min com o turno correndo: avisa (gasta bateria e conta hora à toa).
    private var vigia = VigiaParado(System.currentTimeMillis())
    private var vigiaJob: Job? = null

    // Objeto completo, e não lambda: antes do Android 11 os outros métodos do
    // LocationListener não têm corpo padrão, e desligar o GPS no meio da
    // jornada fechava o app inteiro (o leitor de ofertas caía junto).
    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            try {
                processarLocalizacao(loc)
            } catch (e: Throwable) {
                RegistroErros.anotar("GPS da jornada", e)
            }
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Só existe até o Android 10")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) {
            encerrarTurno()
            return START_NOT_STICKY
        }
        if (intent?.action == ACAO_PAUSAR_PARADO) {
            pausarDesdeQueParou()
            return START_NOT_STICKY
        }

        // Obrigatório entrar em primeiro plano logo após startForegroundService().
        if (!entrarEmPrimeiroPlano()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val repo = (application as MotoristaApp).repository
        escopo.launch {
            val jornada = repo.obterJornadaAtiva()
            if (jornada == null) {
                pararServico()
                return@launch
            }
            jornadaId = jornada.id
            inicioEm = jornada.inicioEm
            metros = jornada.metrosGps.toDouble()
            ultimoPersistido = metros
            _rodando.value = true
            atualizarNotificacao(forcar = true)
            iniciarGps()
            vigiarParado()
        }
        // Não pedimos reinício automático: Android 12+ bloqueia serviço em 1º plano iniciado do fundo.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pararGps()
        vigiaJob?.cancel()
        tirarAvisoParado()
        _rodando.value = false
        escopo.cancel()
        super.onDestroy()
    }

    // ================================================================== GPS

    private fun temPermissaoLocalizacao(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun iniciarGps() {
        if (!temPermissaoLocalizacao()) {
            Log.w(TAG, "Sem permissão de localização: turno segue só com cronômetro")
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, INTERVALO_MS, DISTANCIA_MIN_M, listener, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão de localização negada", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Aparelho sem GPS", e)
        }
    }

    private fun pararGps() {
        try {
            locationManager?.removeUpdates(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao parar GPS", e)
        }
        locationManager = null
    }

    /** Soma a distância filtrando leituras imprecisas e saltos impossíveis. */
    private fun processarLocalizacao(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > PRECISAO_MAX_M) return
        val andou = vigia.posicao(loc.latitude, loc.longitude, System.currentTimeMillis()) { lat, lon ->
            val r = FloatArray(1)
            Location.distanceBetween(lat, lon, loc.latitude, loc.longitude, r)
            r[0].toDouble()
        }
        if (andou) tirarAvisoParado()
        val anterior = ancora
        if (anterior == null) {
            ancora = loc
            return
        }
        val d = anterior.distanceTo(loc).toDouble()
        if (d < PASSO_MIN_M) return          // parado/deriva do GPS: mantém a âncora
        val dtSeg = (loc.time - anterior.time) / 1000.0
        val velocidade = if (dtSeg > 0) d / dtSeg else Double.MAX_VALUE
        if (velocidade <= VELOCIDADE_MAX_MS) metros += d
        ancora = loc

        val agora = System.currentTimeMillis()
        if (metros - ultimoPersistido >= 100 || agora - ultimaGravacaoEm >= 30_000) persistir()
        atualizarNotificacao()
    }

    private fun persistir() {
        if (jornadaId == 0L) return
        val id = jornadaId
        val valor = metros.toLong()
        ultimoPersistido = metros
        ultimaGravacaoEm = System.currentTimeMillis()
        val repo = (application as MotoristaApp).repository
        escopo.launch {
            runCatching { repo.atualizarMetrosJornada(id, valor) }
                .onFailure { Log.e(TAG, "Falha ao gravar km", it) }
        }
    }

    // ================================================================== parado

    /**
     * Confere de minuto em minuto. Só com GPS: sem permissão de localização
     * não dá para saber se está parado. Uma vez por parada.
     */
    private fun vigiarParado() {
        if (!temPermissaoLocalizacao() || vigiaJob?.isActive == true) return
        vigia = VigiaParado(System.currentTimeMillis())
        val repo = (application as MotoristaApp).repository
        vigiaJob = escopo.launch {
            while (true) {
                delay(60_000)
                val jornada = runCatching { repo.obterJornadaAtiva() }.getOrNull() ?: continue
                val agora = System.currentTimeMillis()
                if (jornada.pausada) {
                    vigia.reiniciar(agora)
                    tirarAvisoParado()
                    continue
                }
                if (vigia.deveAvisar(agora, pausada = false)) avisarParado(agora - vigia.paradoDesde)
            }
        }
    }

    @SuppressLint("MissingPermission") // checado em Notificacoes.podeNotificar()
    private fun avisarParado(paradoMs: Long) {
        if (!Notificacoes.podeNotificar(this)) return
        val abrirApp = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pausar = PendingIntent.getService(
            this, 3,
            Intent(this, JornadaService::class.java).setAction(ACAO_PAUSAR_PARADO),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val encerrar = PendingIntent.getService(
            this, 4,
            Intent(this, JornadaService::class.java).setAction(ACAO_PARAR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val minutos = paradoMs / 60_000
        val aviso = NotificationCompat.Builder(this, Notificacoes.CANAL_RESUMO)
            .setSmallIcon(com.motoristapro.R.drawable.ic_notificacao)
            .setContentTitle("Parado há $minutos min com a jornada rodando")
            .setContentText("Pausar não conta esse tempo como hora rodada.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Você está no mesmo lugar há $minutos minutos e o cronômetro segue contando. " +
                        "Pausar desconta o tempo parado; se acabou o dia, encerre e economize bateria."
                )
            )
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .addAction(0, "Pausar", pausar)
            .addAction(0, "Encerrar turno", encerrar)
            .build()
        runCatching { androidx.core.app.NotificationManagerCompat.from(this).notify(Notificacoes.ID_PARADO, aviso) }
    }

    private fun tirarAvisoParado() {
        runCatching { androidx.core.app.NotificationManagerCompat.from(this).cancel(Notificacoes.ID_PARADO) }
    }

    /** "Pausar" do aviso: a pausa começa de quando parou, não de agora. */
    private fun pausarDesdeQueParou() {
        tirarAvisoParado()
        val desde = vigia.paradoDesde
        val repo = (application as MotoristaApp).repository
        escopo.launch {
            runCatching {
                val j = repo.obterJornadaAtiva() ?: return@runCatching
                if (!j.pausada) repo.pausarJornada(j.id, maxOf(desde, j.inicioEm))
            }.onFailure { RegistroErros.anotar("pausar pelo aviso", it) }
            vigia.reiniciar(System.currentTimeMillis())
            // Serviço já tinha sido fechado pelo sistema: só veio pausar, não fica de pé.
            if (!_rodando.value) stopSelf()
        }
    }

    // ================================================================== encerrar

    private fun encerrarTurno() {
        pararGps()
        val id = jornadaId
        val valor = metros.toLong()
        val repo = (application as MotoristaApp).repository
        escopo.launch {
            runCatching {
                val alvo = if (id != 0L) id else repo.obterJornadaAtiva()?.id
                if (alvo != null) {
                    if (id != 0L) repo.atualizarMetrosJornada(alvo, valor)
                    repo.encerrarJornada(alvo)
                }
            }.onFailure { Log.e(TAG, "Falha ao encerrar jornada", it) }
            pararServico()
        }
    }

    private fun pararServico() {
        _rodando.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ================================================================== notificação

    private fun entrarEmPrimeiroPlano(): Boolean = try {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && temPermissaoLocalizacao())
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, Notificacoes.ID_JORNADA, montarNotificacao(), tipo)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Não foi possível iniciar em primeiro plano", e)
        false
    }

    @SuppressLint("MissingPermission") // checado em Notificacoes.podeNotificar()
    private fun atualizarNotificacao(forcar: Boolean = false) {
        val agora = System.currentTimeMillis()
        if (!forcar && agora - ultimaNotificacaoEm < 15_000) return
        ultimaNotificacaoEm = agora
        if (!Notificacoes.podeNotificar(this)) return
        try {
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(Notificacoes.ID_JORNADA, montarNotificacao())
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão de notificação", e)
        }
    }

    private fun montarNotificacao(): Notification {
        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val encerrar = PendingIntent.getService(
            this, 1,
            Intent(this, JornadaService::class.java).setAction(ACAO_PARAR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val km = String.format(Locale("pt", "BR"), "%.1f km rodados", metros / 1000.0)
        return NotificationCompat.Builder(this, Notificacoes.CANAL_JORNADA)
            .setSmallIcon(com.motoristapro.R.drawable.ic_notificacao)
            .setContentTitle("Jornada em andamento")
            .setContentText(if (temPermissaoLocalizacao()) km else "Cronômetro ativo (GPS sem permissão)")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(inicioEm > 0)
            .setWhen(if (inicioEm > 0) inicioEm else System.currentTimeMillis())
            .setUsesChronometer(inicioEm > 0)
            .setContentIntent(abrirApp)
            .addAction(0, "Encerrar turno", encerrar)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "MotoristaPro"
        const val ACAO_INICIAR = "com.motoristapro.jornada.INICIAR"
        const val ACAO_PARAR = "com.motoristapro.jornada.PARAR"
        private const val ACAO_PAUSAR_PARADO = "com.motoristapro.jornada.PAUSAR_PARADO"

        private const val INTERVALO_MS = 3_000L
        private const val DISTANCIA_MIN_M = 10f
        private const val PRECISAO_MAX_M = 35f
        private const val PASSO_MIN_M = 12.0
        private const val VELOCIDADE_MAX_MS = 55.0     // ~200 km/h: acima disso é salto de GPS

        private val _rodando = MutableStateFlow(false)

        /** true enquanto o GPS do turno está medindo. */
        val rodando: StateFlow<Boolean> = _rodando.asStateFlow()

        /**
         * Liga o serviço (a jornada já deve existir no banco). Devolve false quando
         * NÃO é seguro ligar agora — aí o turno segue só com o cronômetro.
         *
         * Por que conferir antes: depois de startForegroundService() o Android
         * EXIGE que o serviço entre em primeiro plano. Se ele não consegue (sem a
         * localização precisa, ou com o app fora da tela no Android 14+, que só
         * deixa ligar o GPS em primeiro plano com o app aberto), o sistema derruba
         * o processo inteiro — e o leitor de ofertas, que mora no mesmo processo,
         * caía junto. Era uma das formas de o leitor "fechar sozinho".
         */
        fun iniciar(context: Context): Boolean {
            if (!podeLigar(context)) {
                Log.w(TAG, "GPS do turno não ligado: sem localização precisa ou app fora da tela")
                return false
            }
            val i = Intent(context, JornadaService::class.java).setAction(ACAO_INICIAR)
            return try {
                ContextCompat.startForegroundService(context, i)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Não foi possível iniciar o GPS do turno", e)
                false
            }
        }

        /** Localização precisa concedida E o app na frente agora. */
        fun podeLigar(context: Context): Boolean {
            val temLocal = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!temLocal) return false
            val estado = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(estado)
            return estado.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }

        /** Encerra o turno (grava km + fim) e desliga o serviço. */
        fun parar(context: Context) {
            try {
                context.startService(Intent(context, JornadaService::class.java).setAction(ACAO_PARAR))
            } catch (e: Exception) {
                Log.e(TAG, "Não foi possível parar o serviço", e)
            }
        }
    }
}
