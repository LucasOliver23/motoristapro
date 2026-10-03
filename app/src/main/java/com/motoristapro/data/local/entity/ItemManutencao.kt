package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Um item de manutenção do veículo: óleo, relação, pneu, revisão.
 *
 * O controle é por QUILOMETRAGEM, não por data: moto de aplicativo roda em dois
 * meses o que um carro de família roda em um ano, e "trocar a cada 6 meses"
 * não quer dizer nada para quem faz 300 km por dia.
 *
 * [odometroUltimaKm] é o hodômetro no dia da última troca, digitado pelo
 * motorista. O hodômetro de hoje o app já sabe: é o do último abastecimento com
 * km informado. A conta do que falta é a subtração dos dois.
 */
@Entity(tableName = "manutencoes")
data class ItemManutencao(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** "Troca de óleo", "Relação", "Pneu traseiro"... */
    val nome: String,

    /** De quantos em quantos km se faz. */
    @ColumnInfo(name = "intervalo_km")
    val intervaloKm: Long,

    /** Hodômetro na última vez que foi feito. */
    @ColumnInfo(name = "odometro_ultima_km")
    val odometroUltimaKm: Long,

    /** Quando foi lançado (epoch millis), só para o histórico. */
    @ColumnInfo(name = "atualizado_em")
    val atualizadoEm: Long = System.currentTimeMillis()
) {
    /** O hodômetro em que vence. */
    val odometroDoVencimento: Long get() = odometroUltimaKm + intervaloKm

    /** Quanto falta rodar. Negativo = já passou da hora. */
    fun faltamKm(odometroAtual: Long): Long = odometroDoVencimento - odometroAtual

    /** Quanto do intervalo já foi gasto, de 0 a 1. */
    fun fracaoUsada(odometroAtual: Long): Float =
        if (intervaloKm <= 0) 0f
        else ((odometroAtual - odometroUltimaKm).toFloat() / intervaloKm).coerceIn(0f, 1f)
}
