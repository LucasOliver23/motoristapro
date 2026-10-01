package com.motoristapro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.motoristapro.data.local.entity.CustoFixo
import kotlinx.coroutines.flow.Flow

@Dao
interface CustoFixoDao {

    @Insert
    suspend fun inserir(custo: CustoFixo): Long

    @Update
    suspend fun atualizar(custo: CustoFixo)

    @Query("DELETE FROM custos_fixos WHERE id = :id")
    suspend fun excluir(id: Long)

    @Query("SELECT * FROM custos_fixos ORDER BY valor_mensal_centavos DESC")
    fun observarTodos(): Flow<List<CustoFixo>>

    /** Soma mensal dos custos fixos ativos (centavos). */
    @Query("SELECT COALESCE(SUM(valor_mensal_centavos), 0) FROM custos_fixos WHERE ativo = 1")
    fun observarTotalMensal(): Flow<Long>

    /** Apaga os que o assistente criou, para reescrevê-los com a conta nova. */
    @Query("DELETE FROM custos_fixos WHERE origem = :origem")
    suspend fun apagarPorOrigem(origem: String)
}
