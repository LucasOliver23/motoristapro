package com.motoristapro.service

/**
 * Divisão protegida: devolve [padrao] quando o divisor é zero/negativo
 * ou quando o resultado não é um número finito (NaN / Infinity).
 */
internal fun dividirSeguro(dividendo: Double, divisor: Double, padrao: Double = 0.0): Double {
    if (divisor <= 0.0 || divisor.isNaN()) return padrao
    val r = dividendo / divisor
    return if (r.isFinite()) r else padrao
}

/**
 * Oferta extraída da tela. km e minutos já são a SOMA de busca + viagem.
 * Todos os cálculos são protegidos contra divisão por zero.
 */
data class Oferta(
    val valor: Double,        // R$
    val km: Double,           // km total (até o passageiro + viagem)
    val minutos: Int,         // minutos totais
    /** Endereços lidos no card (1º costuma ser o embarque). Usado no botão Street View. */
    val enderecos: List<String> = emptyList(),
    /** Entregas/paradas da rota (iFood: "Entrega 1", "Entrega 2"...). 0 = não informado. */
    val paradas: Int = 0,
    /** iFood: "Possibilidade de devolução: Sim" (pode ter que voltar ao restaurante). */
    val devolucao: Boolean = false,
    /** Nota do passageiro lida na tela (1,0 a 5,0). null = a tela não mostrou. */
    val nota: Double? = null
) {
    /** R$ por km. 0.0 se km == 0. */
    val reaisPorKm: Double get() = dividirSeguro(valor, km)

    /** R$ por hora. 0.0 se minutos == 0. */
    val reaisPorHora: Double get() = dividirSeguro(valor, minutos / 60.0)

    /** R$ por minuto. 0.0 se minutos == 0. */
    val reaisPorMinuto: Double get() = dividirSeguro(valor, minutos.toDouble())

    /** Lucro estimado = valor − km × custo/km (custo vindo da Configuração, em centavos). */
    fun lucroEstimado(custoKmCentavos: Long): Double = valor - km * (custoKmCentavos / 100.0)

    /** Lucro estimado como % do valor da oferta. 0 quando o valor é zero. */
    fun lucroPercentual(custoKmCentavos: Long): Double =
        dividirSeguro(lucroEstimado(custoKmCentavos) * 100.0, valor)

    /** Assinatura para não reprocessar/reexibir a mesma oferta a cada evento. */
    val assinatura: String get() = "%.2f|%.2f|%d".format(valor, km, minutos)
}

/**
 * Extrai Valor (R$), Distância (km) e Tempo (min) dos textos visíveis na tela.
 *
 * Regras:
 *  - Valor: o MAIOR "R$" encontrado (o "+R$ 3,00" da dinâmica é só uma parcela do total).
 *  - Distância: soma de todos os "X km" + "X m" (busca + viagem).
 *  - Tempo: soma de todos os "X min" e "X h Y min".
 *  - Leituras implausíveis são descartadas (retorna null).
 *
 * Kotlin puro, sem dependência de UI: coberto por teste unitário (OfertaParserTest).
 * Se a Uber/99 mudar o layout, veja no Logcat (tag "MotoristaPro") os textos lidos e ajuste as Regex.
 */
object OfertaParser {

    // R$ 12,50 | R$12,50 | R$ 1.234,56 | R$ 12 | R$ 12.50 (aparelho em inglês)
    // "RS 13,90": o OCR às vezes lê o cifrão como S (só aceito quando seguido de número com centavos).
    private val RE_VALOR = Regex("""(?:R\$\s*|RS\s?(?=\d+,\d{2}))(\d{1,3}(?:\.\d{3})+,\d{1,2}|\d+[.,]\d{1,2}|\d+)""")

    // 3,2 km | 3.2km | 12 km
    private val RE_KM = Regex("""(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)

    // 800 m (busca curta). O \b final impede casar com "min"; o lookbehind evita pegar "3,5 m".
    private val RE_METROS = Regex("""(?<![\d.,])(\d+)\s*m\b""")

    // Formato Uber/99: "4 min (528 m)" / "19 min (7,6 km)" — tempo e distância de cada trecho juntos.
    private val RE_TRECHO = Regex(
        """(?:(\d+)\s*h(?:oras?)?\s*(?:e\s*)?)?(\d+)\s*min\w*\s*\(\s*(\d+(?:[.,]\d+)?)\s*(km|m)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    // Formato da lista "Solicitações" da 99: "(18 min 6,8 km)" — tempo e distância no mesmo parêntese.
    private val RE_TRECHO_JUNTO = Regex(
        """\(\s*(?:(\d+)\s*h(?:oras?)?\s*)?(\d+)\s*min\w*\s+(\d+(?:[.,]\d+)?)\s*(km|m)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    // Linhas cujo R$ NÃO é o valor da oferta (ganhos do dia, tarifa base, taxa, bônus...).
    private val RE_IGNORAR_VALOR = Regex(
        """ganho|saldo|carteira|hoje|seman|meta|tarifa base|incl\.|inclu[íi]d|taxa|promo|b[oô]nus|gorjeta|/\s*km|/\s*h\b|/\s*min|aprox""",
        RegexOption.IGNORE_CASE
    )

    // 1 h 5 min | 1h05min | 1 hora e 5 min
    private val RE_H_MIN = Regex("""(\d+)\s*h(?:oras?)?\s*(?:e\s*)?(\d+)\s*min""", RegexOption.IGNORE_CASE)

    // 12 min | 12 mins | 12 minutos
    private val RE_MIN = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)

    // Linhas que parecem endereço: "Rua X, 123", "Av. Paulista", "Estrada do Mar"...
    private val RE_ENDERECO = Regex(
        """\b(R\.|Rua|Av\.?|Avenida|Al\.|Alameda|Travessa|Tv\.|Estrada|Est\.|Rodovia|Rod\.|Praça|Pça\.?|Largo|Viela|Servidão)\s+\S""",
        RegexOption.IGNORE_CASE
    )

    // iFood: "Entrega 1", "Entrega 2", "Coleta 1"
    private val RE_PARADA = Regex("""\b(Entrega|Coleta)\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE)

    // iFood: "Possibilidade de devolução" seguido de "Sim" (mesma linha ou na seguinte)
    private val RE_DEVOLUCAO = Regex("""devolu[cç][aã]o\s*:?\s*\n?\s*Sim\b""", RegexOption.IGNORE_CASE)

    // A linha precisa falar de nota: estrela ou a palavra "nota".
    private val RE_TEM_NOTA = Regex("""[★⭐✪✩]|\bnota\b""", RegexOption.IGNORE_CASE)

    // "★ 4,80", "4,80 ⭐", "Nota 4,8" — tudo na MESMA linha ([ \t] e não \s,
    // senão o "R$ 24,50" da linha de cima vira nota 4,50.
    private val RE_NOTA = Regex(
        """[★⭐✪✩][ \t]*(\d(?:[.,]\d{1,2})?)|(\d(?:[.,]\d{1,2})?)[ \t]*[★⭐✪✩]|nota[ \t]*:?[ \t]*(\d(?:[.,]\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Nota do passageiro, ou null. Linha a linha, e sempre tirando os "R$ ..." antes:
     * valor e nota moram perto na tela e têm a mesma cara (4,80).
     * Fora de 1,0–5,0 é descartado.
     */
    fun extrairNota(textos: List<String>): Double? {
        for (linha in textos) {
            if (!RE_TEM_NOTA.containsMatchIn(linha)) continue
            val semDinheiro = RE_VALOR.replace(linha, " ")
            val m = RE_NOTA.find(semDinheiro) ?: continue
            val bruto = m.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: continue
            val n = paraDouble(bruto) ?: continue
            if (n in 1.0..5.0) return n
        }
        return null
    }

    // Telas de ENTREGA (99 Entrega Food, iFood, Uber Flash): muitas não mostram minutos,
    // só o valor e a distância total. Nessas o tempo deixa de ser obrigatório.
    private val RE_ENTREGA = Regex(
        """entrega\s*food|dist[âa]ncia\s+total|tipo\s+de\s+pedido|\bcoleta\b|restaurante|\bflash\b|entrega\s*\(\d""",
        RegexOption.IGNORE_CASE
    )

    /** true quando a tela tem cara de entrega (comida/encomenda), onde o tempo pode não aparecer. */
    fun pareceEntrega(textos: List<String>): Boolean =
        RE_ENTREGA.containsMatchIn(textos.joinToString("\n"))

    // Limites de plausibilidade
    private const val VALOR_MAX = 2_000.0
    private const val KM_MAX = 500.0
    private const val MIN_MAX = 600

    /**
     * @param textos textos dos nós de acessibilidade, na ordem da tela.
     * @param exigirTempo false para apps que às vezes não mostram minutos (iFood): calcula só por km.
     * @return a oferta, ou null se a tela não contém uma oferta completa e válida.
     *         Nunca lança exceção: qualquer erro de parsing vira null.
     */
    fun extrair(textos: List<String>, exigirTempo: Boolean = true): Oferta? {
        if (textos.isEmpty()) return null
        return try {
            val tudo = textos.joinToString("\n")
            extrairOuNull(textos, tudo, exigirTempo)?.copy(
                enderecos = extrairEnderecos(textos),
                paradas = contarParadas(tudo),
                devolucao = RE_DEVOLUCAO.containsMatchIn(tudo),
                nota = extrairNota(textos)
            )
        } catch (e: Exception) {
            // Sem android.util.Log aqui: mantém o parser testável em JUnit puro.
            null
        }
    }

    private fun extrairOuNull(textos: List<String>, tudo: String, exigirTempo: Boolean): Oferta? {
        val valor = extrairValor(textos, tudo) ?: return null

        var km: Double
        var minutos = 0

        // 1ª opção: trechos "N min (X km)" (Uber/99). Repetições idênticas contam uma vez:
        // o mesmo texto costuma vir no nó e na descrição de acessibilidade do card.
        val trechos = (RE_TRECHO.findAll(tudo) + RE_TRECHO_JUNTO.findAll(tudo))
            .map { it.groupValues.drop(1).map { g -> g.lowercase() } }   // [h, min, dist, unidade]
            .distinct()
            .toList()
        if (trechos.isNotEmpty()) {
            km = 0.0
            for ((h, min, dist, unidade) in trechos) {
                minutos += (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
                val d = paraDouble(dist) ?: 0.0
                km += if (unidade == "km") d else d / 1000.0
            }
        } else {
            // 2ª opção: somar tudo que for km/m e min na tela (iFood e formatos soltos).
            val kmPorKm = RE_KM.findAll(tudo).mapNotNull { paraDouble(it.groupValues[1]) }.sum()
            val kmPorMetros = RE_METROS.findAll(tudo).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum() / 1000.0
            km = kmPorKm + kmPorMetros

            val semHoras = RE_H_MIN.replace(tudo) { m ->
                val h = m.groupValues[1].toIntOrNull() ?: 0
                val min = m.groupValues[2].toIntOrNull() ?: 0
                minutos += h * 60 + min
                " "
            }
            minutos += RE_MIN.findAll(semHoras).sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
        }

        // Plausibilidade — também garante que nenhum divisor dos cálculos é zero.
        if (!valor.isFinite() || valor <= 0.0 || valor > VALOR_MAX) return null
        if (!km.isFinite() || km <= 0.0 || km > KM_MAX) return null
        if (minutos > MIN_MAX || minutos < 0) return null
        if (exigirTempo && minutos == 0) return null

        return Oferta(valor, km, minutos)
    }

    /**
     * Telas em LISTA (ex.: "Solicitações" da 99) mostram várias corridas de uma vez.
     * Cada card começa numa linha com o valor em R$; as linhas seguintes pertencem a ele.
     *
     * Com um único valor na tela (pop-up de oferta), devolve o resultado de [extrair] —
     * o comportamento de sempre.
     */
    fun extrairVarias(textos: List<String>, exigirTempo: Boolean = true): List<Oferta> {
        if (textos.isEmpty()) return emptyList()
        val inicios = textos.indices.filter { i ->
            !RE_IGNORAR_VALOR.containsMatchIn(textos[i]) && RE_VALOR.containsMatchIn(textos[i])
        }
        if (inicios.size < 2) return listOfNotNull(extrair(textos, exigirTempo))

        return inicios.mapIndexedNotNull { pos, ini ->
            val fim = inicios.getOrNull(pos + 1) ?: textos.size
            extrair(textos.subList(ini, fim), exigirTempo)
        }
    }

    /**
     * Valor da oferta: o maior R$ entre as linhas que não são ganhos/tarifa base/taxa/bônus.
     * Se todas as linhas forem "ignoráveis", usa o maior R$ da tela.
     */
    private fun extrairValor(textos: List<String>, tudo: String): Double? {
        fun valores(s: String) = RE_VALOR.findAll(s).mapNotNull { paraDouble(it.groupValues[1]) }
        val principais = textos.asSequence()
            .filter { !RE_IGNORAR_VALOR.containsMatchIn(it) }
            .flatMap { valores(it) }
            .maxOrNull()
        return principais ?: valores(tudo).maxOrNull()
    }

    /** Número de entregas/coletas distintas ("Entrega 1" aparece no mapa e no card: conta uma vez). */
    private fun contarParadas(tudo: String): Int =
        RE_PARADA.findAll(tudo).map { it.groupValues[1].lowercase() + it.groupValues[2] }.toSet().size

    /** Linhas com cara de endereço, sem repetição, na ordem da tela (máx. 3). */
    fun extrairEnderecos(textos: List<String>): List<String> =
        textos.asSequence()
            .map { it.trim() }
            .filter { it.length in 6..140 && RE_ENDERECO.containsMatchIn(it) && !it.contains("R$") }
            .distinct()
            .take(3)
            .toList()

    /** "1.234,56" -> 1234.56 | "12,50" -> 12.5 | "3.2" -> 3.2 | lixo -> null */
    internal fun paraDouble(bruto: String): Double? {
        val normalizado = if (bruto.contains(',')) bruto.replace(".", "").replace(',', '.') else bruto
        return normalizado.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
