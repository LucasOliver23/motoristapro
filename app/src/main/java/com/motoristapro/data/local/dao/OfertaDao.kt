package com.motoristapro.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.motoristapro.data.local.entity.OfertaRecebida
import kotlinx.coroutines.flow.Flow

/** Estatísticas das ofertas do período (dinheiro em centavos, distância em metros). */
data class ResumoOfertas(
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "boas") val boas: Int,
    @ColumnInfo(name = "medias") val medias: Int,
    @ColumnInfo(name = "ruins") val ruins: Int,
    @ColumnInfo(name = "registradas") val registradas: Int,
    @ColumnInfo(name = "valor_registradas") val valorRegistradas: Long,
    @ColumnInfo(name = "metros_registradas") val metrosRegistradas: Long,
    @ColumnInfo(name = "valor_nao_registradas") val valorNaoRegistradas: Long,
    @ColumnInfo(name = "metros_nao_registradas") val metrosNaoRegistradas: Long
) {
    val naoRegistradas: Int get() = total - registradas
    val reaisKmRegistradas: Long get() = if (metrosRegistradas > 0) valorRegistradas * 1000 / metrosRegistradas else 0
    val reaisKmNaoRegistradas: Long get() = if (metrosNaoRegistradas > 0) valorNaoRegistradas * 1000 / metrosNaoRegistradas else 0

    companion object {
        val VAZIO = ResumoOfertas(0, 0, 0, 0, 0, 0, 0, 0, 0)
    }
}

@Dao
interface OfertaDao {

    @Insert
    suspend fun inserir(oferta: OfertaRecebida): Long

    @Query("UPDATE ofertas SET registrada = 1 WHERE id = :id")
    suspend fun marcarRegistrada(id: Long)

    @Query("SELECT * FROM ofertas WHERE recebida_em >= :inicio AND recebida_em < :fim ORDER BY recebida_em DESC LIMIT 500")
    fun observarPorPeriodo(inicio: Long, fim: Long): Flow<List<OfertaRecebida>>

    @Query("SELECT * FROM ofertas ORDER BY recebida_em DESC")
    suspend fun listarTodas(): List<OfertaRecebida>

    @Query(
        """
        SELECT
            COUNT(*)                                                                   AS total,
            COALESCE(SUM(classificacao = 'BOA'), 0)                                    AS boas,
            COALESCE(SUM(classificacao = 'MEDIA'), 0)                                  AS medias,
            COALESCE(SUM(classificacao = 'RUIM'), 0)                                   AS ruins,
            COALESCE(SUM(registrada), 0)                                               AS registradas,
            COALESCE(SUM(CASE WHEN registrada = 1 THEN valor_centavos END), 0)         AS valor_registradas,
            COALESCE(SUM(CASE WHEN registrada = 1 THEN metros END), 0)                 AS metros_registradas,
            COALESCE(SUM(CASE WHEN registrada = 0 THEN valor_centavos END), 0)         AS valor_nao_registradas,
            COALESCE(SUM(CASE WHEN registrada = 0 THEN metros END), 0)                 AS metros_nao_registradas
        FROM ofertas
        WHERE recebida_em >= :inicio AND recebida_em < :fim
        """
    )
    fun observarResumo(inicio: Long, fim: Long): Flow<ResumoOfertas>

    /** Limpeza: ofertas antigas não são guardadas para sempre. */
    @Query("DELETE FROM ofertas WHERE recebida_em < :limite")
    suspend fun apagarAntesDe(limite: Long): Int
}
