package com.motoristapro.service

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Fala a avaliação da oferta em voz alta — pensado para quem roda de moto
 * e não pode tirar a mão do guidão para ler o cartão.
 *
 * Usa o TextToSpeech do próprio Android: nenhuma biblioteca, nenhuma internet.
 * Se o aparelho não tiver voz em português instalada, o aviso simplesmente não
 * sai — nada quebra, e o cartão continua na tela normalmente.
 *
 * O áudio sai como ASSISTENTE/NOTIFICAÇÃO, então ele se mistura com o som do
 * GPS em vez de interromper a música do motorista.
 */
class AvisoVoz(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("aviso_voz", Context.MODE_PRIVATE)

    private var tts: TextToSpeech? = null
    private var pronto = false
    private var idiomaOk = false

    var ativo: Boolean
        get() = prefs.getBoolean(KEY_ATIVO, false)
        set(v) {
            prefs.edit().putBoolean(KEY_ATIVO, v).apply()
            if (v) preparar() else liberar()
        }

    /** Ler só o essencial (classe + R$/km) em vez da frase completa. */
    var resumido: Boolean
        get() = prefs.getBoolean(KEY_RESUMIDO, false)
        set(v) = prefs.edit().putBoolean(KEY_RESUMIDO, v).apply()

    /** Cria o motor de voz. Chame quando o serviço conectar; é seguro repetir. */
    fun preparar() {
        if (!ativo || tts != null) return
        tts = TextToSpeech(app) { status ->
            pronto = status == TextToSpeech.SUCCESS
            if (!pronto) {
                Log.w(TAG, "TextToSpeech indisponível neste aparelho")
                return@TextToSpeech
            }
            val motor = tts ?: return@TextToSpeech
            val r = runCatching { motor.setLanguage(PT_BR) }.getOrNull()
            idiomaOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            if (!idiomaOk) Log.w(TAG, "Voz em português não instalada; aviso por voz desligado")
            runCatching {
                motor.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
    }

    /** Fala a oferta. Não faz nada se estiver desligado ou sem voz em português. */
    fun falar(o: Oferta, classe: Classificacao, alertaRisco: String? = null) {
        if (!ativo) return
        val motor = tts
        if (motor == null) {
            preparar()
            return          // esta oferta passa calada; a próxima já encontra o motor pronto
        }
        if (!pronto || !idiomaOk) return
        runCatching {
            motor.speak(frase(o, classe, alertaRisco), TextToSpeech.QUEUE_FLUSH, null, ID_FALA)
        }.onFailure { Log.w(TAG, "Falha ao falar a oferta", it) }
    }

    fun calar() {
        runCatching { tts?.stop() }
    }

    fun liberar() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        pronto = false
        idiomaOk = false
    }

    /**
     * Monta a frase. Números viram palavras do jeito que o leitor fala bem:
     * "2 e 17 por quilômetro" em vez de "R$ 2,17/km", que sai truncado.
     */
    internal fun frase(o: Oferta, classe: Classificacao, alertaRisco: String?): String {
        val acao = when (classe) {
            Classificacao.BOA -> "Aceitar"
            Classificacao.MEDIA -> "Analisar"
            Classificacao.RUIM -> "Recusar"
        }
        val partes = mutableListOf(acao)
        partes += "${porExtenso(o.reaisPorKm)} por quilômetro"
        if (!resumido) {
            if (o.minutos > 0) partes += "${o.reaisPorHora.toInt()} por hora"
            o.nota?.let { partes += "nota ${porExtenso(it)}" }
            if (o.paradas > 1) partes += "${o.paradas} paradas"
        }
        alertaRisco?.let { partes += "atenção, $it" }
        return partes.joinToString(". ") + "."
    }

    /** 2.17 -> "2 e 17"; 3.0 -> "3". */
    private fun porExtenso(v: Double): String {
        if (!v.isFinite() || v < 0) return "zero"
        var inteiro = v.toInt()
        var centavos = Math.round((v - inteiro) * 100).toInt()
        if (centavos >= 100) {          // 2,999 arredonda para 3, não para "2 e 100"
            inteiro += 1
            centavos = 0
        }
        return if (centavos == 0) "$inteiro" else "$inteiro e $centavos"
    }

    private companion object {
        const val TAG = "MotoristaPro"
        const val ID_FALA = "oferta"
        const val KEY_ATIVO = "ativo"
        const val KEY_RESUMIDO = "resumido"
        val PT_BR: Locale = Locale("pt", "BR")
    }
}
