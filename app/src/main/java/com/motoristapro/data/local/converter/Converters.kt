package com.motoristapro.data.local.converter

import androidx.room.TypeConverter
import com.motoristapro.data.local.entity.CategoriaDespesa

class Converters {
    @TypeConverter
    fun categoriaParaTexto(c: CategoriaDespesa): String = c.name

    @TypeConverter
    fun textoParaCategoria(s: String): CategoriaDespesa =
        runCatching { CategoriaDespesa.valueOf(s) }.getOrDefault(CategoriaDespesa.OUTROS)
}
