package com.motoristapro.resumo

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.motoristapro.MotoristaApp
import com.motoristapro.data.prefs.PreferenciasApp
import com.motoristapro.data.repository.Periodo
import com.motoristapro.data.repository.emReais
import com.motoristapro.jornada.Notificacoes
import com.motoristapro.ui.MainActivity
import com.motoristapro.ui.formatarDuracao
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Todo dia às 22h: notificação com o lucro do dia, a % da meta, R$/km e as horas de jornada. */
class ResumoDiarioWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!PreferenciasApp(ctx).resumoDiarioAtivo) return Result.success()
        if (!Notificacoes.podeNotificar(ctx)) return Result.success()

        val repo = (ctx as MotoristaApp).repository
        val (ini, fim) = Periodo.dia(LocalDate.now())
        val r = repo.resumoDoPeriodo(ini, fim).first()
        if (r.qtdCorridas == 0 && r.despesasCentavos == 0L) return Result.success()

        val abrir = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Lucro com a meta do dia, R$/km e horas de jornada: o dia inteiro numa olhada.
        val cfg = repo.configuracao().first()
        val meta = cfg.metaLucroDiarioCentavos
        val lucro = r.lucroLiquidoCentavos
        val agora = System.currentTimeMillis()
        val segundos = runCatching {
            repo.totaisJornadasEncerradas(ini, fim).first().segundos +
                (repo.obterJornadaAtiva()?.segundosRodando(agora) ?: 0L)
        }.getOrDefault(0L)

        val titulo = "Lucro do dia: ${lucro.emReais()}" +
            if (meta > 0) " (${(lucro.coerceAtLeast(0) * 100 / meta)}% da meta)" else ""
        val linha1 = buildList {
            add("${r.qtdCorridas} corrida(s)")
            if (r.metrosRodados > 0) add("${r.ganhoPorKmCentavos.emReais()}/km")
            if (segundos >= 60) add("${segundos.formatarDuracao()} de jornada")
        }.joinToString(" · ")
        val linha2 = when {
            meta <= 0 -> "${r.faturamentoCentavos.emReais()} faturado · ${r.despesasCentavos.emReais()} de despesas"
            lucro >= meta -> "Meta de ${meta.emReais()} batida!"
            else -> "Faltaram ${(meta - lucro).emReais()} para a meta de ${meta.emReais()}."
        }
        val texto = "$linha1\n$linha2"

        val notificacao = NotificationCompat.Builder(ctx, Notificacoes.CANAL_RESUMO)
            .setSmallIcon(com.motoristapro.R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(linha1)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .addAction(0, "VER O DIA", abrir)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(ctx).notify(Notificacoes.ID_RESUMO, notificacao)
            Result.success()
        } catch (e: SecurityException) {
            Result.success()
        }
    }

    companion object {
        private const val NOME = "resumo_diario"
        private val HORARIO: LocalTime = LocalTime.of(22, 0)

        /** Agenda (uma vez) a execução diária às 22h. Seguro chamar a cada abertura do app. */
        fun agendar(context: Context) {
            val agora = LocalDateTime.now()
            var proximo = agora.toLocalDate().atTime(HORARIO)
            if (!proximo.isAfter(agora)) proximo = proximo.plusDays(1)
            val atraso = Duration.between(agora, proximo).toMillis()

            val pedido = PeriodicWorkRequestBuilder<ResumoDiarioWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(atraso, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NOME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
