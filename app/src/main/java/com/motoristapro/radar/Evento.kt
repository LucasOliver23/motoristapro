package com.motoristapro.radar

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TipoEvento(val rotulo: String, val emoji: String) {
    JOGO("Jogo", "⚽"),
    SHOW("Show", "🎤"),
    FESTA("Festa", "🎉"),
    AEROPORTO("Aeroporto", "✈️"),
    OUTRO("Evento", "📍");

    companion object {
        fun deNome(n: String?) = entries.firstOrNull { it.name == n } ?: OUTRO
    }
}

enum class Movimento(val rotulo: String) {
    ALTO("Movimento alto"), MEDIO("Movimento médio"), BAIXO("Movimento baixo");

    companion object {
        fun deNome(n: String?) = entries.firstOrNull { it.name == n } ?: MEDIO
    }
}

/**
 * Um evento do radar: jogo, show, festa, pico do aeroporto.
 *
 * O que importa para o motorista é a [saidaEm] — a hora em que o público vai
 * embora e as chamadas chovem —, mais do que a hora de início.
 */
data class Evento(
    val id: String,
    val titulo: String,
    val local: String,
    val endereco: String?,
    val cidade: String,
    val tipo: TipoEvento,
    val inicioEm: Long,
    val saidaEm: Long,
    val movimento: Movimento,
    /** true = o próprio motorista anotou no celular; false = veio da lista do MotoristaPro. */
    val doMotorista: Boolean = false
)

/** Os recortes das abas do radar. */
enum class JanelaRadar(val rotulo: String) { HOJE("Hoje"), AMANHA("Amanhã"), FIM_DE_SEMANA("Fim de semana") }

/** As contas do radar, sem Android para testar. */
object CalculoRadar {

    /** Evento que já saiu há mais disto some da lista. */
    const val TOLERANCIA_MS = 60 * 60 * 1000L

    /** O aviso sai este tanto antes da saída. */
    const val ANTECEDENCIA_MS = 30 * 60 * 1000L

    /** "Goiânia", "goiania", " GOIÂNIA " são a mesma cidade. */
    fun normalizarCidade(c: String?): String =
        Normalizer.normalize(c.orEmpty().trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    fun filtrar(
        eventos: List<Evento>,
        janela: JanelaRadar,
        cidade: String?,
        agora: Long,
        zona: ZoneId = ZoneId.systemDefault()
    ): List<Evento> {
        val hoje = Instant.ofEpochMilli(agora).atZone(zona).toLocalDate()
        val dias: Set<LocalDate> = when (janela) {
            JanelaRadar.HOJE -> setOf(hoje)
            JanelaRadar.AMANHA -> setOf(hoje.plusDays(1))
            JanelaRadar.FIM_DE_SEMANA -> (0L..6L).map { hoje.plusDays(it) }
                .filter { it.dayOfWeek.value >= 5 }.take(3).toSet() // sexta, sábado e domingo que vêm
        }
        val minhaCidade = normalizarCidade(cidade)
        return eventos
            .filter { it.saidaEm >= agora - TOLERANCIA_MS }
            .filter { Instant.ofEpochMilli(it.inicioEm).atZone(zona).toLocalDate() in dias }
            .filter { minhaCidade.isEmpty() || it.doMotorista || normalizarCidade(it.cidade) == minhaCidade }
            .sortedBy { it.saidaEm }
    }

    /** Quando avisar; null se já passou da hora. */
    fun horaDoAviso(e: Evento, agora: Long): Long? =
        (e.saidaEm - ANTECEDENCIA_MS).takeIf { it > agora }
}
