package com.motoristapro.service

import android.content.Context

/**
 * Critérios de "corrida boa" do motorista — as faixas do semáforo.
 *
 * SharedPreferences (e não Room) porque são lidos a cada oferta, no caminho crítico
 * do serviço de acessibilidade, sem necessidade de query.
 *
 * Regra: qualquer condição MÍNIMA violada derruba a oferta para RUIM na hora
 * (é um veto, não uma média). Passando pelos vetos, a cor sai de R$/km e R$/hora.
 * Um mínimo em 0 (ou nota ausente na tela) significa "não usar esse critério".
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

    /** Lucro líquido mínimo por corrida, em reais. 0 = não usar. */
    var minLucroReais: Float
        get() = prefs.getFloat(KEY_LUCRO, 0f)
        set(v) = prefs.edit().putFloat(KEY_LUCRO, v).apply()

    /** Lucro líquido mínimo como % do valor da oferta. 0 = não usar. */
    var minLucroPercent: Float
        get() = prefs.getFloat(KEY_LUCRO_PCT, 0f)
        set(v) = prefs.edit().putFloat(KEY_LUCRO_PCT, v).apply()

    /** Nota mínima do passageiro (1,0 a 5,0). 0 = não usar. */
    var minNota: Float
        get() = prefs.getFloat(KEY_NOTA, 0f)
        set(v) = prefs.edit().putFloat(KEY_NOTA, v).apply()

    /**
     * @param tarifaMinimaCentavos tarifa mínima da Configuração (Room).
     * @param custoKmCentavos custo/km da Configuração; sem ele o lucro não é avaliado.
     */
    fun classificar(
        o: Oferta,
        tarifaMinimaCentavos: Long = 0,
        custoKmCentavos: Long = 0
    ): Classificacao {
        // --- vetos ---
        if (tarifaMinimaCentavos > 0 && o.valor * 100 < tarifaMinimaCentavos) return Classificacao.RUIM
        if (custoKmCentavos > 0) {
            val lucro = o.lucroEstimado(custoKmCentavos)
            if (minLucroReais > 0f && lucro < minLucroReais) return Classificacao.RUIM
            if (minLucroPercent > 0f && o.lucroPercentual(custoKmCentavos) < minLucroPercent) {
                return Classificacao.RUIM
            }
            // Lucro negativo é prejuízo na certa, independente de qualquer faixa.
            if (lucro <= 0.0) return Classificacao.RUIM
        }
        val nota = o.nota
        if (minNota > 0f && nota != null && nota < minNota) return Classificacao.RUIM

        // --- faixas ---
        val okKm = o.reaisPorKm >= minReaisPorKm
        // Entregas sem minutos na tela: R$/hora é 0 e não deve pesar contra a oferta.
        val okHora = o.minutos <= 0 || o.reaisPorHora >= minReaisPorHora
        return when {
            okKm && okHora -> Classificacao.BOA
            okKm || okHora -> Classificacao.MEDIA
            else -> Classificacao.RUIM
        }
    }

    private companion object {
        const val KEY_KM = "min_reais_km"
        const val KEY_HORA = "min_reais_hora"
        const val KEY_LUCRO = "min_lucro_reais"
        const val KEY_LUCRO_PCT = "min_lucro_pct"
        const val KEY_NOTA = "min_nota"
    }
}

enum class Classificacao(val corFundo: Int, val rotulo: String) {
    BOA(0xE62E7D32.toInt(), "BOA"),
    MEDIA(0xE6F9A825.toInt(), "MÉDIA"),
    RUIM(0xE6C62828.toInt(), "RUIM")
}
