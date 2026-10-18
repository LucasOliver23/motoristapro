package com.motoristapro.custo

import kotlin.math.ceil

/**
 * A conta "quanto a oferta tem que pagar por hora para eu bater a meta".
 *
 * Três passos, na ordem em que aparecem na tela:
 * 1. meta do dia ÷ horas que roda = lucro por hora trabalhada;
 * 2. + o custo do carro nessa hora (km rodados por hora × custo por km)
 *    = quanto precisa FATURAR por hora trabalhada;
 * 3. ÷ a fração do tempo com passageiro = quanto a OFERTA precisa pagar por
 *    hora de corrida — que é o R$/hora que o cartão mostra (ele só conta o
 *    tempo da corrida, não a espera nem a ida até o passageiro).
 */
data class ContaMetaHora(
    val horasPorDia: Int,
    val lucroPorHoraCentavos: Long,
    val kmPorHora: Double,
    /** true quando não há jornada com GPS para medir: o km/h é o padrão. */
    val kmPorHoraEstimado: Boolean,
    val custoPorHoraCentavos: Long,
    val faturarPorHoraCentavos: Long,
    /** 0..1: quanto do turno é com passageiro. */
    val fracaoComPassageiro: Double,
    /** true quando ainda não há turno suficiente para medir: usa [MetaPorHora.FRACAO_PADRAO]. */
    val fracaoEstimada: Boolean,
    /** Arredondado para cima, em reais inteiros: é o número que vai para o semáforo. */
    val ofertaPorHoraReais: Int
) {
    /** O amarelo começa 30% abaixo: não bate a meta, mas ainda dá lucro. */
    val amareloReais: Int get() = Math.round(ofertaPorHoraReais * (1 - MetaPorHora.MARGEM_AMARELO)).toInt()
}

object MetaPorHora {

    /** Sem turno medido, conta que 60% do tempo é com passageiro. */
    const val FRACAO_PADRAO = 0.6

    /** Sem GPS nem rotina no assistente, 15 km rodados por hora de turno. */
    const val KM_POR_HORA_PADRAO = 15.0

    const val MARGEM_AMARELO = 0.3

    /** Abaixo disso de turno a fração ainda é sorte (uma corrida longa logo no começo). */
    const val MINIMO_TURNO_SEG = 3L * 3600

    /**
     * @param kmPorHoraMedido do GPS das jornadas; null se não houver.
     * @param fracaoMedida de [fracaoComPassageiro]; null se não houver.
     * @return null sem meta ou sem horas.
     */
    fun calcular(
        metaDiaCentavos: Long,
        horasPorDia: Int,
        custoKmCentavos: Long,
        kmPorHoraMedido: Double?,
        fracaoMedida: Double?
    ): ContaMetaHora? {
        if (metaDiaCentavos <= 0 || horasPorDia <= 0) return null
        val lucroHora = Math.round(metaDiaCentavos.toDouble() / horasPorDia)
        val kmHora = kmPorHoraMedido ?: KM_POR_HORA_PADRAO
        val custoHora = Math.round(kmHora * custoKmCentavos.coerceAtLeast(0))
        val faturar = lucroHora + custoHora
        val fracao = fracaoMedida ?: FRACAO_PADRAO
        val oferta = ceil(faturar / fracao / 100.0).toInt()
        return ContaMetaHora(
            horasPorDia = horasPorDia,
            lucroPorHoraCentavos = lucroHora,
            kmPorHora = kmHora,
            kmPorHoraEstimado = kmPorHoraMedido == null,
            custoPorHoraCentavos = custoHora,
            faturarPorHoraCentavos = faturar,
            fracaoComPassageiro = fracao,
            fracaoEstimada = fracaoMedida == null,
            ofertaPorHoraReais = oferta
        )
    }

    /**
     * Tempo em corrida ÷ tempo de turno. null com pouco turno, ou quando a conta
     * não fecha (corridas registradas sem turno aberto dariam mais de 100%).
     * Fica entre 20% e 95%: nem o pior dia é só espera, nem o melhor é sem nenhuma.
     */
    fun fracaoComPassageiro(segundosEmCorrida: Long, segundosDeTurno: Long): Double? {
        if (segundosDeTurno < MINIMO_TURNO_SEG || segundosEmCorrida <= 0) return null
        val f = segundosEmCorrida.toDouble() / segundosDeTurno
        if (f > 1.0) return null
        return f.coerceIn(0.2, 0.95)
    }

    /** Km do GPS ÷ horas de turno. null com pouco turno ou GPS desligado. */
    fun kmPorHora(metrosGps: Long, segundosDeTurno: Long): Double? {
        if (segundosDeTurno < MINIMO_TURNO_SEG || metrosGps <= 0) return null
        val v = metrosGps / 1000.0 / (segundosDeTurno / 3600.0)
        return v.takeIf { it in 2.0..80.0 }
    }
}
