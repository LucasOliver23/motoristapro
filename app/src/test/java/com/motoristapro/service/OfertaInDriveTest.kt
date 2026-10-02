package com.motoristapro.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telas reais do inDrive, transcritas dos prints e do vídeo do motorista.
 *
 * O caso mais importante é o da LISTA: ela mostra uma dúzia de ofertas de uma
 * vez e tem que ser ignorada, senão o app junta o valor de uma com o km de outra.
 */
class OfertaInDriveTest {

    private fun perto(esperado: Double, real: Double, oque: String) {
        assertTrue("$oque: esperava $esperado, veio $real", Math.abs(esperado - real) < 0.05)
    }

    // ------------------------------------------------------------ telas

    private val detalhe6 = listOf(
        "Pedido de viagem", "2,5 km", "4 min", "1,0 km", "R$ 1,8/km", "~1,0 km", "R$ 6",
        "Maria", "4.72", "(189)", "2 min.", "Rua T-52 57 (St. Bueno)",
        "SICOOB SECOVICRED - PA AV. D (Avenida D - Setor Oeste, Goiânia - Goiás)", "PIX",
        "Aceitar por R$ 6", "Ofereça sua tarifa", "R$ 7", "R$ 8", "R$ 9", "Fechar"
    )

    private val detalhe13 = listOf(
        "Pedido de viagem", "11 min", "4,3 km", "20 min", "11,9 km",
        "R$ 0,9/km", "~4,3 km", "R$ 13", "Pablo", "4.86", "(158)", "9 min.",
        "Avenida Independência 69 (St. Aeroporto)",
        "eliza fashion (Av. Alberto Einstein - Jardim da Luz, Goiânia - GO)", "PIX",
        "Aceitar por R$ 13", "Ofereça sua tarifa", "R$ 15", "R$ 16", "R$ 18", "Fechar"
    )

    private val entrega15 = listOf(
        "Ignorar a oferta", "19 min.", "7,2 km", "6 min", "2,0 km",
        "Scarlitte", "1.0 (1)", "11 min.", "Rua 1139 261",
        "Oz Rei Das Sandálias - Loja Campinas, Goiânia - GO", "R$15, PIX", "~2,0 km",
        "Entregas", "Porta a porta", "Entrega pra Sabrina",
        "Aceitar por R$ 15", "Ofereça sua tarifa", "R$ 16,50", "R$ 18", "R$ 19"
    )

    private val lista = listOf(
        "Online", "Resolva esses problemas para evitar perder as melhores solicitações",
        "R$ 1,8/km", "~1,1 km", "R$ 6", "Maria", "4.72", "(189)", "2 min.",
        "Rua T-52 57 (St. Bueno)",
        "R$ 1,7/km", "~1,3 km", "R$ 15", "Scarlitte", "1.0(1)", "10 min.",
        "Rua 1139 261 (St. Marista)",
        "R$ 1,2/km", "~2,2 km", "R$ 20", "Preço justo",
        "Pedidos de viagem", "Desempenho"
    )

    // ------------------------------------------------------------ o portão

    @Test
    fun listaDeOfertas_eIgnorada() {
        assertTrue(!OfertaInDrive.ehTelaDeDetalhe(lista))
        assertNull(OfertaInDrive.extrair(lista))
    }

    @Test
    fun telaDeDetalhe_eReconhecida() {
        listOf(detalhe6, detalhe13, entrega15).forEach {
            assertTrue(OfertaInDrive.ehTelaDeDetalhe(it))
        }
    }

    // ------------------------------------------------------------ números

    @Test
    fun valorVemDoBotaoAceitar_eNaoDaContraproposta() {
        // A tela tem R$ 7, R$ 8 e R$ 9 embaixo: o leitor comum cravaria R$ 9.
        assertEquals(6.0, OfertaInDrive.extrair(detalhe6)!!.valor, 0.001)
        assertEquals(13.0, OfertaInDrive.extrair(detalhe13)!!.valor, 0.001)
        assertEquals(15.0, OfertaInDrive.extrair(entrega15)!!.valor, 0.001)
    }

    @Test
    fun distanciaDeBusca_naoContaDuasVezes() {
        // 1,0 km no mapa e ~1,0 km no cartão são a MESMA busca. Com a viagem
        // de 2,5 km o total é 3,5 km — não 4,5.
        perto(3.5, OfertaInDrive.extrair(detalhe6)!!.km, "km do detalhe de R$ 6")
        perto(16.2, OfertaInDrive.extrair(detalhe13)!!.km, "km do detalhe de R$ 13")
        perto(9.2, OfertaInDrive.extrair(entrega15)!!.km, "km da entrega")
    }

    @Test
    fun tempoDoPassageiro_naoEntraNaConta() {
        // "9 min." ao lado da nota é há quanto tempo o pedido chegou.
        assertEquals(31, OfertaInDrive.extrair(detalhe13)!!.minutos)   // 11 + 20
        assertEquals(25, OfertaInDrive.extrair(entrega15)!!.minutos)   // 6 + 19
        assertEquals(4, OfertaInDrive.extrair(detalhe6)!!.minutos)
    }

    @Test
    fun reaisPorKm_bateComOQueOInDriveMostra() {
        // O inDrive exibe R$ 1,3/km nesta corrida de R$ 8.
        val oferta = OfertaInDrive.extrair(
            listOf(
                "Pedido de viagem", "2,6 km", "10 min", "3,7 km", "R$ 1,3/km", "~3,7 km",
                "R$ 8", "Preço justo", "Farkhund", "4.83", "(37)", "1 min.",
                "Rua 7 121 (St. Central)", "PIX",
                "Aceitar por R$ 8", "Ofereça sua tarifa", "R$ 9", "R$ 10", "R$ 11"
            )
        )!!
        perto(1.27, oferta.reaisPorKm, "R$/km")
    }

    @Test
    fun notaDoPassageiro_eLida() {
        assertEquals(4.72, OfertaInDrive.extrair(detalhe6)!!.nota!!, 0.001)
        assertEquals(1.0, OfertaInDrive.extrair(entrega15)!!.nota!!, 0.001)
    }

    @Test
    fun entrega_eReconhecida() {
        assertTrue(OfertaInDrive.pareceEntrega(entrega15))
        assertTrue(!OfertaInDrive.pareceEntrega(detalhe6))
    }

    // ------------------------------------------------- leitura incompleta

    private val detalhe11 = listOf(
        "Pedido de viagem", "12 min", "6,0 km", "10 min", "4,4 km",
        "R$ 1,1/km", "~4,4 km", "R$ 11", "Preço justo", "Bianca", "4.93", "(17)", "8 min.",
        "Rua Catauai Q 30 89 (Parque Amazonia)",
        "Espaço Lidiany Loiola Hair (Rua U-53 - Vila Uniao, Goiânia - GO)", "PIX",
        "Aceitar por R$ 11", "Ofereça sua tarifa", "R$ 13", "R$ 14", "Fechar"
    )

    @Test
    fun buscaMaisViagem_quandoAsDuasAparecem() {
        val oferta = OfertaInDrive.extrair(detalhe11)!!
        perto(10.4, oferta.km, "4,4 de busca + 6,0 de viagem")
        // O inDrive mostra R$ 1,1/km nesta corrida.
        perto(1.06, oferta.reaisPorKm, "R$/km")
        assertEquals(22, oferta.minutos)
    }

    @Test
    fun soABusca_naoValePorCartao() {
        // Caso real: o mapa e o cartão são janelas diferentes e só a do cartão
        // foi lida. Com 4,4 km o app anunciou R$ 2,50/km numa corrida de
        // R$ 1,06/km — corrida ruim com cara de ótima. Melhor não mostrar nada.
        val soOCartao = listOf(
            "R$ 1,1/km", "~4,4 km", "R$ 11", "Preço justo", "Bianca", "4.93", "(17)", "8 min.",
            "Rua Catauai Q 30 89 (Parque Amazonia)",
            "Aceitar por R$ 11", "Ofereça sua tarifa", "R$ 13", "R$ 14", "Fechar"
        )
        assertTrue(OfertaInDrive.ehTelaDeDetalhe(soOCartao))
        assertNull(OfertaInDrive.extrair(soOCartao))
    }

    @Test
    fun buscaIgualAViagem_naoInventaDistancia() {
        // Busca e viagem com o mesmo número: sem como separar, não mostra.
        val ambiguo = listOf(
            "Pedido de viagem", "5 min", "3,0 km", "~3,0 km", "R$ 10",
            "Aceitar por R$ 10", "Ofereça sua tarifa"
        )
        assertNull(OfertaInDrive.extrair(ambiguo))
    }

    @Test
    fun telaVazia_naoQuebra() {
        assertNull(OfertaInDrive.extrair(emptyList()))
        assertNull(OfertaInDrive.extrair(listOf("Aceitar por R$ 10")))   // sem km
    }
}
