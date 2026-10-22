package com.folio.wealth.model

import java.math.BigDecimal

enum class AssetKind(val label: String) {
    SAVINGS("储蓄"), STOCK("股票 / ETF"), FUND("基金")
}

enum class DisplayCurrency(val symbol: String, val hkdPerUnit: BigDecimal) {
    HKD("HK$", BigDecimal.ONE),
    CNY("¥", BigDecimal("1.09")),
    USD("US$", BigDecimal("7.80"))
}

enum class HistoryRange(val label: String, val startLabel: String) {
    WEEK("7天", "10月14日"),
    MONTH("1月", "9月20日"),
    QUARTER("3月", "7月20日"),
    YEAR("1年", "2025年10月")
}

data class Asset(
    val id: String,
    val title: String,
    val kind: AssetKind,
    val currency: DisplayCurrency,
    val balance: BigDecimal = BigDecimal.ZERO,
    val purchases: List<Purchase> = emptyList(),
    val currentPrice: BigDecimal = BigDecimal.ZERO,
    val symbol: String = "",
    val updatedAt: Long = 0,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && title.length <= 100 && symbol.length <= 32)
        require(listOf(balance, currentPrice).all { it.signum() >= 0 && it.precision() <= 24 && it.scale() in 0..8 })
        require(purchases.size <= 10000 && purchases.map { it.id }.distinct().size == purchases.size)
        require(if (kind == AssetKind.SAVINGS) purchases.isEmpty() else purchases.isNotEmpty())
    }

    val isInvestment: Boolean get() = kind != AssetKind.SAVINGS
    val currentShares: BigDecimal get() = purchases.fold(BigDecimal.ZERO) { total, buy -> total.add(buy.shares) }
    val nativeValue: BigDecimal get() = if (isInvestment) currentPrice.multiply(currentShares) else balance
    val holdingCost: BigDecimal get() = purchases.fold(BigDecimal.ZERO) { total, buy -> total.add(buy.price.multiply(buy.shares)) }
    val buyPrice: BigDecimal get() = if (currentShares.signum() == 0) BigDecimal.ZERO else holdingCost.divide(currentShares, 12, java.math.RoundingMode.HALF_UP)
    val unrealizedPnl: BigDecimal get() = if (isInvestment) nativeValue.subtract(holdingCost) else BigDecimal.ZERO
    val pnlPercent: BigDecimal? get() = if (holdingCost.signum() == 0) null else unrealizedPnl.multiply(BigDecimal(100)).divide(holdingCost, 2, java.math.RoundingMode.HALF_UP)
    val subtitle: String get() = "${symbol.ifBlank { kind.label }} · ${currency.name}"
    val unitsLabel: String get() = if (isInvestment) "${currentShares.stripTrailingZeros().toPlainString()} 股" else "现金余额"
    fun estimatedHkd(rates: ExchangeRates): BigDecimal = nativeValue.multiply(rates.hkdPerUnit(currency))
    fun recordPurchase(purchase: Purchase): Asset {
        require(isInvestment)
        return copy(purchases = purchases + purchase, currentPrice = purchase.price, updatedAt = purchase.recordedAt)
    }
}

data class Purchase(val id: String, val price: BigDecimal, val shares: BigDecimal, val recordedAt: Long = System.currentTimeMillis()) {
    init {
        require(id.isNotBlank())
        require(listOf(price, shares).all { it.signum() > 0 && it.precision() <= 24 && it.scale() in 0..8 })
    }
}

data class ExchangeRates(
    val cnyToHkd: BigDecimal = BigDecimal("1.09"),
    val usdToHkd: BigDecimal = BigDecimal("7.80"),
) {
    init { require(listOf(cnyToHkd, usdToHkd).all { it.signum() > 0 && it.precision() <= 24 && it.scale() in 0..8 }) }
    fun hkdPerUnit(currency: DisplayCurrency): BigDecimal = when (currency) {
        DisplayCurrency.HKD -> BigDecimal.ONE
        DisplayCurrency.CNY -> cnyToHkd
        DisplayCurrency.USD -> usdToHkd
    }
}
