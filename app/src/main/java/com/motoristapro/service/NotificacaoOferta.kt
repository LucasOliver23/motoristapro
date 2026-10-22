package com.motoristapro.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.motoristapro.data.repository.nomePlataforma
import com.motoristapro.jornada.Notificacoes
import java.util.Locale

/**
 * A oferta também na barra de notificações: fica lá depois que o cartão some,
 * com os números, os dois endereços e atalhos para o mapa e para registrar.
 *
 * Uma notificação só (sempre o mesmo id): a oferta nova substitui a anterior,
 * e a barra não vira uma lista de corridas que já passaram.
 */
object NotificacaoOferta {

    private val PT = Locale("pt", "BR")
    private const val ID = 1006
    const val ACAO_REGISTRAR = "com.motoristapro.REGISTRAR_OFERTA"

    @SuppressLint("MissingPermission")   // conferida em podeNotificar()
    fun mostrar(context: Context, o: Oferta, classe: Classificacao, custoKmCentavos: Long, pacote: String?) {
        if (!Notificacoes.podeNotificar(context)) return
        runCatching {
            val app = nomePlataforma(pacote)
            val titulo = buildString {
                append("$app · ${moeda(o.valor)}")
                if (o.minutos > 0) append(" · ${o.minutos} min")
                append(String.format(PT, " · %.1f km · ", o.km))
                append(classe.rotulo)
            }
            val numeros = buildList {
                add(String.format(PT, "R$/km %.2f", o.reaisPorKm))
                if (o.minutos > 0) add(String.format(PT, "R$/h %.2f", o.reaisPorHora))
                o.nota?.let { add(String.format(PT, "Nota %.2f", it)) }
                if (custoKmCentavos > 0) add("Lucro ${o.lucroPercentual(custoKmCentavos).toInt()}%")
            }.joinToString("  ·  ")
            val embarque = o.enderecos.firstOrNull()
            val destino = o.enderecos.takeIf { it.size > 1 }?.lastOrNull()
            val texto = buildString {
                append(numeros)
                embarque?.let { append("\n□ $it") }
                destino?.let { append("\n○ $it") }
            }

            val b = NotificationCompat.Builder(context, Notificacoes.CANAL_OFERTA)
                .setSmallIcon(com.motoristapro.R.drawable.ic_notificacao)
                .setContentTitle(titulo)
                .setContentText(numeros)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setColor(
                    when (classe) {
                        Classificacao.BOA -> 0xFF22A06B.toInt()
                        Classificacao.MEDIA -> 0xFFE8A800.toInt()
                        Classificacao.RUIM -> 0xFFE5484D.toInt()
                    }
                )
                .setOnlyAlertOnce(true)
                .setSilent(true)   // o app da corrida já tocou; aqui é para consultar
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            embarque?.let { b.addAction(0, "Mapa embarque", abrirMapa(context, it, 1)) }
            destino?.let { b.addAction(0, "Mapa destino", abrirMapa(context, it, 2)) }
            b.addAction(0, "Registrar", registrar(context))
            NotificationManagerCompat.from(context).notify(ID, b.build())
        }.onFailure { Log.w("MotoristaPro", "Falha ao mostrar notificação da oferta", it) }
    }

    fun cancelar(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }

    private fun abrirMapa(context: Context, endereco: String, codigo: Int): PendingIntent {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(endereco)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun registrar(context: Context): PendingIntent {
        val i = Intent(context, RegistrarOfertaReceiver::class.java).setAction(ACAO_REGISTRAR)
        return PendingIntent.getBroadcast(
            context, 3, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun moeda(v: Double) = if (v.isFinite()) String.format(PT, "R$ %.2f", v) else "—"
}

/** Botão "Registrar" da notificação: o serviço registra a oferta que ela mostra. */
class RegistrarOfertaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificacaoOferta.ACAO_REGISTRAR) return
        val servico = OfertaAccessibilityService.instancia
        if (servico == null) {
            NotificacaoOferta.cancelar(context)
            return
        }
        servico.registrarDaNotificacao()
    }
}
