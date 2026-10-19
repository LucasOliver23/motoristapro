package com.motoristapro.veiculo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * A foto do veículo do motorista, escolhida na galeria.
 *
 * Fica uma cópia reduzida (800 px) dentro do app: a foto original pode ser
 * apagada da galeria e o cartão não pode ficar sem imagem por isso. Só se
 * lê o arquivo que a pessoa escolheu; nada de câmera ou captura de tela.
 */
object FotoVeiculo {

    private const val ARQUIVO = "veiculo_foto.jpg"
    private const val LADO_MAXIMO = 800

    private fun arquivo(context: Context) = File(context.filesDir, ARQUIVO)

    fun existe(context: Context): Boolean = arquivo(context).exists()

    fun carregar(context: Context): Bitmap? =
        arquivo(context).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    /** Copia a foto escolhida, reduzida. false se não deu para ler. */
    fun salvar(context: Context, uri: Uri): Boolean = runCatching {
        val r = context.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        var amostra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (amostra * 2) >= LADO_MAXIMO) amostra *= 2
        val bruto = r.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: return false
        val escala = LADO_MAXIMO.toFloat() / maxOf(bruto.width, bruto.height)
        val final = if (escala < 1f)
            Bitmap.createScaledBitmap(bruto, (bruto.width * escala).toInt(), (bruto.height * escala).toInt(), true)
        else bruto
        arquivo(context).outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    }.getOrElse { Log.w("MotoristaPro", "Falha ao salvar foto do veículo", it); false }

    fun remover(context: Context) {
        arquivo(context).delete()
    }
}
