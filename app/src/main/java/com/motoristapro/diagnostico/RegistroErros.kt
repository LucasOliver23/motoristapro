package com.motoristapro.diagnostico

import android.content.Context
import android.util.Log
import com.motoristapro.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda o último erro do app num arquivo, para o motorista poder mandar.
 *
 * Quando o processo quebra, o leitor de ofertas cai junto — e, se quebrar
 * mais de uma vez, o Android marca o serviço como "não está funcionando" e
 * desliga. Sem computador ligado no celular, o único jeito de saber O QUE
 * quebrou é o próprio app anotar antes de morrer. O Checkup mostra e copia.
 */
object RegistroErros {

    /** Quebra do processo: é o que derruba o leitor, e nunca é sobrescrita por erro tratado. */
    private const val ARQUIVO = "ultimo_erro.txt"

    /** Erros pegos e contornados: úteis, mas em arquivo separado para não apagar a quebra. */
    private const val ARQUIVO_TRATADO = "ultimo_erro_tratado.txt"

    private const val MAXIMO = 6_000

    /** O mesmo erro tratado se repetindo a cada leitura grava no máximo uma vez por minuto. */
    private const val INTERVALO_MS = 60_000L

    private var pasta: File? = null
    private val ultimaGravacao = HashMap<String, Long>()

    /** Primeira coisa do Application.onCreate. Anota e deixa o Android seguir como sempre. */
    fun instalar(context: Context) {
        pasta = context.filesDir
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            runCatching { salvar(ARQUIVO, "QUEBROU (${thread.name})", erro) }
            anterior?.uncaughtException(thread, erro)
        }
    }

    /** Para os escopos dos serviços: o erro de uma tarefa fica anotado e o serviço segue vivo. */
    fun handler(onde: String) = CoroutineExceptionHandler { _, erro ->
        anotar(onde, erro)
    }

    /** Erro pego num try/catch que não devia acontecer: anota sem derrubar nada. */
    fun anotar(onde: String, erro: Throwable) {
        Log.e("MotoristaPro", "Erro em $onde", erro)
        val agora = System.currentTimeMillis()
        synchronized(ultimaGravacao) {
            if (agora - (ultimaGravacao[onde] ?: 0L) < INTERVALO_MS) return
            ultimaGravacao[onde] = agora
        }
        runCatching { salvar(ARQUIVO_TRATADO, "Erro tratado em $onde", erro) }
    }

    /** A quebra (se houver) primeiro, depois o último erro tratado. */
    fun ler(context: Context): String? =
        listOf(ARQUIVO, ARQUIVO_TRATADO)
            .mapNotNull { File(context.filesDir, it).takeIf { f -> f.exists() }?.readText()?.takeIf { t -> t.isNotBlank() } }
            .joinToString("\n\n----------\n\n")
            .takeIf { it.isNotBlank() }

    fun apagar(context: Context) {
        File(context.filesDir, ARQUIVO).delete()
        File(context.filesDir, ARQUIVO_TRATADO).delete()
    }

    private fun salvar(arquivo: String, titulo: String, erro: Throwable) {
        val dir = pasta ?: return
        val pilha = StringWriter().also { erro.printStackTrace(PrintWriter(it)) }.toString()
        val quando = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        val texto = "MotoristaPro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "$quando · $titulo\n" +
            "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" +
            pilha
        File(dir, arquivo).writeText(texto.take(MAXIMO))
    }
}
