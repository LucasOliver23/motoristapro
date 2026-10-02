package com.motoristapro.service

/**
 * Leitor das ofertas do inDrive.
 *
 * O inDrive é diferente da Uber e da 99 em tudo que importa aqui:
 *
 * 1. **A LISTA é a melhor fonte.** Em "Pedidos de viagem" cada card já traz as
 *    DUAS distâncias — a do ponto A (busca) e a do ponto B (viagem) — e é com
 *    elas que o próprio inDrive calcula o R$/km que exibe. Conferido numa tela
 *    com cinco ofertas: 4,8 + 23,6 km em R$ 23 dá R$ 0,81/km, e o inDrive mostra
 *    R$ 0,8/km. Só que a lista mostra uma dúzia de corridas de uma vez, então
 *    ela é LIDA e GUARDADA, sem cartão nenhum na tela.
 *
 * 2. **A tela de detalhe esconde a viagem.** Abrindo a corrida, o cartão traz só
 *    "~4,7 km" (a busca) e a etiqueta verde do mapa — a que tem o km da viagem —
 *    some conforme o zoom. Foi o que deixou o app anunciar R$ 2,50/km numa
 *    corrida de R$ 1,06/km. Por isso o detalhe pergunta à lista: "a corrida de
 *    R$ 23 tinha quantos km no total?".
 *
 * 3. **"Ofereça sua tarifa" tem valores MAIORES na tela.** Embaixo do botão de
 *    aceitar ficam as contrapropostas (R$ 26, R$ 28, R$ 29). O leitor comum pega
 *    o maior R$ da tela e cravaria R$ 29 numa corrida de R$ 23. Aqui o valor vem
 *    do botão "Aceitar por R$ 23".
 *
 * 4. **O "7 min." do card não é tempo de viagem**, é há quanto tempo o pedido
 *    chegou. Só conta o "N min" colado num "N km", que são as etiquetas do mapa.
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

    /** A lista tem esta aba embaixo; a tela de detalhe, não. */
    private val RE_LISTA = Regex("""pedidos\s+de\s+viagem""", RegexOption.IGNORE_CASE)

    /** "R$ 2,4/km", "R$ 1/km" — informação, nunca o valor da corrida. */
    private val RE_POR_KM = Regex("""R\$\s*\d+(?:[.,]\d+)?\s*/\s*km""", RegexOption.IGNORE_CASE)

    /** "R$23", "R$ 19" — o valor de um card da lista. */
    private val RE_VALOR = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*(?:[.,]\d{1,2})?)""")

    /** Entrega: muda o que esperar da tela (às vezes sem minutos). */
    private val RE_ENTREGA = Regex(
        """\bentregas?\b|entregador|porta\s+a\s+porta""",
        RegexOption.IGNORE_CASE
    )

    private const val KM_MAX = 500.0
    private const val MIN_MAX = 600

    /**
     * Uma corrida como a LISTA mostra: valor, km até o passageiro e km da viagem.
     * É a única fonte em que as duas distâncias aparecem sempre.
     */
    data class CorridaDaLista(
        val valor: Double,
        val kmBusca: Double,
        val kmViagem: Double,
        val enderecos: List<String>
    ) {
        val kmTotal: Double get() = kmBusca + kmViagem
    }

    /** A tela aberta é a lista de pedidos (e não o detalhe de uma corrida)? */
    fun ehLista(textos: List<String>): Boolean {
        val tudo = textos.joinToString("\n")
        return RE_LISTA.containsMatchIn(tudo) && !RE_ACEITAR.containsMatchIn(tudo)
    }

    /**
     * As corridas da lista, uma a uma.
     *
     * Cada card começa numa linha com o valor ("R$23") — a linha do "R$ 0,8/km"
     * não serve de fronteira. Dentro do card, a primeira distância é a do ponto A
     * e a segunda a do ponto B; card com menos de duas distâncias é descartado,
     * porque meia leitura aqui vira R$/km inflado lá na frente.
     */
    fun extrairDaLista(textos: List<String>): List<CorridaDaLista> {
        if (textos.isEmpty()) return emptyList()
        val inicios = textos.indices.filter { i ->
            RE_VALOR.containsMatchIn(textos[i]) && !RE_POR_KM.containsMatchIn(textos[i])
        }
        return inicios.mapIndexedNotNull { pos, ini ->
            val fim = inicios.getOrNull(pos + 1) ?: textos.size
            umCard(textos.subList(ini, fim))
        }
    }

    private fun umCard(linhas: List<String>): CorridaDaLista? {
        val tudo = linhas.joinToString("\n")
        val valor = RE_VALOR.find(linhas.first())
            ?.groupValues?.get(1)
            ?.let { OfertaParser.paraDouble(it) }
            ?.takeIf { it > 0.0 }
            ?: return null

        val distancias = RE_KM.findAll(tudo)
            .mapNotNull { OfertaParser.paraDouble(it.groupValues[2])?.takeIf { d -> d > 0.0 } }
            .toList()
        if (distancias.size < 2) return null

        val busca = distancias[0]
        val viagem = distancias[1]
        if (busca + viagem > KM_MAX) return null
        return CorridaDaLista(valor, busca, viagem, OfertaParser.extrairEnderecos(linhas))
    }

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

    /**
     * A oferta da tela de detalhe, completada pelo que a lista já tinha mostrado.
     *
     * @param lista a última leitura da lista de pedidos. A distância de lá é a
     *        boa: no detalhe, a etiqueta do mapa some conforme o zoom, e quando
     *        aparece traz uma rota diferente (23,7 km contra os 28,4 da lista,
     *        na mesma corrida de R$ 23 — o inDrive concorda com a lista).
     */
    fun extrair(textos: List<String>, lista: List<CorridaDaLista> = emptyList()): Oferta? {
        if (textos.isEmpty() || !ehTelaDeDetalhe(textos)) return null
        return try {
            val tudo = textos.joinToString("\n")
            val valor = RE_ACEITAR.find(tudo)
                ?.groupValues?.get(1)
                ?.let { OfertaParser.paraDouble(it) }
                ?: return null
            if (valor <= 0.0) return null

            val daTela = somarDistancias(tudo)
            val daLista = procurarNaLista(valor, tudo, lista)

            // A lista manda. Sem ela, vale o que a tela deu — e sem nenhum dos
            // dois não se mostra nada, em vez de chutar o km da viagem.
            val km = daLista ?: daTela ?: return null
            if (km <= 0.0 || km > KM_MAX || !km.isFinite()) return null

            // Os minutos só valem quando a PRÓPRIA tela trouxe a rota inteira.
            // Se o km veio da lista é porque a etiqueta verde não apareceu — e
            // ela leva o tempo da viagem junto. Contar só os 14 min da busca
            // daria R$ 98/hora numa corrida de R$ 28/hora.
            val minutos = if (daTela != null) somarMinutosDaRota(textos) else 0
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
     * Acha na lista a corrida que está aberta agora.
     *
     * Casa pelo VALOR, que é exato nos dois lados. Com duas corridas do mesmo
     * valor na tela, desempata pela distância de busca mais parecida ("~4,7 km"
     * no detalhe contra "4,8 km" na lista — arredondam diferente, então é o mais
     * próximo, não o igual).
     */
    private fun procurarNaLista(
        valor: Double,
        tudo: String,
        lista: List<CorridaDaLista>
    ): Double? {
        val mesmoValor = lista.filter { Math.abs(it.valor - valor) < 0.01 }
        if (mesmoValor.isEmpty()) return null
        if (mesmoValor.size == 1) return mesmoValor.first().kmTotal

        val buscaNaTela = RE_KM.findAll(tudo)
            .firstOrNull { it.groupValues[1] == "~" }
            ?.let { OfertaParser.paraDouble(it.groupValues[2]) }
            ?: return mesmoValor.first().kmTotal
        return mesmoValor.minByOrNull { Math.abs(it.kmBusca - buscaNaTela) }?.kmTotal
    }

    /**
     * Busca + viagem, contando cada distância uma vez. null = leitura incompleta.
     *
     * O "~" do cartão marca a distância de BUSCA ("~4,4 km"), que o mapa também
     * mostra na etiqueta azul. A etiqueta verde traz a viagem (6,0 km). Somar
     * tudo contaria a busca em dobro; somar só o que está no cartão daria apenas
     * a busca.
     *
     * Esse segundo caso aconteceu de verdade e é o motivo deste método poder
     * devolver null: numa corrida de R$ 11 com 4,4 km de busca e 6,0 km de
     * viagem, o app leu só os 4,4 e anunciou R$ 2,50/km numa corrida de
     * R$ 1,06/km — corrida ruim com cara de ótima, o pior erro possível aqui.
     *
     * Então: achou a busca mas não achou NENHUMA outra distância? A viagem não
     * foi lida, e o certo é não mostrar nada em vez de mostrar um número que
     * engana.
     */
    private fun somarDistancias(tudo: String): Double? {
        val achados = RE_KM.findAll(tudo)
            .mapNotNull { m ->
                OfertaParser.paraDouble(m.groupValues[2])
                    ?.takeIf { it > 0.0 }
                    ?.let { km -> km to (m.groupValues[1] == "~") }
            }
            .toList()
        if (achados.isEmpty()) return null

        val busca = achados.firstOrNull { it.second }?.first
            ?: return achados.map { it.first }.distinct().sum()   // tela sem "~": soma o que há

        val viagem = achados.map { it.first }.filter { it != busca }.distinct()
        if (viagem.isEmpty()) return null                         // só a busca: leitura incompleta
        return busca + viagem.sum()
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
