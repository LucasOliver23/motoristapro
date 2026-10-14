package com.motoristapro.custo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FatiasCustoTest {

    private val moto = DadosCusto(
        tipoVeiculo = TipoVeiculo.MOTO,
        valorVeiculoCentavos = 1_500_000,
        ipvaPercentX100 = 200,                 // 2% de R$ 15.000 = R$ 300/ano
        trocaOleoCentavos = 5_000, intervaloOleoKm = 1_000,   // R$ 0,05/km
        combustivel = TipoCombustivel.GASOLINA,
        precoLitroCentavos = 689, consumoX100 = 3_500,        // 6,89 / 35 = R$ 0,197/km
        kmPorDia = 250, diasPorSemana = 6
    )

    @Test
    fun separaCombustivelManutencaoEFixos() {
        val f = FatiasCusto.de(CalculadoraCusto.calcular(moto))
        assertEquals(19.69, f.combustivel, 0.05)
        assertEquals(5.0, f.manutencao, 0.05)
        // IPVA R$ 25/mês ÷ 6.495 km ≈ R$ 0,0038/km
        assertEquals(0.38, f.fixos, 0.05)
    }

    @Test
    fun fatiasSomamOCustoPorKm() {
        val r = CalculadoraCusto.calcular(moto)
        val f = FatiasCusto.de(r)
        assertEquals(r.custoPorKmCentavos.toDouble(), f.total, 1.0)
    }

    @Test
    fun semKmPorDiaUsaEstimativaEAvisa() {
        val (r, estimado) = CustoAoVivo.calcular(moto.copy(kmPorDia = 0))
        assertTrue(estimado)
        assertTrue(r.custoPorKmCentavos > 0)
    }

    @Test
    fun semNadaPreenchidoDaZero() {
        val f = FatiasCusto.de(CalculadoraCusto.calcular(DadosCusto()))
        assertEquals(0.0, f.total, 0.0)
    }
}
