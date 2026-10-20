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

    private const val LADO_MAXIMO = 800

    /** O veículo 1 fica com o nome de antes da v14; os outros ganham o id no nome. */
    private fun arquivo(context: Context, veiculoId: Long) =
        File(context.filesDir, if (veiculoId == 1L) "veiculo_foto.jpg" else "veiculo_foto_$veiculoId.jpg")

    fun existe(context: Context, veiculoId: Long = 1L): Boolean = arquivo(context, veiculoId).exists()

    fun carregar(context: Context, veiculoId: Long = 1L): Bitmap? =
        arquivo(context, veiculoId).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    /** Copia a foto escolhida, reduzida. false se não deu para ler. */
    fun salvar(context: Context, uri: Uri, veiculoId: Long = 1L): Boolean = runCatching {
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
        arquivo(context, veiculoId).outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    }.getOrElse { Log.w("MotoristaPro", "Falha ao salvar foto do veículo", it); false }

    fun remover(context: Context, veiculoId: Long = 1L) {
        arquivo(context, veiculoId).delete()
    }
}
