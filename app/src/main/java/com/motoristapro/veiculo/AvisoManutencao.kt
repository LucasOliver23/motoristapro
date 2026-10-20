package com.motoristapro.veiculo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.motoristapro.ui.MainActivity
import com.motoristapro.MotoristaApp
import com.motoristapro.diagnostico.RegistroErros
import com.motoristapro.jornada.Notificacoes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * O lembrete de manutenção na barra do celular, com "Fiz agora" e "Lembrar amanhã".
 *
 * Conferido quando o km muda (início/fim da jornada, correção) e quando o app
 * abre: é o km que vence a peça, não o relógio.
 */
object AvisoManutencao {

    const val CANAL = "manutencao"
    private const val PREFS = "lembrete_manutencao"
    private const val CHAVE_AVISADOS = "avisados"
    private const val PREFIXO_ADIADO = "adiado_"
    private const val UM_DIA_MS = 24 * 60 * 60 * 1000L
    private const val ID_BASE = 2000

    const val ACAO_FIZ = "com.motoristapro.MANUTENCAO_FIZ"
    const val ACAO_ADIAR = "com.motoristapro.MANUTENCAO_ADIAR"
    const val EXTRA_ID = "item_id"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun conferir(context: Context) {
        runCatching {
            val app = context.applicationContext as MotoristaApp
            val itens = app.repository.manutencoes().first()
            if (itens.isEmpty()) return
            val km = app.odometro.atual()?.km ?: return
            val p = prefs(context)
            val avisados = p.getStringSet(CHAVE_AVISADOS, emptySet()).orEmpty()
            val adiado = itens.associate { it.id to p.getLong(PREFIXO_ADIADO + it.id, 0L) }
            val lembretes = CalculoLembrete.quaisAvisar(
                itens, km, app.preferencias.avisoManutencaoKm.value, avisados, adiado, System.currentTimeMillis()
            )
            if (lembretes.isEmpty()) return
            criarCanal(context)
            if (!Notificacoes.podeNotificar(context)) return
            lembretes.forEach { notificar(context, it) }
            // Guarda só as chaves dos itens que ainda existem: a lista não cresce para sempre.
            val vivos = itens.map { it.id.toString() + ":" }.toSet()
            p.edit().putStringSet(
                CHAVE_AVISADOS,
                (avisados.filter { a -> vivos.any { a.startsWith(it) } } + lembretes.map { it.chave }).toSet()
            ).apply()
        }.onFailure { RegistroErros.anotar("lembrete de manutenção", it) }
    }

    private fun criarCanal(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Lembrete de manutenção")
                .setDescription("Avisa quando a troca de óleo, pneu ou revisão está chegando pelo km")
                .build()
        )
    }

    private fun notificar(context: Context, l: Lembrete) {
        val id = ID_BASE + l.item.id.toInt()
        fun acao(acao: String, codigo: Int) = PendingIntent.getBroadcast(
            context, id * 10 + codigo,
            Intent(context, AvisoManutencaoReceiver::class.java).setAction(acao).putExtra(EXTRA_ID, l.item.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val abrir = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val vence = String.format(java.util.Locale("pt", "BR"), "%,d", l.item.odometroDoVencimento)
        val titulo = if (l.fase == FaseLembrete.VENCIDO) "${l.item.nome}: passou ${-l.faltamKm} km"
        else "${l.item.nome} em ${l.faltamKm} km"
        val aviso = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(titulo)
            .setContentText("Próxima em $vence km")
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .addAction(0, "Fiz agora", acao(ACAO_FIZ, 1))
            .addAction(0, "Lembrar amanhã", acao(ACAO_ADIAR, 2))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, aviso) }
    }

    /** "Fiz agora" pela notificação: a troca fica no km de hoje. */
    suspend fun fizAgora(context: Context, itemId: Long) {
        val app = context.applicationContext as MotoristaApp
        val item = app.repository.manutencoes().first().firstOrNull { it.id == itemId } ?: return
        val km = app.odometro.atual()?.km ?: return
        app.repository.salvarManutencao(item.copy(odometroUltimaKm = km, atualizadoEm = System.currentTimeMillis()))
        cancelar(context, itemId)
    }

    fun adiar(context: Context, itemId: Long) {
        val p = prefs(context)
        p.edit()
            .putLong(PREFIXO_ADIADO + itemId, System.currentTimeMillis() + UM_DIA_MS)
            // Sem a chave, o mesmo aviso pode voltar depois do prazo.
            .putStringSet(CHAVE_AVISADOS, p.getStringSet(CHAVE_AVISADOS, emptySet()).orEmpty()
                .filterNot { it.startsWith("$itemId:") }.toSet())
            .apply()
        cancelar(context, itemId)
    }

    private fun cancelar(context: Context, itemId: Long) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_BASE + itemId.toInt()) }
    }
}

class AvisoManutencaoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AvisoManutencao.EXTRA_ID, -1L).takeIf { it >= 0 } ?: return
        when (intent.action) {
            AvisoManutencao.ACAO_ADIAR -> AvisoManutencao.adiar(context, id)
            AvisoManutencao.ACAO_FIZ -> {
                val pendente = goAsync()
                val app = context.applicationContext as MotoristaApp
                app.escopoApp.launch {
                    try {
                        AvisoManutencao.fizAgora(context, id)
                    } finally {
                        pendente.finish()
                    }
                }
            }
        }
    }
}
