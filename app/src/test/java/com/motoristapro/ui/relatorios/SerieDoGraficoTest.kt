package com.motoristapro.ui.relatorios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SerieDoGraficoTest {

    private val hoje = LocalDate.of(2026, 10, 14)   // quarta

    @Test
    fun anoViraDozeBarrasPorMes() {
        val dias = listOf(
            LocalDate.of(2026, 1, 5) to 10_000L,
            LocalDate.of(2026, 1, 20) to 5_000L,
            LocalDate.of(2026, 10, 14) to 20_000L
        )
        val s = SerieDoGrafico.montar(PeriodoRelatorio.ANUAL, dias, 20_000, 500_000, hoje)
        assertEquals(12, s.valores.size)
        assertEquals(15_000L, s.valores[0])
        assertEquals(20_000L, s.valores[9])
        assertEquals(500_000L, s.metaPorBarra)
        assertEquals(9, s.emAndamento)
        assertEquals("O", s.rotulos[9])
    }

    @Test
    fun semanaUsaLetraDoDiaEMetaDiaria() {
        val dias = (6 downTo 0).map { hoje.minusDays(it.toLong()) to 1_000L }
        val s = SerieDoGrafico.montar(PeriodoRelatorio.SEMANAL, dias, 20_000, 500_000, hoje)
        assertEquals(listOf("Q", "Q", "S", "S", "D", "S", "T", "Q").takeLast(7), s.rotulos)
        assertEquals(20_000L, s.metaPorBarra)
        assertEquals(6, s.emAndamento)
    }

    @Test
    fun semCorridaEVazia() {
        val dias = (6 downTo 0).map { hoje.minusDays(it.toLong()) to 0L }
        assertTrue(SerieDoGrafico.montar(PeriodoRelatorio.SEMANAL, dias, 0, 0, hoje).vazia)
    }
}
