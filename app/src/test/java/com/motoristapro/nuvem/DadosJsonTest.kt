package com.motoristapro.nuvem

import com.motoristapro.data.local.dao.Pacote
import com.motoristapro.data.local.entity.CategoriaDespesa
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.local.entity.ItemManutencao
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.local.entity.PerfilCusto
import com.motoristapro.data.local.entity.Plataforma
import com.motoristapro.data.local.entity.Veiculo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** O que sobe para a nuvem tem que voltar IGUAL: campo esquecido aqui é dado perdido no restaurar. */
class DadosJsonTest {

    private val pacote = Pacote(
        plataformas = listOf(Plataforma(id = 1, nome = "Uber", corHex = "#000000", ativa = true)),
        corridas = listOf(
            Corrida(
                id = 7, plataformaId = 1, origem = "Rua A, 10", destino = "", valorCentavos = 1_240,
                gorjetaCentavos = 100, distanciaMetros = 4_200, deslocamentoMetros = 800,
                duracaoSegundos = 900, inicioEm = 1_700_000_000_000, observacao = "Resumo 99", criadoEm = 1_700_000_100_000,
                veiculoId = 2
            )
        ),
        despesas = listOf(
            Despesa(
                id = 3, categoria = CategoriaDespesa.COMBUSTIVEL, valorCentavos = 5_000, dataEm = 1_700_000_000_000,
                descricao = null, litrosMl = 8_000, odometroKm = 82_651, criadoEm = 1_700_000_000_500
            )
        ),
        jornadas = listOf(
            Jornada(
                id = 2, inicioEm = 1_700_000_000_000, fimEm = 1_700_018_000_000, metrosGps = 98_400,
                segOffline = 60, segAguardando = 3_000, segBuscando = 900, segEsperando = 120, segEmViagem = 9_000,
                segUber = 7_000, seg99 = 5_000, segIfood = 0, segInDrive = 1_000, pausadaEm = 0, segPausados = 1_800
            )
        ),
        custosFixos = listOf(CustoFixo(id = 4, nome = "IPVA", valorMensalCentavos = 3_000, ativo = true, origem = CustoFixo.ASSISTENTE)),
        configuracao = Configuracao(metaLucroMensalCentavos = 200_000, metaLucroDiarioCentavos = 9_200, custoKmCentavos = 43),
        manutencoes = listOf(ItemManutencao(id = 5, nome = "Óleo", intervaloKm = 2_000, odometroUltimaKm = 82_000, atualizadoEm = 9)),
        perfilCusto = PerfilCusto(tipoVeiculo = "MOTO", veiculoNome = "FAN 160 2021", kmPorDia = 120, horasPorDia = 5),
        veiculos = listOf(
            Veiculo(id = 1, nome = "FAN 160 2021", custoKmCentavos = 43, consumoKmLx100 = 3_500, criadoEm = 5),
            Veiculo(id = 2, nome = "Onix", tipo = "CARRO", custoKmCentavos = 78, criadoEm = 6)
        )
    )

    @Test
    fun idaEVoltaSemPerderNada() {
        assertEquals(pacote, DadosJson.deJson(DadosJson.paraJson(pacote)))
    }

    @Test
    fun backupAntigoSemManutencaoNaoApagaAsDoCelular() {
        val antigo = pacote.copy(manutencoes = null, perfilCusto = null, veiculos = null)
        val volta = DadosJson.deJson(DadosJson.paraJson(antigo))
        assertNull(volta.manutencoes)
        assertNull(volta.perfilCusto)
        assertNull(volta.veiculos)
    }
}
