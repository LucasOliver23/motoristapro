package com.motoristapro.veiculo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometroTest {

    private val inicio = LeituraOdometro(83_544, 1_000, OrigemOdometro.INICIO)

    @Test
    fun exemploDoMotorista_gpsSomaNoMeioDoDia() {
        val agora = CalculoOdometro.atual(inicio, 98_400)!!
        assertEquals(83_642, agora.km)
        assertTrue(agora.estimado)
        assertFalse(CalculoOdometro.atual(inicio, 0)!!.estimado)
    }

    @Test
    fun leituraMaisNovaGanha() {
        val abastecimento = LeituraOdometro(83_400, 500, OrigemOdometro.ABASTECIMENTO)
        assertEquals(inicio, CalculoOdometro.maisRecente(inicio, abastecimento))
        val depois = abastecimento.copy(em = 2_000)
        assertEquals(depois, CalculoOdometro.maisRecente(inicio, depois))
        assertNull(CalculoOdometro.maisRecente(null, null))
    }

    @Test
    fun usoForaDoApp() {
        val ontem = OdometroAtual(LeituraOdometro(83_510, 0, OrigemOdometro.FIM), 0)
        assertEquals(34, CalculoOdometro.foraDoApp(ontem, 83_544))
        assertEquals(0, CalculoOdometro.foraDoApp(ontem, 83_500))
        assertEquals(0, CalculoOdometro.foraDoApp(null, 83_544))
    }

    @Test
    fun avisos() {
        val ontem = OdometroAtual(LeituraOdometro(83_510, 0, OrigemOdometro.FIM), 0)
        assertNull(CalculoOdometro.aviso(83_544, ontem))
        assertNull(CalculoOdometro.aviso(83_544, null))
        assertNotNull(CalculoOdometro.aviso(83_000, ontem))
        assertNotNull(CalculoOdometro.aviso(93_000, ontem))
        assertNotNull(CalculoOdometro.aviso(0, ontem))
    }
}
