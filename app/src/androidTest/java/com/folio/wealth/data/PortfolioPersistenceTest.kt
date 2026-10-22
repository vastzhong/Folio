package com.folio.wealth.data

import androidx.test.platform.app.InstrumentationRegistry
import com.folio.wealth.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.util.UUID

class PortfolioPersistenceTest {
    @Test fun realFileSurvivesNewStoreAndPreservesAllPurchases() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "folio-test-${UUID.randomUUID()}")
        directory.mkdirs()
        try {
            val file = File(directory, "portfolio.dat")
            val a = Asset("stock", "美元股票", AssetKind.STOCK, DisplayCurrency.USD, purchases = listOf(Purchase("first", BigDecimal("10.01"), BigDecimal("12.5"), 1000)), currentPrice = BigDecimal("12.34"))
                .recordPurchase(Purchase("second", BigDecimal("13.56"), BigDecimal("5.25"), 2000))
            val expected = PortfolioData(assets = listOf(a), displayCurrency = DisplayCurrency.CNY, darkMode = false).recordToday()
            LocalPortfolioStore(file).save(expected)
            assertEquals(expected, LocalPortfolioStore(file).load())
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertThrows(Exception::class.java) { LocalPortfolioStore(file).load() }
            assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        } finally { directory.deleteRecursively() }
    }
}
