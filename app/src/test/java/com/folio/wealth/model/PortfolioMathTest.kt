package com.folio.wealth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PortfolioMathTest {
    private val demo = SamplePortfolio.assets
    private fun assertAmount(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test fun totalsMatchAllCategories() {
        assertAmount("175309", PortfolioMath.totalHkd(demo))
        assertAmount("87750", PortfolioMath.byKindHkd(demo, AssetKind.SAVINGS))
        assertAmount("63906", PortfolioMath.byKindHkd(demo, AssetKind.STOCK))
        assertAmount("23653", PortfolioMath.byKindHkd(demo, AssetKind.FUND))
    }

    @Test fun currencyConversionUsesFixedDemoRatesAndRounding() {
        assertEquals(BigDecimal("100.00"), PortfolioMath.convertFromHkd(BigDecimal("780"), DisplayCurrency.USD))
        assertEquals(BigDecimal("100.00"), PortfolioMath.convertFromHkd(BigDecimal("109"), DisplayCurrency.CNY))
        assertEquals(BigDecimal("175309.00"), PortfolioMath.convertFromHkd(BigDecimal("175309"), DisplayCurrency.HKD))
        assertEquals(BigDecimal("22475.51"), PortfolioMath.convertFromHkd(BigDecimal("175309"), DisplayCurrency.USD))
    }

    @Test fun allocationSharesAddToApproximatelyOneHundredPercent() {
        val shares = AssetKind.entries.map { PortfolioMath.sharePercent(demo, it) }
        assertTrue(shares.reduce(BigDecimal::add).subtract(BigDecimal("100.0")).abs() <= BigDecimal("0.2"))
    }

    @Test fun historyRangesEndAtPresentTotal() {
        HistoryRange.entries.forEach { range ->
            val history = SamplePortfolio.history.getValue(range)
            assertTrue("History $range must contain at least two values", history.size > 1)
            assertEquals(0, PortfolioMath.totalHkd(demo).compareTo(history.last()))
            assertTrue(PortfolioMath.changeAmount(history) > BigDecimal.ZERO)
        }
    }

    @Test fun zeroPortfolioOrShortHistoryAreSafe() {
        assertEquals(BigDecimal("0.0"), PortfolioMath.sharePercent(emptyList(), AssetKind.FUND))
        assertEquals(BigDecimal.ZERO, PortfolioMath.changeAmount(listOf(BigDecimal.TEN)))
        assertEquals(BigDecimal.ZERO, PortfolioMath.changePercent(listOf(BigDecimal.ZERO, BigDecimal.TEN)))
    }
}
