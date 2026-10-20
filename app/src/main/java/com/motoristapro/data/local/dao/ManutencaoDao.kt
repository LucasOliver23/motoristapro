package com.motoristapro.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.motoristapro.data.local.entity.ItemManutencao
import kotlinx.coroutines.flow.Flow

@Dao
interface ManutencaoDao {

    /** Ordenado pelo que vence primeiro: o topo da lista é o que o motorista precisa ver. */
    @Query("SELECT * FROM manutencoes ORDER BY (odometro_ultima_km + intervalo_km) ASC")
    fun observarTodos(): Flow<List<ItemManutencao>>

    /** Só os itens de um veículo (o ativo, na tela e no lembrete). */
    @Query("SELECT * FROM manutencoes WHERE veiculo_id = :veiculoId ORDER BY (odometro_ultima_km + intervalo_km) ASC")
    fun observarDoVeiculo(veiculoId: Long): Flow<List<ItemManutencao>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(item: ItemManutencao): Long

    @Update
    suspend fun atualizar(item: ItemManutencao)

    @Delete
    suspend fun excluir(item: ItemManutencao)

    @Query("DELETE FROM manutencoes WHERE id = :id")
    suspend fun excluirPorId(id: Long)
}
