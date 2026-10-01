package com.motoristapro.data.prefs

import android.content.Context

/** Liga/desliga recursos do app (SharedPreferences: leitura instantânea, sem query). */
class PreferenciasApp(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("preferencias_app", Context.MODE_PRIVATE)

    /** Bolha flutuante com o lucro do dia e o cronômetro do turno. */
    var bolhaAtiva: Boolean
        get() = prefs.getBoolean(KEY_BOLHA, true)
        set(v) = prefs.edit().putBoolean(KEY_BOLHA, v).apply()

    /** Notificação com o resumo do dia às 22h. */
    var resumoDiarioAtivo: Boolean
        get() = prefs.getBoolean(KEY_RESUMO, true)
        set(v) = prefs.edit().putBoolean(KEY_RESUMO, v).apply()

    /** Leitura pela imagem (OCR) para apps que escondem o texto da oferta (ex.: 99). */
    var ocrAtivo: Boolean
        get() = prefs.getBoolean(KEY_OCR, true)
        set(v) = prefs.edit().putBoolean(KEY_OCR, v).apply()

    private companion object {
        const val KEY_OCR = "ocr_ativo"
        const val KEY_BOLHA = "bolha_ativa"
        const val KEY_RESUMO = "resumo_diario_ativo"
    }
}
