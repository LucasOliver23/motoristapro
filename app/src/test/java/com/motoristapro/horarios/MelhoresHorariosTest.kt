package com.motoristapro.horarios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MelhoresHorariosTest {

    private fun f(dia: Int, faixa: Int, app: String, n: Int, valor: Long, seg: Long) =
        FatiaCorridas(dia, faixa, app, n, valor, metros = valor * 1000 / 200, segundos = seg)

    @Test
    fun somaOsAppsDoMesmoHorario() {
        val h = MelhoresHorarios.somar(listOf(f(5, 9, "Uber", 2, 4_000, 3_600), f(5, 9, "99", 2, 2_000, 3_600))).single()
        assertEquals(4, h.corridas)
        assertEquals(3_000, h.porHoraCentavos)
        assertEquals(listOf("Uber" to 4_000L, "99" to 2_000L), h.porApp)
        assertEquals("Sexta · 18h–20h", h.rotulo)
    }

    @Test
    fun podioIgnoraHorarioComPoucasCorridas() {
        val horarios = MelhoresHorarios.somar(
            listOf(
                f(5, 9, "Uber", 4, 16_000, 14_400),   // R$ 40/h, 4 corridas
                f(6, 11, "Uber", 3, 11_400, 10_800),  // R$ 38/h
                f(1, 3, "Uber", 1, 9_000, 1_800),     // R$ 180/h, mas 1 corrida só
                f(1, 7, "99", 5, 10_000, 18_000)      // R$ 20/h
            )
        )
        val podio = MelhoresHorarios.podio(horarios)
        assertEquals(listOf(5 to 9, 6 to 11, 1 to 7), podio.map { it.diaSemana to it.faixa })
    }

    @Test
    fun gradeComEscala() {
        val (g, maior) = MelhoresHorarios.grade(MelhoresHorarios.somar(listOf(f(0, 11, "Uber", 3, 9_000, 3_600))))
        assertEquals(9_000, g[11][0])
        assertEquals(9_000, maior)
        assertTrue(g[0].all { it == 0L })
        assertEquals("Domingo · 22h–24h", MelhoresHorarios.somar(listOf(f(0, 11, "Uber", 3, 9_000, 3_600))).single().rotulo)
    }
}
