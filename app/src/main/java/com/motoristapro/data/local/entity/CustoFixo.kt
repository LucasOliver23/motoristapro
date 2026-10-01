package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Custo fixo mensal (seguro, parcela, IPVA, internet...). NÃO é lançado como despesa:
 * é rateado por dia trabalhado (valor ÷ dias_trabalho_mes da Configuração).
 */
@Entity(tableName = "custos_fixos")
data class CustoFixo(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "nome")
    val nome: String,

    @ColumnInfo(name = "valor_mensal_centavos")
    val valorMensalCentavos: Long,

    @ColumnInfo(name = "ativo", defaultValue = "1")
    val ativo: Boolean = true,

    /**
     * MANUAL = o motorista digitou aqui. ASSISTENTE = veio do assistente de custo
     * e é reescrito toda vez que ele recalcula — por isso a tela não deixa editar
     * esses, senão a próxima conta apagaria a edição sem avisar.
     */
    @ColumnInfo(name = "origem", defaultValue = "MANUAL")
    val origem: String = MANUAL
) {
    val doAssistente: Boolean get() = origem == ASSISTENTE

    companion object {
        const val MANUAL = "MANUAL"
        const val ASSISTENTE = "ASSISTENTE"
    }
}
