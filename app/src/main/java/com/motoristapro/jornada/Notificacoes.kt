package com.motoristapro.jornada

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notificacoes {
    const val CANAL_JORNADA = "jornada"
    const val CANAL_RESUMO = "resumo_diario"

    const val ID_JORNADA = 1001
    const val ID_RESUMO = 1002

    /** Cria os canais (idempotente — pode chamar a cada abertura do app). */
    fun criarCanais(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL_JORNADA, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Jornada em andamento")
                .setDescription("Cronômetro e km do turno medidos pelo GPS")
                .build()
        )
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL_RESUMO, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Resumo diário")
                .setDescription("Resumo dos ganhos do dia às 22h")
                .build()
        )
    }

    /** Android 13+ exige a permissão POST_NOTIFICATIONS concedida em tempo de execução. */
    fun podeNotificar(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
