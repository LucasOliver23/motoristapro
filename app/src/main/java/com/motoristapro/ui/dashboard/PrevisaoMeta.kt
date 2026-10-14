package com.motoristapro.ui.dashboard

/**
 * "Quanto falta para a meta" e, se der para estimar, a que horas ela chega.
 *
 * Kotlin puro (sem Android) para ser testado em [PrevisaoMetaTest].
 */
sealed interface PrevisaoMeta {
    /** Lucro do dia já passou da meta. */
    data object Batida : PrevisaoMeta

    /**
     * Ainda falta [faltaCentavos].
     * [ritmoCentavosHora] só vem depois de [MINIMO_PARA_RITMO_SEG] rodados e com lucro positivo:
     * antes disso uma corrida boa nos primeiros 10 minutos daria um ritmo de mentira.
     * [chegaEmMillis] só vem com o turno andando (não pausado) e uma previsão de até
     * [MAXIMO_HORAS_PREVISAO] horas — "termina às 4h da manhã" não ajuda ninguém.
     */
    data class Falta(
        val faltaCentavos: Long,
        val ritmoCentavosHora: Long?,
        val chegaEmMillis: Long?
    ) : PrevisaoMeta

    companion object {
        const val MINIMO_PARA_RITMO_SEG = 30L * 60
        const val MAXIMO_HORAS_PREVISAO = 10L

        /**
         * @param segundosRodados tempo de turno hoje, SEM as pausas.
         * @param turnoAndando há turno aberto e não pausado agora.
         * @return null quando não há meta definida.
         */
        fun calcular(
            metaCentavos: Long,
            lucroCentavos: Long,
            segundosRodados: Long,
            turnoAndando: Boolean,
            agoraMillis: Long
        ): PrevisaoMeta? {
            if (metaCentavos <= 0) return null
            if (lucroCentavos >= metaCentavos) return Batida
            val falta = metaCentavos - lucroCentavos

            val ritmo = if (segundosRodados >= MINIMO_PARA_RITMO_SEG && lucroCentavos > 0)
                lucroCentavos * 3600 / segundosRodados else null

            val chegaEm = if (ritmo != null && ritmo > 0 && turnoAndando) {
                val segundosFaltando = falta * 3600 / ritmo
                if (segundosFaltando <= MAXIMO_HORAS_PREVISAO * 3600) agoraMillis + segundosFaltando * 1000 else null
            } else null

            return Falta(falta, ritmo, chegaEm)
        }
    }
}
