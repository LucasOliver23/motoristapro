package com.motoristapro.service

/**
 * Dos textos de UMA tela, qual oferta vale — a regra que o leitor usa na rua.
 *
 * Saiu de dentro do [OfertaAccessibilityService] para o "Testar com print"
 * usar exatamente a mesma conta: um print que dá certo na tela de teste tem
 * que dar certo no cartão de verdade, e o contrário também.
 *
 * Kotlin puro: coberto por LeituraDePrintTest.
 */
object EscolhaOferta {

    const val PACOTE_IFOOD = "br.com.ifood.driver.app"

    /** A oferta escolhida e quantas ofertas havia na tela (listas mostram várias). */
    data class Escolha(val oferta: Oferta, val total: Int)

    /**
     * @param pacote app de onde vieram os textos; null = não se sabe (vale a regra geral).
     * @param listaInDrive corridas que o leitor guardou da lista do inDrive (traz o km da viagem).
     */
    fun escolher(
        textos: List<String>,
        pacote: String?,
        listaInDrive: List<OfertaInDrive.CorridaDaLista> = emptyList()
    ): Escolha? {
        // inDrive tem leitor proprio: a tela de lista mostra uma duzia de ofertas
        // de uma vez (o leitor comum faria um Frankenstein) e os botoes de
        // contraproposta tem valores MAIORES que o da corrida.
        if (pacote == OfertaInDrive.PACOTE) {
            val oferta = OfertaInDrive.extrair(textos, listaInDrive) ?: return null
            return Escolha(oferta, 1)
        }

        // Corrida de passageiro sempre mostra minutos; entrega (99 Entrega Food, iFood)
        // muitas vezes mostra só "Distância total X km" — aí aceitamos sem o tempo.
        val exigirTempo = pacote != PACOTE_IFOOD && !OfertaParser.pareceEntrega(textos)
        val ofertas = OfertaParser.extrairVarias(textos, exigirTempo = exigirTempo)
        val melhor = ofertas.maxByOrNull { it.reaisPorKm } ?: return null
        return Escolha(melhor, ofertas.size)
    }

    /** true quando a regra deste app aceita oferta sem o tempo (min) na tela. */
    fun aceitaSemTempo(textos: List<String>, pacote: String?): Boolean =
        pacote == PACOTE_IFOOD || OfertaParser.pareceEntrega(textos)
}
