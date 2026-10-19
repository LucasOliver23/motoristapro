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

    private const val ARQUIVO = "ultimo_erro.txt"
    private const val MAXIMO = 6_000
    private var pasta: File? = null

    /** Primeira coisa do Application.onCreate. Anota e deixa o Android seguir como sempre. */
    fun instalar(context: Context) {
        pasta = context.filesDir
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            runCatching { salvar("QUEBROU (${thread.name})", erro) }
            anterior?.uncaughtException(thread, erro)
        }
    }

    /** Para os escopos dos serviços: o erro de uma tarefa fica anotado e o serviço segue vivo. */
    fun handler(onde: String) = CoroutineExceptionHandler { _, erro ->
        Log.e("MotoristaPro", "Erro em $onde", erro)
        runCatching { salvar("Erro tratado em $onde", erro) }
    }

    /** Erro pego num try/catch que não devia acontecer: anota sem derrubar nada. */
    fun anotar(onde: String, erro: Throwable) {
        Log.e("MotoristaPro", "Erro em $onde", erro)
        runCatching { salvar("Erro tratado em $onde", erro) }
    }

    fun ler(context: Context): String? =
        File(context.filesDir, ARQUIVO).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun apagar(context: Context) {
        File(context.filesDir, ARQUIVO).delete()
    }

    private fun salvar(titulo: String, erro: Throwable) {
        val dir = pasta ?: return
        val pilha = StringWriter().also { erro.printStackTrace(PrintWriter(it)) }.toString()
        val quando = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        val texto = "MotoristaPro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "$quando · $titulo\n" +
            "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" +
            pilha
        File(dir, ARQUIVO).writeText(texto.take(MAXIMO))
    }
}
