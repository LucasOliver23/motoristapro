package com.motoristapro.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Lê o texto da tela pela IMAGEM, para apps que escondem o conteúdo da acessibilidade (ex.: 99).
 *
 * - Captura: AccessibilityService.takeScreenshot (Android 11+), sem pedir permissão extra
 *   (exige android:canTakeScreenshot="true" no accessibility_service_config.xml).
 * - Reconhecimento: ML Kit Text Recognition, 100% no aparelho (nada é enviado para a internet).
 * - Recorte: só a parte de baixo da tela (onde ficam os cards de oferta), o que também deixa de fora
 *   a nossa própria janela flutuante, que fica no topo.
 */
class LeitorOcr(private val servico: AccessibilityService) {

    private val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile
    var ocupado = false
        private set

    /** Captura + OCR. [aoTerminar] recebe as linhas lidas (de cima para baixo) ou null em caso de falha. */
    fun ler(aoTerminar: (List<String>?) -> Unit) {
        if (ocupado) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ocupado = true
            capturar(aoTerminar)
        } else {
            aoTerminar(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun capturar(aoTerminar: (List<String>?) -> Unit) {
        try {
            servico.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                servico.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(resultado: AccessibilityService.ScreenshotResult) {
                        val recorte = try {
                            prepararImagem(resultado)
                        } catch (e: Exception) {
                            Log.e(TAG, "Falha ao preparar a captura", e)
                            null
                        }
                        if (recorte == null) {
                            finalizar(aoTerminar, null)
                            return
                        }
                        reconhecer(recorte, aoTerminar)
                    }

                    override fun onFailure(codigoErro: Int) {
                        // Ex.: ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT (capturas muito seguidas).
                        Log.w(TAG, "takeScreenshot falhou: código $codigoErro")
                        finalizar(aoTerminar, null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot indisponível", e)
            finalizar(aoTerminar, null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun prepararImagem(resultado: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = resultado.hardwareBuffer
        val hardware = Bitmap.wrapHardwareBuffer(buffer, resultado.colorSpace)
        if (hardware == null) {
            buffer.close()
            return null
        }
        // ML Kit precisa de bitmap em memória comum (não "hardware").
        val completo = hardware.copy(Bitmap.Config.ARGB_8888, false)
        hardware.recycle()
        buffer.close()
        if (completo == null) return null

        val topo = (completo.height * FRACAO_TOPO_IGNORADA).toInt()
        val recorte = Bitmap.createBitmap(completo, 0, topo, completo.width, completo.height - topo)
        if (recorte != completo) completo.recycle()
        return recorte
    }

    private fun reconhecer(imagem: Bitmap, aoTerminar: (List<String>?) -> Unit) {
        try {
            reconhecedor.process(InputImage.fromBitmap(imagem, 0))
                .addOnSuccessListener { texto ->
                    // Blocos em ordem de leitura (de cima para baixo, esquerda para direita).
                    val linhas = texto.textBlocks
                        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        .flatMap { bloco -> bloco.lines.map { it.text.trim() } }
                        .filter { it.isNotEmpty() }
                    finalizar(aoTerminar, linhas)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR falhou", e)
                    finalizar(aoTerminar, null)
                }
                .addOnCompleteListener { imagem.recycle() }
        } catch (e: Exception) {
            Log.e(TAG, "OCR indisponível", e)
            imagem.recycle()
            finalizar(aoTerminar, null)
        }
    }

    private fun finalizar(aoTerminar: (List<String>?) -> Unit, linhas: List<String>?) {
        ocupado = false
        try {
            aoTerminar(linhas)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar leitura OCR", e)
        }
    }

    fun fechar() {
        runCatching { reconhecedor.close() }
    }

    private companion object {
        const val TAG = "MotoristaPro"

        /** Ignora os 30% de cima da tela (mapa + nossa janela flutuante); os cards ficam embaixo. */
        const val FRACAO_TOPO_IGNORADA = 0.30f
    }
}
