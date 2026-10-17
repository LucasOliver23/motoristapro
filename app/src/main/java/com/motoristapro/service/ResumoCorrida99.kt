package com.motoristapro.service

import java.time.LocalDateTime

/** A corrida como a 99 resume depois que ela termina: o valor que caiu de verdade. */
data class ResumoCorrida(
    val valorCentavos: Long,
    val metros: Long,
    val segundos: Long,
    /** "Moto", "99Pop"... */
    val tipo: String?,
    /** Data e hora que a 99 mostra no fim da tela; null se ela não estava na árvore. */
    val quando: LocalDateTime?,
    val origem: String?,
    val destino: String?
) {
    /** Identifica a mesma tela entre uma varredura e outra (não reabre o cartão a cada evento). */
    val assinatura: String get() = "$valorCentavos|$metros|$segundos|$quando"
}

/**
 * Leitor da tela "Detalhes da corrida" da 99, a que abre DEPOIS da corrida.
 *
 * Não é oferta: serve para registrar a corrida que o leitor perdeu (o cartão
 * da oferta não apareceu) com o valor final, que já vem com o pacote 0% de taxa.
 * Os rótulos chegam colados no valor ("Duração12m30s") ou em linhas separadas,
 * conforme a versão da 99 — por isso a leitura é sobre o texto todo junto.
 */
object ResumoCorrida99 {

    private val RE_VALOR = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})""")
    private val RE_DURACAO = Regex(
        """Dura[çc][ãa]o\s*(?:(\d+)\s*h)?\s*(?:(\d+)\s*m(?:in)?)?\s*(?:(\d+)\s*s)?""",
        RegexOption.IGNORE_CASE
    )
    private val RE_DISTANCIA = Regex("""Dist[âa]ncia\s*(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val RE_TIPO = Regex("""Tipo de corrida\s*([^\n]+)""", RegexOption.IGNORE_CASE)
    private val RE_DATA = Regex("""(\d{2})/(\d{2})/(\d{4})\s+(\d{2}):(\d{2})(?::(\d{2}))?""")

    /** É a tela de resumo? Tem que ter o título e o "Você ganhou". */
    fun ehResumo(textos: List<String>): Boolean =
        textos.any { it.contains("Detalhes da corrida", ignoreCase = true) } &&
            textos.any { it.contains("Você ganhou", ignoreCase = true) }

    fun ler(textos: List<String>): ResumoCorrida? {
        if (!ehResumo(textos)) return null
        val linhas = textos.map { it.trim() }.filter { it.isNotEmpty() }

        // O valor é o R$ do "Você ganhou" (na mesma linha ou na seguinte) — não o
        // "Total" pago pelo passageiro nem o que a 99 completou.
        val iGanhou = linhas.indexOfFirst { it.contains("Você ganhou", ignoreCase = true) }
        val valor = linhas.drop(iGanhou).take(3).firstNotNullOfOrNull { RE_VALOR.find(it) }
            ?.groupValues?.get(1)?.let(::centavos) ?: return null
        if (valor <= 0) return null

        val tudo = linhas.joinToString("\n")
        val segundos = RE_DURACAO.find(tudo)?.let { m ->
            val h = m.groupValues[1].toLongOrNull() ?: 0
            val min = m.groupValues[2].toLongOrNull() ?: 0
            val s = m.groupValues[3].toLongOrNull() ?: 0
            h * 3600 + min * 60 + s
        } ?: 0
        val metros = RE_DISTANCIA.find(tudo)?.let { m ->
            val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let 0L
            if (m.groupValues[2].equals("km", ignoreCase = true)) Math.round(n * 1000) else Math.round(n)
        } ?: 0
        // Sem km e sem tempo não dá para conferir nada: é melhor não oferecer.
        if (metros <= 0 && segundos <= 0) return null

        val tipo = RE_TIPO.find(tudo)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 30 }

        val iData = linhas.indexOfFirst { RE_DATA.containsMatchIn(it) }
        val quando = if (iData >= 0) RE_DATA.find(linhas[iData])?.let { m ->
            runCatching {
                LocalDateTime.of(
                    m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt(),
                    m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toIntOrNull() ?: 0
                )
            }.getOrNull()
        } else null

        // Logo depois da data vêm os dois endereços (embarque e destino).
        val enderecos = if (iData >= 0) linhas.drop(iData + 1)
            .filter { pareceEndereco(it) }
            .take(2)
            .map { it.trimEnd(',', ' ') }
        else emptyList()

        return ResumoCorrida(
            valorCentavos = valor,
            metros = metros,
            segundos = segundos,
            tipo = tipo,
            quando = quando,
            origem = enderecos.getOrNull(0),
            destino = enderecos.getOrNull(1)
        )
    }

    private fun pareceEndereco(s: String): Boolean =
        !s.contains("R$") && s.length in 6..120 && (s.contains(',') || s.contains(" - "))

    private fun centavos(s: String): Long {
        val (reais, cent) = s.replace(".", "").split(',')
        return reais.toLong() * 100 + cent.toLong()
    }
}
