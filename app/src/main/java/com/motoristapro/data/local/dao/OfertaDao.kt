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

/**
 * Uma faixa de 2 horas do dia (0 = 00h–02h, 1 = 02h–04h ... 11 = 22h–00h),
 * com tudo que o leitor viu nela. Serve para responder "a que horas vale a pena rodar".
 */
data class FaixaHoraria(
    @ColumnInfo(name = "faixa") val faixa: Int,
    @ColumnInfo(name = "ofertas") val ofertas: Int,
    @ColumnInfo(name = "valor_centavos") val valorCentavos: Long,
    @ColumnInfo(name = "metros") val metros: Long,
    @ColumnInfo(name = "minutos") val minutos: Int
) {
    /** R$/km médio das ofertas dessa faixa, em centavos. */
    val reaisPorKmCentavos: Long get() = if (metros > 0) valorCentavos * 1000 / metros else 0

    /** "08h–10h" */
    val rotulo: String get() = "%02dh–%02dh".format(faixa * 2, (faixa * 2 + 2) % 24)
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

    /**
     * Ofertas agrupadas em faixas de 2 horas do dia.
     *
     * strftime com 'localtime' converte o epoch para o fuso do aparelho — sem isso,
     * uma oferta das 21h no Brasil cairia na faixa das 00h (UTC).
     * Faixas com poucas ofertas são descartadas em [minimoOfertas]: 1 corrida boa
     * às 3h da manhã não faz daquele horário o melhor da semana.
     */
    @Query(
        """
        SELECT
            CAST(strftime('%H', recebida_em / 1000, 'unixepoch', 'localtime') AS INTEGER) / 2 AS faixa,
            COUNT(*)                            AS ofertas,
            COALESCE(SUM(valor_centavos), 0)    AS valor_centavos,
            COALESCE(SUM(metros), 0)            AS metros,
            COALESCE(SUM(minutos), 0)           AS minutos
        FROM ofertas
        WHERE recebida_em >= :inicio AND recebida_em < :fim AND metros > 0
        GROUP BY faixa
        HAVING COUNT(*) >= :minimoOfertas
        ORDER BY faixa
        """
    )
    fun observarFaixasHorarias(inicio: Long, fim: Long, minimoOfertas: Int = 3): Flow<List<FaixaHoraria>>

    /**
     * O mesmo, podendo olhar um dia da semana só — "como costuma ser a minha
     * terça-feira". diaSemana segue o strftime do SQLite: 0 = domingo ... 6 =
     * sábado; -1 = todos os dias.
     */
    @Query(
        """
        SELECT
            CAST(strftime('%H', recebida_em / 1000, 'unixepoch', 'localtime') AS INTEGER) / 2 AS faixa,
            COUNT(*)                            AS ofertas,
            COALESCE(SUM(valor_centavos), 0)    AS valor_centavos,
            COALESCE(SUM(metros), 0)            AS metros,
            COALESCE(SUM(minutos), 0)           AS minutos
        FROM ofertas
        WHERE recebida_em >= :inicio AND recebida_em < :fim AND metros > 0
          AND (
              :diaSemana < 0
              OR CAST(strftime('%w', recebida_em / 1000, 'unixepoch', 'localtime') AS INTEGER) = :diaSemana
          )
        GROUP BY faixa
        HAVING COUNT(*) >= :minimoOfertas
        ORDER BY faixa
        """
    )
    fun observarFaixasPorDiaDaSemana(
        inicio: Long,
        fim: Long,
        diaSemana: Int,
        minimoOfertas: Int = 1
    ): Flow<List<FaixaHoraria>>

    /** Limpeza: ofertas antigas não são guardadas para sempre. */
    @Query("DELETE FROM ofertas WHERE recebida_em < :limite")
    suspend fun apagarAntesDe(limite: Long): Int
}
