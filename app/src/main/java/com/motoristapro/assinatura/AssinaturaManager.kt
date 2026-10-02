package com.motoristapro.assinatura

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.motoristapro.auth.AutenticacaoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Teste grátis e assinatura, com a verdade no servidor.
 *
 * Documento `assinaturas/{uid}` no Firestore:
 *   trialInicio    Timestamp  — carimbado pelo servidor no primeiro login
 *   assinaturaAte  Timestamp  — só a função do Mercado Pago escreve aqui
 *   plano          String     — "mensal" ou "trimestral"
 *   ultimoAcesso   Timestamp  — carimbo de servidor a cada abertura
 *
 * As regras do Firestore deixam o motorista CRIAR o documento com trialInicio
 * (uma vez) e ler o dele, mas não deixam mudar trialInicio nem assinaturaAte.
 * Sem isso, assinar seria editar um campo.
 *
 * Relógio: o celular pode ser adiantado ou atrasado de propósito. Por isso
 * guardamos o último carimbo de servidor que vimos (`ultimoAcesso`) e, quando o
 * relógio do aparelho está ATRÁS dele, usamos o carimbo. Adiantar o relógio só
 * faz o teste acabar antes — não ajuda ninguém.
 */
class AssinaturaManager(
    private val context: Context,
    private val autenticacao: AutenticacaoManager
) {

    private val prefs = context.getSharedPreferences("assinatura", Context.MODE_PRIVATE)
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val firestore: FirebaseFirestore? = try {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "Firestore indisponível; app segue liberado", e)
        null
    }

    private val _acesso = MutableStateFlow(
        if (firestore == null || !autenticacao.disponivel) {
            Acesso(situacao = SituacaoAcesso.SEM_CONTA)
        } else {
            Acesso(situacao = SituacaoAcesso.CARREGANDO)
        }
    )
    val acesso: StateFlow<Acesso> = _acesso.asStateFlow()

    private val _precos = MutableStateFlow(PrecoPlano.PADRAO)
    val precos: StateFlow<List<PrecoPlano>> = _precos.asStateFlow()

    private var ouvinte: ListenerRegistration? = null

    init {
        if (firestore != null && autenticacao.disponivel) {
            escopo.launch {
                autenticacao.usuario.collectLatest { usuario ->
                    ouvinte?.remove()
                    ouvinte = null
                    if (usuario == null) {
                        _acesso.value = Acesso(situacao = SituacaoAcesso.CARREGANDO)
                    } else {
                        garantirDocumento(usuario.uid)
                        acompanhar(usuario.uid)
                    }
                }
            }
            carregarPrecos()
        }
    }

    /** Primeiro login da conta: cria o documento e deixa o SERVIDOR carimbar o início do teste. */
    private fun garantirDocumento(uid: String) {
        val doc = firestore?.collection(COLECAO)?.document(uid) ?: return
        doc.get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                doc.set(
                    mapOf(
                        "trialInicio" to FieldValue.serverTimestamp(),
                        "ultimoAcesso" to FieldValue.serverTimestamp(),
                        "email" to autenticacao.usuario.value?.email
                    )
                ).addOnFailureListener { Log.w(TAG, "Não consegui criar a assinatura", it) }
            } else {
                doc.update("ultimoAcesso", FieldValue.serverTimestamp())
                    .addOnFailureListener { Log.w(TAG, "Não consegui carimbar o acesso", it) }
            }
        }.addOnFailureListener { Log.w(TAG, "Não consegui ler a assinatura", it) }
    }

    /** Fica ouvindo o documento: o pagamento cai e a tela destrava sozinha. */
    private fun acompanhar(uid: String) {
        ouvinte = firestore?.collection(COLECAO)?.document(uid)
            ?.addSnapshotListener { snap, erro ->
                if (erro != null) {
                    Log.w(TAG, "Falha ao acompanhar a assinatura", erro)
                    // Sem internet não se tranca o app de quem já pagou: vale o
                    // que estava guardado da última vez que deu certo.
                    _acesso.value = daMemoria()
                    return@addSnapshotListener
                }
                val trialInicio = snap?.getTimestamp("trialInicio")?.toDate()?.time ?: 0
                val assinaturaAte = snap?.getTimestamp("assinaturaAte")?.toDate()?.time ?: 0
                val servidor = snap?.getTimestamp("ultimoAcesso")?.toDate()?.time ?: 0
                val plano = snap?.getString("plano")
                    ?.let { chave -> Plano.entries.firstOrNull { it.chave == chave } }

                guardar(trialInicio, assinaturaAte, servidor, plano)
                _acesso.value = montar(trialInicio, assinaturaAte, servidor, plano)
            }
    }

    /** Preços e links de checkout, para mudar sem publicar versão nova. */
    private fun carregarPrecos() {
        firestore?.collection("config")?.document("precos")?.addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) return@addSnapshotListener
            val lista = Plano.entries.map { plano ->
                PrecoPlano(
                    plano = plano,
                    centavos = snap.getLong("${plano.chave}_centavos")
                        ?: PrecoPlano.PADRAO.first { it.plano == plano }.centavos,
                    linkCheckout = snap.getString("${plano.chave}_link")
                )
            }
            _precos.value = lista
        }
    }

    // ------------------------------------------------------------------ contas

    private fun montar(
        trialInicioMs: Long,
        assinaturaAteMs: Long,
        servidorMs: Long,
        plano: Plano?
    ): Acesso {
        val agora = agoraConfiavel(servidorMs)
        val trialFim = if (trialInicioMs > 0) trialInicioMs + Acesso.MS_DE_TESTE else 0

        val situacao = when {
            assinaturaAteMs > agora -> SituacaoAcesso.ASSINATURA_ATIVA
            assinaturaAteMs > 0 -> SituacaoAcesso.ASSINATURA_VENCIDA
            trialFim > agora -> SituacaoAcesso.TESTE_ATIVO
            trialFim > 0 -> SituacaoAcesso.TESTE_ACABOU
            // Documento ainda sendo criado: não trava ninguém por causa de latência.
            else -> SituacaoAcesso.CARREGANDO
        }
        return Acesso(situacao, trialFim, assinaturaAteMs, plano, agora)
    }

    /**
     * O "agora" que vale: o do celular, a não ser que ele esteja ATRÁS do último
     * carimbo de servidor que já vimos — aí vale o carimbo.
     */
    private fun agoraConfiavel(servidorMs: Long): Long {
        val visto = maxOf(servidorMs, prefs.getLong(KEY_SERVIDOR, 0))
        return maxOf(System.currentTimeMillis(), visto)
    }

    private fun guardar(trialInicio: Long, assinaturaAte: Long, servidor: Long, plano: Plano?) {
        prefs.edit()
            .putLong(KEY_TRIAL, trialInicio)
            .putLong(KEY_ATE, assinaturaAte)
            .putLong(KEY_SERVIDOR, maxOf(servidor, prefs.getLong(KEY_SERVIDOR, 0)))
            .putString(KEY_PLANO, plano?.chave)
            .apply()
    }

    /** O que sabíamos da última vez que o Firestore respondeu (modo sem internet). */
    private fun daMemoria(): Acesso = montar(
        trialInicioMs = prefs.getLong(KEY_TRIAL, 0),
        assinaturaAteMs = prefs.getLong(KEY_ATE, 0),
        servidorMs = prefs.getLong(KEY_SERVIDOR, 0),
        plano = prefs.getString(KEY_PLANO, null)
            ?.let { chave -> Plano.entries.firstOrNull { it.chave == chave } }
    )

    /** Chamado quando o motorista volta do navegador: força uma releitura. */
    fun reconferir() {
        autenticacao.usuario.value?.uid?.let { uid ->
            firestore?.collection(COLECAO)?.document(uid)?.get()
                ?.addOnSuccessListener { snap ->
                    val trialInicio = snap.getTimestamp("trialInicio")?.toDate()?.time ?: 0
                    val ate = snap.getTimestamp("assinaturaAte")?.toDate()?.time ?: 0
                    val servidor = snap.getTimestamp("ultimoAcesso")?.toDate()?.time ?: 0
                    val plano = snap.getString("plano")
                        ?.let { chave -> Plano.entries.firstOrNull { it.chave == chave } }
                    guardar(trialInicio, ate, servidor, plano)
                    _acesso.value = montar(trialInicio, ate, servidor, plano)
                }
        }
    }

    /** O identificador que vai no checkout, para o pagamento voltar na conta certa. */
    val referencia: String? get() = autenticacao.usuario.value?.uid

    private companion object {
        const val TAG = "MotoristaPro"
        const val COLECAO = "assinaturas"
        const val KEY_TRIAL = "trial_inicio"
        const val KEY_ATE = "assinatura_ate"
        const val KEY_SERVIDOR = "servidor_visto"
        const val KEY_PLANO = "plano"
    }
}
