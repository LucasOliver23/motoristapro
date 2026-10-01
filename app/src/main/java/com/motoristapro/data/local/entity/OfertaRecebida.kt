package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Toda oferta que o leitor mostrou na janela flutuante (aceita ou não).
 * registrada = o motorista tocou em "Registrar corrida" (proxy de "aceitei").
 */
@Entity(
    tableName = "ofertas",
    indices = [Index(value = ["recebida_em"])]
)
data class OfertaRecebida(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "recebida_em")
    val recebidaEm: Long,

    /** "Uber", "99", "iFood"... */
    @ColumnInfo(name = "plataforma")
    val plataforma: String,

    @ColumnInfo(name = "valor_centavos")
    val valorCentavos: Long,

    @ColumnInfo(name = "metros")
    val metros: Long,

    @ColumnInfo(name = "minutos")
    val minutos: Int,

    @ColumnInfo(name = "paradas", defaultValue = "0")
    val paradas: Int = 0,

    /** BOA / MEDIA / RUIM (nome do enum Classificacao). */
    @ColumnInfo(name = "classificacao")
    val classificacao: String,

    @ColumnInfo(name = "registrada", defaultValue = "0")
    val registrada: Boolean = false,

    /** Nota do passageiro lida na tela. null = o app não mostrou. */
    @ColumnInfo(name = "nota")
    val nota: Double? = null,

    /** Primeiro endereço do card (costuma ser o embarque/retirada). */
    @ColumnInfo(name = "origem")
    val origem: String? = null,

    /** Último endereço do card (o destino), quando a tela mostra os dois. */
    @ColumnInfo(name = "destino")
    val destino: String? = null
) {
    val km: Double get() = metros / 1000.0
    val reaisPorKmCentavos: Long get() = if (metros > 0) valorCentavos * 1000 / metros else 0

    val reaisPorHoraCentavos: Long get() = if (minutos > 0) valorCentavos * 60 / minutos else 0

    /** Lucro estimado com o custo/km do motorista, em centavos. */
    fun lucroCentavos(custoKmCentavos: Long): Long =
        valorCentavos - Math.round(km * custoKmCentavos)

    fun lucroPercentual(custoKmCentavos: Long): Int =
        if (valorCentavos > 0) (lucroCentavos(custoKmCentavos) * 100 / valorCentavos).toInt() else 0

    /**
     * Leitura que quase certamente veio errada da tela do app de corrida.
     *
     * Veio de um caso real: o leitor pegou o total de ganhos do dia da 99 em vez
     * do valor da corrida e gravou R$ 928 em 9,1 km — R$ 102/km. Nenhuma moto faz
     * isso, e uma linha dessas sozinha estraga a media dos melhores horarios.
     */
    val leituraSuspeita: Boolean get() = reaisPorKmCentavos > TETO_REAIS_KM_CENTAVOS

    companion object {
        /** R$ 50,00 por km: acima disso e erro de leitura, nao corrida boa. */
        const val TETO_REAIS_KM_CENTAVOS = 5_000L
    }
}
