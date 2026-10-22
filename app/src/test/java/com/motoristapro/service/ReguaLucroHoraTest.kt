package com.motoristapro.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReguaLucroHoraTest {

    /** Meta de R$ 25 limpos por hora de turno, custo de R$ 0,30/km, nada medido ainda. */
    private val novato = ParametrosRegua(metaLimpaHoraCentavos = 2500, custoKmCentavos = 30)

    @Test
    fun exemploDoDocumento_naoBateAMeta_ficaAtencao() {
        // 20 min, 4 km: lucro = 13,20 − 1,20 = 12,00 → R$ 36/h; precisa 25 ÷ 0,6 = 41,67.
        val a = ReguaLucroHora.avaliar(Oferta(13.20, 4.0, 20), novato)!!
        assertEquals(12.0, a.lucroReais, 0.001)
        assertEquals(36.0, a.lucroPorHoraReais, 0.001)
        assertEquals(41.67, a.alvoPorHoraReais, 0.01)
        assertEquals(Classificacao.MEDIA, a.classe)
        assertTrue(a.estimado)
        assertTrue(a.fracaoEstimada)
        assertFalse(a.tempoPeloKm)
    }

    @Test
    fun bateAMeta_aceitar() {
        val a = ReguaLucroHora.avaliar(Oferta(20.0, 4.0, 20), novato)!!
        assertEquals(Classificacao.BOA, a.classe)
    }

    @Test
    fun muitoAbaixoDaMeta_recusar() {
        // lucro 8,80 em 30 min = 17,60/h; 70% de 41,67 = 29,17.
        val a = ReguaLucroHora.avaliar(Oferta(10.0, 4.0, 30), novato)!!
        assertEquals(Classificacao.RUIM, a.classe)
    }

    @Test
    fun prejuizo_semprerecusar() {
        val a = ReguaLucroHora.avaliar(Oferta(1.0, 10.0, 1), novato)!!
        assertEquals(Classificacao.RUIM, a.classe)
    }

    @Test
    fun fracaoMedida_mudaOAlvo_eTiraOEstimado() {
        val p = novato.copy(fracaoMedida = 0.8)
        val a = ReguaLucroHora.avaliar(Oferta(13.20, 4.0, 20), p)!!
        assertEquals(31.25, a.alvoPorHoraReais, 0.01)
        assertEquals(Classificacao.BOA, a.classe)
        assertFalse(a.estimado)
    }

    @Test
    fun entregaSemTempo_usaVelocidadePadrao_eMarcaEstimado() {
        // 99 Entrega Food real: R$ 16,69, 8,7 km. 8,7 km a 15 km/h = 34,8 → 35 min.
        val a = ReguaLucroHora.avaliar(Oferta(16.69, 8.7, 0), novato.copy(fracaoMedida = 0.6))!!
        assertTrue(a.tempoPeloKm)
        assertEquals(35, a.minutos)
        assertTrue(a.velocidadeEstimada)
        assertTrue(a.estimado)
    }

    @Test
    fun entregaSemTempo_comVelocidadeMedida_naoEhEstimado() {
        val p = novato.copy(fracaoMedida = 0.6, velocidadeMedidaKmH = 25.0)
        val a = ReguaLucroHora.avaliar(Oferta(16.69, 8.7, 0), p)!!
        assertEquals(21, a.minutos)          // 8,7 ÷ 25 × 60 = 20,9 → 21
        assertFalse(a.estimado)
    }

    @Test
    fun corridaComTempo_naoUsaAVelocidade() {
        val a = ReguaLucroHora.avaliar(Oferta(16.69, 8.7, 30), novato.copy(fracaoMedida = 0.6))!!
        assertEquals(30, a.minutos)
        assertFalse(a.tempoPeloKm)
        assertFalse(a.estimado)              // velocidade padrão não entrou na conta
    }

    @Test
    fun semMeta_naoAvalia() {
        assertNull(ReguaLucroHora.avaliar(Oferta(20.0, 4.0, 20), novato.copy(metaLimpaHoraCentavos = 0)))
        assertNull(ReguaLucroHora.avaliar(Oferta(20.0, 4.0, 20), novato.copy(custoKmCentavos = -1)))
    }

    /** Bike quitada: custo zero é custo de verdade, o lucro é o valor inteiro. */
    @Test
    fun bikeComCustoZero_avalia() {
        val a = ReguaLucroHora.avaliar(Oferta(7.0, 2.26, 17), novato.copy(custoKmCentavos = 0))!!
        assertEquals(7.0, a.lucroReais, 0.001)
        // R$ 24,71/h contra R$ 41,67/h: menos de 70% do que precisa.
        assertEquals(Classificacao.RUIM, a.classe)
    }

    @Test
    fun valorParaBaterMeta_viraAceitar() {
        val o = Oferta(13.20, 4.0, 20)
        val v = ReguaLucroHora.valorParaBaterMeta(o, novato)!!
        // 41,67 × 1/3 h + 4 × 0,30 = 13,89 + 1,20 = 15,09
        assertEquals(15.09, v, 0.01)
        assertEquals(Classificacao.BOA, ReguaLucroHora.avaliar(o.copy(valor = v + 0.01), novato)!!.classe)
    }

    @Test
    fun velocidade_precisaDe10Corridas() {
        val nove = List(9) { 5_000L to 900L }           // 5 km em 15 min = 20 km/h
        assertNull(ReguaLucroHora.velocidadeEmCorrida(nove))
        assertEquals(20.0, ReguaLucroHora.velocidadeEmCorrida(nove + (5_000L to 900L))!!, 0.001)
    }

    @Test
    fun velocidade_ignoraCorridaSemKmOuSemTempo_eDescartaAbsurdo() {
        val validas = List(10) { 5_000L to 900L }
        assertEquals(20.0, ReguaLucroHora.velocidadeEmCorrida(validas + (0L to 600L) + (3_000L to 0L))!!, 0.001)
        // 50 km em 1 min cada: 3.000 km/h, digitado errado
        assertNull(ReguaLucroHora.velocidadeEmCorrida(List(10) { 50_000L to 60L }))
    }
}
