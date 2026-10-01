package com.motoristapro.service

import android.content.Context
import java.text.Normalizer

/**
 * Palavras que marcam uma corrida como arriscada antes do aceite.
 *
 * Duas fontes:
 *  1. a lista do motorista — bairro, rua, região: quem conhece a cidade é ele;
 *  2. o detector de mercado/atacadão, ligado por padrão: embarque em mercado
 *     costuma dar espera longa e cancelamento.
 *
 * A comparação ignora acento e maiúscula, então "Jardim Botânico" cadastrado
 * casa com "JARDIM BOTANICO" lido na tela.
 */
class EnderecosDeRisco(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("enderecos_risco", Context.MODE_PRIVATE)

    /** Palavras do motorista, na ordem em que ele cadastrou. */
    var palavras: List<String>
        get() = prefs.getString(KEY_PALAVRAS, "")
            .orEmpty()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        set(v) = prefs.edit()
            .putString(KEY_PALAVRAS, v.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"))
            .apply()

    var ativo: Boolean
        get() = prefs.getBoolean(KEY_ATIVO, true)
        set(v) = prefs.edit().putBoolean(KEY_ATIVO, v).apply()

    /** Avisar quando o embarque/destino parece ser mercado ou atacadão. */
    var alertarMercados: Boolean
        get() = prefs.getBoolean(KEY_MERCADOS, true)
        set(v) = prefs.edit().putBoolean(KEY_MERCADOS, v).apply()

    fun adicionar(palavra: String) {
        val limpa = palavra.trim()
        if (limpa.isEmpty()) return
        if (palavras.any { igual(it, limpa) }) return
        palavras = palavras + limpa
    }

    fun remover(palavra: String) {
        palavras = palavras.filterNot { igual(it, palavra) }
    }

    /**
     * Motivo do alerta para esta oferta, ou null quando não há risco.
     * Devolve texto curto, pronto para o cartão (ex.: "Centro" ou "mercado/atacadão").
     */
    fun alerta(enderecos: List<String>): String? {
        if (!ativo || enderecos.isEmpty()) return null
        val texto = normalizar(enderecos.joinToString(" | "))

        palavras.firstOrNull { texto.contains(normalizar(it)) }?.let { return it }

        if (alertarMercados && MERCADOS.any { texto.contains(it) }) return "mercado/atacadão"
        return null
    }

    private fun igual(a: String, b: String) = normalizar(a) == normalizar(b)

    private companion object {
        const val KEY_PALAVRAS = "palavras"
        const val KEY_ATIVO = "ativo"
        const val KEY_MERCADOS = "mercados"

        /** Já normalizados (sem acento, minúsculos). */
        val MERCADOS = listOf(
            "supermercado", "mercado", "atacadao", "atacado", "hipermercado",
            "assai", "carrefour", "big bompreco", "bompreco", "extra hiper",
            "makro", "tenda atacado", "mart minas", "sam s club", "sams club"
        )

        /** Minúsculas e sem acento: "Jardim Botânico" -> "jardim botanico". */
        fun normalizar(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}
