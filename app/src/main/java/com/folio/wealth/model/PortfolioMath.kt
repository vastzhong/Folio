package com.folio.wealth.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Pure financial math, deliberately independent of Android/Compose. */
object PortfolioMath {
    fun totalHkd(assets: List<Asset>, rates: ExchangeRates = ExchangeRates()): BigDecimal =
        assets.fold(BigDecimal.ZERO) { sum, asset -> sum.add(asset.estimatedHkd(rates)) }

    fun byKindHkd(assets: List<Asset>, kind: AssetKind, rates: ExchangeRates = ExchangeRates()): BigDecimal =
        totalHkd(assets.filter { it.kind == kind }, rates)

    fun convertFromHkd(amountHkd: BigDecimal, target: DisplayCurrency, rates: ExchangeRates = ExchangeRates()): BigDecimal =
        amountHkd.divide(rates.hkdPerUnit(target), 2, RoundingMode.HALF_UP)

    fun nativeTotal(assets: List<Asset>, currency: DisplayCurrency): BigDecimal =
        assets.filter { it.currency == currency }.fold(BigDecimal.ZERO) { sum, asset -> sum.add(asset.nativeValue) }

    fun sharePercent(assets: List<Asset>, kind: AssetKind, rates: ExchangeRates = ExchangeRates()): BigDecimal {
        val total = totalHkd(assets, rates)
        if (total.signum() == 0) return BigDecimal.ZERO.setScale(1)
        return byKindHkd(assets, kind, rates)
            .multiply(BigDecimal(100))
            .divide(total, 1, RoundingMode.HALF_UP)
    }

    fun changeAmount(history: List<BigDecimal>): BigDecimal =
        if (history.size < 2) BigDecimal.ZERO else history.last().subtract(history.first())

    /** This percentage is net asset VALUE change, not investment-only performance. */
    fun changePercent(history: List<BigDecimal>): BigDecimal {
        if (history.size < 2 || history.first().signum() == 0) return BigDecimal.ZERO
        return changeAmount(history)
            .multiply(BigDecimal(100))
            .divide(history.first(), 2, RoundingMode.HALF_UP)
    }
}
