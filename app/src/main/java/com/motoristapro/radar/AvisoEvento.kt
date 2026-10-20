package com.motoristapro.radar

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.motoristapro.jornada.Notificacoes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * "Avisar 20h30": um trabalho do WorkManager marcado para 30 min antes da
 * saída do evento. Sobrevive a reiniciar o celular e a fechar o app.
 */
object AvisoEvento {

    const val CANAL = "radar_eventos"

    private fun nome(id: String) = "radar_$id"

    fun agendar(context: Context, e: Evento): Boolean {
        val quando = CalculoRadar.horaDoAviso(e, System.currentTimeMillis()) ?: return false
        val pedido = OneTimeWorkRequestBuilder<AvisoEventoWorker>()
            .setInitialDelay(quando - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "id" to e.id, "titulo" to e.titulo, "local" to e.local,
                    "endereco" to (e.endereco ?: e.local), "saidaEm" to e.saidaEm
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(nome(e.id), ExistingWorkPolicy.REPLACE, pedido)
        return true
    }

    fun cancelar(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(nome(id))
    }

    fun criarCanal(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Radar de eventos")
                .setDescription("Avisa 30 min antes da saída de jogos, shows e festas que você marcou")
                .build()
        )
    }
}

class AvisoEventoWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        val ctx = applicationContext
        if (!Notificacoes.podeNotificar(ctx)) return Result.success()
        AvisoEvento.criarCanal(ctx)
        val titulo = inputData.getString("titulo") ?: return Result.success()
        val local = inputData.getString("local").orEmpty()
        val endereco = inputData.getString("endereco").orEmpty()
        val saida = Instant.ofEpochMilli(inputData.getLong("saidaEm", 0)).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH'h'mm"))
        val mapa = PendingIntent.getActivity(
            ctx, titulo.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(endereco)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val aviso = NotificationCompat.Builder(ctx, AvisoEvento.CANAL)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("Saída em 30 min: $titulo")
            .setContentText("$local · público saindo por volta das $saida")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mapa)
            .setAutoCancel(true)
            .addAction(0, "Ver no mapa", mapa)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(3000 + (titulo.hashCode() and 0xFFF), aviso) }
        return Result.success()
    }
}
