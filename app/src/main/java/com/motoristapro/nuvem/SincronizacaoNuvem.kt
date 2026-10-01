package com.motoristapro.nuvem

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.motoristapro.auth.AutenticacaoManager
import com.motoristapro.auth.esperar
import com.motoristapro.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** O que a tela mostra sobre a nuvem. */
data class EstadoNuvem(
    val ocupado: Boolean = false,
    val ultimoEnvioEm: Long = 0,
    val atualizadoNaNuvemEm: Long = 0,
    val corridasNaNuvem: Int = -1,
    val erro: String? = null
)

/**
 * Backup dos dados na nuvem (Firestore), um documento por conta: usuarios/{uid}.
 *
 * O conteúdo vai como JSON compactado (gzip) num único campo, o que mantém o documento
 * pequeno e bem abaixo do limite de 1 MB do Firestore.
 *
 * O histórico de ofertas NÃO sobe: é grande, muda o tempo todo e só serve no aparelho.
 */
class SincronizacaoNuvem(
    private val context: Context,
    private val db: AppDatabase,
    private val autenticacao: AutenticacaoManager
) {

    private val prefs = context.getSharedPreferences("sincronizacao", Context.MODE_PRIVATE)

    private val _estado = MutableStateFlow(
        EstadoNuvem(ultimoEnvioEm = prefs.getLong(KEY_ULTIMO_ENVIO, 0))
    )
    val estado: StateFlow<EstadoNuvem> = _estado.asStateFlow()

    private val firestore: FirebaseFirestore?
        get() = if (autenticacao.disponivel) runCatching { FirebaseFirestore.getInstance() }.getOrNull() else null

    private fun documento(): com.google.firebase.firestore.DocumentReference? {
        val uid = autenticacao.usuario.value?.uid ?: return null
        return firestore?.collection("usuarios")?.document(uid)
    }

    // ================================================================== enviar

    /** Sobe uma cópia completa dos dados deste celular para a conta logada. */
    suspend fun enviar(): Int = withContext(Dispatchers.IO) {
        val doc = documento() ?: error("Entre na sua conta para usar a nuvem")
        _estado.value = _estado.value.copy(ocupado = true, erro = null)
        try {
            val pacote = db.sincronizacaoDao().exportar()
            val comprimido = comprimir(DadosJson.paraJson(pacote))
            val agora = System.currentTimeMillis()
            doc.set(
                mapOf(
                    "dados" to Blob.fromBytes(comprimido),
                    "atualizadoEm" to agora,
                    "versaoBanco" to AppDatabase.VERSAO,
                    "aparelho" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "totalCorridas" to pacote.corridas.size,
                    "bytes" to comprimido.size
                )
            ).esperar()
            prefs.edit().putLong(KEY_ULTIMO_ENVIO, agora).apply()
            _estado.value = _estado.value.copy(
                ocupado = false, ultimoEnvioEm = agora,
                atualizadoNaNuvemEm = agora, corridasNaNuvem = pacote.corridas.size
            )
            pacote.corridas.size
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar para a nuvem", e)
            _estado.value = _estado.value.copy(ocupado = false, erro = e.message)
            throw e
        }
    }

    // ================================================================== baixar

    /** Substitui TODOS os dados deste celular pelos da nuvem. */
    suspend fun baixar(): Int = withContext(Dispatchers.IO) {
        val doc = documento() ?: error("Entre na sua conta para usar a nuvem")
        _estado.value = _estado.value.copy(ocupado = true, erro = null)
        try {
            val snapshot = doc.get().esperar()
            val blob = snapshot.getBlob("dados") ?: error("Ainda não há backup na nuvem nesta conta")
            val pacote = DadosJson.deJson(descomprimir(blob.toBytes()))
            db.sincronizacaoDao().importar(pacote)
            val atualizado = snapshot.getLong("atualizadoEm") ?: 0
            _estado.value = _estado.value.copy(
                ocupado = false, atualizadoNaNuvemEm = atualizado, corridasNaNuvem = pacote.corridas.size
            )
            pacote.corridas.size
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao baixar da nuvem", e)
            _estado.value = _estado.value.copy(ocupado = false, erro = e.message)
            throw e
        }
    }

    /** Só consulta o que existe na nuvem, sem mexer nos dados locais. */
    suspend fun consultar() = withContext(Dispatchers.IO) {
        val doc = documento() ?: return@withContext
        runCatching {
            val s = doc.get().esperar()
            _estado.value = _estado.value.copy(
                atualizadoNaNuvemEm = s.getLong("atualizadoEm") ?: 0,
                corridasNaNuvem = (s.getLong("totalCorridas") ?: -1L).toInt()
            )
        }.onFailure { Log.w(TAG, "Não foi possível consultar a nuvem", it) }
    }

    /**
     * Chamado logo depois do login.
     * - Celular sem nenhum dado e nuvem com backup -> baixa (caso de celular novo).
     * - Nos outros casos -> envia, para a nuvem ficar igual a este celular.
     * Nunca apaga dados locais sem que estejam vazios; para trocar de celular com dados,
     * o motorista usa o botão "Baixar da nuvem".
     */
    suspend fun sincronizarAoEntrar(): String = withContext(Dispatchers.IO) {
        val doc = documento() ?: return@withContext "Entre na sua conta para usar a nuvem"
        try {
            val snapshot = doc.get().esperar()
            val temNuvem = snapshot.getBlob("dados") != null
            val pacoteLocal = db.sincronizacaoDao().exportar()
            val localVazio = pacoteLocal.corridas.isEmpty() && pacoteLocal.despesas.isEmpty()

            if (temNuvem && localVazio) {
                val qtd = baixar()
                "Dados da sua conta restaurados ($qtd corridas)"
            } else {
                val qtd = enviar()
                "Backup na nuvem atualizado ($qtd corridas)"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha na sincronização inicial", e)
            _estado.value = _estado.value.copy(ocupado = false, erro = e.message)
            "Não foi possível sincronizar agora: ${e.message}"
        }
    }

    // ================================================================== compactação

    private fun comprimir(texto: String): ByteArray {
        val saida = ByteArrayOutputStream()
        GZIPOutputStream(saida).use { it.write(texto.toByteArray(Charsets.UTF_8)) }
        return saida.toByteArray()
    }

    private fun descomprimir(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }

    private companion object {
        const val TAG = "MotoristaPro"
        const val KEY_ULTIMO_ENVIO = "ultimo_envio_em"
    }
}
