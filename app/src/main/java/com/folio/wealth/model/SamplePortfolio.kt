package com.folio.wealth.model

import java.math.BigDecimal

/** Demo-only, deterministic sample portfolio. No bank, broker, internet, or permissions. */
object SamplePortfolio {
    private fun bd(value: String) = BigDecimal(value)
    private fun holding(id: String, title: String, kind: AssetKind, currency: DisplayCurrency, buy: String, shares: String, price: String, symbol: String = "") =
        Asset(id, title, kind, currency, purchases = listOf(Purchase("sample-$id", bd(buy), bd(shares), 0)), currentPrice = bd(price), symbol = symbol)

    val assets: List<Asset> = listOf(
        Asset("hsbc", "汇丰银行储蓄", AssetKind.SAVINGS, DisplayCurrency.HKD, balance = bd("42000")),
        Asset("cny", "人民币储蓄", AssetKind.SAVINGS, DisplayCurrency.CNY, balance = bd("25000")),
        holding("qqqm", "纳斯达克100 ETF", AssetKind.STOCK, DisplayCurrency.USD, "220", "12", "237.50", "QQQM"),
        holding("voo", "标普500 ETF", AssetKind.STOCK, DisplayCurrency.USD, "550", "4", "600", "VOO"),
        Asset("za", "ZA Bank 储蓄", AssetKind.SAVINGS, DisplayCurrency.HKD, balance = bd("18500")),
        holding("tencent", "腾讯控股", AssetKind.STOCK, DisplayCurrency.HKD, "500", "30", "526", "0700"),
        holding("hs300", "沪深300指数基金", AssetKind.FUND, DisplayCurrency.CNY, "1", "12000", "1.1"),
        holding("mmf", "货币市场基金", AssetKind.FUND, DisplayCurrency.CNY, "1", "8500", "1"),
        holding("aapl", "苹果公司", AssetKind.STOCK, DisplayCurrency.USD, "200", "4", "230", "AAPL"),
    )

    val history: Map<HistoryRange, List<BigDecimal>> = mapOf(
        HistoryRange.WEEK to listOf("173210", "174060", "173490", "174650", "174020", "175840", "175309").map(::bd),
        HistoryRange.MONTH to listOf("165400", "166950", "169200", "168100", "171450", "170900", "173100", "172500", "174240", "175309").map(::bd),
        HistoryRange.QUARTER to listOf("143000", "146500", "151800", "150500", "156900", "159200", "161150", "165800", "166400", "170000", "172300", "175309").map(::bd),
        HistoryRange.YEAR to listOf("55000", "68000", "79000", "88000", "97000", "112000", "122000", "135000", "145000", "155500", "166500", "175309").map(::bd),
    )
}
