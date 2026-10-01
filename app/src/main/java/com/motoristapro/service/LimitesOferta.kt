package com.motoristapro.service

import android.content.Context

/**
 * Critérios de "corrida boa" do motorista. SharedPreferences (e não Room)
 * porque são 2 números lidos a cada oferta, sem necessidade de query.
 */
class LimitesOferta(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("limites_oferta", Context.MODE_PRIVATE)

    var minReaisPorKm: Float
        get() = prefs.getFloat(KEY_KM, 1.80f)
        set(v) = prefs.edit().putFloat(KEY_KM, v).apply()

    var minReaisPorHora: Float
        get() = prefs.getFloat(KEY_HORA, 30f)
        set(v) = prefs.edit().putFloat(KEY_HORA, v).apply()

    /**
     * @param tarifaMinimaCentavos tarifa mínima da Configuração (Room). Oferta abaixo dela é sempre RUIM.
     */
    fun classificar(o: Oferta, tarifaMinimaCentavos: Long = 0): Classificacao {
        if (tarifaMinimaCentavos > 0 && o.valor * 100 < tarifaMinimaCentavos) return Classificacao.RUIM
        val okKm = o.reaisPorKm >= minReaisPorKm
        val okHora = o.reaisPorHora >= minReaisPorHora
        return when {
            okKm && okHora -> Classificacao.BOA
            okKm || okHora -> Classificacao.MEDIA
            else -> Classificacao.RUIM
        }
    }

    private companion object {
        const val KEY_KM = "min_reais_km"
        const val KEY_HORA = "min_reais_hora"
    }
}

enum class Classificacao(val corFundo: Int, val rotulo: String) {
    BOA(0xE62E7D32.toInt(), "BOA"),
    MEDIA(0xE6F9A825.toInt(), "MÉDIA"),
    RUIM(0xE6C62828.toInt(), "RUIM")
}
