package com.folio.wealth.model

import com.folio.wealth.data.PortfolioCodec
import com.folio.wealth.data.PortfolioCsv
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BillAndCsvTest {
    private val day = LocalDate.of(2026, 10, 22)
    private fun cash(id: String = "cash", currency: DisplayCurrency = DisplayCurrency.CNY) = Asset(id, "储蓄,$id", AssetKind.SAVINGS, currency, balance = BigDecimal("100.00"))
    private fun bill(id: String = "bill", kind: BillKind = BillKind.EXPENSE, amount: String = "25.50", accountId: String = "cash", currency: DisplayCurrency = DisplayCurrency.CNY) = Bill(id, accountId, kind, currency, BigDecimal(amount), day, "餐饮", "午饭", 1234)
    private fun balance(data: PortfolioData, expected: String, id: String = "cash") = assertEquals(0, BigDecimal(expected).compareTo(data.assets.single { it.id == id }.balance))

    @Test fun incomeAndExpensesUpdateOnlyTheirAccountAndPortfolioTotal() {
        val start = PortfolioData(assets = listOf(cash(), cash("usd", DisplayCurrency.USD)))
        val expense = start.saveBill(bill(), 100)
        balance(expense, "74.50"); balance(expense, "100", "usd")
        val income = expense.saveBill(bill("income", BillKind.INCOME, "10.01"), 200)
        balance(income, "84.51")
        assertEquals(0, BigDecimal("92.1159").compareTo(PortfolioMath.byKindHkd(income.assets.filter { it.id == "cash" }, AssetKind.SAVINGS, income.rates)))
        balance(start, "100")
    }

    @Test fun editingReplacesTheEffectInsteadOfApplyingItTwice() {
        val first = PortfolioData(assets = listOf(cash())).saveBill(bill(), 0)
        val edited = first.saveBill(bill(amount = "40.00"), 1)
        balance(edited, "60")
        assertEquals(1, edited.bills.size)
        balance(edited.saveBill(bill(kind = BillKind.INCOME, amount = "5.00"), 2), "105")
        balance(edited.deleteBill("bill", 3), "100")
    }

    @Test fun changingAccountsReversesTheOldEffectAndAppliesTheNewOne() {
        val first = PortfolioData(assets = listOf(cash(), cash("other"))).saveBill(bill(), 0)
        val edited = first.saveBill(bill(accountId = "other", amount = "10.00"), 1)
        balance(edited, "100"); balance(edited, "90", "other")
    }

    @Test fun netEditsCanIncreaseIncomeEvenAfterOriginalIncomeHasBeenSpent() {
        val initial = PortfolioData(assets = listOf(cash().copy(balance = BigDecimal.ZERO)))
            .saveBill(bill(kind = BillKind.INCOME, amount = "10"), 0)
            .saveBill(bill("spend", amount = "10"), 0)
        balance(initial.saveBill(bill(kind = BillKind.INCOME, amount = "20"), 1), "10")
        assertThrows(IllegalArgumentException::class.java) { initial.deleteBill("bill") }
        balance(initial, "0")
    }

    @Test fun insufficientFundsAndInvalidReferencesLeaveOriginalUntouched() {
        val data = PortfolioData(assets = listOf(cash()))
        assertThrows(IllegalArgumentException::class.java) { data.saveBill(bill(amount = "100.01")) }
        assertThrows(IllegalArgumentException::class.java) { data.saveBill(bill(currency = DisplayCurrency.USD)) }
        assertThrows(IllegalArgumentException::class.java) { data.saveBill(bill(accountId = "missing")) }
        val stock = Asset("stock", "股", AssetKind.STOCK, DisplayCurrency.CNY, purchases = listOf(Purchase("p", BigDecimal.ONE, BigDecimal.ONE)))
        assertThrows(IllegalArgumentException::class.java) { PortfolioData(assets = listOf(stock)).saveBill(bill(accountId = "stock")) }
        balance(data, "100"); assertTrue(data.bills.isEmpty())
    }

    @Test fun zeroNegativeAndOverpreciseBillAmountsAreRejected() {
        listOf("0", "-1", "1.001").forEach { assertThrows(IllegalArgumentException::class.java) { bill(amount = it) } }
        val data = PortfolioData(assets = listOf(cash())).saveBill(bill())
        assertThrows(IllegalArgumentException::class.java) { data.copy(assets = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { data.copy(bills = data.bills + data.bills) }
    }

    @Test fun backdatedBillChangesTodayWithoutRewritingHistoricalSnapshot() {
        val data = PortfolioData(assets = listOf(cash())).recordToday(day.minusDays(1))
        val result = data.saveBill(bill().copy(date = day.minusMonths(1)), 0).recordToday(day)
        assertEquals(data.snapshots.first(), result.snapshots.first())
        assertEquals(0, BigDecimal("81.2050").compareTo(result.snapshots.last().valueHkd))
    }

    private fun richData(): PortfolioData {
        val stock = Asset("stock", "=SUM(1,2)\n\"股票\"", AssetKind.STOCK, DisplayCurrency.USD, purchases = listOf(Purchase("p", BigDecimal("1.12345678"), BigDecimal("2.87654321"), 0)), currentPrice = BigDecimal("3.12345678"), symbol = "@TEST")
        return PortfolioData(assets = listOf(cash(), stock), rates = ExchangeRates(BigDecimal("1.12345678"), BigDecimal("7.87654321")), displayCurrency = DisplayCurrency.USD, darkMode = false)
            .saveBill(bill().copy(note = "'原样,\"中文\"\r\n第二行"), 0).recordToday(day)
    }

    @Test fun fullCsvRoundTripPreservesUnicodeQuotesNewlinesAndExactValuations() {
        val data = richData()
        val encoded = PortfolioCsv.encode(data)
        assertTrue(encoded.toString(Charsets.UTF_8).startsWith("\uFEFF"))
        assertTrue(encoded.toString(Charsets.UTF_8).contains("'=SUM"))
        assertEquals(data, PortfolioCsv.decode(encoded))
        assertEquals(data, PortfolioCodec.decode(PortfolioCodec.encode(data)))
    }

    @Test fun csvImportRestoresCurrentBalancesWithoutReplayingExpenses() {
        val data = PortfolioData(assets = listOf(cash())).saveBill(bill(), 0)
        val imported = PortfolioCsv.decode(PortfolioCsv.encode(data))
        balance(imported, "74.50")
        balance(imported.deleteBill("bill", 0), "100")
    }

    @Test fun emptyPortfolioAlsoRoundTripsWithoutFakeData() {
        assertEquals(PortfolioData(), PortfolioCsv.decode(PortfolioCsv.encode(PortfolioData())))
    }

    @Test fun csvRejectsBrokenQuotingHeadersVersionNumbersAndDuplicateRecords() {
        val valid = PortfolioCsv.encode(PortfolioData(assets = listOf(cash()))).toString(Charsets.UTF_8)
        val bad = listOf(valid.replace("schema_version", "bad_header"), valid + "\"unfinished", valid.replace("\"1\",\"asset\"", "\"99\",\"asset\""), valid.replace("\"100.00\"", "\"-100.00\""), valid + valid.lineSequence().first { it.contains("\"asset\"") } + "\r\n", valid.replace("\"asset\"", "\"unknown\""))
        bad.forEach { text -> assertThrows(Exception::class.java) { PortfolioCsv.decode(text.toByteArray(Charsets.UTF_8)) } }
    }

    @Test fun csvRejectsOrphanPurchasesMismatchedCurrenciesAndMalformedUtf8() {
        val valid = PortfolioCsv.encode(richData()).toString(Charsets.UTF_8)
        assertThrows(Exception::class.java) { PortfolioCsv.decode(valid.replace("\"p\",\"stock\"", "\"p\",\"missing\"").toByteArray(Charsets.UTF_8)) }
        assertThrows(Exception::class.java) { PortfolioCsv.decode(valid.replace("\"bill\",\"bill\",\"cash\"", "\"bill\",\"bill\",\"stock\"").toByteArray(Charsets.UTF_8)) }
        assertThrows(Exception::class.java) { PortfolioCsv.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
        assertThrows(Exception::class.java) { PortfolioCsv.decode(ByteArray(PortfolioCsv.MAX_BYTES + 1)) }
    }

    @Test fun versionTwoBinaryFilesMigrateWithoutLosingAssetsRatesOrHistory() {
        val oldData = richData().copy(bills = emptyList())
        // v2 has exactly the same prefix, and no trailing bill-count section.
        val v2 = PortfolioCodec.encode(oldData).dropLast(4).toByteArray().also { it[7] = 2 }
        assertEquals(oldData, PortfolioCodec.decode(v2))
        assertEquals(oldData, PortfolioCodec.decode(PortfolioCodec.encode(PortfolioCodec.decode(v2))))
    }

    @Test fun checkedInCsvExampleHasConsistentBalancesAndValuation() {
        val file = listOf(java.io.File("examples/folio-example.csv"), java.io.File("../examples/folio-example.csv")).first { it.exists() }
        val data = PortfolioCsv.decode(file.readBytes())
        balance(data, "1063.38", "cash-cny")
        assertEquals(2, data.bills.size)
        assertEquals(0, data.snapshots.single().valueHkd.compareTo(PortfolioMath.totalHkd(data.assets, data.rates)))
        assertEquals(data, PortfolioCsv.decode(PortfolioCsv.encode(data)))
    }
}
