package com.motoristapro.ui.dashboard

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Dias seguidos batendo a meta, para a tela de comemoração.
 *
 * Só conta dia TRABALHADO: folga (dia sem nenhum lançamento) não quebra a
 * sequência. Quem roda de segunda a sábado não pode perder o fogo todo domingo.
 * Kotlin puro, testado em [SequenciaMetaTest].
 */
data class SequenciaMeta(
    /** Dias trabalhados seguidos (até hoje) em que o lucro passou da meta. */
    val atual: Int,
    /** A maior sequência no histórico recebido (nunca menor que [atual]). */
    val recorde: Int,
    /** Segunda a domingo da semana de hoje. */
    val semana: List<DiaSemana>
) {
    enum class Situacao { BATIDA, NAO_BATIDA, SEM_TRABALHO, FUTURO }

    data class DiaSemana(val data: LocalDate, val situacao: Situacao)

    companion object {
        /**
         * @param lucroPorDia lucro líquido (centavos) de cada dia com lançamento.
         * @param metaCentavos meta de hoje — vale para todo o histórico, porque o app
         *        não guarda a meta de cada dia.
         */
        fun calcular(lucroPorDia: Map<LocalDate, Long>, metaCentavos: Long, hoje: LocalDate): SequenciaMeta {
            val trabalhados = lucroPorDia.filterKeys { !it.isAfter(hoje) }.toSortedMap()

            var recorde = 0
            var corrida = 0
            for ((_, lucro) in trabalhados) {
                corrida = if (metaCentavos > 0 && lucro >= metaCentavos) corrida + 1 else 0
                recorde = maxOf(recorde, corrida)
            }
            // "corrida" no fim do laço é a sequência que chega até o último dia trabalhado.
            val atual = corrida

            val segunda = hoje.with(DayOfWeek.MONDAY)
            val semana = (0L until 7L).map { i ->
                val d = segunda.plusDays(i)
                val lucro = trabalhados[d]
                val situacao = when {
                    d.isAfter(hoje) -> Situacao.FUTURO
                    lucro == null -> Situacao.SEM_TRABALHO
                    metaCentavos > 0 && lucro >= metaCentavos -> Situacao.BATIDA
                    else -> Situacao.NAO_BATIDA
                }
                DiaSemana(d, situacao)
            }
            return SequenciaMeta(atual, maxOf(recorde, atual), semana)
        }
    }
}
