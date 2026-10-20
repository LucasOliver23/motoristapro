package com.motoristapro.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
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

    /**
     * A cópia da imagem (~10 MB numa tela cheia) acontece aqui, fora da thread
     * principal: na principal ela travava o cartão e a bolha, e trava longa é
     * o que faz o Android derrubar o leitor.
     */
    private val trabalho = Executors.newSingleThreadExecutor()

    /**
     * Repassa para [trabalho] sem nunca lançar exceção. Depois de [fechar], uma
     * captura que ainda estava a caminho seria recusada pelo executor encerrado
     * (RejectedExecutionException) — e a recusa não pode virar uma quebra.
     */
    private val executorSeguro = Executor { tarefa ->
        try {
            trabalho.execute(tarefa)
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Captura chegou depois de o leitor fechar; descartada")
        }
    }

    private val principal = Handler(Looper.getMainLooper())

    /**
     * Número da leitura em andamento. O resultado de uma leitura que já passou
     * do prazo (e foi dada como falha) chega com número velho e é ignorado.
     */
    private val geracao = AtomicInteger(0)

    @Volatile
    var ocupado = false
        private set

    /** Captura + OCR. [aoTerminar] recebe as linhas lidas (de cima para baixo) ou null em caso de falha. */
    fun ler(aoTerminar: (List<String>?) -> Unit) {
        if (ocupado) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ocupado = true
            val minha = geracao.incrementAndGet()
            // Prazo: em alguns aparelhos o Android não devolve a captura nem
            // com sucesso nem com falha. Sem isto, "ocupado" ficava true para
            // sempre e a leitura por imagem (a única que lê a 99) parava de vez.
            principal.postDelayed({
                if (ocupado && geracao.get() == minha) {
                    Log.w(TAG, "Leitura por imagem passou do prazo; liberando para a próxima")
                    finalizar(minha, aoTerminar, null)
                }
            }, PRAZO_LEITURA_MS)
            capturar(minha, aoTerminar)
        } else {
            aoTerminar(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun capturar(minha: Int, aoTerminar: (List<String>?) -> Unit) {
        try {
            servico.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executorSeguro,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(resultado: AccessibilityService.ScreenshotResult) {
                        val recorte = try {
                            prepararImagem(resultado)
                        } catch (e: Throwable) {
                            // Throwable: falta de memória na cópia da imagem também
                            // (tela cheia em ARGB = ~10 MB) não pode derrubar o leitor.
                            Log.e(TAG, "Falha ao preparar a captura", e)
                            null
                        }
                        if (recorte == null) {
                            finalizar(minha, aoTerminar, null)
                            return
                        }
                        reconhecer(minha, recorte, aoTerminar)
                    }

                    override fun onFailure(codigoErro: Int) {
                        // Ex.: ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT (capturas muito seguidas).
                        Log.w(TAG, "takeScreenshot falhou: código $codigoErro")
                        finalizar(minha, aoTerminar, null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot indisponível", e)
            finalizar(minha, aoTerminar, null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun prepararImagem(resultado: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = resultado.hardwareBuffer
        // finally: com falta de memória na cópia, o buffer da captura ficava aberto.
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, resultado.colorSpace) ?: return null
            // ML Kit precisa de bitmap em memória comum (não "hardware").
            val completo = hardware.copy(Bitmap.Config.ARGB_8888, false)
            hardware.recycle()
            if (completo == null) return null

            val topo = (completo.height * FRACAO_TOPO_IGNORADA).toInt()
            val recorte = Bitmap.createBitmap(completo, 0, topo, completo.width, completo.height - topo)
            if (recorte != completo) completo.recycle()
            // 720 px de largura sobram para ler "R$ 12,40" e é metade do trabalho do ML Kit.
            if (recorte.width <= LARGURA_MAXIMA) return recorte
            val escala = LARGURA_MAXIMA.toFloat() / recorte.width
            val menor = Bitmap.createScaledBitmap(recorte, LARGURA_MAXIMA, (recorte.height * escala).toInt(), true)
            if (menor != recorte) recorte.recycle()
            return menor
        } finally {
            buffer.close()
        }
    }

    private fun reconhecer(minha: Int, imagem: Bitmap, aoTerminar: (List<String>?) -> Unit) {
        try {
            reconhecedor.process(InputImage.fromBitmap(imagem, 0))
                .addOnSuccessListener { texto ->
                    // Blocos em ordem de leitura (de cima para baixo, esquerda para direita).
                    val linhas = texto.textBlocks
                        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        .flatMap { bloco -> bloco.lines.map { it.text.trim() } }
                        .filter { it.isNotEmpty() }
                    finalizar(minha, aoTerminar, linhas)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR falhou", e)
                    finalizar(minha, aoTerminar, null)
                }
                .addOnCompleteListener { imagem.recycle() }
        } catch (e: Exception) {
            Log.e(TAG, "OCR indisponível", e)
            imagem.recycle()
            finalizar(minha, aoTerminar, null)
        }
    }

    /**
     * O resultado sempre volta na thread principal: é lá que mora o estado do leitor.
     * Cada leitura termina UMA vez: o que chega depois (do prazo ou da captura
     * atrasada) é descartado pelo número da leitura.
     */
    private fun finalizar(minha: Int, aoTerminar: (List<String>?) -> Unit, linhas: List<String>?) {
        principal.post {
            if (geracao.get() != minha || !ocupado) return@post
            // Avança o número: um segundo aviso desta mesma leitura não passa.
            geracao.incrementAndGet()
            ocupado = false
            try {
                aoTerminar(linhas)
            } catch (e: Throwable) {
                com.motoristapro.diagnostico.RegistroErros.anotar("processar leitura OCR", e)
            }
        }
    }

    fun fechar() {
        principal.removeCallbacksAndMessages(null)
        runCatching { reconhecedor.close() }
        runCatching { trabalho.shutdown() }
    }

    private companion object {
        const val TAG = "MotoristaPro"

        /** Ignora os 30% de cima da tela (mapa + nossa janela flutuante); os cards ficam embaixo. */
        const val FRACAO_TOPO_IGNORADA = 0.30f

        const val LARGURA_MAXIMA = 720

        /** Captura + reconhecimento normalmente levam menos de 1 s; 8 s é sinal de que travou. */
        const val PRAZO_LEITURA_MS = 8_000L
    }
}
