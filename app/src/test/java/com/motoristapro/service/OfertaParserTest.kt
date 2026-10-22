package com.motoristapro.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class OfertaParserTest {

    @Test
    fun uber_buscaMaisViagem() {
        val o = OfertaParser.extrair(
            listOf("UberX", "R$ 18,40", "+R$ 3,00 incluído", "4 min (1,2 km) de distância", "Viagem de 22 min (9,8 km)")
        )!!
        assertEquals(18.40, o.valor, 0.001)
        assertEquals(11.0, o.km, 0.001)
        assertEquals(26, o.minutos)
        assertEquals(1.672, o.reaisPorKm, 0.001)
        assertEquals(42.46, o.reaisPorHora, 0.01)
    }

    @Test
    fun metrosEHoras() {
        val o = OfertaParser.extrair(listOf("R$ 1.050,00", "2 min (800 m)", "1 h 5 min (70.5 km)"))!!
        assertEquals(1050.0, o.valor, 0.001)
        assertEquals(71.3, o.km, 0.001)
        assertEquals(67, o.minutos)
    }

    @Test
    fun telaSemOferta() {
        assertNull(OfertaParser.extrair(listOf("Você está online", "Procurando viagens")))
        assertNull(OfertaParser.extrair(listOf("R$ 245,30", "Ganhos de hoje")))   // sem km/min
        assertNull(OfertaParser.extrair(emptyList()))
    }

    @Test
    fun zeroKmOuZeroMinutos_naoGeraOferta() {
        assertNull(OfertaParser.extrair(listOf("R$ 10,00", "0 min (0 km)")))
        assertNull(OfertaParser.extrair(listOf("R$ 10,00", "5 min (0,0 km)")))
    }

    @Test
    fun divisaoPorZero_retornaZero() {
        val o = Oferta(valor = 10.0, km = 0.0, minutos = 0)
        assertEquals(0.0, o.reaisPorKm, 0.0)
        assertEquals(0.0, o.reaisPorHora, 0.0)
        assertEquals(0.0, dividirSeguro(1.0, Double.NaN), 0.0)
    }

    @Test
    fun numeroGigante_naoLancaExcecao() {
        assertNull(OfertaParser.extrair(listOf("R$ 10,00", "99999999999999 min", "3 km")))
    }

    @Test
    fun enderecos_saoExtraidos() {
        val o = OfertaParser.extrair(
            listOf("R$ 15,00", "5 min (2 km)", "Rua das Flores, 120 - Centro", "Viagem de 10 min (4 km)", "Av. Brasil, 500")
        )!!
        assertEquals(listOf("Rua das Flores, 120 - Centro", "Av. Brasil, 500"), o.enderecos)
    }

    /** Textos do card real do iFood para Entregadores (print do usuário). */
    @Test
    fun ifood_rotaMotoBau() {
        val textos = listOf(
            "Entrega 1", "Entrega 1", "Goiania Sul", "R$ 37,38", "Rota para Moto Baú",
            "Distância total", "24,91 km", "Tempo aproximado de rota", "29 min",
            "Possibilidade de devolução", "Sim", "Rejeitar", "Aceitar", "54"
        )
        val o = OfertaParser.extrair(textos, exigirTempo = false)!!
        assertEquals(37.38, o.valor, 0.001)
        assertEquals(24.91, o.km, 0.001)
        assertEquals(29, o.minutos)
        assertEquals(1, o.paradas)
        assertEquals(true, o.devolucao)
        assertEquals(1.50, o.reaisPorKm, 0.01)
        assertEquals(77.34, o.reaisPorHora, 0.01)
        assertEquals(1.29, o.reaisPorMinuto, 0.01)
    }

    @Test
    fun ifood_semTempo_calculaSoPorKm() {
        val o = OfertaParser.extrair(listOf("R$ 12,00", "Distância total", "6,0 km", "Entrega 1", "Entrega 2"), exigirTempo = false)!!
        assertEquals(0, o.minutos)
        assertEquals(2, o.paradas)
        assertEquals(2.0, o.reaisPorKm, 0.001)
        assertEquals(0.0, o.reaisPorHora, 0.0)
    }

    /** Card real da 99 Moto (print do usuário): R$ colado, tarifa base e taxa na tela. */
    @Test
    fun noventaENove_moto() {
        val textos = listOf(
            "Moto", "R$8,80", "1,7x", "0% de Taxa99 semanal", "R$0,97 Tarifa base dinâmica incl.",
            "4,81", "+999 corridas", "Perfil Premium",
            "1 min (61 m)", "Edifício New Times Square, Avenida T-10 esquina com Rua T-27, Quadra 102",
            "8 min (3,2 km)", "Clínica Karol Albuquerque - Unidade Sul, Rua 86, 421", "Aceitar"
        )
        val o = OfertaParser.extrair(textos)!!
        assertEquals(8.80, o.valor, 0.001)
        assertEquals(3.261, o.km, 0.001)
        assertEquals(9, o.minutos)
        assertEquals(2.70, o.reaisPorKm, 0.01)
    }

    /** Mesmo card repetido na descrição do container: trechos iguais contam uma vez. */
    @Test
    fun noventaENove_descricaoDuplicada_naoSomaEmDobro() {
        val textos = listOf(
            "R$15,00", "R$1,08 Tarifa base dinâmica incl.", "4 min (528 m)", "19 min (7,6 km)",
            "Moto R$15,00 4 min (528 m) Mundo Kids 19 min (7,6 km) Residencial"
        )
        val o = OfertaParser.extrair(textos)!!
        assertEquals(15.0, o.valor, 0.001)
        assertEquals(8.128, o.km, 0.001)
        assertEquals(23, o.minutos)
    }

    @Test
    fun ganhosDoDiaNoFundo_naoViramValorDaOferta() {
        val o = OfertaParser.extrair(listOf("Ganhos de hoje R$ 245,30", "R$8,80", "1 min (61 m)", "8 min (3,2 km)"))!!
        assertEquals(8.80, o.valor, 0.001)
    }

    /** Card real da Uber (print do usuário): "R$0,34/km aprox." não é o valor; "5 minutos (1.5 km)". */
    @Test
    fun uber_uberX_cardReal() {
        val textos = listOf(
            "UberX", "Exclusivo", "R$ 0,92", "R$0,34/km aprox.", "4,96 (301)",
            "4 min (1.2 km)", "Avenida Afonso Pena, Washington Pires, Belo Horizonte",
            "5 minutos (1.5 km)", "Av. Dos Andradas, 367, Centro, Belo Horizonte", "Aceitar"
        )
        val o = OfertaParser.extrair(textos)!!
        assertEquals(0.92, o.valor, 0.001)
        assertEquals(2.7, o.km, 0.001)
        assertEquals(9, o.minutos)
        assertEquals(0.34, o.reaisPorKm, 0.01)
    }

    /** Linhas como o OCR lê o card da 99 (inclui a nossa bolha "R$ 0" e rótulos do mapa). */
    @Test
    fun noventaENove_viaOcr() {
        val linhas = listOf(
            "R$ 0", "0h10", "Moto", "RS13,90", "1,2x", "0% de Taxa99 semanal",
            "R$1,19 Tarifa base dinâmica incl.", "4,96 206 corridas", "Perfil Premium",
            "7 min ( 1,3 km )", "Academia Bluefit Avenida 85, Avenida", "85, E, 3190",
            "22 min (10,3 km)", "Avenida Juíz de Fora, 725, Jardim Novo", "Mundo", "Aceitar", "GO-352"
        )
        val o = OfertaParser.extrair(linhas)!!
        assertEquals(13.90, o.valor, 0.001)
        assertEquals(11.6, o.km, 0.001)
        assertEquals(29, o.minutos)
        assertEquals(1.20, o.reaisPorKm, 0.01)
    }

    /** Tela "Solicitações" da 99: formato "(18 min 6,8 km)" — tempo e km no mesmo parêntese. */
    @Test
    fun noventaENove_listaSolicitacoes() {
        val textos = listOf(
            "Solicitações", "Corridas", "Entregas", "Escolha uma", "Km até o embarque",
            "Moto Nova", "R$7,70", "4,92", "Perfil Premium",
            "(18 min 6,8 km) Exclusiva Centro De Beleza, Avenida Itália - Jardim Ana Lúcia, Goiânia",
            "(14 min 6 km) Catunda Odontologia e Saúde, Avenida C-255, 419 Qd. 599, Lt. 09 Sala 1",
            "Escolher", "Isso é tudo por enquanto"
        )
        val o = OfertaParser.extrair(textos)!!
        assertEquals(7.70, o.valor, 0.001)
        assertEquals(12.8, o.km, 0.001)      // 6,8 até o embarque + 6 da viagem
        assertEquals(32, o.minutos)
        assertEquals(0.60, o.reaisPorKm, 0.01)
    }

    /** Lista com duas corridas: cada card é lido separadamente (não soma um no outro). */
    @Test
    fun listaComDuasCorridas_separaOsCards() {
        val textos = listOf(
            "Escolha uma",
            "R$7,70", "(18 min 6,8 km) Rua A", "(14 min 6 km) Rua B", "Escolher",
            "R$ 22,50", "(5 min 1,2 km) Rua C", "(30 min 15 km) Rua D", "Escolher"
        )
        val ofertas = OfertaParser.extrairVarias(textos)
        assertEquals(2, ofertas.size)
        assertEquals(7.70, ofertas[0].valor, 0.001)
        assertEquals(12.8, ofertas[0].km, 0.001)
        assertEquals(22.50, ofertas[1].valor, 0.001)
        assertEquals(16.2, ofertas[1].km, 0.001)
        // O app mostra a de melhor R$/km.
        assertEquals(22.50, ofertas.maxByOrNull { it.reaisPorKm }!!.valor, 0.001)
    }

    /** Com uma oferta só, extrairVarias devolve exatamente o mesmo que extrair. */
    @Test
    fun extrairVarias_comUmaOferta() {
        val textos = listOf("Moto", "R$8,80", "1 min (61 m)", "8 min (3,2 km)")
        val ofertas = OfertaParser.extrairVarias(textos)
        assertEquals(1, ofertas.size)
        assertEquals(OfertaParser.extrair(textos), ofertas[0])
    }

    // ============================================================ nota do passageiro

    @Test
    fun nota_lidaDaEstrela() {
        val textos = listOf("R$ 24,50", "★ 4,85", "6 min (1,2 km)", "19 min (7,6 km)")
        val o = OfertaParser.extrair(textos)!!
        assertEquals(4.85, o.nota!!, 0.001)
        assertEquals(24.50, o.valor, 0.001)
    }

    @Test
    fun nota_naoConfundeComOValorDaLinhaDeCima() {
        // "R$ 4,80" logo acima da estrela já virou nota 4,80 numa versão anterior.
        val textos = listOf("R$ 4,80", "★ 4,92", "12 min (3,0 km)")
        assertEquals(4.92, OfertaParser.extrair(textos)!!.nota!!, 0.001)
    }

    @Test
    fun nota_ausenteQuandoATelaNaoMostra() {
        val textos = listOf("R$18,30", "(11 min 5,2 km) Rua A", "(37 min 22,9 km) Rua B")
        assertNull(OfertaParser.extrair(textos)!!.nota)
    }

    @Test
    fun nota_foraDaFaixaEhDescartada() {
        assertNull(OfertaParser.extrairNota(listOf("★ 8,20")))
        assertNull(OfertaParser.extrairNota(listOf("★ 0,50")))
        assertEquals(5.0, OfertaParser.extrairNota(listOf("★ 5"))!!, 0.001)
    }

    // ============================================================ entrega sem minutos

    @Test
    fun entregaSemMinutos_eReconhecidaQuandoNaoExigeTempo() {
        val textos = listOf(
            "Verifique o tipo de pedido", "Entrega Food (2)", "R$17,35",
            "Distância total 9,3 km", "Aceitar (2)"
        )
        assertTrue(OfertaParser.pareceEntrega(textos))
        val o = OfertaParser.extrair(textos, exigirTempo = false)!!
        assertEquals(17.35, o.valor, 0.001)
        assertEquals(9.3, o.km, 0.001)
        assertEquals(0, o.minutos)
        assertEquals(0.0, o.reaisPorHora, 0.001)       // divisão por zero protegida
        assertEquals(1.866, o.reaisPorKm, 0.01)
    }

    @Test
    fun corridaDePassageiro_continuaExigindoTempo() {
        val textos = listOf("R$ 12,00", "5,0 km")        // sem minutos e sem cara de entrega
        assertFalse(OfertaParser.pareceEntrega(textos))
        assertNull(OfertaParser.extrair(textos, exigirTempo = true))
    }

    // ============================================================ lucro

    @Test
    fun lucroEPercentual() {
        val o = Oferta(valor = 20.0, km = 10.0, minutos = 25)
        assertEquals(14.0, o.lucroEstimado(60), 0.001)          // 20 − 10 × 0,60
        assertEquals(70.0, o.lucroPercentual(60), 0.001)
        assertEquals(20.0, o.lucroEstimado(0), 0.001)           // sem custo cadastrado
    }

    @Test
    fun lucroPercentual_naoEstouraComValorZero() {
        val o = Oferta(valor = 0.0, km = 5.0, minutos = 10)
        assertEquals(0.0, o.lucroPercentual(60), 0.001)
    }

    // ---- telas reais mandadas pelo motorista (17/10/2026)

    @Test
    fun ifood_bicicleta() {
        val o = OfertaParser.extrair(listOf(
            "Coleta 1", "Entrega 1", "Parque Santo Antonio", "R$ 7,00", "Rota para Bicicleta",
            "Distância total", "2,26 km", "Tempo aproximado de rota", "17 min",
            "Possibilidade de devolução", "Sim", "Rejeitar", "Aceitar", "80"
        ))!!
        assertEquals(7.0, o.valor, 0.001)
        assertEquals(2.26, o.km, 0.001)
        assertEquals(17, o.minutos)
        // Coleta + uma entrega é UMA parada (antes contava 2 e dividia o valor por 2).
        assertEquals(1, o.paradas)
    }

    @Test
    fun uber_blackBag_notaSemEstrelaEEnderecoDeAeroporto() {
        val o = OfertaParser.extrair(listOf(
            "Black Bag", "Exclusivo", "R$ 188,36", "4,92 (924)", "Verificado", "+R$ 3,00 incluído",
            "1 min (0,1 km) de distância",
            "Terminal 3, Aeroporto Internacional de São Paulo (GRU), Guarulhos",
            "Viagem de 1 h e 48 min (43,6 km)",
            "Rua Pires De Oliveira, 486 - Santo Amaro - São Paulo - SP, 04717-002",
            "Viagem longa (mais de 90 min)", "Aceitar"
        ))!!
        assertEquals(188.36, o.valor, 0.001)
        assertEquals(43.7, o.km, 0.001)
        assertEquals(109, o.minutos)
        assertEquals(4.92, o.nota!!, 0.001)
        assertEquals("Terminal 3, Aeroporto Internacional de São Paulo (GRU), Guarulhos", o.enderecos.first())
        assertEquals(2, o.enderecos.size)
    }

    @Test
    fun noventaENove_corridaLonga_comAreaDeRisco() {
        val o = OfertaParser.extrair(listOf(
            "Corrida longa", "R$54,89", "R$2,20 por km", "x2,0", "R$15.50 incluídos",
            "4,8", "12 corridas", "Cartão Verif.",
            "5Min (2,5km)", "Shopping Penha, R. Dr. João Ribeiro, 304 - Penha de França",
            "32Min (6,3km)", "Área de risco", "Estação Prefeito Saladino Tamanduateí 4, Santo André"
        ))!!
        assertEquals(54.89, o.valor, 0.001)
        assertEquals(8.8, o.km, 0.001)
        assertEquals(37, o.minutos)
        assertEquals(4.8, o.nota!!, 0.001)
        assertEquals("Estação Prefeito Saladino Tamanduateí 4, Santo André", o.enderecos.last())
    }

    /** Print real da Uber Envios Moto (Testar com print, Xiaomi). Um trecho só e nota "* 5,00 (41)". */
    @Test
    fun uber_enviosMoto_printReal() {
        val textos = listOf(
            "Envios Moto", "X", "R$ 4,96", "* 5,00 (41)", "O 19 min (10.9 km)", "BR-",
            "Rua Exemplo 1, 100", "- Bairro Exemplo, Cidade -", "Rua Exemplo 2, 100",
            "Rua Exemplo 3, 100", "Bairro Exemplo, Cidade", "Aceitar", "Ab"
        )
        val o = OfertaParser.extrair(textos)!!
        assertEquals(4.96, o.valor, 0.001)
        assertEquals(10.9, o.km, 0.001)
        assertEquals(19, o.minutos)
        assertEquals(5.0, o.nota!!, 0.001)
        assertTrue(OfertaParser.pareceEntrega(textos))
    }

    /** Uber Envios mostrando só o km: entrega, então não exige o tempo. */
    @Test
    fun uber_envios_semMinutos_lidoPelaRegraDeEntrega() {
        val textos = listOf("Envios Moto", "R$ 9,80", "Distância 6,2 km", "Aceitar")
        assertTrue(OfertaParser.pareceEntrega(textos))
        val e = EscolhaOferta.escolher(textos, "com.ubercab.driver")!!
        assertEquals(9.80, e.oferta.valor, 0.001)
        assertEquals(6.2, e.oferta.km, 0.001)
        assertEquals(0, e.oferta.minutos)
    }

    /** Print real do iFood com coleta no mercado (Testar com print, Xiaomi). O OCR leu "Fntrena". */
    @Test
    fun ifood_coletaNoMercado_printReal() {
        val textos = listOf(
            "COIMBRA", "2 Fntrena 2", "Coleta 1 • Mercado", "Entrega 1", "Campina",
            "Americanas | 24 de", "Outubro", "R$ 15,75", "Rota para Moto Baú", "Distância total",
            "8,5 km", "43 min", "Tempo aproximado de rota", "Possibilidade de devolução", "Sim",
            "Rejeitar", "Aceitar", "70"
        )
        val e = EscolhaOferta.escolher(textos, EscolhaOferta.PACOTE_IFOOD)!!
        val o = e.oferta
        assertEquals(15.75, o.valor, 0.001)
        assertEquals(8.5, o.km, 0.001)
        assertEquals(43, o.minutos)
        assertEquals(1.85, o.reaisPorKm, 0.01)
        assertEquals(21.98, o.reaisPorHora, 0.01)
        assertTrue(o.devolucao)
        assertEquals(1, e.total)
        // "Fntrena 2" é a "Entrega 2" meio escondida no mapa: duas entregas.
        assertEquals(2, o.paradas)
    }

    /** Print real da 99 Entrega Food com 2 pedidos (Testar com print, Xiaomi). Sem minutos na tela. */
    @Test
    fun noventaENove_entregaFoodDoisPedidos_printReal() {
        val textos = listOf(
            "VILA", "NAT", "Entrega Food (2)", "R$16,69", "S Distância total 8,7 km",
            "* Restaurante Exemplo", "Açaí Exemplo", "A", "Escola Exemplo",
            "Rua Exemplo 1, 100", "Rua Exemplo 2, 100", "A", "Rua Exemplo 3, 100", "Aceitar (2)"
        )
        val e = EscolhaOferta.escolher(textos, "com.app99.driver")!!
        val o = e.oferta
        assertEquals(16.69, o.valor, 0.001)
        assertEquals(8.7, o.km, 0.001)
        assertEquals(0, o.minutos)
        assertEquals(1.92, o.reaisPorKm, 0.01)
        assertEquals(2, o.paradas)
        assertEquals(1, e.total)
    }

    @Test
    fun paradas_numeroEscritoNoMapa() {
        val o = OfertaParser.extrair(
            listOf("2 Paradas", "Entrega 1", "R$ 20,00", "Distância total", "9,0 km"),
            exigirTempo = false
        )!!
        assertEquals(2, o.paradas)
    }

    @Test
    fun paradas_soAEntrega2Visivel_contaDuas() {
        val o = OfertaParser.extrair(listOf("Entrega 2", "R$ 20,00", "Distância total", "9,0 km"), exigirTempo = false)!!
        assertEquals(2, o.paradas)
    }

    /** Print com o valor cortado: o "R$0,34/km aprox." não pode virar o valor da corrida. */
    @Test
    fun valorCortado_precoPorKmNaoViraValor() {
        val textos = listOf(
            "R$0,34/km aprox.", "4,96 (301)", "4 min (1.2 km)", "Rua Exemplo 1, 100",
            "5 minutos (1.5 km)", "Rua Exemplo 2, 100", "Aceitar"
        )
        assertNull(OfertaParser.extrair(textos))
    }

    @Test
    fun valorEPrecoPorKmNaMesmaLinha_valeOValor() {
        val o = OfertaParser.extrair(listOf("R$ 12,00 • R$1,20/km", "10 min (10 km)"))!!
        assertEquals(12.0, o.valor, 0.001)
    }
}
