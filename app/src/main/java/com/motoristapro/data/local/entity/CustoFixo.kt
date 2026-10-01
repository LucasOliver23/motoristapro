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
    val ativo: Boolean = true
)
