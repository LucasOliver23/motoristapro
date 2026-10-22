package com.motoristapro.service

import com.motoristapro.custo.MetaPorHora

/**
 * O que a régua precisa saber do motorista, já medido (ou estimado) pelo app.
 *
 * @param metaLimpaHoraCentavos o lucro limpo que ele quer por hora de TURNO
 *        (meta do dia ÷ horas por dia).
 * @param custoKmCentavos custo por km do veículo ativo. Zero vale (bike quitada).
 * @param fracaoMedida do turno, quanto é com corrida (0..1). null = ainda não
 *        mediu: vale [MetaPorHora.FRACAO_PADRAO], marcado como estimado.
 * @param velocidadeMedidaKmH velocidade média nas corridas registradas. null =
 *        ainda não tem corridas suficientes: vale [ReguaLucroHora.VELOCIDADE_PADRAO_KMH].
 * @param custoBasico o custo/km ainda não tem desgaste (óleo, pneus...): fica um
 *        pouco abaixo do real até o motorista completar o assistente.
 */
data class ParametrosRegua(
    val metaLimpaHoraCentavos: Long,
    val custoKmCentavos: Long,
    val fracaoMedida: Double? = null,
    val velocidadeMedidaKmH: Double? = null,
    val custoBasico: Boolean = false
)

/** O veredito da régua e a conta por trás dele, para o cartão mostrar. */
data class AvaliacaoRegua(
    val classe: Classificacao,
    /** Lucro da corrida: valor − km × custo/km. */
    val lucroReais: Double,
    /** Lucro por hora DE CORRIDA. */
    val lucroPorHoraReais: Double,
    /** Quanto a corrida precisa render por hora de corrida para bater a meta do turno. */
    val alvoPorHoraReais: Double,
    /** Minutos usados na conta: os do card ou, sem eles, os estimados pelo km. */
    val minutos: Int,
    /** true quando o card não mostrou o tempo e ele saiu da velocidade. */
    val tempoPeloKm: Boolean,
    val fracaoEstimada: Boolean,
    val velocidadeEstimada: Boolean
) {
    /** A conta ainda usa número padrão (não medido do motorista): o cartão avisa. */
    val estimado: Boolean get() = fracaoEstimada || (tempoPeloKm && velocidadeEstimada)
}

/**
 * A régua do cartão (4.16): a corrida compensa se o LUCRO POR HORA dela bate a
 * meta limpa por hora do motorista — já contando a espera entre corridas.
 *
 * Exemplo: meta de R$ 25 limpos por hora de turno, com corrida em 60% do turno.
 * A corrida precisa render R$ 25 ÷ 0,6 ≈ R$ 41,67 por hora DE CORRIDA. Uma
 * corrida de 20 min com R$ 12 de lucro rende R$ 36/h: não bate, fica ATENÇÃO.
 *
 * - lucro ≤ 0 → RECUSAR (prejuízo é prejuízo);
 * - lucro/h ≥ alvo → ACEITAR;
 * - lucro/h ≥ 70% do alvo → ATENÇÃO (não bate a meta, mas paga);
 * - abaixo disso → RECUSAR.
 *
 * Kotlin puro: coberto por ReguaLucroHoraTest.
 */
object ReguaLucroHora {

    /** Sem corridas medidas: 15 km/h (o mesmo padrão da meta por hora). */
    const val VELOCIDADE_PADRAO_KMH = MetaPorHora.KM_POR_HORA_PADRAO

    /** Corridas registradas a partir das quais a velocidade é a do motorista. */
    const val MINIMO_CORRIDAS_VELOCIDADE = 10

    /**
     * @return null quando falta a meta (ou o custo é inválido): aí vale o semáforo antigo.
     */
    fun avaliar(o: Oferta, p: ParametrosRegua): AvaliacaoRegua? {
        if (p.metaLimpaHoraCentavos <= 0 || p.custoKmCentavos < 0) return null
        if (o.km <= 0.0 || !o.valor.isFinite()) return null

        val fracao = p.fracaoMedida?.takeIf { it > 0.0 && it <= 1.0 }
        val velocidade = p.velocidadeMedidaKmH?.takeIf { it > 0.0 }
        val tempoPeloKm = o.minutos <= 0
        val minutos = if (tempoPeloKm) {
            Math.ceil(o.km / (velocidade ?: VELOCIDADE_PADRAO_KMH) * 60.0).toInt().coerceAtLeast(1)
        } else {
            o.minutos
        }

        val lucro = o.lucroEstimado(p.custoKmCentavos)
        val lucroHora = dividirSeguro(lucro, minutos / 60.0)
        val alvo = dividirSeguro(p.metaLimpaHoraCentavos / 100.0, fracao ?: MetaPorHora.FRACAO_PADRAO)

        val classe = when {
            lucro <= 0.0 -> Classificacao.RUIM
            lucroHora >= alvo -> Classificacao.BOA
            lucroHora >= alvo * (1 - MetaPorHora.MARGEM_AMARELO) -> Classificacao.MEDIA
            else -> Classificacao.RUIM
        }
        return AvaliacaoRegua(
            classe = classe,
            lucroReais = lucro,
            lucroPorHoraReais = lucroHora,
            alvoPorHoraReais = alvo,
            minutos = minutos,
            tempoPeloKm = tempoPeloKm,
            fracaoEstimada = fracao == null,
            velocidadeEstimada = velocidade == null
        )
    }

    /**
     * Quanto a corrida teria que pagar para virar ACEITAR pela régua:
     * (V − km × custo) ÷ horas ≥ alvo  →  V ≥ alvo × horas + km × custo.
     */
    fun valorParaBaterMeta(o: Oferta, p: ParametrosRegua): Double? {
        val a = avaliar(o, p) ?: return null
        return a.alvoPorHoraReais * (a.minutos / 60.0) + o.km * (p.custoKmCentavos / 100.0)
    }

    /**
     * Velocidade média nas corridas registradas (km/h), ou null com menos de
     * [MINIMO_CORRIDAS_VELOCIDADE] corridas — ou quando a conta sai fora do
     * possível (corrida lançada à mão com tempo errado).
     *
     * @param corridas metros e segundos de cada corrida.
     */
    fun velocidadeEmCorrida(corridas: List<Pair<Long, Long>>): Double? {
        val validas = corridas.filter { (m, s) -> m > 0 && s > 0 }
        if (validas.size < MINIMO_CORRIDAS_VELOCIDADE) return null
        val km = validas.sumOf { it.first } / 1000.0
        val horas = validas.sumOf { it.second } / 3600.0
        return dividirSeguro(km, horas).takeIf { it in 5.0..80.0 }
    }
}
