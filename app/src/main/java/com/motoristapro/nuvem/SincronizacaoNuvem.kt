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

    /**
     * De quem sao os dados que estao neste celular (uid da conta).
     *
     * null = ninguem logou ainda; os dados sao de quem usava o app sem conta e
     * passam a ser da primeira conta que entrar — por isso a troca so dispara
     * quando ja existe um dono DIFERENTE, nunca no primeiro login.
     */
    private var dono: String?
        get() = prefs.getString(KEY_DONO, null)
        set(v) = prefs.edit().putString(KEY_DONO, v).apply()

    /**
     * Apaga os ajustes que sao do motorista, nao do aparelho: faixas do semaforo,
     * enderecos de risco, estilo do cartao, voz, app de navegacao.
     *
     * O tema (claro/escuro) fica: e preferencia de quem esta olhando a tela, e
     * troca-lo sozinho no login so assustaria.
     */
    /**
     * "Comecar do zero neste celular", do botao em Mais > Conta e nuvem.
     *
     * Nao mexe na nuvem: o backup da conta continua la, e o motorista pode
     * trazer tudo de volta com "Baixar da nuvem" se tiver se arrependido.
     */
    suspend fun zerarCelular() = withContext(Dispatchers.IO) {
        db.sincronizacaoDao().zerar()
        limparPreferenciasDoMotorista()
        dono = autenticacao.usuario.value?.uid
    }

    private fun limparPreferenciasDoMotorista() {
        listOf(
            "limites_oferta", "enderecos_risco", "estilo_cartao",
            "aviso_voz", "navegacao", "preferencias_app", "preparacao"
        ).forEach { arquivo ->
            runCatching {
                context.getSharedPreferences(arquivo, Context.MODE_PRIVATE).edit().clear().apply()
            }
        }
    }

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
        val uid = autenticacao.usuario.value?.uid
            ?: return@withContext "Entre na sua conta para usar a nuvem"
        val doc = documento() ?: return@withContext "Entre na sua conta para usar a nuvem"

        // TROCA DE CONTA: os dados deste celular sao do motorista anterior.
        // Antes isto SUBIA o historico dele para a conta nova — o contrario do
        // que se espera de um app em que cada um entra com a sua conta.
        val trocouDeConta = dono != null && dono != uid
        if (trocouDeConta) {
            db.sincronizacaoDao().zerar()
            limparPreferenciasDoMotorista()
        }

        try {
            val snapshot = doc.get().esperar()
            val temNuvem = snapshot.getBlob("dados") != null
            val pacoteLocal = db.sincronizacaoDao().exportar()
            val localVazio = pacoteLocal.corridas.isEmpty() && pacoteLocal.despesas.isEmpty()

            val mensagem = when {
                temNuvem && localVazio -> "Dados da sua conta restaurados (${baixar()} corridas)"
                // Conta nova e sem backup: comeca do zero mesmo, sem subir nada.
                trocouDeConta -> "Conta nova neste celular — começando do zero"
                else -> "Backup na nuvem atualizado (${enviar()} corridas)"
            }
            dono = uid
            mensagem
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
        const val KEY_DONO = "dono_dos_dados"
    }
}
