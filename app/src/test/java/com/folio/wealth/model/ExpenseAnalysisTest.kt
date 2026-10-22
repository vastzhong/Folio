package com.folio.wealth.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class ExpenseAnalysisTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val month = YearMonth.from(today)
    private fun bill(id: String, amount: String, date: LocalDate = today, category: String = "餐饮", currency: DisplayCurrency = DisplayCurrency.CNY, kind: BillKind = BillKind.EXPENSE) = Bill(id, "cash", kind, currency, BigDecimal(amount), date, category, createdAt = 0)
    private fun amount(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test fun analysisSeparatesCurrencyIncomeAndMonth() {
        val records = listOf(bill("a", "30"), bill("b", "10", category = "交通"), bill("usd", "200", currency = DisplayCurrency.USD), bill("income", "1000", kind = BillKind.INCOME), bill("old", "20", date = today.minusMonths(1)))
        val result = analyzeExpenses(records, month, DisplayCurrency.CNY, today)
        amount("40", result.total); amount("20", result.previousTotal)
        amount("100", requireNotNull(result.changePercent))
        assertEquals(listOf("餐饮", "交通"), result.categories.map { it.category })
        amount("75", result.categories.first().percent)
        amount("200", analyzeExpenses(records, month, DisplayCurrency.USD, today).total)
    }

    @Test fun currentMonthComparesSameElapsedDaysAndIgnoresFutureEntries() {
        val records = listOf(bill("now", "60"), bill("future", "900", today.plusDays(1)), bill("previous", "40", today.minusMonths(1)), bill("late", "1000", today.minusMonths(1).plusDays(1)))
        val result = analyzeExpenses(records, month, DisplayCurrency.CNY, today)
        assertTrue(result.samePeriod)
        amount("60", result.total); amount("40", result.previousTotal); amount("50", requireNotNull(result.changePercent))
        assertEquals(22, result.days.size)
    }

    @Test fun historicalMonthsCompareCompleteMonths() {
        val records = listOf(bill("sep", "30", LocalDate.of(2026, 9, 30)), bill("aug", "60", LocalDate.of(2026, 8, 31)))
        val result = analyzeExpenses(records, month.minusMonths(1), DisplayCurrency.CNY, today)
        assertFalse(result.samePeriod)
        amount("30", result.total); amount("60", result.previousTotal); amount("-50", requireNotNull(result.changePercent))
        assertEquals(30, result.days.size)
    }

    @Test fun comparisonClampsToShorterPreviousMonthIncludingLeapYears() {
        val march = LocalDate.of(2024, 3, 31)
        val result = analyzeExpenses(listOf(bill("leap", "10", LocalDate.of(2024, 2, 29))), YearMonth.from(march), DisplayCurrency.CNY, march)
        amount("10", result.previousTotal)
        assertEquals(29, analyzeExpenses(emptyList(), YearMonth.of(2024, 2), DisplayCurrency.CNY, march).days.size)
    }

    @Test fun zeroBaselineNeverProducesInfinityAndEmptyMonthsStayZero() {
        val fresh = analyzeExpenses(listOf(bill("new", "12.34")), month, DisplayCurrency.CNY, today)
        assertNull(fresh.changePercent)
        val empty = analyzeExpenses(emptyList(), month, DisplayCurrency.CNY, today)
        assertNull(empty.changePercent); amount("0", empty.total)
        assertTrue(empty.categories.isEmpty()); assertEquals(22, empty.days.size)
        assertTrue(empty.days.all { it.amount.signum() == 0 })
    }

    @Test fun categoriesAndDaysAggregateExactDecimalAmounts() {
        val records = listOf(bill("a", "0.10", category = " 餐饮 "), bill("b", "0.20"), bill("c", "0.30", today.minusDays(1), category = "购物"))
        val result = analyzeExpenses(records, month, DisplayCurrency.CNY, today)
        amount("0.60", result.total)
        assertEquals(2, result.categories.size)
        result.categories.forEach { amount("0.30", it.amount); amount("50", it.percent) }
        amount("0.30", result.days.last().amount)
        amount("0.60", result.days.fold(BigDecimal.ZERO) { sum, day -> sum + day.amount })
        assertEquals(today, result.days.last().date)
    }

    @Test fun deletingOrEditingExpenseRecomputesAnalysis() {
        val cash = Asset("cash", "日常", AssetKind.SAVINGS, DisplayCurrency.CNY, balance = BigDecimal("100"))
        val first = PortfolioData(assets = listOf(cash)).saveBill(bill("b", "10"))
        amount("10", analyzeExpenses(first.bills, month, DisplayCurrency.CNY, today).total)
        val edited = first.saveBill(bill("b", "20", category = "购物"))
        assertEquals("购物", analyzeExpenses(edited.bills, month, DisplayCurrency.CNY, today).categories.single().category)
        amount("0", analyzeExpenses(edited.deleteBill("b").bills, month, DisplayCurrency.CNY, today).total)
    }

    @Test fun preferredAccountUsesSavedChoiceThenLatestBillThenAvailableSavings() {
        val cny = Asset("cny", "人民币", AssetKind.SAVINGS, DisplayCurrency.CNY)
        val usd = cny.copy(id = "usd", currency = DisplayCurrency.USD)
        val records = listOf(bill("recent", "1").copy(accountId = "usd", currency = DisplayCurrency.USD))
        assertEquals("cny", preferredBillAccount(listOf(cny, usd), records, "cny"))
        assertEquals("usd", preferredBillAccount(listOf(cny, usd), records, "deleted"))
        assertEquals("cny", preferredBillAccount(listOf(cny), records, "deleted"))
        assertNull(preferredBillAccount(emptyList(), records, "deleted"))
        assertTrue(commonBillCategories(BillKind.EXPENSE).containsAll(listOf("餐饮", "交通", "购物")))
        assertTrue(commonBillCategories(BillKind.INCOME).contains("工资"))
    }
}
