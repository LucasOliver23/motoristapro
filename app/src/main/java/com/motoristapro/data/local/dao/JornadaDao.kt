package com.motoristapro.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.motoristapro.data.local.entity.Jornada
import kotlinx.coroutines.flow.Flow

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
}
