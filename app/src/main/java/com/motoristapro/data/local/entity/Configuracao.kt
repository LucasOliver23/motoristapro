package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Configurações do motorista — tabela de linha única (o id é sempre 1).
 *
 * Fica no Room (e não em DataStore) porque os valores entram direto nas queries SQL:
 * o custo/km, por exemplo, é usado num subselect para calcular o lucro estimado.
 *
 * Dinheiro sempre em CENTAVOS (R$ 0,45 = 45).
 */
@Entity(tableName = "configuracoes")
data class Configuracao(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = ID_UNICO,

    /** Meta de lucro líquido por dia. 0 = sem meta. */
    @ColumnInfo(name = "meta_lucro_diario_centavos", defaultValue = "0")
    val metaLucroDiarioCentavos: Long = 0,

    /** Meta de lucro líquido por mês. 0 = sem meta. */
    @ColumnInfo(name = "meta_lucro_mensal_centavos", defaultValue = "0")
    val metaLucroMensalCentavos: Long = 0,

    /** Custo operacional por km rodado (combustível, manutenção, seguro diluídos). */
    @ColumnInfo(name = "custo_km_centavos", defaultValue = "0")
    val custoKmCentavos: Long = 0,

    /**
     * Tarifa mínima desejada por corrida: ofertas abaixo deste valor são marcadas
     * como RUIM pelo leitor de ofertas. 0 = desativado. (Adicionada na versão 2 do banco.)
     */
    @ColumnInfo(name = "tarifa_minima_centavos", defaultValue = "0")
    val tarifaMinimaCentavos: Long = 0,

    /** Meta de lucro líquido por semana (segunda a domingo). 0 = sem meta. (v3) */
    @ColumnInfo(name = "meta_lucro_semanal_centavos", defaultValue = "0")
    val metaLucroSemanalCentavos: Long = 0,

    /** Veículo (ex.: "Onix 1.0 2020"). (v3) */
    @ColumnInfo(name = "veiculo_nome")
    val veiculoNome: String? = null,

    /** Consumo médio em km/L × 100 (12,5 km/L = 1250). 0 = não informado. (v3) */
    @ColumnInfo(name = "consumo_km_l_x100", defaultValue = "0")
    val consumoKmLx100: Long = 0,

    /** Preço do litro do combustível em centavos (R$ 5,89 = 589). (v3) */
    @ColumnInfo(name = "preco_litro_centavos", defaultValue = "0")
    val precoLitroCentavos: Long = 0,

    /** Nome do motorista, mostrado no perfil. (v6) */
    @ColumnInfo(name = "nome_motorista")
    val nomeMotorista: String? = null,

    /** Telefone para contato. (v6) */
    @ColumnInfo(name = "telefone")
    val telefone: String? = null,

    /** Cidade onde roda. (v6) */
    @ColumnInfo(name = "cidade")
    val cidade: String? = null,

    /** Dias trabalhados por mês (usado para ratear custos fixos mensais). */
    @ColumnInfo(name = "dias_trabalho_mes", defaultValue = "26")
    val diasTrabalhoMes: Int = 26
) {
    /** Custo só de combustível por km, em centavos (preço ÷ consumo). null se não configurado. */
    val custoCombustivelKmCentavos: Double?
        get() = if (consumoKmLx100 > 0 && precoLitroCentavos > 0)
            precoLitroCentavos * 100.0 / consumoKmLx100 else null

    companion object {
        const val ID_UNICO = 1
    }
}
