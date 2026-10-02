package com.motoristapro

import android.app.Application
import android.util.Log
import com.motoristapro.assinatura.AssinaturaManager
import com.motoristapro.atualizacao.AtualizacaoManager
import com.motoristapro.auth.AutenticacaoManager
import com.motoristapro.data.local.AppDatabase
import com.motoristapro.data.prefs.PreferenciasApp
import com.motoristapro.data.repository.FinanceiroRepository
import com.motoristapro.jornada.Notificacoes
import com.motoristapro.jornada.RelogioJornada
import com.motoristapro.nuvem.SincronizacaoNuvem
import com.motoristapro.resumo.ResumoDiarioWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Injeção de dependência manual: sem Hilt/Koin para manter o build simples. */
class MotoristaApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: FinanceiroRepository by lazy { FinanceiroRepository(database) }
    val preferencias: PreferenciasApp by lazy { PreferenciasApp(this) }
    val autenticacao: AutenticacaoManager by lazy { AutenticacaoManager(this) }
    val nuvem: SincronizacaoNuvem by lazy { SincronizacaoNuvem(this, database, autenticacao) }
    val atualizacao: AtualizacaoManager by lazy { AtualizacaoManager(this) }
    val assinatura: AssinaturaManager by lazy { AssinaturaManager(this, autenticacao) }
    val relogio: RelogioJornada by lazy { RelogioJornada(this, repository) }

    /** Escopo para tarefas curtas de manutenção (vive enquanto o processo viver). */
    val escopoApp = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notificacoes.criarCanais(this)
        try {
            ResumoDiarioWorker.agendar(this)
        } catch (e: Exception) {
            Log.e("MotoristaPro", "Falha ao agendar resumo diário", e)
        }
        // Histórico de ofertas: guarda só os últimos 180 dias.
        escopoApp.launch {
            runCatching { repository.limparOfertasAntigas() }
                .onFailure { Log.w("MotoristaPro", "Falha ao limpar ofertas antigas", it) }
        }
    }
}
