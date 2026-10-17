package com.motoristapro.pacote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PacoteTaxaTest {

    private val hoje = LocalDate.of(2026, 10, 17)

    @Test
    fun semPacote_nuncaLanca() {
        assertFalse(PacoteTaxa.precisaLancar(PlanoPacote.NENHUM, hoje, null, 0))
    }

    @Test
    fun primeiraCorrida_lancaOPacote() {
        assertTrue(PacoteTaxa.precisaLancar(PlanoPacote.DIA, hoje, null, 0))
    }

    @Test
    fun pacoteDeUmDia_lancaUmaVezPorDia() {
        assertFalse(PacoteTaxa.precisaLancar(PlanoPacote.DIA, hoje, hoje, 0))
        assertTrue(PacoteTaxa.precisaLancar(PlanoPacote.DIA, hoje.plusDays(1), hoje, 0))
    }

    @Test
    fun pacoteDeTresDias_valeTresDias() {
        assertFalse(PacoteTaxa.precisaLancar(PlanoPacote.TRES_DIAS, hoje.plusDays(2), hoje, 0))
        assertTrue(PacoteTaxa.precisaLancar(PlanoPacote.TRES_DIAS, hoje.plusDays(3), hoje, 0))
    }

    @Test
    fun pacoteDeGanhos_lancaQuandoOLimiteAcaba() {
        assertFalse(PacoteTaxa.precisaLancar(PlanoPacote.GANHOS_135, hoje, hoje, 13_499))
        assertTrue(PacoteTaxa.precisaLancar(PlanoPacote.GANHOS_135, hoje, hoje, 13_500))
        assertTrue(PacoteTaxa.precisaLancar(PlanoPacote.GANHOS_135, hoje.plusDays(180), hoje, 0))
    }

    @Test
    fun corridaComDataAntiga_naoLanca() {
        assertFalse(PacoteTaxa.precisaLancar(PlanoPacote.DIA, hoje.minusDays(2), hoje, 0))
    }

    @Test
    fun custoDoDia_porTempoDivideOPreco() {
        assertEquals(299, PacoteTaxa.custoDoDiaCentavos(PlanoPacote.DIA, 10_000))
        assertEquals(233, PacoteTaxa.custoDoDiaCentavos(PlanoPacote.TRES_DIAS, 10_000))
        assertEquals(0, PacoteTaxa.custoDoDiaCentavos(PlanoPacote.DIA, 0))
    }

    @Test
    fun custoDoDia_porGanhosEhProporcional() {
        // R$ 67,50 de R$ 135 = metade dos R$ 3,99
        assertEquals(200, PacoteTaxa.custoDoDiaCentavos(PlanoPacote.GANHOS_135, 6_750))
    }

    @Test
    fun economia_comparaComATaxaMedia() {
        // R$ 124,30 na 99: sem pacote ~ R$ 24,86 de taxa; menos R$ 2,99
        assertEquals(2_486, PacoteTaxa.taxaEvitadaCentavos(12_430))
        assertEquals(2_187, PacoteTaxa.economiaDoDiaCentavos(PlanoPacote.DIA, 12_430))
        // Dia fraco: R$ 9,30 não paga o pacote de 1 dia
        assertTrue(PacoteTaxa.economiaDoDiaCentavos(PlanoPacote.DIA, 930) < 0)
    }
}
