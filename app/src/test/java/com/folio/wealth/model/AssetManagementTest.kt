package com.folio.wealth.model

import com.folio.wealth.data.PortfolioCodec
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AssetManagementTest {
    private fun bd(s: String) = BigDecimal(s)
    private fun amount(expected: String, actual: BigDecimal) = assertEquals(0, bd(expected).compareTo(actual))
    private fun saving(id: String, currency: DisplayCurrency, value: String) = Asset(id, id, AssetKind.SAVINGS, currency, balance = bd(value))
    private fun stock() = Asset("stock", "股票", AssetKind.STOCK, DisplayCurrency.USD, purchases = listOf(Purchase("first", bd("10"), bd("40"), 1000)), currentPrice = bd("12"))

    @Test fun nativeBalancesStaySeparateWhileEquivalentTotalsCombine() {
        val assets = listOf(saving("cny", DisplayCurrency.CNY, "100"), saving("hkd", DisplayCurrency.HKD, "100"), saving("usd", DisplayCurrency.USD, "10"))
        amount("100", PortfolioMath.nativeTotal(assets, DisplayCurrency.CNY))
        amount("100", PortfolioMath.nativeTotal(assets, DisplayCurrency.HKD))
        amount("10", PortfolioMath.nativeTotal(assets, DisplayCurrency.USD))
        amount("287", PortfolioMath.totalHkd(assets))
    }

    @Test fun marketValueAndUnrealizedPnlUseCurrentShares() {
        val a = stock()
        amount("480", a.nativeValue)
        amount("400", a.holdingCost)
        amount("80", a.unrealizedPnl)
        amount("20", requireNotNull(a.pnlPercent))
        amount("40", a.currentShares)
        amount("3744", a.estimatedHkd(ExchangeRates()))
    }

    @Test fun zeroQuoteDoesNotErasePurchasedSharesOrCost() {
        val a = stock().copy(currentPrice = BigDecimal.ZERO)
        amount("0", a.nativeValue)
        amount("-400", a.unrealizedPnl)
        amount("40", a.currentShares)
        amount("-100", requireNotNull(a.pnlPercent))
    }

    @Test fun lossesAndFractionalSharesKeepDecimalPrecision() {
        val a = stock().copy(purchases = listOf(Purchase("fraction", bd("10.125"), bd("0.5"), 0)), currentPrice = bd("12.5"))
        amount("6.25", a.nativeValue)
        amount("5.0625", a.holdingCost)
        amount("1.1875", a.unrealizedPnl)
        amount("23.46", requireNotNull(a.pnlPercent))
        amount("-80", stock().copy(currentPrice = bd("8")).unrealizedPnl)
    }

    @Test fun editingFxChangesOnlyEquivalentAmounts() {
        val a = saving("cny", DisplayCurrency.CNY, "100")
        val rates = ExchangeRates(bd("2"), bd("10"))
        amount("200", PortfolioMath.totalHkd(listOf(a), rates))
        amount("100", a.nativeValue)
        amount("20", PortfolioMath.convertFromHkd(bd("200"), DisplayCurrency.USD, rates))
        assertThrows(IllegalArgumentException::class.java) { ExchangeRates(BigDecimal.ZERO, BigDecimal.ONE) }
    }

    @Test fun conversionRoundsOnceAfterAggregating() {
        val a = stock().copy(currentPrice = bd("0.005"), purchases = listOf(Purchase("one", BigDecimal.TEN, BigDecimal.ONE, 0)), currency = DisplayCurrency.HKD)
        amount("0.01", PortfolioMath.convertFromHkd(PortfolioMath.totalHkd(listOf(a, a.copy(id = "other"))), DisplayCurrency.HKD))
    }

    @Test fun validFormPreservesCurrencyAndManualQuote() {
        val input = AssetInput(" 我的股票 ", AssetKind.STOCK, DisplayCurrency.CNY, buyPrice = "10.5", boughtShares = "100", symbol = " 600000 ")
        assertTrue(input.errors().isEmpty())
        val a = input.toAsset("id", 1234)
        assertEquals("我的股票", a.title)
        assertEquals("600000", a.symbol)
        assertEquals(DisplayCurrency.CNY, a.currency)
        amount("1050", a.nativeValue)
        amount("10.5", a.currentPrice)
        assertEquals(1, a.purchases.size)
        assertEquals(1234L, a.updatedAt)
    }

    @Test fun invalidNumbersAreRejectedBeforeSaving() {
        listOf("", "-1", "NaN", "1e30", "1,000", "1.123456789").forEach { assertNull(parseDecimal(it)) }
        assertNull(parseDecimal("1.001", 2))
        assertFalse(AssetInput("", AssetKind.SAVINGS, DisplayCurrency.HKD, balance = "-1").errors().isEmpty())
        assertTrue(AssetInput("现金", AssetKind.SAVINGS, DisplayCurrency.HKD, balance = "0").errors().isEmpty())
        assertTrue(AssetInput("股", AssetKind.STOCK, DisplayCurrency.USD, buyPrice = "0", boughtShares = "0").errors().keys.containsAll(listOf("buyPrice", "boughtShares")))
    }

    @Test fun snapshotsAreDailyAndDoNotInventEarlierHistory() {
        val day = LocalDate.of(2026, 10, 22)
        val first = PortfolioData(assets = listOf(saving("a", DisplayCurrency.HKD, "100"))).recordToday(day)
        assertEquals(1, first.history(HistoryRange.YEAR, day).size)
        val updated = first.copy(assets = listOf(saving("a", DisplayCurrency.HKD, "200"))).recordToday(day)
        assertEquals(1, updated.snapshots.size)
        amount("200", updated.snapshots.single().valueHkd)
        val next = updated.recordToday(day.plusDays(1))
        assertEquals(2, next.snapshots.size)
        assertTrue(PortfolioData().recordToday(day).snapshots.isEmpty())
    }

    @Test fun historyFilteringExcludesFutureAndOldRecords() {
        val day = LocalDate.of(2026, 10, 22)
        val data = PortfolioData(snapshots = listOf(AssetSnapshot(day.minusDays(8), bd("1")), AssetSnapshot(day, bd("2")), AssetSnapshot(day.plusDays(1), bd("3"))))
        assertEquals(listOf(AssetSnapshot(day, bd("2"))), data.history(HistoryRange.WEEK, day))
    }

    @Test fun storageRoundTripsUnicodeAndExactDecimals() {
        val data = PortfolioData(assets = listOf(stock().copy(title = "我的美元股票", purchases = listOf(Purchase("precise", bd("10.000001"), bd("40"), 1000))), saving("cny", DisplayCurrency.CNY, "12345.67")), rates = ExchangeRates(bd("1.12345678"), bd("7.81")), displayCurrency = DisplayCurrency.CNY, darkMode = false).recordToday(LocalDate.of(2026, 10, 22))
        assertEquals(data, PortfolioCodec.decode(PortfolioCodec.encode(data)))
    }

    @Test fun damagedOrUnsupportedStorageFailsInsteadOfResetting() {
        val bytes = PortfolioCodec.encode(PortfolioData())
        assertThrows(Exception::class.java) { PortfolioCodec.decode(bytes.copyOf(12)) }
        val wrongVersion = bytes.clone().also { it[7] = 99 }
        assertThrows(IllegalArgumentException::class.java) { PortfolioCodec.decode(wrongVersion) }
        assertThrows(IllegalArgumentException::class.java) { PortfolioData(assets = listOf(stock(), stock())) }
    }

    @Test fun laterPurchasesAutomaticallyAccumulateSharesAndWeightedCost() {
        val first = stock()
        val second = first.recordPurchase(Purchase("second", bd("20"), bd("60"), 2000))
        amount("100", second.currentShares)
        amount("1600", second.holdingCost)
        amount("16", second.buyPrice)
        amount("2000", second.nativeValue)
        amount("400", second.unrealizedPnl)
        amount("25", requireNotNull(second.pnlPercent))
        assertEquals(2, second.purchases.size)
        assertEquals(1, first.purchases.size)
        assertThrows(IllegalArgumentException::class.java) { first.recordPurchase(first.purchases.single()) }
    }

    @Test fun quoteUpdatesNeverChangeBuyHistoryOrCost() {
        val first = stock().recordPurchase(Purchase("second", bd("20"), bd("60"), 2000))
        val repriced = first.copy(currentPrice = bd("14"))
        amount("-200", repriced.unrealizedPnl)
        assertEquals(first.purchases, repriced.purchases)
        assertEquals(first.holdingCost, repriced.holdingCost)
    }

    @Test fun pnlUsesExactLotCostInsteadOfRoundedAverage() {
        val a = stock().copy(purchases = listOf(Purchase("a", bd("1"), bd("1"), 0), Purchase("b", bd("2"), bd("2"), 0)), currentPrice = bd("2"))
        amount("5", a.holdingCost)
        amount("1", a.unrealizedPnl)
    }

    @Test fun historicalConversionUsesRecordedFxRatherThanRewritingThePast() {
        val day = LocalDate.of(2026, 10, 22)
        val first = PortfolioData(assets = listOf(saving("usd", DisplayCurrency.USD, "100"))).recordToday(day)
        val second = first.copy(rates = ExchangeRates(bd("1.09"), bd("8"))).recordToday(day.plusDays(1))
        val values = second.snapshots.map { PortfolioMath.convertFromHkd(it.valueHkd, DisplayCurrency.USD, it.rates) }
        values.forEach { amount("100", it) }
        amount("0", PortfolioMath.changeAmount(values))
        amount("20", second.snapshots.last().valueHkd.subtract(second.snapshots.first().valueHkd))
        assertEquals(second, PortfolioCodec.decode(PortfolioCodec.encode(second)))
    }

    @Test fun amountAndPercentageFormattingUseDecimalRounding() {
        assertEquals("HK$6.25", com.folio.wealth.ui.format.nativeMoney(bd("6.245"), DisplayCurrency.HKD))
        assertEquals("+1.01%", com.folio.wealth.ui.format.signedPercentage(bd("1.005")))
    }
}
