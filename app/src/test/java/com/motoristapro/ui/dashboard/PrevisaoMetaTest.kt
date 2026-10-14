package com.motoristapro.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrevisaoMetaTest {

    private val agora = 1_000_000_000L

    @Test
    fun semMetaNaoMostraNada() {
        assertNull(PrevisaoMeta.calcular(0, 5_000, 3600, true, agora))
    }

    @Test
    fun lucroIgualOuAcimaDaMetaEBatida() {
        assertEquals(PrevisaoMeta.Batida, PrevisaoMeta.calcular(25_000, 25_000, 3600, true, agora))
        assertEquals(PrevisaoMeta.Batida, PrevisaoMeta.calcular(25_000, 30_000, 3600, false, agora))
    }

    @Test
    fun ritmoEHorarioComTurnoAndando() {
        // R$ 128 em 4 h = R$ 32/h; faltam R$ 122 -> 13725 s (3h48m45s).
        val p = PrevisaoMeta.calcular(25_000, 12_800, 4 * 3600, true, agora) as PrevisaoMeta.Falta
        assertEquals(12_200L, p.faltaCentavos)
        assertEquals(3_200L, p.ritmoCentavosHora)
        assertEquals(agora + 13_725_000L, p.chegaEmMillis)
    }

    @Test
    fun turnoPausadoTemRitmoMasNaoTemHorario() {
        val p = PrevisaoMeta.calcular(25_000, 12_800, 4 * 3600, false, agora) as PrevisaoMeta.Falta
        assertEquals(3_200L, p.ritmoCentavosHora)
        assertNull(p.chegaEmMillis)
    }

    @Test
    fun poucoTempoRodadoNaoInventaRitmo() {
        val p = PrevisaoMeta.calcular(25_000, 4_000, 10 * 60, true, agora) as PrevisaoMeta.Falta
        assertEquals(21_000L, p.faltaCentavos)
        assertNull(p.ritmoCentavosHora)
        assertNull(p.chegaEmMillis)
    }

    @Test
    fun prejuizoNaoTemRitmo() {
        val p = PrevisaoMeta.calcular(25_000, -2_000, 2 * 3600, true, agora) as PrevisaoMeta.Falta
        assertEquals(27_000L, p.faltaCentavos)
        assertNull(p.ritmoCentavosHora)
    }

    @Test
    fun previsaoLongaDemaisNaoMostraHorario() {
        // R$ 5/h e faltam R$ 245: 49 h.
        val p = PrevisaoMeta.calcular(25_000, 500, 3600, true, agora) as PrevisaoMeta.Falta
        assertEquals(500L, p.ritmoCentavosHora)
        assertNull(p.chegaEmMillis)
    }
}
