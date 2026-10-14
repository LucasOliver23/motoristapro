package com.motoristapro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.motoristapro.MotoristaApp
import com.motoristapro.R
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.repository.Periodo
import com.motoristapro.data.repository.emReais
import com.motoristapro.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Widget "Lucro de hoje" na tela inicial: o lucro, a barra da meta e um botão
 * de iniciar a jornada (ou abrir o app, se o turno já está rodando).
 *
 * Duas fontes de atualização:
 *  - com o app vivo, [acompanhar] redesenha a cada corrida/despesa/turno;
 *  - com o app fechado, o Android chama [onUpdate] a cada 30 min.
 */
class WidgetLucro : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as MotoristaApp
        val pendente = goAsync()
        app.escopoApp.launch {
            try {
                desenhar(context, lerDados(app).first())
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao atualizar o widget", e)
            } finally {
                pendente.finish()
            }
        }
    }

    /** O que o widget mostra. Dinheiro em centavos. */
    data class Dados(
        val lucroCentavos: Long,
        val metaCentavos: Long,
        val jornada: Jornada?
    ) {
        val progresso: Int
            get() = if (metaCentavos > 0)
                (lucroCentavos * 100 / metaCentavos).coerceIn(0, 100).toInt() else 0
    }

    companion object {
        private const val TAG = "MotoristaPro"

        /** Ação do botão: abre o app e já pede para iniciar a jornada. */
        const val ACAO_INICIAR_JORNADA = "com.motoristapro.INICIAR_JORNADA"

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun lerDados(app: MotoristaApp): Flow<Dados> {
            val repo = app.repository
            val lucroHoje: Flow<Long> = Periodo.hoje().flatMapLatest { dia: LocalDate ->
                val (ini, fim) = Periodo.dia(dia)
                repo.resumoDoPeriodo(ini, fim)
            }.map { it.lucroLiquidoCentavos }
            return combine(lucroHoje, repo.configuracao(), repo.jornadaAtiva()) { lucro, cfg, jornada ->
                Dados(lucro, cfg.metaLucroDiarioCentavos, jornada)
            }
        }

        /**
         * Chamado uma vez no onCreate do app: enquanto o processo viver, o widget
         * acompanha o banco. Sem widget na tela, só desenha no vazio (custa nada).
         */
        fun acompanhar(app: MotoristaApp, escopo: CoroutineScope) {
            escopo.launch {
                lerDados(app)
                    .distinctUntilChanged()
                    .catch { Log.w(TAG, "Widget parou de acompanhar o banco", it) }
                    .collect { dados ->
                        if (temWidget(app)) runCatching { desenhar(app, dados) }
                            .onFailure { Log.w(TAG, "Falha ao desenhar o widget", it) }
                    }
            }
        }

        private fun temWidget(c: Context): Boolean =
            AppWidgetManager.getInstance(c)
                .getAppWidgetIds(ComponentName(c, WidgetLucro::class.java)).isNotEmpty()

        private fun desenhar(c: Context, d: Dados) {
            val manager = AppWidgetManager.getInstance(c)
            val ids = manager.getAppWidgetIds(ComponentName(c, WidgetLucro::class.java))
            if (ids.isEmpty()) return

            val v = RemoteViews(c.packageName, R.layout.widget_lucro)
            v.setTextViewText(R.id.widget_valor, d.lucroCentavos.emReais())
            v.setTextColor(R.id.widget_valor, if (d.lucroCentavos < 0) COR_PREJUIZO else COR_TEXTO)
            v.setProgressBar(R.id.widget_barra, 100, d.progresso, false)
            v.setTextViewText(
                R.id.widget_meta,
                when {
                    d.metaCentavos <= 0 -> "Defina a meta no app"
                    d.lucroCentavos >= d.metaCentavos -> "Meta batida! 🎉"
                    else -> "${d.progresso}% da meta de ${d.metaCentavos.emReais()}"
                }
            )
            v.setTextViewText(
                R.id.widget_situacao,
                when {
                    d.jornada == null -> "turno parado"
                    d.jornada.pausada -> "turno pausado"
                    else -> "● rodando"
                }
            )

            val abrir = intentApp(c, null, CODIGO_ABRIR)
            v.setOnClickPendingIntent(R.id.widget_raiz, abrir)
            if (d.jornada == null) {
                v.setTextViewText(R.id.widget_botao, "▶  Iniciar jornada")
                v.setTextColor(R.id.widget_botao, COR_SOBRE_AZUL)
                v.setInt(R.id.widget_botao, "setBackgroundResource", R.drawable.widget_botao)
                v.setOnClickPendingIntent(R.id.widget_botao, intentApp(c, ACAO_INICIAR_JORNADA, CODIGO_INICIAR))
            } else {
                v.setTextViewText(R.id.widget_botao, "Abrir o app")
                v.setTextColor(R.id.widget_botao, COR_TEXTO)
                v.setInt(R.id.widget_botao, "setBackgroundResource", R.drawable.widget_botao_escuro)
                v.setOnClickPendingIntent(R.id.widget_botao, abrir)
            }
            manager.updateAppWidget(ids, v)
        }

        private fun intentApp(c: Context, acao: String?, codigo: Int): PendingIntent {
            val i = Intent(c, MainActivity::class.java)
                .setAction(acao ?: Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                c, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private const val CODIGO_ABRIR = 4100
        private const val CODIGO_INICIAR = 4101
        private val COR_TEXTO = 0xFFFFFFFF.toInt()
        private val COR_SOBRE_AZUL = 0xFF04131B.toInt()
        private val COR_PREJUIZO = 0xFFFF5252.toInt()
    }
}
