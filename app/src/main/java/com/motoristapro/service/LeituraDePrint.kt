package com.motoristapro.service

/** De qual app é o print que o motorista quer testar. */
enum class AppDoPrint(val rotulo: String, val pacote: String?) {
    UBER("Uber", "com.ubercab.driver"),
    NOVENTA_E_NOVE("99", "com.app99.driver"),
    IFOOD("iFood", EscolhaOferta.PACOTE_IFOOD),
    INDRIVE("inDrive", OfertaInDrive.PACOTE),
    /** Sem app: vale a regra geral (passageiro exige tempo; entrega não). */
    NAO_SEI("Não sei", null)
}

/**
 * "Testar com print": a imagem de uma oferta (galeria, WhatsApp) passa pela
 * mesma regra do leitor ([EscolhaOferta]) e o motorista vê o que sairia no
 * cartão — sem precisar de conta na Uber ou no iFood para testar.
 *
 * O print é lido duas vezes pelo OCR:
 *  1. só a parte de baixo, igual o leitor faz na rua (ele pula o topo, onde
 *     ficam o mapa e o nosso cartão);
 *  2. a imagem inteira, para o print que já veio recortado só no card.
 * Quando só a 2ª acha a oferta, a tela avisa: no celular, ao vivo, o leitor
 * talvez não leia aquele card.
 *
 * Kotlin puro: coberto por LeituraDePrintTest.
 */
object LeituraDePrint {

    enum class Parte(val rotulo: String) {
        BAIXO("parte de baixo do print (igual ao leitor)"),
        INTEIRO("print inteiro")
    }

    data class Resultado(
        val oferta: Oferta?,
        /** Ofertas na tela (lista da 99 mostra várias; vale a de melhor R$/km). */
        val total: Int,
        /** Qual leitura achou a oferta; null quando nenhuma achou. */
        val parte: Parte?,
        /** As linhas que valeram (as da leitura que achou, ou as do print inteiro). */
        val linhas: List<String>,
        /** Quando não achou: em uma frase, o que faltou. */
        val motivo: String?
    )

    fun interpretar(linhasBaixo: List<String>, linhasInteiro: List<String>, app: AppDoPrint): Resultado {
        EscolhaOferta.escolher(linhasBaixo, app.pacote)?.let {
            return Resultado(it.oferta, it.total, Parte.BAIXO, linhasBaixo, null)
        }
        EscolhaOferta.escolher(linhasInteiro, app.pacote)?.let {
            return Resultado(it.oferta, it.total, Parte.INTEIRO, linhasInteiro, null)
        }
        return Resultado(null, 0, null, linhasInteiro, porQueNaoLeu(linhasInteiro, app))
    }

    private val RE_TEM_VALOR = Regex("""R\$\s*\d|RS\s?\d+,\d{2}""")
    private val RE_TEM_DISTANCIA = Regex("""\d\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val RE_TEM_TEMPO = Regex("""\d\s*min""", RegexOption.IGNORE_CASE)

    /** O que faltou para virar oferta, na língua do motorista. */
    fun porQueNaoLeu(linhas: List<String>, app: AppDoPrint): String {
        if (linhas.isEmpty()) {
            return "Não achei texto nenhum na imagem. Confira se o print está nítido e mostra o card da oferta."
        }
        if (app == AppDoPrint.INDRIVE) return OfertaInDrive.porQueNaoLeu(linhas)
        val tudo = linhas.joinToString("\n")
        return when {
            !RE_TEM_VALOR.containsMatchIn(tudo) -> "Não achei o valor da corrida (R$)."
            !RE_TEM_DISTANCIA.containsMatchIn(tudo) -> "Achei o valor, mas não a distância (km)."
            !RE_TEM_TEMPO.containsMatchIn(tudo) && !EscolhaOferta.aceitaSemTempo(linhas, app.pacote) ->
                "Achei valor e km, mas não o tempo (min). Corrida de passageiro precisa do tempo. " +
                    "Se for entrega, escolha iFood ou confira se o card diz que é entrega."
            else -> "Achei valor, km e tempo, mas os números não fecham uma oferta " +
                "(zero ou grande demais). Copie os textos e mande para ajustar o leitor."
        }
    }

    // Mesma cara de endereço que o OfertaParser usa, mais CEP.
    private val RE_ENDERECO = Regex(
        """\b(R\.|Rua|Av\.?|Avenida|Al\.|Alameda|Travessa|Tv\.|Estrada|Est\.|Rodovia|Rod\.|Praça|Pça\.?|Largo|Viela|Servidão)\s+\S|\b\d{5}-?\d{3}\b""",
        RegexOption.IGNORE_CASE
    )

    // O que o OCR deixa de endereço quando o card quebra a linha: "R.104, 116"
    // (sem espaço), quadra e lote ("qd. 24", "lt. 07" — que o OCR lê "It. 07"),
    // setor e bairro ("St. Bueno", "Núcleo Anhanguera", "Vila Nova").
    private val RE_PEDACO_ENDERECO = Regex(
        """\bR\.\s*\d|\b(qd|quadra|[li]t|lote)\.?\s*\d|\bSt\.\s*\S|\b(setor|bairro|n[úu]cleo|jardim|jd\.|vila|residencial|parque|conjunto)\s+\S""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Troca os endereços (e pedaços deles: quadra, lote, setor, bairro) por
     * "Rua Exemplo N, 100" antes de o motorista copiar
     * os textos: o print vira teste no projeto e não pode levar a casa de
     * ninguém junto. A troca mantém a cara de endereço, então o leitor trata a
     * linha igual. Nome de passageiro não dá para achar com segurança — a
     * tela pede para conferir antes de mandar.
     */
    fun anonimizar(linhas: List<String>): List<String> {
        val doParser = OfertaParser.extrairEnderecos(linhas).toSet()
        val trocas = mutableMapOf<String, String>()
        return linhas.map { linha ->
            val l = linha.trim()
            val pareceEndereco = RE_ENDERECO.containsMatchIn(l) || RE_PEDACO_ENDERECO.containsMatchIn(l)
            if (l in doParser || (!l.contains("R$") && pareceEndereco)) {
                trocas.getOrPut(l) { "Rua Exemplo ${trocas.size + 1}, 100" }
            } else {
                linha
            }
        }
    }

    /** Texto para mandar pelo WhatsApp: vira teste automático no projeto. */
    fun textoParaEnviar(r: Resultado, app: AppDoPrint, aparelho: String): String = buildString {
        appendLine("MotoristaPro - teste com print")
        appendLine("App: ${app.rotulo}  Aparelho: $aparelho")
        val o = r.oferta
        if (o != null) {
            appendLine(
                "Leu: R$ %.2f • %.2f km • %d min (%s)".format(o.valor, o.km, o.minutos, r.parte?.rotulo ?: "")
            )
        } else {
            appendLine("Não leu: ${r.motivo ?: ""}")
        }
        appendLine("Textos (endereços trocados):")
        anonimizar(r.linhas).forEach { appendLine(it) }
    }
}
