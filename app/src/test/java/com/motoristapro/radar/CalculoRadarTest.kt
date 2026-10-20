package com.motoristapro.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CalculoRadarTest {

    private val zona = ZoneId.of("America/Sao_Paulo")
    private fun ms(d: LocalDateTime) = d.atZone(zona).toInstant().toEpochMilli()

    // Segunda, 19/10/2026, 17h
    private val agora = ms(LocalDateTime.of(2026, 10, 19, 17, 0))

    private fun ev(id: String, inicio: LocalDateTime, saida: LocalDateTime, cidade: String = "Goiânia") =
        Evento(id, "Evento $id", "Local", null, cidade, TipoEvento.JOGO, ms(inicio), ms(saida), Movimento.ALTO)

    private val jogoHoje = ev("1", LocalDateTime.of(2026, 10, 19, 19, 0), LocalDateTime.of(2026, 10, 19, 21, 0))
    private val showAmanha = ev("2", LocalDateTime.of(2026, 10, 20, 22, 0), LocalDateTime.of(2026, 10, 21, 1, 0))
    private val sabado = ev("3", LocalDateTime.of(2026, 10, 24, 20, 0), LocalDateTime.of(2026, 10, 24, 23, 0))
    private val outraCidade = ev("4", LocalDateTime.of(2026, 10, 19, 20, 0), LocalDateTime.of(2026, 10, 19, 22, 0), "Anápolis")
    private val jaAcabou = ev("5", LocalDateTime.of(2026, 10, 19, 10, 0), LocalDateTime.of(2026, 10, 19, 12, 0))

    private val todos = listOf(jogoHoje, showAmanha, sabado, outraCidade, jaAcabou)

    @Test
    fun hojeSoDaMinhaCidadeENaoAcabado() {
        assertEquals(listOf("1"), CalculoRadar.filtrar(todos, JanelaRadar.HOJE, "goiania", agora, zona).map { it.id })
    }

    @Test
    fun amanhaEFimDeSemana() {
        assertEquals(listOf("2"), CalculoRadar.filtrar(todos, JanelaRadar.AMANHA, "Goiânia", agora, zona).map { it.id })
        assertEquals(listOf("3"), CalculoRadar.filtrar(todos, JanelaRadar.FIM_DE_SEMANA, "Goiânia", agora, zona).map { it.id })
    }

    @Test
    fun semCidadeMostraTodas() {
        assertEquals(setOf("1", "4"), CalculoRadar.filtrar(todos, JanelaRadar.HOJE, null, agora, zona).map { it.id }.toSet())
    }

    @Test
    fun avisoTrintaMinutosAntesDaSaida() {
        assertEquals(ms(LocalDateTime.of(2026, 10, 19, 20, 30)), CalculoRadar.horaDoAviso(jogoHoje, agora))
        assertNull(CalculoRadar.horaDoAviso(jaAcabou, agora))
    }

    @Test
    fun cidadeSemAcentoEMaiuscula() {
        assertTrue(CalculoRadar.normalizarCidade(" GOIÂNIA ") == CalculoRadar.normalizarCidade("goiania"))
    }
}
