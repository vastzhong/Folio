package com.folio.wealth.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

data class CategoryExpense(val category: String, val amount: BigDecimal, val percent: BigDecimal)
data class DailyExpense(val date: LocalDate, val amount: BigDecimal)
data class ExpenseAnalysis(
    val total: BigDecimal,
    val previousTotal: BigDecimal,
    val samePeriod: Boolean,
    val categories: List<CategoryExpense>,
    val days: List<DailyExpense>,
) {
    val difference: BigDecimal get() = total - previousTotal
    val changePercent: BigDecimal? get() = if (previousTotal.signum() == 0) null else difference.multiply(BigDecimal(100)).divide(previousTotal, 1, RoundingMode.HALF_UP)
}

/** Compare like currencies and, in the current month, the same elapsed calendar days. */
fun analyzeExpenses(bills: List<Bill>, month: YearMonth, currency: DisplayCurrency, today: LocalDate = LocalDate.now()): ExpenseAnalysis {
    val samePeriod = month == YearMonth.from(today)
    val end = minOf(month.atEndOfMonth(), today)
    val previousMonth = month.minusMonths(1)
    val previousEnd = if (samePeriod) previousMonth.atDay(minOf(today.dayOfMonth, previousMonth.lengthOfMonth())) else previousMonth.atEndOfMonth()
    val expenses = bills.filter { it.kind == BillKind.EXPENSE && it.currency == currency }
    val current = expenses.filter { YearMonth.from(it.date) == month && !it.date.isAfter(end) }
    fun List<Bill>.total() = fold(BigDecimal.ZERO) { sum, bill -> sum + bill.amount }
    val total = current.total()
    val previous = expenses.filter { YearMonth.from(it.date) == previousMonth && !it.date.isAfter(previousEnd) }.total()
    val categories = current.groupBy { it.category.trim() }.map { (category, records) ->
        val amount = records.total()
        CategoryExpense(category, amount, if (total.signum() == 0) BigDecimal.ZERO else amount.multiply(BigDecimal(100)).divide(total, 1, RoundingMode.HALF_UP))
    }.sortedWith(compareByDescending<CategoryExpense> { it.amount }.thenBy { it.category })
    val byDay = current.groupBy { it.date }.mapValues { it.value.total() }
    val days = if (end.isBefore(month.atDay(1))) emptyList() else (1..end.dayOfMonth).map { day ->
        val date = month.atDay(day)
        DailyExpense(date, byDay[date] ?: BigDecimal.ZERO)
    }
    return ExpenseAnalysis(total, previous, samePeriod, categories, days)
}

fun preferredBillAccount(accounts: List<Asset>, bills: List<Bill>, lastAccountId: String?): String? {
    val savings = accounts.filter { it.kind == AssetKind.SAVINGS }
    return savings.find { it.id == lastAccountId }?.id
        ?: bills.filter { bill -> savings.any { it.id == bill.accountId } }.maxByOrNull { it.createdAt }?.accountId
        ?: savings.firstOrNull()?.id
}

fun commonBillCategories(kind: BillKind): List<String> = when (kind) {
    BillKind.EXPENSE -> listOf("餐饮", "交通", "购物", "居住", "日用", "娱乐", "医疗", "学习", "旅行", "其他")
    BillKind.INCOME -> listOf("工资", "奖金", "红包", "理财", "其他")
}
