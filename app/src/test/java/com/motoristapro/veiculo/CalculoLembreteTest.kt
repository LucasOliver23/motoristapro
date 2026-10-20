package com.motoristapro.veiculo

import com.motoristapro.data.local.entity.ItemManutencao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculoLembreteTest {

    private val oleo = ItemManutencao(id = 1, nome = "Óleo", intervaloKm = 2_000, odometroUltimaKm = 82_000)
    private val agora = 1_000_000L

    private fun avisar(km: Long, avisados: Set<String> = emptySet(), adiado: Map<Long, Long> = emptyMap()) =
        CalculoLembrete.quaisAvisar(listOf(oleo), km, 300, avisados, adiado, agora)

    @Test
    fun longeDoVencimentoNaoAvisa() {
        assertTrue(avisar(82_651).isEmpty())
    }

    @Test
    fun chegandoAvisaUmaVez() {
        val l = avisar(83_720).single()
        assertEquals(FaseLembrete.CHEGANDO, l.fase)
        assertEquals(280, l.faltamKm)
        assertTrue(avisar(83_800, avisados = setOf(l.chave)).isEmpty())
    }

    @Test
    fun vencidoAvisaDeNovoMesmoDepoisDoChegando() {
        val chegando = avisar(83_720).single()
        val vencido = avisar(84_050, avisados = setOf(chegando.chave)).single()
        assertEquals(FaseLembrete.VENCIDO, vencido.fase)
    }

    @Test
    fun adiadoNaoAvisaAteOPrazo() {
        assertTrue(avisar(83_720, adiado = mapOf(1L to agora + 1)).isEmpty())
        assertEquals(1, avisar(83_720, adiado = mapOf(1L to agora - 1)).size)
    }

    @Test
    fun trocaFeitaMudaAChave() {
        val antes = Lembrete.chaveDe(oleo, FaseLembrete.CHEGANDO)
        val depois = Lembrete.chaveDe(oleo.copy(odometroUltimaKm = 84_000), FaseLembrete.CHEGANDO)
        assertTrue(antes != depois)
    }

    @Test
    fun semKmNaoAvisa() {
        assertTrue(avisar(0).isEmpty())
    }
}
