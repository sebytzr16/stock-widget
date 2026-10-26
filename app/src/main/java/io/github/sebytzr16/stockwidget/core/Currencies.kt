package io.github.sebytzr16.stockwidget.core

import java.util.Locale

/** Currency symbols and the minor-unit quirks of Yahoo quotes. */
object Currencies {

    /**
     * How a quote currency maps to a real currency. Yahoo prices some markets in minor
     * units: London in pence ("GBp"), Johannesburg in cents ("ZAc"), Tel Aviv in agorot
     * ("ILA"). Their values are 100× the major unit and display with a suffix.
     */
    data class Unit(val major: String, val factor: Double, val suffix: String?)

    fun unit(code: String): Unit = when (code) {
        "GBp", "GBX" -> Unit("GBP", 0.01, "p")
        "ZAc", "ZAC" -> Unit("ZAR", 0.01, "c")
        "ILA" -> Unit("ILS", 0.01, " ag.")
        else -> Unit(code.uppercase(Locale.ROOT), 1.0, null)
    }

    fun symbol(code: String): String? = SYMBOLS[code.uppercase(Locale.ROOT)]

    /** Currencies whose symbol goes after the amount, e.g. "1,234.50 kr". */
    val SUFFIXED = setOf("SEK", "NOK", "DKK", "PLN", "CZK", "HUF")

    /** Currencies without a minor unit in everyday use. */
    val ZERO_DECIMALS = setOf("JPY", "KRW", "CLP", "ISK", "HUF", "VND", "IDR")

    /** Home currencies offered in settings. */
    val HOME_CHOICES = listOf(
        "USD", "EUR", "GBP", "CHF", "JPY", "CAD", "AUD", "NZD", "SEK", "NOK", "DKK", "PLN",
        "CZK", "HUF", "INR", "CNY", "HKD", "SGD", "KRW", "BRL", "MXN", "ZAR", "TRY", "ILS",
    )

    private val SYMBOLS = mapOf(
        "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "CNY" to "CN¥",
        "HKD" to "HK$", "AUD" to "A$", "CAD" to "CA$", "NZD" to "NZ$", "SGD" to "S$",
        "INR" to "₹", "KRW" to "₩", "BRL" to "R$", "RUB" to "₽", "ZAR" to "R",
        "TRY" to "₺", "TWD" to "NT$", "THB" to "฿", "MXN" to "MX$", "ILS" to "₪",
        "PHP" to "₱", "VND" to "₫", "NGN" to "₦", "UAH" to "₴", "CHF" to "CHF",
        "SEK" to "kr", "NOK" to "kr", "DKK" to "kr", "PLN" to "zł", "CZK" to "Kč",
        "HUF" to "Ft",
    )
}
