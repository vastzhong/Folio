package com.folio.wealth.ui.format

import com.folio.wealth.model.DisplayCurrency
import com.folio.wealth.model.PortfolioMath
import com.folio.wealth.model.ExchangeRates
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

fun money(valueHkd: BigDecimal, currency: DisplayCurrency, rates: ExchangeRates = ExchangeRates()): String =
    nativeMoney(PortfolioMath.convertFromHkd(valueHkd, currency, rates), currency)

fun nativeMoney(amount: BigDecimal, currency: DisplayCurrency): String {
    val n = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        roundingMode = java.math.RoundingMode.HALF_UP
    }
    return currency.symbol + n.format(amount)
}

fun priceMoney(amount: BigDecimal, currency: DisplayCurrency): String = currency.symbol +
    NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 8; roundingMode = java.math.RoundingMode.HALF_UP }.format(amount)

fun shortMoney(valueHkd: BigDecimal, currency: DisplayCurrency): String {
    val k = PortfolioMath.convertFromHkd(valueHkd, currency).toDouble() / 1000.0
    return "%s%.1fk".format(Locale.US, currency.symbol, k)
}

fun signedPercentage(value: BigDecimal): String =
    (if (value.signum() >= 0) "+" else "") + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%"
