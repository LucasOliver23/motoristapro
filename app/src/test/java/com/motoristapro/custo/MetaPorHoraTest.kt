package com.motoristapro.custo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaPorHoraTest {

    /** O exemplo do motorista: R$ 95 por dia em 5 horas, R$ 0,50/km, 62% com passageiro. */
    @Test
    fun exemploDoMotorista() {
        val c = MetaPorHora.calcular(9_500, 5, 50, 15.0, 0.62)!!
        assertEquals(1_900, c.lucroPorHoraCentavos)
        assertEquals(750, c.custoPorHoraCentavos)
        assertEquals(2_650, c.faturarPorHoraCentavos)
        assertEquals(43, c.ofertaPorHoraReais)
        assertEquals(30, c.amareloReais)
        assertFalse(c.kmPorHoraEstimado)
        assertFalse(c.fracaoEstimada)
    }

    @Test
    fun semMedicaoUsaOPadrao() {
        val c = MetaPorHora.calcular(9_500, 5, 50, null, null)!!
        assertTrue(c.kmPorHoraEstimado)
        assertTrue(c.fracaoEstimada)
        // 26,50 ÷ 0,6 = 44,17 → 45 (arredonda para cima: abaixo disso não bate)
        assertEquals(45, c.ofertaPorHoraReais)
    }

    @Test
    fun semCustoContaSoOLucro() {
        val c = MetaPorHora.calcular(10_000, 5, 0, null, 0.5)!!
        assertEquals(0, c.custoPorHoraCentavos)
        assertEquals(40, c.ofertaPorHoraReais)
    }

    @Test
    fun semMetaOuSemHoras() {
        assertNull(MetaPorHora.calcular(0, 5, 50, null, null))
        assertNull(MetaPorHora.calcular(9_500, 0, 50, null, null))
    }

    @Test
    fun fracaoPrecisaDeTurnoQueFeche() {
        assertNull(MetaPorHora.fracaoComPassageiro(3_600, 2 * 3_600))
        assertEquals(0.5, MetaPorHora.fracaoComPassageiro(5 * 1_800, 5 * 3_600)!!, 1e-9)
        // Corridas registradas sem turno aberto: mais tempo de corrida que de turno.
        assertNull(MetaPorHora.fracaoComPassageiro(20 * 3_600, 10 * 3_600))
        assertEquals(0.2, MetaPorHora.fracaoComPassageiro(60, 10 * 3_600)!!, 1e-9)
    }

    @Test
    fun kmPorHoraDoGps() {
        assertEquals(15.0, MetaPorHora.kmPorHora(75_000, 5 * 3_600)!!, 1e-9)
        assertNull(MetaPorHora.kmPorHora(0, 5 * 3_600))
        assertNull(MetaPorHora.kmPorHora(75_000, 3_600))
    }
}
