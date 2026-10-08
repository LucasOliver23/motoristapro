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
import com.motoristapro.nuvem.SincronizacaoNuvem
import com.motoristapro.resumo.ResumoDiarioWorker
import com.motoristapro.service.AvisoLeitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

    /** Escopo para tarefas curtas de manutenção (vive enquanto o processo viver). */
    val escopoApp = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notificacoes.criarCanais(this)
        runCatching { AvisoLeitor.criarCanal(this) }
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
        escopoApp.launch { adotarNomeDaConta() }
    }

    /**
     * Quem entrou com a conta do Google não deveria digitar o próprio nome.
     *
     * Roda uma vez por sessão e só preenche o que está VAZIO: se o motorista
     * trocou o nome na mão depois, o nome dele fica — o do Google não volta por
     * cima. Vale para o telefone também, quando a conta traz um.
     */
    private suspend fun adotarNomeDaConta() {
        runCatching {
            val conta = autenticacao.usuario.filterNotNull().first()
            val doGoogle = conta.nome?.trim().orEmpty()
            if (doGoogle.isBlank()) return
            val cfg = repository.configuracao().first()
            if (!cfg.nomeMotorista.isNullOrBlank()) return
            repository.salvarConfiguracao(cfg.copy(nomeMotorista = doGoogle))
            Log.i("MotoristaPro", "Nome do perfil preenchido pela conta do Google")
        }.onFailure { Log.w("MotoristaPro", "Não deu para usar o nome da conta", it) }
    }
}
