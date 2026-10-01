package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Um turno de trabalho (cronômetro de horas + km medido pelo GPS).
 * fim_em == null significa jornada em andamento (só existe uma por vez).
 */
@Entity(
    tableName = "jornadas",
    indices = [Index(value = ["inicio_em"])]
)
data class Jornada(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "inicio_em")
    val inicioEm: Long,

    @ColumnInfo(name = "fim_em")
    val fimEm: Long? = null,

    /** Distância percorrida medida pelo GPS, em metros. */
    @ColumnInfo(name = "metros_gps", defaultValue = "0")
    val metrosGps: Long = 0
) {
    val ativa: Boolean get() = fimEm == null
}
