package com.motoristapro.custo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadoraCustoTest {

    /** JUnit não tem assertEquals com tolerância para Long; arredondamento pede uma. */
    private fun perto(esperado: Long, real: Long, tolerancia: Long, oque: String = "valor") {
        assertTrue(
            "$oque: esperava algo perto de $esperado, veio $real",
            Math.abs(esperado - real) <= tolerancia
        )
    }

    /**
     * Caso de referência: os mesmos números que o Rota Pro mostrou no vídeo do motorista.
     * Moto quitada, 200 km/dia, 6 dias/semana, 35 km/L, gasolina a R$ 6,89,
     * veículo R$ 15.000, IPVA 4%/ano, desvalorização 12%/ano, seguro R$ 90/mês,
     * meta de lucro R$ 1.500/mês.
     *
     * Serve para garantir que, se alguém mexer na fórmula, a conta continue batendo
     * com um app de mercado — e não só com ela mesma.
     */
    private val referencia = DadosCusto(
        forma = FormaAquisicao.QUITADO,
        tipoVeiculo = TipoVeiculo.MOTO,
        valorVeiculoCentavos = 1_500_000,
        seguroMensalCentavos = 9_000,
        ipvaPercentX100 = 400,
        desvalorizacaoAnualX100 = 1_200,
        trocaOleoCentavos = 3_500,
        intervaloOleoKm = 1_000,
        jogoPneusCentavos = 45_000,
        duracaoPneusKm = 15_000,
        combustivel = TipoCombustivel.GASOLINA,
        precoLitroCentavos = 689,
        consumoX100 = 3_500,
        kmPorDia = 200,
        diasPorSemana = 6,
        horasPorDia = 10,
        metaLucroMensalCentavos = 150_000
    )

    @Test
    fun kmPorMes_bateComOMercado() {
        assertEquals(5196, CalculadoraCusto.calcular(referencia).kmPorMes)
    }

    @Test
    fun combustivel_bateCentavoACentavo() {
        val r = CalculadoraCusto.calcular(referencia)
        val comb = r.itens.first { it.nome == "Combustível" }
        assertEquals(102_287, comb.mensalCentavos)       // R$ 1.022,87
    }

    @Test
    fun depreciacaoEIpva_saemDoValorDoVeiculo() {
        val r = CalculadoraCusto.calcular(referencia)
        assertEquals(15_000, r.itens.first { it.nome == "Depreciação" }.mensalCentavos)  // R$ 150,00
        assertEquals(5_000, r.itens.first { it.nome == "IPVA" }.mensalCentavos)          // R$ 50,00
    }

    @Test
    fun tarifaMinima_cobreCustoMaisMeta() {
        val r = CalculadoraCusto.calcular(referencia)
        val esperado = (r.custoMensalCentavos + 150_000) * 1.0 / r.kmPorMes
        assertEquals(esperado, r.tarifaMinimaPorKmCentavos.toDouble(), 1.0)
        assertTrue(r.tarifaMinimaPorKmCentavos > r.custoPorKmCentavos)
    }

    @Test
    fun itensSomamOCustoMensal() {
        val r = CalculadoraCusto.calcular(referencia)
        perto(r.custoMensalCentavos, r.itens.sumOf { it.mensalCentavos }, 5, "soma dos itens")
        perto(100, r.itens.sumOf { it.percentual }.toLong(), 2, "soma dos percentuais")
    }

    @Test
    fun maiorCustoVemPrimeiro() {
        val itens = CalculadoraCusto.calcular(referencia).itens
        assertEquals("Combustível", itens.first().nome)
        assertTrue(itens.zipWithNext().all { (a, b) -> a.mensalCentavos >= b.mensalCentavos })
    }

    @Test
    fun projecaoDoMes_bateComOsTotais() {
        val r = CalculadoraCusto.calcular(referencia)
        val mes = r.projecoes.first { it.rotulo == "Mês" }
        assertEquals(r.kmPorMes, mes.km)
        perto(r.custoMensalCentavos, mes.custoCentavos, 50, "custo do mês")
        perto(150_000, mes.lucroCentavos, 100, "lucro do mês")           // a meta do motorista
    }

    @Test
    fun projecaoDoAno_eDozeMeses() {
        val r = CalculadoraCusto.calcular(referencia)
        val mes = r.projecoes.first { it.rotulo == "Mês" }
        val ano = r.projecoes.first { it.rotulo == "Ano" }
        perto(mes.km * 12, ano.km, 12, "km do ano")
        perto(mes.lucroCentavos * 12, ano.lucroCentavos, 200, "lucro do ano")
    }

    // ------------------------------------------------------------ casos de borda

    @Test
    fun semKmRodado_naoQuebraEnaoDivideporZero() {
        val r = CalculadoraCusto.calcular(DadosCusto(kmPorDia = 0))
        assertEquals(0, r.custoPorKmCentavos)
        assertEquals(0, r.kmPorMes)
        assertFalse(r.valido)
    }

    @Test
    fun formularioVazio_naoQuebra() {
        val r = CalculadoraCusto.calcular(DadosCusto(kmPorDia = 150))
        assertEquals(0, r.custoMensalCentavos)
        assertTrue(r.itens.isEmpty())
    }

    @Test
    fun alugado_naoTemDepreciacaoNemIpva_masTemAluguel() {
        val r = CalculadoraCusto.calcular(
            referencia.copy(forma = FormaAquisicao.ALUGADO, aluguelMensalCentavos = 120_000)
        )
        assertTrue(r.itens.none { it.nome == "Depreciação" })
        assertTrue(r.itens.none { it.nome == "IPVA" })
        assertEquals(120_000, r.itens.first { it.nome == "Aluguel" }.mensalCentavos)
    }

    @Test
    fun financiado_entraComAParcela() {
        val r = CalculadoraCusto.calcular(
            referencia.copy(forma = FormaAquisicao.FINANCIADO, parcelaMensalCentavos = 68_000)
        )
        assertEquals(68_000, r.itens.first { it.nome == "Parcela" }.mensalCentavos)
        assertTrue(r.itens.any { it.nome == "Depreciação" })   // financiado ainda desvaloriza no seu bolso
    }

    @Test
    fun consumoZero_ignoraCombustivelEmVezDeExplodir() {
        val r = CalculadoraCusto.calcular(referencia.copy(consumoX100 = 0))
        assertTrue(r.itens.none { it.nome == "Combustível" })
        assertTrue(r.custoPorKmCentavos > 0)      // os outros custos continuam valendo
    }

    @Test
    fun eletrico_usaAMesmaConta() {
        val r = CalculadoraCusto.calcular(
            referencia.copy(combustivel = TipoCombustivel.ELETRICO, consumoX100 = 2_000, precoLitroCentavos = 95)
        )
        // 5196 km / 20 km/kWh = 259,8 kWh × R$ 0,95
        perto(24_681, r.itens.first { it.nome == "Combustível" }.mensalCentavos, 50, "recarga")
    }

    // ------------------------------------------------------------ avisos

    @Test
    fun avisa_quandoOIntervaloDoOleoEhAbsurdo() {
        val r = CalculadoraCusto.calcular(referencia.copy(intervaloOleoKm = 2))
        assertTrue(r.avisos.any { it.campo == "Intervalo do óleo" })
    }

    @Test
    fun naoAvisa_comValoresNormais() {
        assertTrue(CalculadoraCusto.calcular(referencia).avisos.isEmpty())
    }

    @Test
    fun avisa_consumoDeMotoAbsurdoParaCarro() {
        val comoCarro = referencia.copy(tipoVeiculo = TipoVeiculo.CARRO)   // 35 km/L num carro
        assertTrue(CalculadoraCusto.calcular(comoCarro).avisos.any { it.campo == "Consumo médio" })
        assertTrue(CalculadoraCusto.calcular(referencia).avisos.none { it.campo == "Consumo médio" })
    }

    @Test
    fun avisa_valorDoVeiculoComUmZeroAMenos() {
        val r = CalculadoraCusto.calcular(referencia.copy(valorVeiculoCentavos = 150_000))  // R$ 1.500
        assertTrue(r.avisos.any { it.campo == "Valor do veículo" })
    }
}
