package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Toda oferta que o leitor mostrou na janela flutuante (aceita ou não).
 * registrada = o motorista tocou em "Registrar corrida" (proxy de "aceitei").
 */
@Entity(
    tableName = "ofertas",
    indices = [Index(value = ["recebida_em"])]
)
data class OfertaRecebida(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "recebida_em")
    val recebidaEm: Long,

    /** "Uber", "99", "iFood"... */
    @ColumnInfo(name = "plataforma")
    val plataforma: String,

    @ColumnInfo(name = "valor_centavos")
    val valorCentavos: Long,

    @ColumnInfo(name = "metros")
    val metros: Long,

    @ColumnInfo(name = "minutos")
    val minutos: Int,

    @ColumnInfo(name = "paradas", defaultValue = "0")
    val paradas: Int = 0,

    /** BOA / MEDIA / RUIM (nome do enum Classificacao). */
    @ColumnInfo(name = "classificacao")
    val classificacao: String,

    @ColumnInfo(name = "registrada", defaultValue = "0")
    val registrada: Boolean = false
) {
    val km: Double get() = metros / 1000.0
    val reaisPorKmCentavos: Long get() = if (metros > 0) valorCentavos * 1000 / metros else 0
}
