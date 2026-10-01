package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabela de domínio: Uber, 99, inDrive, Particular...
 * Normalizada (e não um texto solto na corrida) para permitir renomear,
 * desativar e agrupar relatórios sem inconsistência de grafia.
 */
@Entity(
    tableName = "plataformas",
    indices = [Index(value = ["nome"], unique = true)]
)
data class Plataforma(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "nome") val nome: String,
    @ColumnInfo(name = "cor_hex") val corHex: String? = null,
    @ColumnInfo(name = "ativa", defaultValue = "1") val ativa: Boolean = true
)
