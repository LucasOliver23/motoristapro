package com.motoristapro.comparar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparacaoAppsTest {

    private val corridas = listOf(
        CorridasDoApp("Uber", 58, 200_000, 1_230_000, 17_500),      // R$ 41/h
        CorridasDoApp("99", 51, 140_000, 820_000, 13_600),          // R$ 37/h
        CorridasDoApp("inDrive", 3, 30_000, 150_000, 1_800)         // R$ 60/h, mas só 3 corridas
    )
    private val ofertas = listOf(OfertasDoApp("99", 210, 51), OfertasDoApp("Uber", 0, 0))

    @Test
    fun melhorPorHoraPrecisaDeCorridasSuficientes() {
        val r = ComparacaoApps.montar(corridas, ofertas, 43)
        assertEquals("Uber", r.first().plataforma)
        assertTrue(r.first().melhorPorHora)
        assertFalse(r.first { it.plataforma == "inDrive" }.melhorPorHora)
    }

    @Test
    fun contas() {
        val uber = ComparacaoApps.montar(corridas, ofertas, 43).first { it.plataforma == "Uber" }
        assertEquals(41_142, uber.porHoraCentavos)
        assertEquals(162, uber.porKmCentavos)
        assertEquals(200_000 - 52_890, uber.lucroCentavos)
        assertNull(uber.aceitePercent)
        assertEquals(24, ComparacaoApps.montar(corridas, ofertas, 43).first { it.plataforma == "99" }.aceitePercent)
    }

    @Test
    fun semCorridaNaoAparece() {
        assertTrue(ComparacaoApps.montar(listOf(CorridasDoApp("iFood", 0, 0, 0, 0)), emptyList(), 43).isEmpty())
    }
}
