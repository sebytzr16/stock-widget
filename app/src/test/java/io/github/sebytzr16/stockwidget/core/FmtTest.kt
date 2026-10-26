package io.github.sebytzr16.stockwidget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FmtTest {

    private val us = Locale.US
    private val italy = Locale.ITALY

    @Test
    fun pricesUseShortCurrencySymbols() {
        assertEquals("$227.48", Fmt.price(227.48, "USD", 2, us))
        assertEquals("€702.40", Fmt.price(702.4, "EUR", 2, us))
        assertEquals("CHF 12.30", Fmt.price(12.3, "CHF", 2, us))
        assertEquals("1,234.50 kr", Fmt.price(1234.5, "SEK", 2, us))
        assertEquals("1.0852", Fmt.price(1.0852, "", 4, us))
    }

    @Test
    fun minorUnitQuotesKeepTheirUnit() {
        // London quotes are in pence: 70.42 GBp is 70.42p, not £70.42.
        assertEquals("70.42p", Fmt.price(70.42, "GBp", 2, us))
        assertEquals("15,000.00c", Fmt.price(15000.0, "ZAc", 2, us))
    }

    @Test
    fun subCentPricesKeepSignificantDigits() {
        assertEquals("$0.00001742", Fmt.price(0.00001742, "USD", -1, us))
        assertEquals("$0.5000", Fmt.price(0.5, "USD", -1, us))
        assertEquals("$67,210.50", Fmt.price(67210.5, "USD", -1, us))
    }

    @Test
    fun separatorsFollowTheLocale() {
        assertEquals("$1.234,56", Fmt.price(1234.56, "USD", 2, italy))
        assertEquals("+1,24%", Fmt.percent(1.2449, true, italy))
    }

    @Test
    fun signedChanges() {
        assertEquals("+$2.79", Fmt.signedPrice(2.79, "USD", 2, us))
        assertEquals("−$1.75", Fmt.signedPrice(-1.75, "USD", 2, us))
        assertEquals("+0.84p", Fmt.signedPrice(0.84, "GBp", 2, us))
        assertEquals("+$0.00", Fmt.signedPrice(-0.001, "USD", 2, us))
        assertEquals("−0.42%", Fmt.percent(-0.42, true, us))
        assertEquals("0.42%", Fmt.percent(-0.42, false, us))
        assertEquals("+0.00%", Fmt.percent(-0.001, true, us))
    }

    @Test
    fun moneyTotals() {
        assertEquals("£14.08", Fmt.money(14.084, "GBP", false, us))
        assertEquals("¥1,500", Fmt.money(1500.4, "JPY", false, us))
        assertEquals("+€16.65", Fmt.money(16.65, "EUR", true, us))
        assertEquals("−€9.40", Fmt.money(-9.4, "EUR", true, us))
    }

    @Test
    fun compactAndQuantity() {
        assertEquals("48.2M", Fmt.compact(48_200_000, us))
        assertEquals("1.1B", Fmt.compact(1_100_000_000, us))
        assertEquals("9,500", Fmt.compact(9_500, us))
        assertEquals("10", Fmt.quantity(10.0, us))
        assertEquals("0.05", Fmt.quantity(0.05, us))
    }

    @Test
    fun parsesEitherDecimalSeparator() {
        val germany = Locale.GERMANY
        assertEquals(1.5, Fmt.parse("1.5", us)!!, 1e-9)
        assertEquals(1.5, Fmt.parse("1,5", us)!!, 1e-9)
        assertEquals(1.5, Fmt.parse("1.5", germany)!!, 1e-9)
        assertEquals(1250.5, Fmt.parse("1,250.50", us)!!, 1e-9)
        assertEquals(1250.5, Fmt.parse("1.250,50", germany)!!, 1e-9)
        assertEquals(1250.0, Fmt.parse("1,250", us)!!, 1e-9)
        assertEquals(1250.0, Fmt.parse("1.250", germany)!!, 1e-9)
        assertEquals(1.25, Fmt.parse("1.250", us)!!, 1e-9)
        assertEquals(0.125, Fmt.parse("0,125", us)!!, 1e-9)
        assertEquals(1234567.0, Fmt.parse("1 234 567", italy)!!, 1e-9)
        assertEquals(0.00001742, Fmt.parse(Fmt.plain(0.00001742, italy), italy)!!, 1e-12)
        assertEquals(12345.678, Fmt.parse(Fmt.plain(12345.678, us), us)!!, 1e-9)
    }

    @Test
    fun rejectsWhatIsNotANumber() {
        for (bad in listOf("", " ", ".", "-3", "abc", "1.2.3,4,5", "1e5", "12a")) {
            assertEquals(bad, null, Fmt.parse(bad, us))
        }
    }
}
