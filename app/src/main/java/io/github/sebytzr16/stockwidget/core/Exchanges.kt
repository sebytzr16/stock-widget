package io.github.sebytzr16.stockwidget.core

import java.util.Locale

/**
 * Maps Yahoo Finance exchange codes (the short `exchangeName`, e.g. "NMS", "NYQ") to
 * friendly names. Falls back to a tidied `fullExchangeName`, then the raw code.
 */
object Exchanges {

    fun friendly(exchangeName: String?, fullExchangeName: String?): String {
        val code = exchangeName?.trim().orEmpty()
        CODE_MAP[code.uppercase(Locale.US)]?.let { return it }

        val full = fullExchangeName?.trim().orEmpty()
        if (full.isNotBlank()) return tidyFull(full)
        return code
    }

    private fun tidyFull(full: String): String = when {
        full.startsWith("Nasdaq", ignoreCase = true) -> "NASDAQ"
        full.equals("NYSEArca", ignoreCase = true) -> "NYSE Arca"
        full.equals("NYSEAmerican", ignoreCase = true) -> "NYSE American"
        else -> full
    }

    private val CODE_MAP = mapOf(
        // United States
        "NMS" to "NASDAQ", "NGM" to "NASDAQ", "NCM" to "NASDAQ", "NAS" to "NASDAQ",
        "NYQ" to "NYSE", "NYS" to "NYSE",
        "PCX" to "NYSE Arca", "ASE" to "NYSE American",
        "BATS" to "Cboe BZX", "BTS" to "Cboe BZX",
        "PNK" to "OTC", "OQB" to "OTCQB", "OQX" to "OTCQX",
        "SNP" to "S&P", "DJI" to "Dow Jones",
        "CCC" to "Crypto", "CCY" to "FX",
        // Canada
        "TOR" to "Toronto (TSX)", "VAN" to "TSX Venture", "CNQ" to "CSE", "NEO" to "NEO",
        // United Kingdom & Europe
        "LSE" to "London", "IOB" to "London (IOB)",
        "GER" to "XETRA", "FRA" to "Frankfurt", "STU" to "Stuttgart", "BER" to "Berlin",
        "MUN" to "Munich", "HAM" to "Hamburg", "DUS" to "Düsseldorf",
        "PAR" to "Paris", "AMS" to "Amsterdam", "BRU" to "Brussels", "LIS" to "Lisbon",
        "MCE" to "Madrid", "MIL" to "Milan", "VIE" to "Vienna",
        "EBS" to "SIX Swiss", "VTX" to "SIX Swiss",
        "STO" to "Stockholm", "HEL" to "Helsinki", "CPH" to "Copenhagen", "OSL" to "Oslo",
        "WSE" to "Warsaw", "IST" to "Istanbul",
        // Asia-Pacific
        "HKG" to "Hong Kong", "SHH" to "Shanghai", "SHZ" to "Shenzhen",
        "TYO" to "Tokyo", "JPX" to "Tokyo",
        "KSC" to "Korea (KOSPI)", "KOE" to "Korea (KOSDAQ)",
        "TAI" to "Taiwan", "TWO" to "Taipei (OTC)",
        "SES" to "Singapore", "KLS" to "Kuala Lumpur", "SET" to "Bangkok", "JKT" to "Jakarta",
        "BSE" to "Bombay (BSE)", "NSI" to "India (NSE)",
        "ASX" to "ASX", "NZE" to "New Zealand",
        // Middle East, Africa, Latin America
        "TLV" to "Tel Aviv", "SAU" to "Tadawul", "DFM" to "Dubai", "JNB" to "Johannesburg",
        "SAO" to "São Paulo (B3)", "BUE" to "Buenos Aires", "MEX" to "Mexico", "SGO" to "Santiago"
    )
}
