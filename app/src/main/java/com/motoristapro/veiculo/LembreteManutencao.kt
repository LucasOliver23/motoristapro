package com.motoristapro.veiculo

import com.motoristapro.data.local.entity.ItemManutencao

/** Em que ponto o item está para o aviso. */
enum class FaseLembrete { CHEGANDO, VENCIDO }

/** Um aviso a disparar. [chave] identifica o aviso para não repetir. */
data class Lembrete(val item: ItemManutencao, val faltamKm: Long, val fase: FaseLembrete) {
    val chave: String get() = chaveDe(item, fase)

    companion object {
        /** Muda quando o item é refeito (outro vencimento): aí o aviso vale de novo. */
        fun chaveDe(item: ItemManutencao, fase: FaseLembrete) = "${item.id}:${item.odometroDoVencimento}:${fase.name}"
    }
}

/**
 * Quais itens de manutenção merecem aviso agora. Kotlin puro (testado).
 *
 * Um aviso quando entra na faixa de [avisoKm] ("Troca de óleo em 280 km") e
 * outro quando vence. Cada um só uma vez por vencimento, a não ser que o
 * motorista tenha tocado em "Lembrar amanhã" e o prazo já passou.
 */
object CalculoLembrete {

    val OPCOES_KM = listOf(100, 300, 500)
    const val PADRAO_KM = 300

    fun quaisAvisar(
        itens: List<ItemManutencao>,
        odometroKm: Long,
        avisoKm: Int,
        jaAvisados: Set<String>,
        /** id do item -> até quando o motorista pediu para não lembrar. */
        adiadoAte: Map<Long, Long>,
        agora: Long
    ): List<Lembrete> {
        if (odometroKm <= 0) return emptyList()
        return itens.mapNotNull { item ->
            if (item.intervaloKm <= 0) return@mapNotNull null
            if ((adiadoAte[item.id] ?: 0L) > agora) return@mapNotNull null
            val faltam = item.faltamKm(odometroKm)
            val fase = when {
                faltam <= 0 -> FaseLembrete.VENCIDO
                faltam <= avisoKm -> FaseLembrete.CHEGANDO
                else -> return@mapNotNull null
            }
            Lembrete(item, faltam, fase).takeIf { it.chave !in jaAvisados }
        }
    }
}
