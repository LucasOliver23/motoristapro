package com.motoristapro.veiculo

/** De onde veio o número do painel. */
enum class OrigemOdometro(val rotulo: String) {
    INICIO("informado ao iniciar o dia"),
    FIM("informado ao encerrar o dia"),
    MANUAL("corrigido à mão"),
    ABASTECIMENTO("do último abastecimento")
}

/** Um km lido no painel, com a hora em que foi lido. */
data class LeituraOdometro(val km: Long, val em: Long, val origem: OrigemOdometro)

/**
 * O km de agora: a última leitura do painel mais o que o GPS mediu depois dela.
 * [kmGps] > 0 quer dizer que o número é estimado (ainda não foi conferido).
 */
data class OdometroAtual(val base: LeituraOdometro, val kmGps: Long) {
    val km: Long get() = base.km + kmGps
    val estimado: Boolean get() = kmGps > 0
}

/**
 * As contas do hodômetro, sem Android para dar para testar.
 *
 * O painel é a verdade; o GPS só preenche o meio do dia. Por isso o app
 * pergunta o km ao abrir e ao fechar a jornada: o que a moto rodou fora do app
 * (mercado, fim de semana) só aparece quando alguém olha o painel.
 */
object CalculoOdometro {

    /** A leitura mais nova entre as duas fontes (a das jornadas e a do abastecimento). */
    fun maisRecente(a: LeituraOdometro?, b: LeituraOdometro?): LeituraOdometro? = when {
        a == null -> b
        b == null -> a
        b.em > a.em -> b
        else -> a
    }

    fun atual(base: LeituraOdometro?, metrosGpsDepois: Long): OdometroAtual? =
        base?.let { OdometroAtual(it, (metrosGpsDepois.coerceAtLeast(0) + 500) / 1000) }

    /**
     * Km rodado fora do app entre a última leitura e a de agora. Só conta
     * quando o painel andou MAIS do que o GPS viu (o GPS perde sinal, nunca inventa).
     */
    fun foraDoApp(anterior: OdometroAtual?, agoraKm: Long): Long {
        val a = anterior ?: return 0
        return (agoraKm - a.km).coerceAtLeast(0)
    }

    /**
     * Um aviso quando o número digitado parece engano; null = parece certo.
     * É aviso, não trava: trocar o painel ou corrigir um erro de digitação
     * pode, sim, fazer o km "voltar".
     */
    fun aviso(novoKm: Long, anterior: OdometroAtual?): String? {
        if (novoKm <= 0) return "Digite o km do painel"
        val a = anterior ?: return null
        return when {
            novoKm < a.base.km -> "Menor que o último km (${a.base.km}). Confira o painel."
            novoKm - a.km > SALTO_MAXIMO_KM -> "Mais de $SALTO_MAXIMO_KM km desde a última leitura. Confira o painel."
            else -> null
        }
    }

    const val SALTO_MAXIMO_KM = 2_000L
}
