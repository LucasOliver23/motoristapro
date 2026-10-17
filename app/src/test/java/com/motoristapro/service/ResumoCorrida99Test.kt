package com.motoristapro.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ResumoCorrida99Test {

    /** Os textos do diagnóstico real (99 Motorista, 17/10/2026). */
    private val telaReal = listOf(
        "Detalhes da corrida", "Você ganhou", "R$9,30", "Pacote 0% de Taxa99 semanal",
        "Duração12m30s", "Distância6,7 km", "Tipo de corridaMoto", "Método de pagamento", "Online",
        "Seus ganhos", "Valor da corridaR$8,30", "Tarifa baseR$1,00", "TotalR$9,30",
        "Pago pelo passageiro", "Total", "R$7,84", "Recebido pela 99",
        "Valor a receber da 99-R$0,50", "A 99 completouR$1,96", "TotalR$1,46",
        "Detalhes da corrida", "Maria", "17/10/2026 08:15:24",
        "Jardim Maria Inês, GO,", "Avenida St. Marista, Goiânia - GO"
    )

    @Test
    fun telaReal_eLidaComOValorQueCaiu() {
        val r = ResumoCorrida99.ler(telaReal)!!
        assertEquals(930, r.valorCentavos)
        assertEquals(6_700, r.metros)
        assertEquals(750, r.segundos)
        assertEquals("Moto", r.tipo)
        assertEquals(LocalDateTime.of(2026, 10, 17, 8, 15, 24), r.quando)
        assertEquals("Jardim Maria Inês, GO", r.origem)
        assertEquals("Avenida St. Marista, Goiânia - GO", r.destino)
    }

    @Test
    fun rotulosEmLinhasSeparadas() {
        val r = ResumoCorrida99.ler(
            listOf("Detalhes da corrida", "Você ganhou R$ 23,45", "Duração", "1h 05m", "Distância", "18,2 km")
        )!!
        assertEquals(2_345, r.valorCentavos)
        assertEquals(3_900, r.segundos)
        assertEquals(18_200, r.metros)
        assertNull(r.quando)
    }

    @Test
    fun ofertaNaoEhResumo() {
        val oferta = listOf("99Pop", "R$ 12,40", "4,2 km", "8 min", "Aceitar")
        assertFalse(ResumoCorrida99.ehResumo(oferta))
        assertNull(ResumoCorrida99.ler(oferta))
    }

    @Test
    fun semKmESemTempo_naoOferece() {
        assertTrue(ResumoCorrida99.ehResumo(listOf("Detalhes da corrida", "Você ganhou", "R$9,30")))
        assertNull(ResumoCorrida99.ler(listOf("Detalhes da corrida", "Você ganhou", "R$9,30")))
    }
}
