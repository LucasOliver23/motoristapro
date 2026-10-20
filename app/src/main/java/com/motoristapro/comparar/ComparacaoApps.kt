package com.motoristapro.comparar

/** As corridas registradas de um app no período, já somadas. */
data class CorridasDoApp(
    val plataforma: String,
    val corridas: Int,
    val valorCentavos: Long,
    /** Km da corrida + ida até o passageiro: é o km que gasta o veículo. */
    val metros: Long,
    val segundos: Long
)

/** As ofertas que o leitor viu de um app no período. */
data class OfertasDoApp(val plataforma: String, val total: Int, val aceitas: Int)

/** A linha de um app na comparação. */
data class ResultadoApp(
    val plataforma: String,
    val corridas: Int,
    val valorCentavos: Long,
    val porHoraCentavos: Long,
    val porKmCentavos: Long,
    /** Valor menos o custo do veículo nos km rodados (custo por km do assistente). */
    val lucroCentavos: Long,
    val ofertas: Int,
    /** 0..100; null sem oferta lida. */
    val aceitePercent: Int?,
    val melhorPorHora: Boolean
)

/**
 * "Comparar apps": qual plataforma paga mais para ESTE motorista.
 *
 * Kotlin puro (testado). O selo "melhor R$/h" só vai para app com pelo menos
 * [MINIMO_CORRIDAS] corridas: duas corridas boas não fazem um app bom.
 */
object ComparacaoApps {

    const val MINIMO_CORRIDAS = 5

    fun montar(corridas: List<CorridasDoApp>, ofertas: List<OfertasDoApp>, custoKmCentavos: Long): List<ResultadoApp> {
        val porNome = ofertas.associateBy { it.plataforma }
        val linhas = corridas.filter { it.corridas > 0 }.map { c ->
            val o = porNome[c.plataforma]
            ResultadoApp(
                plataforma = c.plataforma,
                corridas = c.corridas,
                valorCentavos = c.valorCentavos,
                porHoraCentavos = if (c.segundos > 0) c.valorCentavos * 3600 / c.segundos else 0,
                porKmCentavos = if (c.metros > 0) c.valorCentavos * 1000 / c.metros else 0,
                lucroCentavos = c.valorCentavos - Math.round(c.metros / 1000.0 * custoKmCentavos.coerceAtLeast(0)),
                ofertas = o?.total ?: 0,
                aceitePercent = o?.takeIf { it.total > 0 }?.let { it.aceitas * 100 / it.total },
                melhorPorHora = false
            )
        }
        val melhor = linhas.filter { it.corridas >= MINIMO_CORRIDAS && it.porHoraCentavos > 0 }
            .maxByOrNull { it.porHoraCentavos }?.plataforma
        return linhas.map { it.copy(melhorPorHora = it.plataforma == melhor) }
            .sortedWith(compareByDescending<ResultadoApp> { it.melhorPorHora }.thenByDescending { it.lucroCentavos })
    }
}
