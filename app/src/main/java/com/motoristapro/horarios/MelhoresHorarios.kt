package com.motoristapro.horarios

/**
 * Uma fatia das corridas registradas: dia da semana (0 = domingo, como o
 * strftime('%w') do SQLite) × faixa de 2 h (0 = 00h–02h ... 11 = 22h–24h) × app.
 */
data class FatiaCorridas(
    val diaSemana: Int,
    val faixa: Int,
    val plataforma: String,
    val corridas: Int,
    val valorCentavos: Long,
    val metros: Long,
    val segundos: Long
)

/** Um horário (dia × faixa) já somado, com todos os apps juntos. */
data class Horario(
    val diaSemana: Int,
    val faixa: Int,
    val corridas: Int,
    val valorCentavos: Long,
    val metros: Long,
    val segundos: Long,
    /** R$/h de cada app nesse horário, do maior para o menor. */
    val porApp: List<Pair<String, Long>>
) {
    /** R$ por hora DE CORRIDA (o tempo com passageiro), em centavos. */
    val porHoraCentavos: Long get() = if (segundos > 0) valorCentavos * 3600 / segundos else 0
    val porKmCentavos: Long get() = if (metros > 0) valorCentavos * 1000 / metros else 0

    /** "Sexta · 18h–20h" */
    val rotulo: String get() = "${DIAS[diaSemana]} · %02dh–%02dh".format(faixa * 2, faixa * 2 + 2)

    companion object {
        val DIAS = listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")
    }
}

/**
 * "Melhores horários": em que dia e hora as SUAS corridas pagaram mais por hora.
 *
 * Kotlin puro para testar. Horário com menos de [MINIMO_CORRIDAS] corridas não
 * entra no pódio: uma corrida boa sozinha não faz um horário bom.
 */
object MelhoresHorarios {

    const val MINIMO_CORRIDAS = 3

    fun somar(fatias: List<FatiaCorridas>): List<Horario> =
        fatias.groupBy { it.diaSemana to it.faixa }.map { (chave, lista) ->
            val porApp = lista.groupBy { it.plataforma }.map { (app, f) ->
                val v = f.sumOf { it.valorCentavos }
                val s = f.sumOf { it.segundos }
                app to (if (s > 0) v * 3600 / s else 0L)
            }.filter { it.second > 0 }.sortedByDescending { it.second }
            Horario(
                diaSemana = chave.first,
                faixa = chave.second,
                corridas = lista.sumOf { it.corridas },
                valorCentavos = lista.sumOf { it.valorCentavos },
                metros = lista.sumOf { it.metros },
                segundos = lista.sumOf { it.segundos },
                porApp = porApp
            )
        }

    /** Os [quantos] melhores por R$/h, só entre os que têm corrida suficiente. */
    fun podio(horarios: List<Horario>, quantos: Int = 3): List<Horario> =
        horarios.filter { it.corridas >= MINIMO_CORRIDAS && it.segundos > 0 }
            .sortedByDescending { it.porHoraCentavos }
            .take(quantos)

    /**
     * A grade da semana: [faixa][diaSemana] = R$/h (0 = sem corrida), e o maior
     * valor para a escala de cor.
     */
    fun grade(horarios: List<Horario>): Pair<Array<LongArray>, Long> {
        val g = Array(12) { LongArray(7) }
        horarios.forEach { h -> if (h.faixa in 0..11 && h.diaSemana in 0..6) g[h.faixa][h.diaSemana] = h.porHoraCentavos }
        return g to (g.maxOf { it.max() })
    }
}
