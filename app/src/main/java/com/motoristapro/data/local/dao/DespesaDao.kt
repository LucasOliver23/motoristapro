package com.motoristapro.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.motoristapro.data.local.entity.CategoriaDespesa
import com.motoristapro.data.local.entity.Despesa
import kotlinx.coroutines.flow.Flow

/** Total de uma categoria no período (centavos). */
data class TotalPorCategoria(
    @ColumnInfo(name = "categoria") val categoria: CategoriaDespesa,
    @ColumnInfo(name = "total_centavos") val totalCentavos: Long
)

/**
 * Acesso à tabela despesas.
 * Períodos semiabertos: [inicio, fim) em epoch millis.
 */
@Dao
interface DespesaDao {

    // ------------------------------------------------------------------ escrita

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(despesa: Despesa): Long

    @Update
    suspend fun atualizar(despesa: Despesa)

    @Delete
    suspend fun excluir(despesa: Despesa)

    @Query("DELETE FROM despesas WHERE id = :id")
    suspend fun excluirPorId(id: Long)

    // ------------------------------------------------------------------ leitura

    @Query("SELECT * FROM despesas WHERE id = :id")
    suspend fun buscarPorId(id: Long): Despesa?

    /** Todas as despesas (exportação CSV). */
    @Query("SELECT * FROM despesas ORDER BY data_em DESC")
    suspend fun listarTodas(): List<Despesa>

    @Query("SELECT * FROM despesas WHERE data_em >= :inicio AND data_em < :fim ORDER BY data_em DESC")
    fun observarPorPeriodo(inicio: Long, fim: Long): Flow<List<Despesa>>

    @Query(
        """
        SELECT * FROM despesas
        WHERE categoria = :categoria AND data_em >= :inicio AND data_em < :fim
        ORDER BY data_em DESC
        """
    )
    fun observarPorCategoria(categoria: CategoriaDespesa, inicio: Long, fim: Long): Flow<List<Despesa>>

    /** Último abastecimento com hodômetro — base para sugerir o próximo valor no formulário. */
    @Query(
        """
        SELECT * FROM despesas
        WHERE categoria = 'COMBUSTIVEL' AND odometro_km IS NOT NULL
        ORDER BY data_em DESC
        LIMIT 1
        """
    )
    suspend fun ultimoAbastecimentoComOdometro(): Despesa?

    // ------------------------------------------------------------------ somas

    /** DESPESAS do período = soma de todas as categorias (centavos). */
    @Query(
        """
        SELECT COALESCE(SUM(valor_centavos), 0)
        FROM despesas
        WHERE data_em >= :inicio AND data_em < :fim
        """
    )
    fun observarTotal(inicio: Long, fim: Long): Flow<Long>

    /** Versão pontual (sem Flow). */
    @Query(
        """
        SELECT COALESCE(SUM(valor_centavos), 0)
        FROM despesas
        WHERE data_em >= :inicio AND data_em < :fim
        """
    )
    suspend fun total(inicio: Long, fim: Long): Long

    /** Total por categoria no período, da maior para a menor. */
    @Query(
        """
        SELECT categoria, SUM(valor_centavos) AS total_centavos
        FROM despesas
        WHERE data_em >= :inicio AND data_em < :fim
        GROUP BY categoria
        ORDER BY total_centavos DESC
        """
    )
    fun observarTotalPorCategoria(inicio: Long, fim: Long): Flow<List<TotalPorCategoria>>
}
