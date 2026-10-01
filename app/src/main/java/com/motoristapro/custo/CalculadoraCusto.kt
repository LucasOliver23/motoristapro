package com.motoristapro.custo

import kotlin.math.roundToLong

/** Como o motorista conseguiu o veículo — muda quais custos entram na conta. */
enum class FormaAquisicao(val rotulo: String) {
    QUITADO("Quitado"),
    FINANCIADO("Financiado"),
    ALUGADO("Alugado")
}

enum class TipoVeiculo(val rotulo: String) { CARRO("Carro"), MOTO("Moto") }

/** O que o motorista roda. Muda o que o app espera de km/dia e de ganho por corrida. */
enum class TipoTrabalho(val rotulo: String) {
    PASSAGEIROS("Passageiros"),
    ENTREGAS("Entregas"),
    AMBOS("Ambos")
}

enum class TipoCombustivel(val rotulo: String, val unidade: String) {
    GASOLINA("Gasolina", "km/L"),
    ETANOL("Etanol", "km/L"),
    DIESEL("Diesel", "km/L"),
    GNV("GNV", "km/m³"),
    ELETRICO("Elétrico", "km/kWh"),
    HIBRIDO("Híbrido", "km/L")
}

/**
 * Tudo que o motorista informa no assistente de custo.
 *
 * Dinheiro em CENTAVOS, percentuais em CENTÉSIMOS (4% = 400), consumo em
 * centésimos (12,5 km/L = 1250) — o mesmo padrão do resto do app, para não
 * ter número quebrado guardado em lugar nenhum.
 */
data class DadosCusto(
    val forma: FormaAquisicao = FormaAquisicao.QUITADO,
    val tipoVeiculo: TipoVeiculo = TipoVeiculo.MOTO,
    val tipoTrabalho: TipoTrabalho = TipoTrabalho.PASSAGEIROS,
    /** Como o motorista chama o veículo: "Fan 160 2021". */
    val veiculoNome: String = "",

    // ---- fixos ----
    val valorVeiculoCentavos: Long = 0,
    val parcelaMensalCentavos: Long = 0,     // financiado
    val aluguelMensalCentavos: Long = 0,     // alugado
    val seguroMensalCentavos: Long = 0,
    val ipvaPercentX100: Long = 0,           // ao ano, sobre o valor do veículo
    /** true = o motorista digitou o IPVA em reais por ano, não em %. */
    val ipvaEmReais: Boolean = false,
    val ipvaAnualCentavos: Long = 0,         // usado quando ipvaEmReais
    val desvalorizacaoAnualX100: Long = 0,   // ao ano, sobre o valor do veículo
    val outrosMensaisCentavos: Long = 0,

    // ---- manutenção e desgaste ----
    val revisaoCentavos: Long = 0,
    val intervaloRevisaoKm: Long = 0,
    val trocaOleoCentavos: Long = 0,
    val intervaloOleoKm: Long = 0,
    val jogoPneusCentavos: Long = 0,
    val duracaoPneusKm: Long = 0,
    val outrosDesgasteCentavos: Long = 0,
    val outrosDesgasteKm: Long = 0,

    // ---- combustível ----
    val combustivel: TipoCombustivel = TipoCombustivel.GASOLINA,
    val precoLitroCentavos: Long = 0,
    val consumoX100: Long = 0,

    // ---- rotina e meta ----
    val kmPorDia: Long = 0,
    val diasPorSemana: Int = 6,
    val horasPorDia: Int = 10,
    val metaLucroMensalCentavos: Long = 0
) {
    /** 6 dias/semana ≈ 26 dias/mês (6 × 4,33). */
    val diasPorMes: Double get() = diasPorSemana * SEMANAS_NO_MES

    val kmPorMes: Double get() = kmPorDia * diasPorMes

    /**
     * IPVA do ano em centavos, venha ele como % do valor do veículo ou digitado
     * em reais. Uma fonte só para a tela e para o cálculo não divergirem.
     */
    val ipvaAnualEmCentavos: Double
        get() = if (ipvaEmReais) ipvaAnualCentavos.toDouble()
        else valorVeiculoCentavos * (ipvaPercentX100 / 10_000.0)

    val ipvaMensalEmCentavos: Double get() = ipvaAnualEmCentavos / 12.0

    /**
     * A meta do dia e a da semana saem da MENSAL — o motorista informa uma só.
     * Antes as três eram digitadas à mão e viviam se contradizendo (R$ 200/dia
     * com 26 dias dá R$ 5.200, não os R$ 5.000 que estavam na mensal).
     */
    val metaLucroDiarioCentavos: Long
        get() = if (diasPorMes > 0) Math.round(metaLucroMensalCentavos / diasPorMes) else 0

    val metaLucroSemanalCentavos: Long
        get() = Math.round(metaLucroMensalCentavos / SEMANAS_NO_MES)

    /**
     * Os custos fixos que viram linha na lista do Início (rateada por dia
     * trabalhado). Só os de valor fixo no mês — combustível e manutenção
     * dependem do km rodado e entram no R$/km, não aqui.
     */
    fun custosFixosDoMes(): List<Pair<String, Long>> = buildList {
        when (forma) {
            FormaAquisicao.FINANCIADO -> add("Parcela do financiamento" to parcelaMensalCentavos)
            FormaAquisicao.ALUGADO -> add("Aluguel do veículo" to aluguelMensalCentavos)
            FormaAquisicao.QUITADO -> Unit
        }
        if (forma != FormaAquisicao.ALUGADO) {
            add("IPVA" to Math.round(ipvaMensalEmCentavos))
            add("Depreciação do veículo" to
                Math.round(valorVeiculoCentavos * (desvalorizacaoAnualX100 / 10_000.0) / 12.0))
        }
        add("Seguro" to seguroMensalCentavos)
        add("Outros custos fixos" to outrosMensaisCentavos)
    }.filter { it.second > 0 }

    private companion object {
        const val SEMANAS_NO_MES = 4.33
    }
}

/** Uma linha da composição do custo. */
data class ItemCusto(
    val nome: String,
    val mensalCentavos: Long,
    val porKmCentavos: Long,
    /** Fatia do custo mensal total, em % (0–100). */
    val percentual: Int
)

/** Receita, custo e lucro projetados para um período. */
data class Projecao(
    val rotulo: String,
    val km: Long,
    val receitaCentavos: Long,
    val custoCentavos: Long
) {
    val lucroCentavos: Long get() = receitaCentavos - custoCentavos
}

/** Aviso de valor que parece digitado errado. Nunca bloqueia — só pergunta. */
data class Aviso(val campo: String, val texto: String)

data class ResultadoCusto(
    val custoPorKmCentavos: Long,
    val custoMensalCentavos: Long,
    val kmPorMes: Long,
    val itens: List<ItemCusto>,
    /** Quanto a corrida precisa pagar por km para bater a meta: custo + lucro desejado. */
    val tarifaMinimaPorKmCentavos: Long,
    val projecoes: List<Projecao>,
    val avisos: List<Aviso>
) {
    val valido: Boolean get() = custoPorKmCentavos > 0 && kmPorMes > 0
}

/**
 * Transforma os dados do veículo no custo real por km e na tarifa mínima.
 *
 * Kotlin puro, sem Android: coberto por teste unitário (CalculadoraCustoTest).
 * Toda divisão é protegida — campo em branco vira zero, nunca NaN nem crash.
 */
object CalculadoraCusto {

    fun calcular(d: DadosCusto): ResultadoCusto {
        val kmMes = d.kmPorMes
        if (kmMes <= 0.0) {
            return ResultadoCusto(0, 0, 0, emptyList(), 0, emptyList(), avisos(d))
        }

        val itens = mutableListOf<Pair<String, Double>>()   // nome -> custo mensal em centavos

        // ---------- combustível ----------
        // consumoX100 = km por litro × 100. Elétrico e GNV usam a mesma conta,
        // trocando litro por kWh ou m³ — a matemática é idêntica.
        val consumo = d.consumoX100 / 100.0
        if (consumo > 0 && d.precoLitroCentavos > 0) {
            itens += "Combustível" to (kmMes / consumo) * d.precoLitroCentavos
        }

        // ---------- desgaste (vira custo mensal via km rodado) ----------
        var desgastePorKm = 0.0
        desgastePorKm += porKm(d.revisaoCentavos, d.intervaloRevisaoKm)
        desgastePorKm += porKm(d.trocaOleoCentavos, d.intervaloOleoKm)
        desgastePorKm += porKm(d.jogoPneusCentavos, d.duracaoPneusKm)
        desgastePorKm += porKm(d.outrosDesgasteCentavos, d.outrosDesgasteKm)
        if (desgastePorKm > 0) itens += "Manutenção" to desgastePorKm * kmMes

        // ---------- fixos ----------
        when (d.forma) {
            FormaAquisicao.FINANCIADO -> if (d.parcelaMensalCentavos > 0) {
                itens += "Parcela" to d.parcelaMensalCentavos.toDouble()
            }
            FormaAquisicao.ALUGADO -> if (d.aluguelMensalCentavos > 0) {
                itens += "Aluguel" to d.aluguelMensalCentavos.toDouble()
            }
            FormaAquisicao.QUITADO -> Unit
        }

        // Veículo alugado não desvaloriza no seu bolso, e o IPVA é de quem aluga.
        if (d.forma != FormaAquisicao.ALUGADO) {
            if (d.valorVeiculoCentavos > 0) {
                val depreciacao = d.valorVeiculoCentavos * (d.desvalorizacaoAnualX100 / 10_000.0) / 12.0
                if (depreciacao > 0) itens += "Depreciação" to depreciacao
            }
            // Em reais o IPVA vale mesmo sem a FIPE preenchida; em % ele depende dela.
            val ipva = d.ipvaMensalEmCentavos
            if (ipva > 0) itens += "IPVA" to ipva
        }

        if (d.seguroMensalCentavos > 0) itens += "Seguro" to d.seguroMensalCentavos.toDouble()
        if (d.outrosMensaisCentavos > 0) itens += "Outros" to d.outrosMensaisCentavos.toDouble()

        val mensalTotal = itens.sumOf { it.second }
        val custoKm = mensalTotal / kmMes

        // Maior fatia primeiro: é a que o motorista precisa olhar.
        val linhas = itens
            .sortedByDescending { it.second }
            .map { (nome, mensal) ->
                ItemCusto(
                    nome = nome,
                    mensalCentavos = mensal.roundToLong(),
                    porKmCentavos = (mensal / kmMes).roundToLong(),
                    percentual = if (mensalTotal > 0) ((mensal / mensalTotal) * 100).roundToInt0() else 0
                )
            }

        // Tarifa mínima: cobre o custo e ainda entrega a meta de lucro do mês.
        val tarifaMinima = (mensalTotal + d.metaLucroMensalCentavos) / kmMes

        return ResultadoCusto(
            custoPorKmCentavos = custoKm.roundToLong(),
            custoMensalCentavos = mensalTotal.roundToLong(),
            kmPorMes = kmMes.roundToLong(),
            itens = linhas,
            tarifaMinimaPorKmCentavos = tarifaMinima.roundToLong(),
            projecoes = projetar(d, custoKm, tarifaMinima),
            avisos = avisos(d)
        )
    }

    private fun projetar(d: DadosCusto, custoKm: Double, tarifaKm: Double): List<Projecao> {
        fun p(rotulo: String, km: Double) = Projecao(
            rotulo = rotulo,
            km = km.roundToLong(),
            receitaCentavos = (km * tarifaKm).roundToLong(),
            custoCentavos = (km * custoKm).roundToLong()
        )
        val kmDia = d.kmPorDia.toDouble()
        val kmMes = d.kmPorMes
        return listOf(
            p("Dia", kmDia),
            p("Semana", kmDia * d.diasPorSemana),
            p("Mês", kmMes),
            p("Ano", kmMes * 12)
        )
    }

    /** Custo de um item que dura N km, convertido para centavos por km. */
    private fun porKm(valorCentavos: Long, duracaoKm: Long): Double =
        if (valorCentavos > 0 && duracaoKm > 0) valorCentavos.toDouble() / duracaoKm else 0.0

    private fun Double.roundToInt0(): Int = if (isFinite()) Math.round(this).toInt() else 0

    /**
     * Valores que quase certamente são erro de digitação (vírgula no lugar errado,
     * zero a menos). Não impedem o cálculo — o app pergunta e deixa seguir.
     */
    private fun avisos(d: DadosCusto): List<Aviso> {
        val lista = mutableListOf<Aviso>()

        fun faixa(campo: String, valor: Long, min: Long, max: Long, comoLer: String) {
            if (valor > 0 && (valor < min || valor > max)) {
                lista += Aviso(campo, "$campo fora do comum (normal: $comoLer).")
            }
        }

        faixa("Intervalo do óleo", d.intervaloOleoKm, 1_000, 30_000, "1 mil a 30 mil km")
        faixa("Duração dos pneus", d.duracaoPneusKm, 3_000, 90_000, "3 mil a 90 mil km")
        faixa("Intervalo da revisão", d.intervaloRevisaoKm, 1_000, 40_000, "1 mil a 40 mil km")
        faixa("Km por dia", d.kmPorDia, 10, 800, "10 a 800 km")

        val consumoMax = if (d.tipoVeiculo == TipoVeiculo.MOTO) 6000L else 2500L
        if (d.consumoX100 > 0 && (d.consumoX100 < 150 || d.consumoX100 > consumoMax)) {
            lista += Aviso(
                "Consumo médio",
                "Consumo fora do comum para ${d.tipoVeiculo.rotulo.lowercase()} " +
                    "(normal: 1,5 a ${consumoMax / 100} ${d.combustivel.unidade})."
            )
        }
        if (d.precoLitroCentavos > 0 && (d.precoLitroCentavos < 100 || d.precoLitroCentavos > 2_000)) {
            lista += Aviso("Preço por litro", "Preço fora do comum (normal: R$ 1,00 a R$ 20,00).")
        }
        if (d.valorVeiculoCentavos in 1..99_999) {
            lista += Aviso("Valor do veículo", "Valor abaixo de R$ 1.000 — confira se não faltou um zero.")
        }
        if (d.horasPorDia !in 1..18) {
            lista += Aviso("Horas por dia", "Horas por dia fora do comum (normal: 1 a 18).")
        }
        // IPVA em % passou a ser livre (antes era cortado em silêncio no 100%),
        // então aqui é onde o motorista é avisado de que digitou reais no campo de %.
        if (!d.ipvaEmReais && d.ipvaPercentX100 > 1_000) {
            lista += Aviso(
                "IPVA",
                "IPVA de ${d.ipvaPercentX100 / 100}% ao ano é fora do comum (normal: 2% a 4%). " +
                    "Se você quis dizer reais, troque o campo para R$."
            )
        }
        return lista
    }
}
