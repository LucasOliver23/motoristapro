package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma corrida realizada.
 *
 * Convenções de armazenamento (valem para todo o banco):
 *  - Dinheiro  -> Long em CENTAVOS (R$ 23,50 = 2350). Nunca Double: evita erro de arredondamento.
 *  - Distância -> Long em METROS   (12,4 km = 12400).
 *  - Duração   -> Long em SEGUNDOS.
 *  - Datas     -> Long em epoch MILLIS (System.currentTimeMillis()).
 */
@Entity(
    tableName = "corridas",
    foreignKeys = [
        ForeignKey(
            entity = Plataforma::class,
            parentColumns = ["id"],
            childColumns = ["plataforma_id"],
            onDelete = ForeignKey.RESTRICT      // não deixa apagar plataforma que tem corridas
        )
    ],
    indices = [
        Index(value = ["plataforma_id"]),
        Index(value = ["inicio_em"])            // todos os relatórios filtram por período
    ]
)
data class Corrida(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Uber, 99, inDrive, Particular... (tabela plataformas). */
    @ColumnInfo(name = "plataforma_id")
    val plataformaId: Long,

    @ColumnInfo(name = "origem")
    val origem: String,

    @ColumnInfo(name = "destino")
    val destino: String,

    /** Valor líquido que caiu para o motorista (já descontada a taxa do app). */
    @ColumnInfo(name = "valor_centavos")
    val valorCentavos: Long,

    @ColumnInfo(name = "gorjeta_centavos", defaultValue = "0")
    val gorjetaCentavos: Long = 0,

    /** Km com passageiro, em metros. */
    @ColumnInfo(name = "distancia_m")
    val distanciaMetros: Long,

    /** Km rodado até buscar o passageiro, em metros (gasta combustível, mas não é pago). */
    @ColumnInfo(name = "deslocamento_m", defaultValue = "0")
    val deslocamentoMetros: Long = 0,

    @ColumnInfo(name = "duracao_seg")
    val duracaoSegundos: Long,

    @ColumnInfo(name = "inicio_em")
    val inicioEm: Long,

    @ColumnInfo(name = "observacao")
    val observacao: String? = null,

    @ColumnInfo(name = "criado_em")
    val criadoEm: Long = System.currentTimeMillis()
) {
    /** Valor + gorjeta. */
    val receitaCentavos: Long get() = valorCentavos + gorjetaCentavos

    /** Km total (passageiro + deslocamento). */
    val kmTotal: Double get() = (distanciaMetros + deslocamentoMetros) / 1000.0
}
