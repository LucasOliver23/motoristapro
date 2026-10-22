package com.motoristapro.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * OCR de um print escolhido na galeria (ou compartilhado do WhatsApp) para o
 * "Testar com print". Mesmo ML Kit, mesmo recorte e mesma ordem de linhas do
 * [LeitorOcr] — só que lendo um arquivo em vez da tela, e sem precisar do
 * leitor ligado.
 */
object OcrDoPrint {

    /** As linhas da parte de baixo (como o leitor lê) e as da imagem inteira. */
    data class Linhas(val baixo: List<String>, val inteiro: List<String>)

    /** Lado maior ao abrir o arquivo: sobra para o OCR e não estoura a memória. */
    private const val LADO_MAXIMO = 2_000

    /** null quando não deu para abrir a imagem. */
    suspend fun ler(context: Context, uri: Uri): Linhas? = withContext(Dispatchers.IO) {
        val imagem = abrir(context, uri) ?: return@withContext null
        val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            Linhas(
                baixo = lerParte(reconhecedor, imagem, FRACAO_TOPO_IGNORADA),
                inteiro = lerParte(reconhecedor, imagem, 0f)
            )
        } catch (e: Throwable) {
            // Throwable: imagem enorme sem memória também não derruba o app.
            Log.e("MotoristaPro", "Falha ao ler o print", e)
            null
        } finally {
            imagem.recycle()
            runCatching { reconhecedor.close() }
        }
    }

    private suspend fun lerParte(reconhecedor: TextRecognizer, imagem: Bitmap, fracaoTopo: Float): List<String> {
        val parte = recortarParaOcr(imagem, fracaoTopo)
        try {
            return reconhecer(reconhecedor, parte)
        } finally {
            if (parte !== imagem) parte.recycle()
        }
    }

    private suspend fun reconhecer(reconhecedor: TextRecognizer, bitmap: Bitmap): List<String> =
        suspendCancellableCoroutine { cont ->
            reconhecedor.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { texto -> if (cont.isActive) cont.resume(linhasEmOrdem(texto)) }
                .addOnFailureListener { e ->
                    Log.w("MotoristaPro", "OCR do print falhou", e)
                    if (cont.isActive) cont.resume(emptyList())
                }
        }

    private fun abrir(context: Context, uri: Uri): Bitmap? = runCatching {
        val r = context.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var amostra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (amostra * 2) >= LADO_MAXIMO) amostra *= 2
        r.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        }
    }.getOrElse { Log.w("MotoristaPro", "Não abriu o print", it); null }
}
