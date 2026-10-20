package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Categorias de despesa. Salvas como TEXT (nome do enum) via Converters:
 * adicionar uma categoria nova NÃO exige migration.
 * Nunca renomeie ou remova um valor que já foi gravado no aparelho de alguém.
 */
enum class CategoriaDespesa(val rotulo: String) {
    COMBUSTIVEL("Combustível"),
    MANUTENCAO("Manutenção"),
    SEGURO("Seguro"),
    /** Pacote 0% de taxa da 99 e afins: o que se paga ao app para não ter desconto na corrida. */
    TAXA_APP("Taxa do app"),
    OUTROS("Outros")
}

/** Uma despesa lançada pelo motorista. Dinheiro em centavos, data em epoch millis. */
@Entity(
    tableName = "despesas",
    indices = [
        Index(value = ["data_em"]),
        Index(value = ["categoria", "data_em"])
    ]
)
data class Despesa(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "categoria")
    val categoria: CategoriaDespesa,

    @ColumnInfo(name = "valor_centavos")
    val valorCentavos: Long,

    @ColumnInfo(name = "data_em")
    val dataEm: Long,

    @ColumnInfo(name = "descricao")
    val descricao: String? = null,

    /** Só para COMBUSTIVEL: volume abastecido em mililitros (40,5 L = 40500). */
    @ColumnInfo(name = "litros_ml")
    val litrosMl: Long? = null,

    /** Leitura do hodômetro no abastecimento (permite calcular km/L real). */
    @ColumnInfo(name = "odometro_km")
    val odometroKm: Long? = null,

    @ColumnInfo(name = "criado_em")
    val criadoEm: Long = System.currentTimeMillis(),

    /** Veículo da despesa (v14). 1 = o que já existia antes de ter vários. */
    @ColumnInfo(name = "veiculo_id", defaultValue = "1")
    val veiculoId: Long = 1
)
