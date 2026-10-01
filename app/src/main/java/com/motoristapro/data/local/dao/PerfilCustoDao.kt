package com.motoristapro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.motoristapro.data.local.entity.PerfilCusto
import kotlinx.coroutines.flow.Flow

@Dao
interface PerfilCustoDao {

    @Query("SELECT * FROM perfil_custo WHERE id = ${PerfilCusto.ID_UNICO}")
    fun observar(): Flow<PerfilCusto?>

    @Query("SELECT * FROM perfil_custo WHERE id = ${PerfilCusto.ID_UNICO}")
    suspend fun obter(): PerfilCusto?

    /** Linha única: salvar é sempre substituir. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(perfil: PerfilCusto)
}
