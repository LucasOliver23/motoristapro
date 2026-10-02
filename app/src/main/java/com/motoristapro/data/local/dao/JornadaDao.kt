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

    @Query("UPDATE jornadas SET pausada_em = :em WHERE id = :id")
    suspend fun pausar(id: Long, em: Long)

    /**
     * Soma segundos num estado e nos apps que estavam online.
     *
     * Tudo numa UPDATE só, somando em cima do que já havia: duas chamadas
     * concorrentes (o leitor vendo uma tela enquanto a tela da Jornada atualiza)
     * não se atropelam, porque quem soma é o SQLite.
     */
    @Query(
        """
        UPDATE jornadas SET
            seg_offline    = seg_offline    + :offline,
            seg_aguardando = seg_aguardando + :aguardando,
            seg_buscando   = seg_buscando   + :buscando,
            seg_esperando  = seg_esperando  + :esperando,
            seg_em_viagem  = seg_em_viagem  + :emViagem,
            seg_uber       = seg_uber       + :uber,
            seg_99         = seg_99         + :noventaENove,
            seg_ifood      = seg_ifood      + :ifood,
            seg_indrive    = seg_indrive    + :indrive
        WHERE id = :id
        """
    )
    suspend fun somarTempos(
        id: Long,
        offline: Long,
        aguardando: Long,
        buscando: Long,
        esperando: Long,
        emViagem: Long,
        uber: Long,
        noventaENove: Long,
        ifood: Long,
        indrive: Long
    )

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
}
