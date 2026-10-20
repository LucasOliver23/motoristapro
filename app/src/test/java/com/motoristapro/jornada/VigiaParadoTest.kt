package com.motoristapro.jornada

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VigiaParadoTest {

    private val min = 60_000L
    private val perto: (Double, Double) -> Double = { _, _ -> 20.0 }
    private val longe: (Double, Double) -> Double = { _, _ -> 500.0 }

    @Test
    fun avisaDepoisDe30MinutosParado() {
        val v = VigiaParado(0)
        v.posicao(1.0, 1.0, 0, perto)
        assertFalse(v.deveAvisar(29 * min, pausada = false))
        assertTrue(v.deveAvisar(30 * min, pausada = false))
    }

    @Test
    fun avisaSoUmaVezPorParada() {
        val v = VigiaParado(0)
        v.posicao(1.0, 1.0, 0, perto)
        assertTrue(v.deveAvisar(31 * min, false))
        v.posicao(1.0, 1.0, 40 * min, perto)   // deriva do GPS: continua parado
        assertFalse(v.deveAvisar(60 * min, false))
    }

    @Test
    fun andarLiberaNovoAviso() {
        val v = VigiaParado(0)
        v.posicao(1.0, 1.0, 0, perto)
        assertTrue(v.deveAvisar(31 * min, false))
        assertTrue(v.posicao(2.0, 2.0, 35 * min, longe))   // andou: tira o aviso
        assertFalse(v.deveAvisar(50 * min, false))
        assertTrue(v.deveAvisar(66 * min, false))
    }

    @Test
    fun jornadaPausadaNaoAvisa() {
        val v = VigiaParado(0)
        assertFalse(v.deveAvisar(90 * min, pausada = true))
        v.reiniciar(90 * min)
        assertFalse(v.deveAvisar(100 * min, false))
        assertTrue(v.deveAvisar(120 * min, false))
    }

    @Test
    fun semGpsContaDesdeOInicio() {
        val v = VigiaParado(10 * min)
        assertFalse(v.deveAvisar(39 * min, false))
        assertTrue(v.deveAvisar(40 * min, false))
    }
}
