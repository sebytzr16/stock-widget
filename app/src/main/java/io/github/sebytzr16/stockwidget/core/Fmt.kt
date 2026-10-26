package io.github.sebytzr16.stockwidget.core

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Formatting for prices, changes and totals. Digits and separators follow the phone's
 * locale; currency symbols stay short and consistent ("$227.48", "€702.40", "70.42p").
 */
object Fmt {

    const val MINUS = "−"

    /** Decimals for a price: Yahoo's hint when known, more for tiny values (crypto, penny stocks). */
    fun priceDecimals(value: Double, priceHint: Int = -1): Int {
        val a = abs(value)
        val adaptive = when {
            a == 0.0 || a >= 1.0 -> 2
            a >= 0.01 -> 4
            else -> min(10, ceil(-log10(a)).toInt() + 3)
        }
        return when {
            priceHint !in 0..10 -> adaptive
            a < 1.0 -> max(priceHint, adaptive)
            else -> priceHint
        }
    }

    /** The absolute value with exactly [decimals] decimals, in the locale's style. */
    fun number(value: Double, decimals: Int, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            roundingMode = RoundingMode.HALF_UP
        }.format(abs(value))

    /** A price in its quote currency: "$227.48", "€702.40", "70.42p", "1.0852". */
    fun price(
        value: Double,
        currency: String,
        priceHint: Int = -1,
        locale: Locale = Locale.getDefault(),
    ): String {
        val decimals = priceDecimals(value, priceHint)
        return withCurrency(number(value, decimals, locale), currency, isNegative(value, decimals))
    }

    /** A signed amount with the given decimals: "+$2.79", "−0.84p". */
    fun signedPrice(
        delta: Double,
        currency: String,
        decimals: Int,
        locale: Locale = Locale.getDefault(),
    ): String {
        val negative = isNegative(delta, decimals)
        val body = withCurrency(number(delta, decimals, locale), currency, negative)
        return if (negative) body else "+$body"
    }

    /** "+1.24%" / "−0.42%", or the bare "1.24%" when the direction is shown another way. */
    fun percent(value: Double, signed: Boolean = true, locale: Locale = Locale.getDefault()): String {
        val sign = when {
            !signed -> ""
            isNegative(value, 2) -> MINUS
            else -> "+"
        }
        return sign + number(value, 2, locale) + "%"
    }

    /** A total in a real currency (positions, portfolio): two decimals, none for JPY and the like. */
    fun money(
        value: Double,
        currency: String,
        signed: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): String {
        val code = Currencies.unit(currency).major
        val decimals = if (code in Currencies.ZERO_DECIMALS) 0 else 2
        val negative = isNegative(value, decimals)
        val body = withCurrency(number(value, decimals, locale), code, negative)
        return if (signed && !negative) "+$body" else body
    }

    /** Large counts such as volume: "48.2M", "1.1B", "950K". */
    fun compact(value: Long, locale: Locale = Locale.getDefault()): String {
        val a = abs(value.toDouble())
        return when {
            a >= 1e12 -> number(a / 1e12, 1, locale) + "T"
            a >= 1e9 -> number(a / 1e9, 1, locale) + "B"
            a >= 1e6 -> number(a / 1e6, 1, locale) + "M"
            a >= 1e4 -> number(a / 1e3, 1, locale) + "K"
            else -> number(a, 0, locale)
        }
    }

    /** A quantity without trailing zeros: "10", "0.05", "1,250.5". */
    fun quantity(value: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 8
        }.format(value)

    /** A number for an input field: no grouping, so [parse] reads it back unambiguously. */
    fun plain(value: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 8
        }.format(value)

    /**
     * Reads a number typed by the user, accepting either decimal separator: "1.5", "1,5",
     * "1,250.50", "1.250,50". A lone separator followed by exactly three digits counts as
     * grouping only when it is the locale's grouping character ("1,250" in English, "1.250"
     * in German). Returns null for anything that isn't a finite number.
     */
    fun parse(text: String, locale: Locale = Locale.getDefault()): Double? {
        val s = text.trim().filterNot { it == ' ' || it == ' ' || it == ' ' || it == '\'' }
        if (s.isEmpty() || '-' in s) return null
        val dots = s.count { it == '.' }
        val commas = s.count { it == ',' }
        val normalized = when {
            dots > 0 && commas > 0 -> {
                val decimal = if (s.lastIndexOf('.') > s.lastIndexOf(',')) '.' else ','
                val grouping = if (decimal == '.') ',' else '.'
                if (s.count { it == decimal } > 1) return null
                s.replace(grouping.toString(), "").replace(decimal, '.')
            }
            dots > 1 || commas > 1 -> s.replace(".", "").replace(",", "")
            dots == 1 || commas == 1 -> {
                val sep = if (dots == 1) '.' else ','
                val (int, frac) = s.split(sep)
                val grouping = java.text.DecimalFormatSymbols.getInstance(locale).groupingSeparator
                val isGrouping = sep == grouping && frac.length == 3 && int.isNotEmpty() && !int.startsWith("0")
                if (isGrouping) int + frac else "$int.$frac"
            }
            else -> s
        }
        if (!normalized.all { it.isDigit() || it == '.' } || normalized == ".") return null
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun isNegative(value: Double, decimals: Int): Boolean =
        value < 0 && abs(value) >= 0.5 * 10.0.pow(-decimals)

    private fun withCurrency(number: String, currency: String, negative: Boolean): String {
        val sign = if (negative) MINUS else ""
        if (currency.isBlank()) return sign + number
        val unit = Currencies.unit(currency)
        if (unit.suffix != null) return sign + number + unit.suffix
        val code = unit.major
        val symbol = Currencies.symbol(code) ?: return "$sign$number $code"
        return when {
            code in Currencies.SUFFIXED -> "$sign$number $symbol"
            symbol.all { it.isLetter() } -> "$sign$symbol $number"
            else -> "$sign$symbol$number"
        }
    }
}
