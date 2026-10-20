package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Um veículo do motorista (v14). Corridas, despesas, jornadas e manutenção
 * apontam para ele por `veiculo_id`; o ATIVO (preferência do app) é o que
 * recebe o que for lançado e cujo custo por km vale no semáforo.
 *
 * O id 1 é o veículo que já existia antes da v14: a migração aponta tudo
 * o que estava no celular para ele.
 */
@Entity(tableName = "veiculos")
data class Veiculo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val nome: String,

    /** "MOTO" ou "CARRO" (TipoVeiculo). */
    val tipo: String = "MOTO",

    /** "GASOLINA", "ETANOL"... (TipoCombustivel). */
    val combustivel: String = "GASOLINA",

    @ColumnInfo(name = "consumo_km_l_x100")
    val consumoKmLx100: Long = 0,

    @ColumnInfo(name = "preco_litro_centavos")
    val precoLitroCentavos: Long = 0,

    @ColumnInfo(name = "custo_km_centavos")
    val custoKmCentavos: Long = 0,

    @ColumnInfo(name = "tarifa_minima_centavos")
    val tarifaMinimaCentavos: Long = 0,

    @ColumnInfo(name = "criado_em")
    val criadoEm: Long = System.currentTimeMillis()
) {
    companion object {
        /** O veículo de antes da v14 (e o primeiro de quem instala agora). */
        const val PRIMEIRO = 1L
    }
}
