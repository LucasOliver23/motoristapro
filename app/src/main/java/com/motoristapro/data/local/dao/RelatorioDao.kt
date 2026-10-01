package com.motoristapro.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.motoristapro.data.local.model.ConsumoCombustivel
import com.motoristapro.data.local.model.CustoKmPorCategoria
import com.motoristapro.data.local.model.CustoPorKm
import com.motoristapro.data.local.model.GanhoPorHora
import com.motoristapro.data.local.model.LucroDiario
import com.motoristapro.data.local.model.ResumoPlataforma
import kotlinx.coroutines.flow.Flow

/**
 * Todas as agregações financeiras ficam aqui, calculadas no SQLite
 * (nada de carregar listas inteiras para somar em Kotlin).
 * Parâmetros :inicio/:fim = epoch millis, intervalo [inicio, fim).
 */
@Dao
interface RelatorioDao {

    /**
     * LUCRO LÍQUIDO DIÁRIO.
     * UNION ALL de corridas (entradas) e despesas (saídas) agrupado por dia local,
     * para que dias só com despesa (ex.: troca de óleo numa folga) também apareçam.
     */
    @Query(
        """
        SELECT
            dia,
            SUM(eh_corrida)                         AS qtd_corridas,
            SUM(receita)                            AS receita_centavos,
            SUM(despesa)                            AS despesa_centavos,
            SUM(receita) - SUM(despesa)             AS lucro_liquido_centavos,
            SUM(receita) - CAST(ROUND(SUM(metros) *
                (SELECT COALESCE(MAX(custo_km_centavos), 0) FROM configuracoes WHERE id = 1)
                / 1000.0) AS INTEGER)               AS lucro_estimado_centavos,
            SUM(metros)                             AS metros_rodados,
            SUM(segundos)                           AS segundos_trabalhados
        FROM (
            SELECT strftime('%Y-%m-%d', inicio_em / 1000, 'unixepoch', 'localtime') AS dia,
                   valor_centavos + gorjeta_centavos  AS receita,
                   0                                  AS despesa,
                   distancia_m + deslocamento_m       AS metros,
                   duracao_seg                        AS segundos,
                   1                                  AS eh_corrida
            FROM corridas
            WHERE inicio_em >= :inicio AND inicio_em < :fim
            UNION ALL
            SELECT strftime('%Y-%m-%d', data_em / 1000, 'unixepoch', 'localtime'),
                   0, valor_centavos, 0, 0, 0
            FROM despesas
            WHERE data_em >= :inicio AND data_em < :fim
        )
        GROUP BY dia
        ORDER BY dia DESC
        """
    )
    fun observarLucroDiario(inicio: Long, fim: Long): Flow<List<LucroDiario>>

    /**
     * CUSTO REAL POR KM no período = total de despesas / km total rodado
     * (km com passageiro + deslocamento). Retorna custo em centavos por km.
     */
    @Query(
        """
        SELECT
            d.total AS despesa_centavos,
            k.total AS metros_rodados,
            CASE WHEN k.total > 0 THEN d.total * 1000.0 / k.total END AS custo_km_centavos
        FROM
            (SELECT COALESCE(SUM(valor_centavos), 0) AS total
               FROM despesas WHERE data_em >= :inicio AND data_em < :fim) d,
            (SELECT COALESCE(SUM(distancia_m + deslocamento_m), 0) AS total
               FROM corridas WHERE inicio_em >= :inicio AND inicio_em < :fim) k
        """
    )
    fun observarCustoPorKm(inicio: Long, fim: Long): Flow<CustoPorKm>

    /** Custo por km quebrado por categoria (quanto do R$/km é combustível, manutenção...). */
    @Query(
        """
        SELECT
            d.categoria,
            SUM(d.valor_centavos) AS despesa_centavos,
            CASE WHEN k.total > 0 THEN SUM(d.valor_centavos) * 1000.0 / k.total END AS custo_km_centavos
        FROM despesas d,
            (SELECT COALESCE(SUM(distancia_m + deslocamento_m), 0) AS total
               FROM corridas WHERE inicio_em >= :inicio AND inicio_em < :fim) k
        WHERE d.data_em >= :inicio AND d.data_em < :fim
        GROUP BY d.categoria
        ORDER BY despesa_centavos DESC
        """
    )
    fun observarCustoKmPorCategoria(inicio: Long, fim: Long): Flow<List<CustoKmPorCategoria>>

    /** Ranking de plataformas: qual paga melhor por km. */
    @Query(
        """
        SELECT
            p.id AS plataforma_id,
            p.nome AS plataforma,
            COUNT(c.id) AS qtd_corridas,
            COALESCE(SUM(c.valor_centavos + c.gorjeta_centavos), 0) AS receita_centavos,
            COALESCE(SUM(c.distancia_m + c.deslocamento_m), 0) AS metros_rodados,
            CASE WHEN SUM(c.distancia_m + c.deslocamento_m) > 0
                 THEN SUM(c.valor_centavos + c.gorjeta_centavos) * 1000.0
                      / SUM(c.distancia_m + c.deslocamento_m) END AS ganho_km_centavos
        FROM plataformas p
        INNER JOIN corridas c ON c.plataforma_id = p.id
        WHERE c.inicio_em >= :inicio AND c.inicio_em < :fim
        GROUP BY p.id, p.nome
        ORDER BY receita_centavos DESC
        """
    )
    fun observarResumoPorPlataforma(inicio: Long, fim: Long): Flow<List<ResumoPlataforma>>

    /** Melhores horários: receita e tempo em corrida por hora do dia (fuso local). */
    @Query(
        """
        SELECT
            CAST(strftime('%H', inicio_em / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hora,
            COUNT(*)                                                 AS qtd_corridas,
            COALESCE(SUM(valor_centavos + gorjeta_centavos), 0)      AS receita_centavos,
            COALESCE(SUM(duracao_seg), 0)                            AS segundos
        FROM corridas
        WHERE inicio_em >= :inicio AND inicio_em < :fim
        GROUP BY hora
        ORDER BY hora
        """
    )
    fun observarGanhoPorHoraDoDia(inicio: Long, fim: Long): Flow<List<GanhoPorHora>>

    /**
     * Consumo real (método tanque cheio): km entre o 1º e o último hodômetro
     * dividido pelos litros abastecidos DEPOIS do 1º abastecimento.
     */
    @Query(
        """
        SELECT
            MAX(odometro_km) - MIN(odometro_km) AS km_percorridos,
            SUM(litros_ml) - (SELECT litros_ml FROM despesas
                               WHERE categoria = 'COMBUSTIVEL' AND odometro_km IS NOT NULL
                                 AND litros_ml IS NOT NULL
                                 AND data_em >= :inicio AND data_em < :fim
                               ORDER BY odometro_km ASC LIMIT 1) AS litros_ml,
            CASE WHEN COUNT(*) >= 2 THEN
                (MAX(odometro_km) - MIN(odometro_km)) * 1000.0 /
                (SUM(litros_ml) - (SELECT litros_ml FROM despesas
                                    WHERE categoria = 'COMBUSTIVEL' AND odometro_km IS NOT NULL
                                      AND litros_ml IS NOT NULL
                                      AND data_em >= :inicio AND data_em < :fim
                                    ORDER BY odometro_km ASC LIMIT 1))
            END AS km_por_litro
        FROM despesas
        WHERE categoria = 'COMBUSTIVEL' AND odometro_km IS NOT NULL AND litros_ml IS NOT NULL
          AND data_em >= :inicio AND data_em < :fim
        """
    )
    suspend fun consumoCombustivel(inicio: Long, fim: Long): ConsumoCombustivel
}
