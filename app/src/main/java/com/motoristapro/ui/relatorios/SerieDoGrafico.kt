package com.motoristapro.ui.relatorios

import java.time.LocalDate

/**
 * As barras do gráfico de Atividade, já agrupadas e com a meta de cada barra.
 *
 * Semana e mês: uma barra por dia, com a meta do DIA. Ano: uma barra por mês
 * (antes eram 365 risquinhos ilegíveis), com a meta do MÊS. Kotlin puro,
 * testado em [SerieDoGraficoTest].
 */
data class SerieDoGrafico(
    /** Lucro de cada barra, em centavos. */
    val valores: List<Long>,
    val rotulos: List<String>,
    /** A linha tracejada: meta de cada barra (0 = sem meta, sem linha). */
    val metaPorBarra: Long,
    /** Índice da barra que ainda está correndo (hoje, ou o mês atual), ou -1. */
    val emAndamento: Int
) {
    /** Nada para desenhar: sem corrida nenhuma no período. */
    val vazia: Boolean get() = valores.all { it == 0L }

    companion object {
        private val LETRA_DO_DIA = listOf("S", "T", "Q", "Q", "S", "S", "D")
        private val LETRA_DO_MES = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

        fun montar(
            periodo: PeriodoRelatorio,
            lucroPorDia: List<Pair<LocalDate, Long>>,
            metaDiaria: Long,
            metaMensal: Long,
            hoje: LocalDate
        ): SerieDoGrafico = when (periodo) {
            PeriodoRelatorio.ANUAL -> {
                val porMes = LongArray(12)
                lucroPorDia.forEach { (d, v) -> if (d.year == hoje.year) porMes[d.monthValue - 1] += v }
                SerieDoGrafico(porMes.toList(), LETRA_DO_MES, metaMensal, hoje.monthValue - 1)
            }
            else -> {
                val passo = when {
                    lucroPorDia.size <= 7 -> 1
                    lucroPorDia.size <= 15 -> 2
                    else -> 5
                }
                val rotulos = lucroPorDia.mapIndexed { i, (d, _) ->
                    when {
                        periodo == PeriodoRelatorio.SEMANAL -> LETRA_DO_DIA[d.dayOfWeek.value - 1]
                        i % passo == 0 || i == lucroPorDia.lastIndex -> d.dayOfMonth.toString()
                        else -> ""
                    }
                }
                SerieDoGrafico(
                    lucroPorDia.map { it.second }, rotulos, metaDiaria,
                    lucroPorDia.indexOfFirst { it.first == hoje }
                )
            }
        }
    }
}
