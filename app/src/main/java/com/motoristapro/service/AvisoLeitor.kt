package com.motoristapro.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.motoristapro.jornada.Notificacoes

/**
 * O vigia do leitor de ofertas.
 *
 * O serviço de acessibilidade pode cair sem o motorista encostar nele: o
 * fabricante mata o processo para "economizar bateria" (Xiaomi, Samsung e
 * Motorola fazem isso de fábrica), ou o próprio Android desliga o serviço se
 * ele quebrar uma vez. O estrago só aparece no fim do dia, quando o histórico
 * está vazio — por isso o aviso tem que vir na hora, na barra de notificações,
 * e com um toque que leva direto para a tela de religar.
 */
object AvisoLeitor {

    const val CANAL = "leitor_caiu"
    const val ID = 1003

    private const val PREFS = "leitor_estado"
    private const val CHAVE_LIGADO = "estava_ligado"

    fun criarCanal(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Leitor de ofertas desligou")
                .setDescription("Avisa na hora em que o leitor para de ler, para não perder corridas")
                .build()
        )
    }

    /** Guardado quando o leitor conecta: é o que permite saber que ele CAIU, e não que nunca ligou. */
    fun marcarLigado(context: Context) {
        prefs(context).edit().putBoolean(CHAVE_LIGADO, true).apply()
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }

    /** O motorista desligou de propósito: não avisa. */
    fun marcarDesligadoDeProposito(context: Context) {
        prefs(context).edit().putBoolean(CHAVE_LIGADO, false).apply()
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }

    /** Chamado no onDestroy do serviço. Só avisa se ele estava de fato trabalhando. */
    fun aoCair(context: Context) {
        if (!prefs(context).getBoolean(CHAVE_LIGADO, false)) return
        if (!Notificacoes.podeNotificar(context)) return
        criarCanal(context)

        val abrir = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val aviso = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("O leitor de ofertas desligou")
            .setContentText("As ofertas pararam de ser lidas. Toque para religar.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "O Android desligou o leitor do MotoristaPro. Enquanto ele estiver " +
                        "desligado, nenhuma oferta entra no histórico. Toque para abrir " +
                        "Acessibilidade e ligar de novo."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(ID, aviso) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
