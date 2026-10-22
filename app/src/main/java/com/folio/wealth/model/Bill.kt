package com.folio.wealth.model

import java.math.BigDecimal
import java.time.LocalDate

enum class BillKind(val label: String) { EXPENSE("支出"), INCOME("收入") }

data class Bill(
    val id: String,
    val accountId: String,
    val kind: BillKind,
    val currency: DisplayCurrency,
    val amount: BigDecimal,
    val date: LocalDate,
    val category: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    init {
        require(id.isNotBlank() && accountId.isNotBlank()) { "账单或账户编号为空" }
        require(amount.signum() > 0 && amount.precision() <= 24 && amount.scale() in 0..2) { "账单金额必须大于 0，最多 2 位小数" }
        require(category.isNotBlank() && category.length <= 40 && note.length <= 500) { "分类最多 40 字，备注最多 500 字" }
    }
    val signedAmount: BigDecimal get() = if (kind == BillKind.INCOME) amount else amount.negate()
}

/** Apply the net change once, including when an edit switches accounts or income/expense. */
fun PortfolioData.saveBill(bill: Bill, now: Long = System.currentTimeMillis()): PortfolioData =
    changeBill(bills.find { it.id == bill.id }, bill, now)

fun PortfolioData.deleteBill(id: String, now: Long = System.currentTimeMillis()): PortfolioData =
    changeBill(requireNotNull(bills.find { it.id == id }) { "账单不存在" }, null, now)

private fun PortfolioData.changeBill(old: Bill?, next: Bill?, now: Long): PortfolioData {
    if (next != null) {
        val account = assets.find { it.id == next.accountId }
        require(account?.kind == AssetKind.SAVINGS && account.currency == next.currency) { "请选择同币种的储蓄账户" }
    }
    val deltas = mutableMapOf<String, BigDecimal>()
    fun delta(id: String, amount: BigDecimal) { deltas[id] = (deltas[id] ?: BigDecimal.ZERO) + amount }
    old?.let { delta(it.accountId, it.signedAmount.negate()) }
    next?.let { delta(it.accountId, it.signedAmount) }
    val updated = assets.map { asset ->
        val change = deltas[asset.id] ?: return@map asset
        val balance = asset.balance + change
        require(balance.signum() >= 0) { "${asset.title}余额不足，无法保存或撤销这笔账单" }
        require(balance.precision() <= 24) { "账户金额超出支持范围" }
        asset.copy(balance = balance, updatedAt = now)
    }
    return copy(assets = updated, bills = bills.filter { it.id != old?.id } + listOfNotNull(next))
}
