package com.motoristapro.service

/**
 * Leitor das ofertas do inDrive.
 *
 * O inDrive é diferente da Uber e da 99 e precisa de regras próprias:
 *
 * 1. **Lista cheia de ofertas.** A tela "Pedidos de viagem" mostra dez, quinze
 *    corridas de uma vez. Ler essa tela daria um número Frankenstein, com o valor
 *    de uma e o km de outra. Por isso este leitor só age na tela de DETALHE —
 *    aquela em que aparecem "Aceitar por R$ X" e "Ofereça sua tarifa", que é
 *    quando o motorista está de fato decidindo uma corrida.
 *
 * 2. **"Ofereça sua tarifa" tem valores maiores na tela.** Embaixo do botão de
 *    aceitar ficam sugestões de contraproposta (R$ 7, R$ 8, R$ 9). O leitor comum
 *    pega o MAIOR R$ da tela e cravaria R$ 9 numa corrida de R$ 6. Aqui o valor
 *    vem do próprio botão "Aceitar por R$ 6" — não tem como errar.
 *
 * 3. **A distância de busca aparece duas vezes.** No mapa ("1,0 km") e no cartão
 *    ("~1,0 km"). Somar tudo contaria a busca em dobro. Aqui as distâncias
 *    repetidas contam uma vez só.
 *
 * 4. **O tempo do passageiro se mistura com o da rota.** Ao lado da nota aparece
 *    um "2 min." que é há quanto tempo o pedido chegou, não trecho de viagem.
 *    Só conta o "N min" que vem colado num "N km" — que são as etiquetas do mapa.
 *
 * Kotlin puro: coberto por teste unitário (OfertaInDriveTest).
 */
object OfertaInDrive {

    const val PACOTE = "sinet.startup.inDriver"

    /** "Aceitar por R$ 6" / "Aceitar por R$ 16,50" — o valor real da corrida. */
    private val RE_ACEITAR = Regex(
        """aceitar\s+por\s+R\$\s*(\d{1,3}(?:\.\d{3})*(?:[.,]\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /** A outra marca da tela de detalhe, caso o botão venha sem texto acessível. */
    private val RE_OFERECA = Regex("""ofere[çc]a\s+sua\s+tarifa""", RegexOption.IGNORE_CASE)

    /** Distância, guardando se veio com o "~" do cartão (que é a busca repetida). */
    private val RE_KM = Regex("""(~?)\s*(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)

    private val RE_MIN = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)

    /** Entrega: muda o que esperar da tela (às vezes sem minutos). */
    private val RE_ENTREGA = Regex(
        """\bentregas?\b|entregador|porta\s+a\s+porta""",
        RegexOption.IGNORE_CASE
    )

    private const val KM_MAX = 500.0
    private const val MIN_MAX = 600

    /**
     * A tela aberta é a de decidir uma corrida?
     *
     * É o portão que o motorista pediu: na lista o app fica quieto; abriu a
     * corrida para ver os km de busca e de viagem, aí sim aparece o cartão.
     */
    fun ehTelaDeDetalhe(textos: List<String>): Boolean {
        val tudo = textos.joinToString("\n")
        return RE_ACEITAR.containsMatchIn(tudo) || RE_OFERECA.containsMatchIn(tudo)
    }

    fun pareceEntrega(textos: List<String>): Boolean =
        RE_ENTREGA.containsMatchIn(textos.joinToString("\n"))

    /** A oferta da tela de detalhe, ou null se não for essa tela ou faltar número. */
    fun extrair(textos: List<String>): Oferta? {
        if (textos.isEmpty() || !ehTelaDeDetalhe(textos)) return null
        return try {
            val tudo = textos.joinToString("\n")
            val valor = RE_ACEITAR.find(tudo)
                ?.groupValues?.get(1)
                ?.let { OfertaParser.paraDouble(it) }
                ?: return null
            if (valor <= 0.0) return null

            val km = somarDistancias(tudo)
            if (km <= 0.0 || km > KM_MAX || !km.isFinite()) return null

            val minutos = somarMinutosDaRota(textos)
            if (minutos < 0 || minutos > MIN_MAX) return null

            Oferta(
                valor = valor,
                km = km,
                minutos = minutos,
                enderecos = OfertaParser.extrairEnderecos(textos),
                nota = OfertaParser.extrairNota(textos)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Busca + viagem, contando cada distância uma vez.
     *
     * O "~1,0 km" do cartão é a mesma busca que o mapa já mostrou; somar os dois
     * daria 4,5 km numa corrida de 3,5 km — e km a mais faz a corrida parecer
     * PIOR do que é, o que é o erro menos perigoso, mas erro.
     */
    private fun somarDistancias(tudo: String): Double {
        val valores = RE_KM.findAll(tudo)
            .mapNotNull { OfertaParser.paraDouble(it.groupValues[2]) }
            .filter { it > 0.0 }
            .toList()
        if (valores.isEmpty()) return 0.0
        // distinct() resolve a repetição da busca; com um valor só, ele é o total.
        return valores.distinct().sum()
    }

    /**
     * Minutos dos trechos do mapa.
     *
     * Só conta o "N min" que esteja na mesma linha de um "N km" ou na linha
     * imediatamente seguinte — que é como as etiquetas do mapa aparecem. O
     * "2 min." ao lado da nota do passageiro não tem km por perto e fica de fora.
     */
    private fun somarMinutosDaRota(textos: List<String>): Int {
        var total = 0
        textos.forEachIndexed { i, linha ->
            val min = RE_MIN.find(linha)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEachIndexed
            val temKmJunto = RE_KM.containsMatchIn(linha) ||
                (textos.getOrNull(i + 1)?.let { RE_KM.containsMatchIn(it) } == true)
            if (temKmJunto) total += min
        }
        return total
    }
}
