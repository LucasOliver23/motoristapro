package com.motoristapro.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private val PT_BR = Locale("pt", "BR")

/** Aceita "45,90", "45.90", "R$ 1.234,56" -> centavos. null se inválido ou <= 0. */
fun String.paraCentavos(): Long? = paraEscala(100)

/** Igual a [paraCentavos], mas aceita zero (ex.: meta desligada). */
fun String.paraCentavosOuZero(): Long? = paraEscala(100, permitirZero = true)

/** "34,5" litros -> 34500 ml. */
fun String.litrosParaMl(): Long? = paraEscala(1000)

private fun String.paraEscala(fator: Int, permitirZero: Boolean = false): Long? = runCatching {
    val limpo = replace("R$", "").replace(" ", "").trim()
    val normalizado = if (limpo.contains(',')) limpo.replace(".", "").replace(',', '.') else limpo
    BigDecimal(normalizado).multiply(BigDecimal(fator)).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()?.takeIf { if (permitirZero) it >= 0 else it > 0 }

/** Centavos fracionados (ex.: custo/km 38.7) -> "R$ 0,39". */
fun Double.centavosEmReais(): String = String.format(PT_BR, "R$ %.2f", this / 100.0)

fun Double.formatarKm(): String = String.format(PT_BR, "%.1f km", this)

fun Long.formatarDuracao(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    return if (h > 0) "${h}h${"%02d".format(m)}" else "${m} min"
}

/** Segundos -> "02:15:09" (cronômetro). */
fun Long.formatarCronometro(): String {
    val s = coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** Reais (Double) -> "R$ 12,34". */
fun Double.emReaisDouble(): String =
    if (isFinite()) String.format(PT_BR, "R$ %.2f", this) else "—"

/** Centavos -> "R$ 1,2 mil" para eixos e rótulos curtos. */
fun Long.reaisCurto(): String {
    val r = this / 100.0
    return when {
        kotlin.math.abs(r) >= 1000 -> String.format(PT_BR, "R$ %.1f mil", r / 1000)
        else -> String.format(PT_BR, "R$ %.0f", r)
    }
}
