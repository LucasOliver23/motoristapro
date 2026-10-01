package com.motoristapro.data.local.model

import androidx.room.ColumnInfo
import com.motoristapro.data.local.entity.CategoriaDespesa

/** Uma linha por dia (fuso local do aparelho). */
data class LucroDiario(
    @ColumnInfo(name = "dia") val dia: String,                         // "2026-09-29"
    @ColumnInfo(name = "qtd_corridas") val qtdCorridas: Int,
    @ColumnInfo(name = "receita_centavos") val receitaCentavos: Long,
    @ColumnInfo(name = "despesa_centavos") val despesaCentavos: Long,
    /** Regime de caixa: receita - despesas lançadas no dia. */
    @ColumnInfo(name = "lucro_liquido_centavos") val lucroLiquidoCentavos: Long,
    /** Regime de competência: receita - (km rodado x custo/km da configuração). */
    @ColumnInfo(name = "lucro_estimado_centavos") val lucroEstimadoCentavos: Long,
    @ColumnInfo(name = "metros_rodados") val metrosRodados: Long,
    @ColumnInfo(name = "segundos_trabalhados") val segundosTrabalhados: Long
) {
    val kmRodados: Double get() = metrosRodados / 1000.0
    val ganhoPorKmCentavos: Long get() = if (metrosRodados > 0) receitaCentavos * 1000 / metrosRodados else 0
    val ganhoPorHoraCentavos: Long get() = if (segundosTrabalhados > 0) receitaCentavos * 3600 / segundosTrabalhados else 0
}

data class CustoPorKm(
    @ColumnInfo(name = "despesa_centavos") val despesaCentavos: Long,
    @ColumnInfo(name = "metros_rodados") val metrosRodados: Long,
    /** null quando não houve km rodado no período (evita divisão por zero). */
    @ColumnInfo(name = "custo_km_centavos") val custoKmCentavos: Double?
)

data class CustoKmPorCategoria(
    @ColumnInfo(name = "categoria") val categoria: CategoriaDespesa,
    @ColumnInfo(name = "despesa_centavos") val despesaCentavos: Long,
    @ColumnInfo(name = "custo_km_centavos") val custoKmCentavos: Double?
)

data class ResumoPlataforma(
    @ColumnInfo(name = "plataforma_id") val plataformaId: Long,
    @ColumnInfo(name = "plataforma") val plataforma: String,
    @ColumnInfo(name = "qtd_corridas") val qtdCorridas: Int,
    @ColumnInfo(name = "receita_centavos") val receitaCentavos: Long,
    @ColumnInfo(name = "metros_rodados") val metrosRodados: Long,
    @ColumnInfo(name = "ganho_km_centavos") val ganhoKmCentavos: Double?
)

data class ConsumoCombustivel(
    @ColumnInfo(name = "km_percorridos") val kmPercorridos: Long?,
    @ColumnInfo(name = "litros_ml") val litrosMl: Long?,
    @ColumnInfo(name = "km_por_litro") val kmPorLitro: Double?
)

/** Ganho agrupado pela hora do dia em que a corrida começou (0..23). */
data class GanhoPorHora(
    @ColumnInfo(name = "hora") val hora: Int,
    @ColumnInfo(name = "qtd_corridas") val qtdCorridas: Int,
    @ColumnInfo(name = "receita_centavos") val receitaCentavos: Long,
    @ColumnInfo(name = "segundos") val segundos: Long
) {
    /** Receita por hora em corrida nessa faixa (centavos). 0 se sem tempo. */
    val ganhoPorHoraCentavos: Long get() = if (segundos > 0) receitaCentavos * 3600 / segundos else 0
}
