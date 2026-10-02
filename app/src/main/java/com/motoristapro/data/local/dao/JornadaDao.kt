package com.motoristapro.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.motoristapro.data.local.entity.Jornada
import kotlinx.coroutines.flow.Flow

/** Tempo conectado num período — a jornada aberta entra com o tempo até agora. */
data class TempoConectado(
    @ColumnInfo(name = "segundos") val segundos: Long,
    @ColumnInfo(name = "metros") val metros: Long,
    @ColumnInfo(name = "jornadas") val jornadas: Int
) {
    companion object { val VAZIO = TempoConectado(0, 0, 0) }
}

/** Soma das jornadas já encerradas num período. */
data class TotaisJornada(
    @ColumnInfo(name = "segundos") val segundos: Long,
    @ColumnInfo(name = "metros") val metros: Long
)

@Dao
interface JornadaDao {

    @Insert
    suspend fun inserir(jornada: Jornada): Long

    @Query("SELECT * FROM jornadas WHERE fim_em IS NULL ORDER BY inicio_em DESC LIMIT 1")
    fun observarAtiva(): Flow<Jornada?>

    @Query("SELECT * FROM jornadas WHERE fim_em IS NULL ORDER BY inicio_em DESC LIMIT 1")
    suspend fun ativa(): Jornada?

    @Query("UPDATE jornadas SET metros_gps = :metros WHERE id = :id")
    suspend fun atualizarMetros(id: Long, metros: Long)

    @Query("UPDATE jornadas SET fim_em = :fim WHERE id = :id AND fim_em IS NULL")
    suspend fun encerrar(id: Long, fim: Long)

    @Query("UPDATE jornadas SET pausada_em = :em WHERE id = :id")
    suspend fun pausar(id: Long, em: Long)

    /** As jornadas de um período, da mais recente para a mais antiga. */
    @Query(
        "SELECT * FROM jornadas WHERE inicio_em >= :inicio AND inicio_em < :fim ORDER BY inicio_em DESC"
    )
    fun observarPorPeriodo(inicio: Long, fim: Long): Flow<List<Jornada>>

    /** Jornadas encerradas que começaram no período [inicio, fim). */
    @Query(
        """
        SELECT COALESCE(SUM(fim_em - inicio_em), 0) / 1000 AS segundos,
               COALESCE(SUM(metros_gps), 0)               AS metros
        FROM jornadas
        WHERE fim_em IS NOT NULL AND inicio_em >= :inicio AND inicio_em < :fim
        """
    )
    fun observarTotaisEncerradas(inicio: Long, fim: Long): Flow<TotaisJornada>

    /**
     * Tempo conectado no período, CONTANDO a jornada que ainda está aberta.
     *
     * É o número do relatório. A versão que só olha jornadas encerradas deixava
     * o dia de hoje em zero até o motorista encerrar o turno — e aí o relatório
     * do dia parecia vazio justamente quando ele mais quer olhar.
     *
     * O tempo pausado sai da conta: pausou para almoçar, não está conectado.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
                   (CASE
                        WHEN fim_em IS NOT NULL   THEN fim_em
                        WHEN pausada_em > 0       THEN pausada_em
                        ELSE :agora
                    END) - inicio_em
               ), 0) / 1000                   AS segundos,
               COALESCE(SUM(metros_gps), 0)   AS metros,
               COUNT(*)                       AS jornadas
        FROM jornadas
        WHERE inicio_em >= :inicio AND inicio_em < :fim
        """
    )
    fun observarTempoConectado(inicio: Long, fim: Long, agora: Long): Flow<TempoConectado>
}
