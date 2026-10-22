package com.folio.wealth.model

import java.math.BigDecimal
import java.time.LocalDate

data class AssetSnapshot(val date: LocalDate, val valueHkd: BigDecimal, val rates: ExchangeRates = ExchangeRates())

data class PortfolioData(
    val assets: List<Asset> = emptyList(),
    val rates: ExchangeRates = ExchangeRates(),
    val snapshots: List<AssetSnapshot> = emptyList(),
    val displayCurrency: DisplayCurrency = DisplayCurrency.HKD,
    val darkMode: Boolean = true,
    val bills: List<Bill> = emptyList(),
) {
    init {
        require(assets.size <= 10000 && assets.map { it.id }.distinct().size == assets.size)
        require(snapshots.size <= 400 && snapshots.map { it.date }.distinct().size == snapshots.size)
        require(snapshots.all { it.valueHkd.signum() >= 0 })
        require(bills.size <= 20000 && bills.map { it.id }.distinct().size == bills.size) { "账单编号重复或账单过多" }
        val accounts = assets.associateBy { it.id }
        require(bills.all { accounts[it.accountId]?.let { a -> a.kind == AssetKind.SAVINGS && a.currency == it.currency } == true }) { "账单必须关联同币种的储蓄账户" }
    }

    fun recordToday(today: LocalDate = LocalDate.now()): PortfolioData {
        if (assets.isEmpty() && snapshots.isEmpty()) return this
        val snapshot = AssetSnapshot(today, PortfolioMath.totalHkd(assets, rates), rates)
        return copy(snapshots = (snapshots.filter { it.date != today } + snapshot).sortedBy { it.date }.takeLast(400))
    }

    fun history(range: HistoryRange, today: LocalDate = LocalDate.now()): List<AssetSnapshot> {
        val start = when (range) {
            HistoryRange.WEEK -> today.minusDays(6)
            HistoryRange.MONTH -> today.minusMonths(1)
            HistoryRange.QUARTER -> today.minusMonths(3)
            HistoryRange.YEAR -> today.minusYears(1)
        }
        return snapshots.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }.sortedBy { it.date }
    }
}

data class AssetInput(
    val title: String,
    val kind: AssetKind,
    val currency: DisplayCurrency,
    val balance: String = "",
    val buyPrice: String = "",
    val boughtShares: String = "",
    val symbol: String = "",
) {
    fun errors(): Map<String, String> = buildMap {
        if (title.trim().isEmpty() || title.trim().length > 100) put("title", "请输入 1–100 字的资产名称")
        if (symbol.trim().length > 32) put("symbol", "代码最多 32 个字符")
        val numbers = if (kind == AssetKind.SAVINGS) listOf("balance" to balance) else listOf("buyPrice" to buyPrice, "boughtShares" to boughtShares)
        numbers.forEach { (key, text) ->
            val parsed = parseDecimal(text, if (key == "balance") 2 else 8)
            if (parsed == null) put(key, if (key == "balance") "请输入非负金额，最多 2 位小数" else "请输入非负数字，最多 8 位小数")
            else if (key in listOf("buyPrice", "boughtShares") && parsed.signum() == 0) put(key, "必须大于 0")
        }
    }

    fun toAsset(id: String, now: Long = System.currentTimeMillis()): Asset {
        require(errors().isEmpty())
        fun number(text: String) = text.trim().toBigDecimal()
        return if (kind == AssetKind.SAVINGS) {
            Asset(id, title.trim(), kind, currency, balance = number(balance), updatedAt = now)
        } else {
            Asset(id, title.trim(), kind, currency, purchases = listOf(Purchase(java.util.UUID.randomUUID().toString(), number(buyPrice), number(boughtShares), now)), currentPrice = number(buyPrice), symbol = symbol.trim(), updatedAt = now)
        }
    }
}

fun parseDecimal(text: String, scale: Int = 8): BigDecimal? {
    val input = text.trim()
    if (input.length !in 1..32 || !input.matches(Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return null
    return input.toBigDecimalOrNull()?.takeIf { it.scale() <= scale && it.precision() <= 24 && it.signum() >= 0 }
}
