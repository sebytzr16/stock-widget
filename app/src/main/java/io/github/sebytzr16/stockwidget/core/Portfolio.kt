package io.github.sebytzr16.stockwidget.core

import kotlin.math.max

/** One holding valued in its real currency (pence quotes become pounds). */
data class Position(val value: Double, val cost: Double, val dayChange: Double, val currency: String) {
    val gain: Double get() = value - cost
    val gainPercent: Double get() = if (cost > 0.0) gain / cost * 100.0 else 0.0
}

/** All holdings converted into the home currency. */
data class PortfolioSummary(
    val currency: String,
    val value: Double,
    val dayChange: Double,
    val cost: Double,
    /** Today's combined value, for the summary sparkline. */
    val series: List<Double>,
    /** False when an exchange rate was missing and some holdings were left out. */
    val complete: Boolean,
) {
    val dayChangePercent: Double
        get() = if (value - dayChange > 0.0) dayChange / (value - dayChange) * 100.0 else 0.0
    val gain: Double get() = value - cost
    val gainPercent: Double get() = if (cost > 0.0) gain / cost * 100.0 else 0.0
}

object Portfolio {

    fun position(h: Holding, q: Quote): Position {
        val unit = Currencies.unit(q.currency)
        return Position(
            value = h.shares * q.price * unit.factor,
            cost = h.shares * h.avgCost * unit.factor,
            dayChange = h.shares * q.change * unit.factor,
            currency = unit.major,
        )
    }

    /** Yahoo FX pair symbol whose price converts [from] into [to], e.g. "USDEUR=X". */
    fun fxSymbol(from: String, to: String): String = "$from$to=X"

    /** How many [to] one [from] buys, from the cached rates (either direction). */
    fun rate(fx: Map<String, Double>, from: String, to: String): Double? {
        if (from == to) return 1.0
        fx[from + to]?.takeIf { it > 0.0 }?.let { return it }
        return fx[to + from]?.takeIf { it > 0.0 }?.let { 1.0 / it }
    }

    /** Real currencies the holdings are in, other than [home]: the FX pairs a refresh needs. */
    fun neededCurrencies(holdings: Map<String, Holding>, quotes: Map<String, Quote>, home: String): Set<String> =
        holdings.keys.mapNotNull { quotes[it]?.currency }
            .map { Currencies.unit(it).major }
            .filter { it.isNotBlank() && it != home }
            .toSet()

    fun summarize(
        holdings: Map<String, Holding>,
        quotes: Map<String, Quote>,
        fx: Map<String, Double>,
        home: String,
    ): PortfolioSummary? {
        var value = 0.0
        var day = 0.0
        var cost = 0.0
        var complete = true
        val parts = ArrayList<Triple<Holding, Quote, Double>>()
        for ((symbol, h) in holdings) {
            val q = quotes[symbol]?.takeIf { it.hasPrice } ?: continue
            val pos = position(h, q)
            val r = rate(fx, pos.currency, home)
            if (r == null) {
                complete = false
                continue
            }
            value += pos.value * r
            day += pos.dayChange * r
            cost += pos.cost * r
            parts += Triple(h, q, r)
        }
        if (parts.isEmpty()) return null
        return PortfolioSummary(home, value, day, cost, intraday(parts), complete)
    }

    /** Each holding's intraday prices on a shared 5-minute grid, carried forward and summed. */
    private fun intraday(parts: List<Triple<Holding, Quote, Double>>): List<Double> {
        val slot = 300_000L
        val slots = parts.flatMap { (_, q, _) -> q.intraday.map { it.time / slot } }.distinct().sorted()
        if (slots.size < 2) return emptyList()
        val step = max(1, slots.size / 120)
        val grid = slots.filterIndexed { i, _ -> i % step == 0 || i == slots.lastIndex }
        return grid.map { s ->
            parts.sumOf { (h, q, r) ->
                val price = q.intraday.lastOrNull { it.time / slot <= s }?.price
                    ?: q.previousClose.takeIf { it > 0.0 } ?: q.price
                h.shares * price * Currencies.unit(q.currency).factor * r
            }
        }
    }
}
