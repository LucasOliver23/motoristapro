package com.motoristapro.service

import android.content.Context

/**
 * As faixas do semáforo: a partir de que número a corrida é ruim, média ou boa.
 *
 * Cada critério tem DOIS limites, não um: abaixo do primeiro é ruim, acima do
 * segundo é boa, no meio é atenção. É isso que o motorista arrasta na tela.
 *
 * SharedPreferences (e não Room) porque são lidos a cada oferta, no caminho
 * crítico do serviço de acessibilidade, sem necessidade de query.
 *
 * Regra da cor: primeiro os VETOS (valor mínimo da viagem, lucro mínimo em R$ e
 * em %) — qualquer um violado derruba para RUIM na hora, sem discussão. Passando
 * pelos vetos, vale a PIOR das faixas: uma corrida não é boa se alguma métrica
 * dela é ruim.
 */
class LimitesOferta(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("limites_oferta", Context.MODE_PRIVATE)

    // ------------------------------------------------------------ R$ por km
    var kmRuimAbaixo: Float
        get() = prefs.getFloat(KEY_KM_RUIM, 1.40f)
        set(v) = prefs.edit().putFloat(KEY_KM_RUIM, v).apply()

    var kmBoaAcima: Float
        get() = prefs.getFloat(KEY_KM_BOA, 1.80f)
        set(v) = prefs.edit().putFloat(KEY_KM_BOA, v).apply()

    // ----------------------------------------------------------- R$ por hora
    var horaRuimAbaixo: Float
        get() = prefs.getFloat(KEY_HORA_RUIM, 30f)
        set(v) = prefs.edit().putFloat(KEY_HORA_RUIM, v).apply()

    var horaBoaAcima: Float
        get() = prefs.getFloat(KEY_HORA_BOA, 40f)
        set(v) = prefs.edit().putFloat(KEY_HORA_BOA, v).apply()

    // ------------------------------------------------------ nota do passageiro
    var notaRuimAbaixo: Float
        get() = prefs.getFloat(KEY_NOTA_RUIM, 4.30f)
        set(v) = prefs.edit().putFloat(KEY_NOTA_RUIM, v).apply()

    var notaBoaAcima: Float
        get() = prefs.getFloat(KEY_NOTA_BOA, 4.90f)
        set(v) = prefs.edit().putFloat(KEY_NOTA_BOA, v).apply()

    /** Usar a nota na cor. Desligado por padrão: nem todo app mostra nota. */
    var usarNota: Boolean
        get() = prefs.getBoolean(KEY_USAR_NOTA, false)
        set(v) = prefs.edit().putBoolean(KEY_USAR_NOTA, v).apply()

    // ------------------------------------------------------------- vetos
    /** Lucro líquido mínimo por corrida, em reais. 0 = não usar. Exige custo/km. */
    var minLucroReais: Float
        get() = prefs.getFloat(KEY_LUCRO, 0f)
        set(v) = prefs.edit().putFloat(KEY_LUCRO, v).apply()

    /** Lucro líquido mínimo como % do valor da oferta. 0 = não usar. Exige custo/km. */
    var minLucroPercent: Float
        get() = prefs.getFloat(KEY_LUCRO_PCT, 0f)
        set(v) = prefs.edit().putFloat(KEY_LUCRO_PCT, v).apply()

    /** Mantém as duas pontas coerentes: o limite "boa" nunca fica abaixo do "ruim". */
    fun definirFaixaKm(ruim: Float, boa: Float) {
        kmRuimAbaixo = minOf(ruim, boa)
        kmBoaAcima = maxOf(ruim, boa)
    }

    fun definirFaixaHora(ruim: Float, boa: Float) {
        horaRuimAbaixo = minOf(ruim, boa)
        horaBoaAcima = maxOf(ruim, boa)
    }

    fun definirFaixaNota(ruim: Float, boa: Float) {
        notaRuimAbaixo = minOf(ruim, boa)
        notaBoaAcima = maxOf(ruim, boa)
    }

    /**
     * @param tarifaMinimaCentavos valor mínimo da viagem (Configuração, no Room).
     * @param custoKmCentavos custo/km; sem ele o lucro não é avaliado.
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
            if (lucro <= 0.0) return Classificacao.RUIM          // prejuízo é prejuízo
            if (minLucroReais > 0f && lucro < minLucroReais) return Classificacao.RUIM
            if (minLucroPercent > 0f && o.lucroPercentual(custoKmCentavos) < minLucroPercent) {
                return Classificacao.RUIM
            }
        }

        // --- faixas: vale a pior das métricas que a tela mostrou ---
        val notas = mutableListOf(faixa(o.reaisPorKm, kmRuimAbaixo, kmBoaAcima))
        // Entrega sem minutos na tela: R$/hora seria 0 e puniria a oferta à toa.
        if (o.minutos > 0) notas += faixa(o.reaisPorHora, horaRuimAbaixo, horaBoaAcima)
        val nota = o.nota
        if (usarNota && nota != null) notas += faixa(nota, notaRuimAbaixo, notaBoaAcima)

        return when {
            notas.any { it == Classificacao.RUIM } -> Classificacao.RUIM
            notas.any { it == Classificacao.MEDIA } -> Classificacao.MEDIA
            else -> Classificacao.BOA
        }
    }

    private fun faixa(valor: Double, ruimAbaixo: Float, boaAcima: Float): Classificacao = when {
        valor < ruimAbaixo -> Classificacao.RUIM
        valor >= boaAcima -> Classificacao.BOA
        else -> Classificacao.MEDIA
    }

    private companion object {
        const val KEY_KM_RUIM = "km_ruim"
        const val KEY_KM_BOA = "km_boa"
        const val KEY_HORA_RUIM = "hora_ruim"
        const val KEY_HORA_BOA = "hora_boa"
        const val KEY_NOTA_RUIM = "nota_ruim"
        const val KEY_NOTA_BOA = "nota_boa"
        const val KEY_USAR_NOTA = "usar_nota"
        const val KEY_LUCRO = "min_lucro_reais"
        const val KEY_LUCRO_PCT = "min_lucro_pct"
    }
}

enum class Classificacao(val corFundo: Int, val rotulo: String) {
    BOA(0xE62E7D32.toInt(), "BOA"),
    MEDIA(0xE6F6B93B.toInt(), "ATENÇÃO"),
    RUIM(0xE6C62828.toInt(), "RUIM")
}
