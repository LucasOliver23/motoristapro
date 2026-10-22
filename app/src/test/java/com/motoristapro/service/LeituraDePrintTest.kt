package com.motoristapro.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeituraDePrintTest {

    /** Card real da Uber (o mesmo do OfertaParserTest). */
    private val uber = listOf(
        "UberX", "Exclusivo", "R$ 0,92", "R$0,34/km aprox.", "4,96 (301)",
        "4 min (1.2 km)", "Avenida Afonso Pena, Washington Pires, Belo Horizonte",
        "5 minutos (1.5 km)", "Av. Dos Andradas, 367, Centro, Belo Horizonte", "Aceitar"
    )

    /** Card real do iFood: sem "min" por trecho; o tempo é da rota inteira. */
    private val ifood = listOf(
        "Entrega 1", "Goiania Sul", "R$ 37,38", "Rota para Moto Baú",
        "Distância total", "24,91 km", "Possibilidade de devolução", "Sim", "Rejeitar", "Aceitar"
    )

    @Test
    fun parteDeBaixo_valeAntesDoPrintInteiro() {
        val r = LeituraDePrint.interpretar(uber, uber + listOf("R$ 500,00 ganhos hoje"), AppDoPrint.UBER)
        assertEquals(LeituraDePrint.Parte.BAIXO, r.parte)
        assertEquals(0.92, r.oferta!!.valor, 0.001)
        assertEquals(2.7, r.oferta!!.km, 0.001)
        assertNull(r.motivo)
    }

    @Test
    fun soOPrintInteiroTemAOferta_avisaQueFoiPeloInteiro() {
        // Print recortado só no card: o recorte do topo come o valor (e o "R$/km aprox.").
        val baixo = uber.drop(4)
        val r = LeituraDePrint.interpretar(baixo, uber, AppDoPrint.UBER)
        assertEquals(LeituraDePrint.Parte.INTEIRO, r.parte)
        assertNotNull(r.oferta)
        assertEquals(uber, r.linhas)
    }

    @Test
    fun ifood_semTempo_lido() {
        val r = LeituraDePrint.interpretar(ifood, ifood, AppDoPrint.IFOOD)
        val o = r.oferta!!
        assertEquals(37.38, o.valor, 0.001)
        assertEquals(24.91, o.km, 0.001)
        assertEquals(0, o.minutos)
        assertTrue(o.devolucao)
    }

    @Test
    fun corridaDePassageiroSemTempo_naoLe_eExplica() {
        val textos = listOf("UberX", "R$ 15,00", "6,0 km", "Aceitar")
        val r = LeituraDePrint.interpretar(textos, textos, AppDoPrint.UBER)
        assertNull(r.oferta)
        assertNull(r.parte)
        assertTrue(r.motivo!!.contains("tempo"))
    }

    @Test
    fun mesmoTextoComoIfood_leSemTempo() {
        val textos = listOf("R$ 15,00", "6,0 km", "Aceitar")
        assertNull(LeituraDePrint.interpretar(textos, textos, AppDoPrint.NAO_SEI).oferta)
        assertNotNull(LeituraDePrint.interpretar(textos, textos, AppDoPrint.IFOOD).oferta)
    }

    @Test
    fun motivos() {
        assertTrue(LeituraDePrint.porQueNaoLeu(emptyList(), AppDoPrint.UBER).contains("texto nenhum"))
        assertTrue(LeituraDePrint.porQueNaoLeu(listOf("Aceitar", "6 km"), AppDoPrint.UBER).contains("valor"))
        assertTrue(LeituraDePrint.porQueNaoLeu(listOf("R$ 10,00", "Aceitar"), AppDoPrint.UBER).contains("distância"))
    }

    @Test
    fun anonimizar_trocaEnderecos_eMantemONumeros() {
        val saida = LeituraDePrint.anonimizar(uber)
        assertFalse(saida.any { it.contains("Afonso Pena") || it.contains("Andradas") })
        assertTrue(saida.contains("Rua Exemplo 1, 100"))
        assertTrue(saida.contains("Rua Exemplo 2, 100"))
        // O que decide a oferta continua igual.
        assertEquals(OfertaParser.extrair(uber)!!.assinatura, OfertaParser.extrair(saida)!!.assinatura)
    }

    @Test
    fun anonimizar_mesmoEnderecoRepetido_viraOMesmoExemplo() {
        val saida = LeituraDePrint.anonimizar(listOf("Rua das Flores, 120", "R$ 10,00", "Rua das Flores, 120"))
        assertEquals(listOf("Rua Exemplo 1, 100", "R$ 10,00", "Rua Exemplo 1, 100"), saida)
    }

    @Test
    fun anonimizar_trocaCep() {
        val saida = LeituraDePrint.anonimizar(listOf("CEP 74000-123", "R$ 10,00"))
        assertEquals("Rua Exemplo 1, 100", saida[0])
    }

    @Test
    fun textoParaEnviar_naoLevaEndereco() {
        val r = LeituraDePrint.interpretar(uber, uber, AppDoPrint.UBER)
        val texto = LeituraDePrint.textoParaEnviar(r, AppDoPrint.UBER, "Teste X")
        assertTrue(texto.contains("App: Uber"))
        assertFalse(texto.contains("Afonso Pena"))
    }

    @Test
    fun escolha_ehAMesmaDoLeitor() {
        // Lista com duas corridas: vale a de melhor R$/km, e o total conta as duas.
        val lista = listOf(
            "R$ 10,00", "(10 min 5,0 km)",
            "R$ 20,00", "(12 min 5,0 km)"
        )
        val e = EscolhaOferta.escolher(lista, "com.app99.driver")!!
        assertEquals(2, e.total)
        assertEquals(20.0, e.oferta.valor, 0.001)
    }
}
