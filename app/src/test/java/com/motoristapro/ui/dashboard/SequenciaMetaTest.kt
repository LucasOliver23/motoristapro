package com.motoristapro.ui.dashboard

import com.motoristapro.ui.dashboard.SequenciaMeta.Situacao
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SequenciaMetaTest {

    // Quarta-feira.
    private val hoje = LocalDate.of(2026, 10, 14)
    private val meta = 25_000L

    private fun dia(menos: Long) = hoje.minusDays(menos)

    @Test
    fun cincoDiasSeguidos() {
        val h = (0L..4L).associate { dia(it) to 30_000L }
        val s = SequenciaMeta.calcular(h, meta, hoje)
        assertEquals(5, s.atual)
        assertEquals(5, s.recorde)
    }

    @Test
    fun folgaNaoQuebraASequencia() {
        // Domingo (dia 11) sem lançamento no meio.
        val h = mapOf(dia(0) to 26_000L, dia(1) to 26_000L, dia(2) to 26_000L, dia(4) to 26_000L)
        assertEquals(4, SequenciaMeta.calcular(h, meta, hoje).atual)
    }

    @Test
    fun diaAbaixoDaMetaZeraMasGuardaRecorde() {
        val h = mapOf(
            dia(9) to 30_000L, dia(8) to 30_000L, dia(7) to 30_000L,
            dia(6) to 10_000L,
            dia(1) to 30_000L, dia(0) to 30_000L
        )
        val s = SequenciaMeta.calcular(h, meta, hoje)
        assertEquals(2, s.atual)
        assertEquals(3, s.recorde)
    }

    @Test
    fun semMetaNaoContaNada() {
        val s = SequenciaMeta.calcular(mapOf(dia(0) to 30_000L), 0, hoje)
        assertEquals(0, s.atual)
        assertEquals(0, s.recorde)
    }

    @Test
    fun semanaDeSegundaADomingo() {
        val h = mapOf(dia(2) to 30_000L, dia(1) to 9_000L, dia(0) to 30_000L)
        val semana = SequenciaMeta.calcular(h, meta, hoje).semana
        assertEquals(7, semana.size)
        assertEquals(LocalDate.of(2026, 10, 12), semana[0].data)
        assertEquals(
            listOf(
                Situacao.BATIDA, Situacao.NAO_BATIDA, Situacao.BATIDA,
                Situacao.FUTURO, Situacao.FUTURO, Situacao.FUTURO, Situacao.FUTURO
            ),
            semana.map { it.situacao }
        )
    }
}
