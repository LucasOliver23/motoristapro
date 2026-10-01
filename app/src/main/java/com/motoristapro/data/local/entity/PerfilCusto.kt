package com.motoristapro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Respostas do assistente de custo — tabela de linha única (id sempre 1).
 *
 * Guardada para o motorista poder recalcular quando a gasolina subir, sem
 * preencher tudo de novo. O resultado do cálculo NÃO fica aqui: ele é derivado
 * (CalculadoraCusto) e o que vale fica em `configuracoes.custo_km_centavos`.
 *
 * Mesmo padrão do resto do app: dinheiro em centavos, percentual em
 * centésimos (4% = 400), consumo em centésimos (35 km/L = 3500).
 */
@Entity(tableName = "perfil_custo")
data class PerfilCusto(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = ID_UNICO,

    /** QUITADO, FINANCIADO ou ALUGADO (nome do enum FormaAquisicao). */
    @ColumnInfo(name = "forma", defaultValue = "QUITADO")
    val forma: String = "QUITADO",

    /** CARRO ou MOTO (nome do enum TipoVeiculo). */
    @ColumnInfo(name = "tipo_veiculo", defaultValue = "MOTO")
    val tipoVeiculo: String = "MOTO",

    /** PASSAGEIROS, ENTREGAS ou AMBOS (nome do enum TipoTrabalho). */
    @ColumnInfo(name = "tipo_trabalho", defaultValue = "PASSAGEIROS")
    val tipoTrabalho: String = "PASSAGEIROS",

    // ---------------------------------------------------------- custos fixos
    @ColumnInfo(name = "valor_veiculo_centavos", defaultValue = "0")
    val valorVeiculoCentavos: Long = 0,

    @ColumnInfo(name = "parcela_mensal_centavos", defaultValue = "0")
    val parcelaMensalCentavos: Long = 0,

    @ColumnInfo(name = "aluguel_mensal_centavos", defaultValue = "0")
    val aluguelMensalCentavos: Long = 0,

    @ColumnInfo(name = "seguro_mensal_centavos", defaultValue = "0")
    val seguroMensalCentavos: Long = 0,

    @ColumnInfo(name = "ipva_percent_x100", defaultValue = "400")
    val ipvaPercentX100: Long = 400,

    /** 1 = o IPVA foi digitado em reais por ano; 0 = em % do valor do veículo. */
    @ColumnInfo(name = "ipva_em_reais", defaultValue = "0")
    val ipvaEmReais: Boolean = false,

    @ColumnInfo(name = "ipva_anual_centavos", defaultValue = "0")
    val ipvaAnualCentavos: Long = 0,

    @ColumnInfo(name = "desvalorizacao_anual_x100", defaultValue = "1000")
    val desvalorizacaoAnualX100: Long = 1000,

    @ColumnInfo(name = "outros_mensais_centavos", defaultValue = "0")
    val outrosMensaisCentavos: Long = 0,

    // ------------------------------------------------------ manutenção/desgaste
    @ColumnInfo(name = "revisao_centavos", defaultValue = "0")
    val revisaoCentavos: Long = 0,

    @ColumnInfo(name = "intervalo_revisao_km", defaultValue = "0")
    val intervaloRevisaoKm: Long = 0,

    @ColumnInfo(name = "troca_oleo_centavos", defaultValue = "0")
    val trocaOleoCentavos: Long = 0,

    @ColumnInfo(name = "intervalo_oleo_km", defaultValue = "0")
    val intervaloOleoKm: Long = 0,

    @ColumnInfo(name = "jogo_pneus_centavos", defaultValue = "0")
    val jogoPneusCentavos: Long = 0,

    @ColumnInfo(name = "duracao_pneus_km", defaultValue = "0")
    val duracaoPneusKm: Long = 0,

    @ColumnInfo(name = "outros_desgaste_centavos", defaultValue = "0")
    val outrosDesgasteCentavos: Long = 0,

    @ColumnInfo(name = "outros_desgaste_km", defaultValue = "0")
    val outrosDesgasteKm: Long = 0,

    // ---------------------------------------------------------- combustível
    /** GASOLINA, ETANOL, DIESEL, GNV, ELETRICO ou HIBRIDO. */
    @ColumnInfo(name = "combustivel", defaultValue = "GASOLINA")
    val combustivel: String = "GASOLINA",

    @ColumnInfo(name = "preco_litro_centavos", defaultValue = "0")
    val precoLitroCentavos: Long = 0,

    @ColumnInfo(name = "consumo_x100", defaultValue = "0")
    val consumoX100: Long = 0,

    // ------------------------------------------------------------- rotina
    @ColumnInfo(name = "km_por_dia", defaultValue = "0")
    val kmPorDia: Long = 0,

    @ColumnInfo(name = "dias_por_semana", defaultValue = "6")
    val diasPorSemana: Int = 6,

    @ColumnInfo(name = "horas_por_dia", defaultValue = "10")
    val horasPorDia: Int = 10,

    @ColumnInfo(name = "meta_lucro_mensal_centavos", defaultValue = "0")
    val metaLucroMensalCentavos: Long = 0,

    /** Quando o motorista rodou o assistente pela última vez. 0 = nunca. */
    @ColumnInfo(name = "calculado_em", defaultValue = "0")
    val calculadoEm: Long = 0
) {
    companion object { const val ID_UNICO = 1 }
}
