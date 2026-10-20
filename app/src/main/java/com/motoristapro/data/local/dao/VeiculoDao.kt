package com.motoristapro.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.motoristapro.data.local.entity.Veiculo
import kotlinx.coroutines.flow.Flow

/** Ganho e gasto de um veículo no período (lucro por veículo em "Meus veículos"). */
data class LucroDoVeiculo(
    @ColumnInfo(name = "veiculo_id") val veiculoId: Long,
    @ColumnInfo(name = "ganhos_centavos") val ganhosCentavos: Long,
    @ColumnInfo(name = "despesas_centavos") val despesasCentavos: Long
) {
    val lucroCentavos: Long get() = ganhosCentavos - despesasCentavos
}

@Dao
interface VeiculoDao {

    @Query("SELECT * FROM veiculos ORDER BY id")
    fun observarTodos(): Flow<List<Veiculo>>

    @Query("SELECT * FROM veiculos ORDER BY id")
    suspend fun todos(): List<Veiculo>

    @Query("SELECT * FROM veiculos WHERE id = :id")
    suspend fun buscar(id: Long): Veiculo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(v: Veiculo): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(v: List<Veiculo>)

    @Update
    suspend fun atualizar(v: Veiculo)

    @Query("DELETE FROM veiculos WHERE id = :id")
    suspend fun excluir(id: Long)

    @Query(
        "SELECT (SELECT COUNT(*) FROM corridas WHERE veiculo_id = :id) + " +
            "(SELECT COUNT(*) FROM despesas WHERE veiculo_id = :id)"
    )
    suspend fun lancamentos(id: Long): Int

    @Query("DELETE FROM veiculos")
    suspend fun limpar()

    /** Ganhos (corridas) e gastos (despesas) de cada veículo no período. */
    @Query(
        """
        SELECT v.id AS veiculo_id,
            COALESCE((SELECT SUM(c.valor_centavos + c.gorjeta_centavos) FROM corridas c
                WHERE c.veiculo_id = v.id AND c.inicio_em >= :inicio AND c.inicio_em < :fim), 0) AS ganhos_centavos,
            COALESCE((SELECT SUM(d.valor_centavos) FROM despesas d
                WHERE d.veiculo_id = v.id AND d.data_em >= :inicio AND d.data_em < :fim), 0) AS despesas_centavos
        FROM veiculos v
        """
    )
    fun observarLucros(inicio: Long, fim: Long): Flow<List<LucroDoVeiculo>>
}
