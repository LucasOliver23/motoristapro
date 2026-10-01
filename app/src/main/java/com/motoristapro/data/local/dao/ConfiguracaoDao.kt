package com.motoristapro.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.motoristapro.data.local.entity.Configuracao
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfiguracaoDao {
    @Query("SELECT * FROM configuracoes WHERE id = 1")
    fun observar(): Flow<Configuracao?>

    @Query("SELECT * FROM configuracoes WHERE id = 1")
    suspend fun obter(): Configuracao?

    @Upsert
    suspend fun salvar(config: Configuracao)

    @Query("UPDATE configuracoes SET custo_km_centavos = :centavos WHERE id = 1")
    suspend fun definirCustoKm(centavos: Long)
}
