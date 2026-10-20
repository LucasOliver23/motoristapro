package com.motoristapro.radar

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.motoristapro.auth.AutenticacaoManager
import com.motoristapro.auth.esperar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * De onde vêm os eventos do radar.
 *
 * - Lista do MotoristaPro: coleção `eventos` no Firestore. Todo motorista
 *   logado lê; só quem está em `admins/{uid}` publica (regras do servidor).
 * - Do motorista: anotados no próprio celular (preferências), sem internet.
 */
class RadarEventos(context: Context, private val autenticacao: AutenticacaoManager) {

    private val prefs = context.applicationContext.getSharedPreferences("radar_eventos", Context.MODE_PRIVATE)

    private val _eventos = MutableStateFlow(locais())
    val eventos: StateFlow<List<Evento>> = _eventos

    private val _admin = MutableStateFlow(false)
    /** Pode publicar na lista de todos (tem documento em admins/{uid}). */
    val admin: StateFlow<Boolean> = _admin

    private val firestore: FirebaseFirestore?
        get() = if (autenticacao.disponivel) runCatching { FirebaseFirestore.getInstance() }.getOrNull() else null

    /** Busca a lista do MotoristaPro (próximos dias) e junta com os do motorista. */
    suspend fun atualizar() = withContext(Dispatchers.IO) {
        val db = firestore
        val uid = autenticacao.usuario.value?.uid
        val daNuvem = if (db != null && uid != null) runCatching {
            val desde = System.currentTimeMillis() - CalculoRadar.TOLERANCIA_MS
            db.collection(COLECAO).whereGreaterThanOrEqualTo("saidaEm", desde)
                .orderBy("saidaEm").limit(200).get().esperar()
                .documents.mapNotNull { d ->
                    Evento(
                        id = d.id,
                        titulo = d.getString("titulo") ?: return@mapNotNull null,
                        local = d.getString("local").orEmpty(),
                        endereco = d.getString("endereco"),
                        cidade = d.getString("cidade").orEmpty(),
                        tipo = TipoEvento.deNome(d.getString("tipo")),
                        inicioEm = d.getLong("inicioEm") ?: return@mapNotNull null,
                        saidaEm = d.getLong("saidaEm") ?: return@mapNotNull null,
                        movimento = Movimento.deNome(d.getString("movimento"))
                    )
                }
        }.onFailure { Log.w(TAG, "Radar: não deu para buscar a lista", it) }.getOrNull() else null

        if (db != null && uid != null) {
            _admin.value = runCatching { db.collection("admins").document(uid).get().esperar().exists() }
                .getOrDefault(false)
        }
        _eventos.value = (daNuvem ?: _eventos.value.filterNot { it.doMotorista }) + locais()
    }

    /** Publica para todos (só admin). */
    suspend fun publicar(e: Evento) = withContext(Dispatchers.IO) {
        val db = firestore ?: error("Entre na sua conta para publicar")
        val uid = autenticacao.usuario.value?.uid ?: error("Entre na sua conta para publicar")
        db.collection(COLECAO).add(
            mapOf(
                "titulo" to e.titulo, "local" to e.local, "endereco" to e.endereco,
                "cidade" to e.cidade, "tipo" to e.tipo.name,
                "inicioEm" to e.inicioEm, "saidaEm" to e.saidaEm,
                "movimento" to e.movimento.name,
                "criadoPor" to uid, "criadoEm" to System.currentTimeMillis()
            )
        ).esperar()
        atualizar()
    }

    /** Apaga da lista de todos (só admin). */
    suspend fun apagarPublicado(id: String) = withContext(Dispatchers.IO) {
        firestore?.collection(COLECAO)?.document(id)?.delete()?.esperar()
        atualizar()
    }

    // ------------------------------------------------------------ do motorista

    fun adicionarLocal(e: Evento) {
        val lista = locais().filter { it.saidaEm >= System.currentTimeMillis() - CalculoRadar.TOLERANCIA_MS } +
            e.copy(id = "local_${System.currentTimeMillis()}", doMotorista = true)
        salvarLocais(lista)
        _eventos.value = _eventos.value.filterNot { it.doMotorista } + lista
    }

    fun removerLocal(id: String) {
        val lista = locais().filterNot { it.id == id }
        salvarLocais(lista)
        _eventos.value = _eventos.value.filterNot { it.doMotorista } + lista
    }

    private fun locais(): List<Evento> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_LOCAIS, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Evento(
                id = o.getString("id"), titulo = o.getString("titulo"), local = o.optString("local"),
                endereco = o.optString("endereco").takeIf { it.isNotBlank() }, cidade = o.optString("cidade"),
                tipo = TipoEvento.deNome(o.optString("tipo")), inicioEm = o.getLong("inicioEm"),
                saidaEm = o.getLong("saidaEm"), movimento = Movimento.deNome(o.optString("movimento")),
                doMotorista = true
            )
        }
    }.getOrDefault(emptyList())

    private fun salvarLocais(lista: List<Evento>) {
        val arr = JSONArray()
        lista.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("titulo", it.titulo).put("local", it.local)
                    .put("endereco", it.endereco.orEmpty()).put("cidade", it.cidade).put("tipo", it.tipo.name)
                    .put("inicioEm", it.inicioEm).put("saidaEm", it.saidaEm).put("movimento", it.movimento.name)
            )
        }
        prefs.edit().putString(KEY_LOCAIS, arr.toString()).apply()
    }

    // ------------------------------------------------------------ avisos marcados

    fun avisosMarcados(): Set<String> = prefs.getStringSet(KEY_AVISOS, emptySet()).orEmpty()

    fun marcarAviso(id: String, ligado: Boolean) {
        val atual = avisosMarcados()
        prefs.edit().putStringSet(KEY_AVISOS, if (ligado) atual + id else atual - id).apply()
    }

    private companion object {
        const val TAG = "MotoristaPro"
        const val COLECAO = "eventos"
        const val KEY_LOCAIS = "locais"
        const val KEY_AVISOS = "avisos"
    }
}
