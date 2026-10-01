package com.motoristapro.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.motoristapro.data.local.entity.Corrida

data class CorridaComPlataforma(
    @Embedded val corrida: Corrida,
    @ColumnInfo(name = "plataforma_nome") val plataformaNome: String
)
