package com.motoristapro.atualizacao

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.motoristapro.BuildConfig
import com.motoristapro.auth.esperar
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Versão publicada, lida do GitHub (ou do Firestore, como reserva). */
data class VersaoPublicada(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notas: String,
    val obrigatoria: Boolean
)

data class EstadoAtualizacao(
    val verificando: Boolean = false,
    val baixando: Boolean = false,
    val progresso: Int = 0,
    val disponivel: VersaoPublicada? = null,
    val mensagem: String? = null
)

/**
 * Atualização do app sem passar pelo Android Studio.
 *
 * Como funciona:
 *  1. Você envia o código para o GitHub; o GitHub Actions compila, assina e publica
 *     o APK junto com um arquivo `versao.json` na Release mais recente.
 *  2. O app lê `https://github.com/<usuario>/<repo>/releases/latest/download/versao.json`
 *     — esse endereço sempre aponta para a Release mais nova, sem nada manual.
 *  3. O app compara com a versão instalada, avisa o motorista, baixa e abre o instalador.
 *
 * Reserva: se o GitHub não estiver configurado ou estiver fora do ar, cai no
 * documento `app/versao` do Firestore (o jeito antigo, editado à mão).
 *
 * O Android sempre pede confirmação para instalar — não dá para instalar escondido,
 * e isso é proteção do sistema, não limitação do app.
 */
class AtualizacaoManager(private val context: Context) {

    private val _estado = MutableStateFlow(EstadoAtualizacao())
    val estado: StateFlow<EstadoAtualizacao> = _estado.asStateFlow()

    val versaoInstalada: String get() = BuildConfig.VERSION_NAME
    val codigoInstalado: Int get() = BuildConfig.VERSION_CODE

    private val disponivelFirebase: Boolean
        get() = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    /** `usuario/repositorio` configurado em res/values/atualizacao.xml. Vazio = não usar GitHub. */
    private val repoGitHub: String
        get() = runCatching {
            context.getString(com.motoristapro.R.string.repo_github).trim()
        }.getOrDefault("").let { if (it.contains("/") && !it.startsWith("SEU-")) it else "" }

    /**
     * Procura versão nova: primeiro no GitHub, depois no Firestore.
     * Retorna a versão nova, ou null se já está atualizado.
     */
    suspend fun verificar(avisarSeAtualizado: Boolean = false): VersaoPublicada? = withContext(Dispatchers.IO) {
        _estado.value = _estado.value.copy(verificando = true, mensagem = null)

        val publicada = lerDoGitHub() ?: lerDoFirestore()
        if (publicada == null) {
            _estado.value = _estado.value.copy(
                verificando = false,
                mensagem = if (avisarSeAtualizado) "Não foi possível verificar agora" else null
            )
            return@withContext null
        }
        if (publicada.versionCode <= codigoInstalado || publicada.url.isBlank()) {
            _estado.value = _estado.value.copy(
                verificando = false,
                disponivel = null,
                mensagem = if (avisarSeAtualizado) "Você já está na versão mais recente" else null
            )
            return@withContext null
        }
        _estado.value = _estado.value.copy(verificando = false, disponivel = publicada)
        publicada
    }

    /**
     * Lê o versao.json da Release mais recente. O endereço `/releases/latest/download/<arquivo>`
     * é fixo: o GitHub redireciona sozinho para a Release mais nova.
     */
    private fun lerDoGitHub(): VersaoPublicada? {
        val repo = repoGitHub.ifBlank { return null }
        val endereco = "https://github.com/$repo/releases/latest/download/versao.json"
        return try {
            val conexao = (URL(endereco).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = TEMPO_LIMITE_MS
                readTimeout = TEMPO_LIMITE_MS
                setRequestProperty("Accept", "application/json")
            }
            val texto = try {
                if (conexao.responseCode !in 200..299) return null
                conexao.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conexao.disconnect()
            }
            val j = JSONObject(texto)
            val codigo = j.optInt("versionCode", 0)
            val url = j.optString("url").orEmpty()
            if (codigo <= 0 || url.isBlank()) return null
            VersaoPublicada(
                versionCode = codigo,
                versionName = j.optString("versionName"),
                url = url,
                notas = j.optString("notas"),
                obrigatoria = j.optBoolean("obrigatoria", false)
            )
        } catch (e: Exception) {
            Log.w(TAG, "GitHub indisponível; tentando o Firestore", e)
            null
        }
    }

    /** Reserva: o documento `app/versao` do Firestore, preenchido à mão. */
    private suspend fun lerDoFirestore(): VersaoPublicada? {
        if (!disponivelFirebase) return null
        return try {
            val doc = FirebaseFirestore.getInstance().collection("app").document("versao").get().esperar()
            val codigo = (doc.getLong("versionCode") ?: 0L).toInt()
            if (codigo <= 0) return null
            VersaoPublicada(
                versionCode = codigo,
                versionName = doc.getString("versionName").orEmpty(),
                url = doc.getString("url").orEmpty(),
                notas = doc.getString("notas").orEmpty(),
                obrigatoria = doc.getBoolean("obrigatoria") ?: false
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao verificar atualização no Firestore", e)
            null
        }
    }

    /** Baixa o APK e abre o instalador do Android. */
    suspend fun baixarEInstalar(versao: VersaoPublicada) = withContext(Dispatchers.IO) {
        _estado.value = _estado.value.copy(baixando = true, progresso = 0, mensagem = null)
        try {
            val pasta = File(context.getExternalFilesDir(null), PASTA).apply { mkdirs() }
            pasta.listFiles()?.forEach { it.delete() }      // não acumula APKs antigos
            val destino = File(pasta, "MotoristaPro-${versao.versionCode}.apk")

            val gerenciador = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val pedido = DownloadManager.Request(Uri.parse(versao.url))
                .setTitle("MotoristaPro ${versao.versionName}")
                .setDescription("Baixando atualização")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                // dirType null = getExternalFilesDir(null), o mesmo caminho declarado em file_paths.xml
                .setDestinationInExternalFilesDir(context, null, "$PASTA/${destino.name}")
                .setMimeType("application/vnd.android.package-archive")
            val id = gerenciador.enqueue(pedido)

            val arquivo = aguardarDownload(gerenciador, id)
            _estado.value = _estado.value.copy(baixando = false, progresso = 100)
            abrirInstalador(arquivo)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao baixar atualização", e)
            _estado.value = _estado.value.copy(baixando = false, mensagem = "Erro ao baixar: ${e.message}")
            throw e
        }
    }

    private suspend fun aguardarDownload(gerenciador: DownloadManager, id: Long): File {
        val consulta = DownloadManager.Query().setFilterById(id)
        var tentativas = 0
        while (tentativas < MAX_ESPERAS) {
            gerenciador.query(consulta).use { c ->
                if (c.moveToFirst()) {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val baixado = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total > 0) {
                        _estado.value = _estado.value.copy(progresso = (baixado * 100 / total).toInt())
                    }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val local = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                            return File(Uri.parse(local).path ?: error("Download sem arquivo"))
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val motivo = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            error("Download falhou (código $motivo)")
                        }
                    }
                }
            }
            delay(INTERVALO_MS)
            tentativas++
        }
        error("O download demorou demais")
    }

    private fun abrirInstalador(arquivo: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Abre a tela onde o Android libera a instalação de apps deste app. */
    fun abrirPermissaoDeInstalacao() {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun podeInstalar(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun limparMensagem() {
        _estado.value = _estado.value.copy(mensagem = null)
    }

    fun dispensar() {
        _estado.value = _estado.value.copy(disponivel = null)
    }

    private companion object {
        const val TAG = "MotoristaPro"
        const val PASTA = "atualizacoes"
        const val INTERVALO_MS = 500L
        const val MAX_ESPERAS = 600      // 5 minutos
        const val TEMPO_LIMITE_MS = 15_000
    }
}
