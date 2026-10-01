package com.motoristapro.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.model.CorridaComPlataforma
import kotlinx.coroutines.flow.Flow

/**
 * Totais financeiros de um período, calculados numa única ida ao banco.
 * Dinheiro em centavos, distância em metros, tempo em segundos.
 */
data class ResumoPeriodo(
    @ColumnInfo(name = "faturamento_centavos") val faturamentoCentavos: Long,
    @ColumnInfo(name = "despesas_centavos") val despesasCentavos: Long,
    @ColumnInfo(name = "lucro_liquido_centavos") val lucroLiquidoCentavos: Long,
    @ColumnInfo(name = "qtd_corridas") val qtdCorridas: Int,
    @ColumnInfo(name = "metros_rodados") val metrosRodados: Long,
    @ColumnInfo(name = "segundos_em_corrida") val segundosEmCorrida: Long
) {
    val kmRodados: Double get() = metrosRodados / 1000.0

    /** Receita por hora em corrida (centavos). 0 se não houve corrida. */
    val ganhoPorHoraCentavos: Long
        get() = if (segundosEmCorrida > 0) faturamentoCentavos * 3600 / segundosEmCorrida else 0

    /** Receita por km rodado (centavos). 0 se não houve km. */
    val ganhoPorKmCentavos: Long
        get() = if (metrosRodados > 0) faturamentoCentavos * 1000 / metrosRodados else 0

    companion object {
        val VAZIO = ResumoPeriodo(0, 0, 0, 0, 0, 0)
    }
}

/**
 * Acesso à tabela corridas.
 * Todos os períodos são semiabertos: [inicio, fim) em epoch millis
 * (use Periodo.dia() / Periodo.mes() do repositório para montar os limites).
 */
@Dao
interface CorridaDao {

    // ------------------------------------------------------------------ escrita

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(corrida: Corrida): Long

    @Update
    suspend fun atualizar(corrida: Corrida)

    @Delete
    suspend fun excluir(corrida: Corrida)

    @Query("DELETE FROM corridas WHERE id = :id")
    suspend fun excluirPorId(id: Long)

    // ------------------------------------------------------------------ leitura

    @Query("SELECT * FROM corridas WHERE id = :id")
    suspend fun buscarPorId(id: Long): Corrida?

    @Query(
        """
        SELECT c.*, p.nome AS plataforma_nome
        FROM corridas c
        INNER JOIN plataformas p ON p.id = c.plataforma_id
        WHERE c.inicio_em >= :inicio AND c.inicio_em < :fim
        ORDER BY c.inicio_em DESC
        """
    )
    fun observarPorPeriodo(inicio: Long, fim: Long): Flow<List<CorridaComPlataforma>>

    /** Todas as corridas (exportação CSV). */
    @Query(
        """
        SELECT c.*, p.nome AS plataforma_nome
        FROM corridas c
        INNER JOIN plataformas p ON p.id = c.plataforma_id
        ORDER BY c.inicio_em DESC
        """
    )
    suspend fun listarTodasComPlataforma(): List<CorridaComPlataforma>

    @Query("SELECT COUNT(*) FROM corridas WHERE inicio_em >= :inicio AND inicio_em < :fim")
    fun observarQuantidade(inicio: Long, fim: Long): Flow<Int>

    // ------------------------------------------------------------------ somas

    /** FATURAMENTO do período = soma de valor + gorjeta das corridas (centavos). */
    @Query(
        """
        SELECT COALESCE(SUM(valor_centavos + gorjeta_centavos), 0)
        FROM corridas
        WHERE inicio_em >= :inicio AND inicio_em < :fim
        """
    )
    fun observarFaturamento(inicio: Long, fim: Long): Flow<Long>

    /** Versão pontual (sem Flow), para uso em workers/relatórios. */
    @Query(
        """
        SELECT COALESCE(SUM(valor_centavos + gorjeta_centavos), 0)
        FROM corridas
        WHERE inicio_em >= :inicio AND inicio_em < :fim
        """
    )
    suspend fun faturamento(inicio: Long, fim: Long): Long

    /**
     * LUCRO LÍQUIDO do período = faturamento das corridas − despesas lançadas no período.
     * A query lê as duas tabelas, então o Flow reemite quando QUALQUER uma delas muda.
     */
    @Query(
        """
        SELECT
            (SELECT COALESCE(SUM(valor_centavos + gorjeta_centavos), 0)
               FROM corridas WHERE inicio_em >= :inicio AND inicio_em < :fim)
          - (SELECT COALESCE(SUM(valor_centavos), 0)
               FROM despesas WHERE data_em >= :inicio AND data_em < :fim)
        """
    )
    fun observarLucroLiquido(inicio: Long, fim: Long): Flow<Long>

    /** Faturamento, despesas, lucro, corridas, km e tempo do período — uma única query. */
    @Query(
        """
        SELECT
            c.faturamento                    AS faturamento_centavos,
            d.total                          AS despesas_centavos,
            c.faturamento - d.total          AS lucro_liquido_centavos,
            c.qtd                            AS qtd_corridas,
            c.metros                         AS metros_rodados,
            c.segundos                       AS segundos_em_corrida
        FROM
            (SELECT COALESCE(SUM(valor_centavos + gorjeta_centavos), 0) AS faturamento,
                    COUNT(*)                                            AS qtd,
                    COALESCE(SUM(distancia_m + deslocamento_m), 0)      AS metros,
                    COALESCE(SUM(duracao_seg), 0)                       AS segundos
               FROM corridas
              WHERE inicio_em >= :inicio AND inicio_em < :fim) c,
            (SELECT COALESCE(SUM(valor_centavos), 0) AS total
               FROM despesas
              WHERE data_em >= :inicio AND data_em < :fim) d
        """
    )
    fun observarResumo(inicio: Long, fim: Long): Flow<ResumoPeriodo>
}
