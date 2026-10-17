package com.motoristapro.pacote

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Os planos do "Pacote 0% de taxa" da 99: o motorista paga um valor fixo e
 * recebe 100% de cada corrida, sem a porcentagem da 99.
 *
 * Dois jeitos de cobrar: por TEMPO (vale N dias) ou por GANHOS (vale até
 * R$ X em corridas, dentro de 180 dias). Preços como aparecem na 99.
 */
enum class PlanoPacote(
    val rotulo: String,
    val detalhe: String,
    val precoCentavos: Long,
    /** Quantos dias o pacote vale. */
    val dias: Int,
    /** Só no pacote de ganhos: quanto em corridas ele cobre. */
    val limiteGanhosCentavos: Long? = null
) {
    NENHUM("Não uso pacote", "A 99 já desconta a taxa dela", 0, 0),
    DIA("1 dia", "Renovação automática", 299, 1),
    TRES_DIAS("3 dias", "Ilimitado", 699, 3),
    GANHOS_135("Até R$ 135 de ganhos", "Válido por 180 dias", 399, 180, 13_500),
    GANHOS_455("Até R$ 455 de ganhos", "Válido por 180 dias", 1_299, 180, 45_500);

    val porGanhos: Boolean get() = limiteGanhosCentavos != null

    /** O texto da despesa no extrato: "Pacote 99 · 1 dia". */
    val descricao: String get() = "$PREFIXO_DESCRICAO · $rotulo"

    companion object {
        /** Toda despesa de pacote começa assim; é por ele que o app acha a última compra. */
        const val PREFIXO_DESCRICAO = "Pacote 99"

        fun deNome(nome: String?): PlanoPacote =
            entries.firstOrNull { it.name == nome } ?: NENHUM
    }
}

/** O que o motorista escolheu na tela do pacote. */
data class ConfigPacote(
    val plano: PlanoPacote = PlanoPacote.NENHUM,
    val lancarSozinho: Boolean = true
) {
    val ativo: Boolean get() = plano != PlanoPacote.NENHUM
}

object PacoteTaxa {

    /**
     * Taxa média que a 99 tira sem pacote. Só serve para a comparação
     * "valeu a pena": não entra em conta nenhuma de lucro.
     */
    const val TAXA_MEDIA_99 = 0.20

    /**
     * Uma corrida da 99 acabou de ser registrada: precisa lançar um pacote novo?
     *
     * - Por tempo: sim se nunca lançou, ou se o último já passou dos dias dele
     *   (o de 1 dia vale só para o dia em que foi lançado).
     * - Por ganhos: sim se nunca lançou, se venceram os 180 dias, ou se as
     *   corridas desde a compra (sem contar esta) já usaram o limite.
     *
     * Corrida lançada com data antiga (antes da última compra) nunca lança.
     */
    fun precisaLancar(
        plano: PlanoPacote,
        diaDaCorrida: LocalDate,
        diaDaUltimaCompra: LocalDate?,
        ganhosDesdeACompraCentavos: Long
    ): Boolean {
        if (plano == PlanoPacote.NENHUM) return false
        if (diaDaUltimaCompra == null) return true
        val dias = ChronoUnit.DAYS.between(diaDaUltimaCompra, diaDaCorrida)
        if (dias < 0) return false
        if (dias >= plano.dias) return true
        val limite = plano.limiteGanhosCentavos ?: return false
        return ganhosDesdeACompraCentavos >= limite
    }

    /**
     * A parte do preço do pacote que cabe a um dia de trabalho.
     *
     * Por tempo, o preço dividido pelos dias (o de 3 dias pesa R$ 2,33 por dia,
     * e não R$ 6,99 no primeiro e zero nos outros). Por ganhos, proporcional ao
     * que se ganhou na 99 nesse dia.
     */
    fun custoDoDiaCentavos(plano: PlanoPacote, ganhos99DoDiaCentavos: Long): Long {
        if (plano == PlanoPacote.NENHUM || ganhos99DoDiaCentavos <= 0) return 0
        val limite = plano.limiteGanhosCentavos
        return if (limite != null) Math.round(plano.precoCentavos.toDouble() * ganhos99DoDiaCentavos / limite)
        else Math.round(plano.precoCentavos.toDouble() / plano.dias)
    }

    /** Quanto a 99 ficaria das corridas do dia sem o pacote (estimativa pela taxa média). */
    fun taxaEvitadaCentavos(ganhos99DoDiaCentavos: Long): Long =
        Math.round(ganhos99DoDiaCentavos * TAXA_MEDIA_99)

    /** Taxa evitada menos o que o pacote custou no dia. Negativo = não compensou. */
    fun economiaDoDiaCentavos(plano: PlanoPacote, ganhos99DoDiaCentavos: Long): Long =
        taxaEvitadaCentavos(ganhos99DoDiaCentavos) - custoDoDiaCentavos(plano, ganhos99DoDiaCentavos)
}
