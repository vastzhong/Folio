package com.folio.wealth.data

import com.folio.wealth.model.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.math.BigDecimal

/** A complete snapshot, not a transaction replay: account balances already include bills. */
object PortfolioCsv {
    const val MAX_BYTES = 8 * 1024 * 1024
    val columns = listOf("schema_version", "record_type", "id", "parent_id", "name", "kind", "currency", "amount", "price", "shares", "date", "note", "created_at", "symbol", "cny_to_hkd", "usd_to_hkd", "display_currency", "dark_mode")
    private val dangerous = setOf('=', '+', '-', '@', '\t', '\r', '\n', '\'')
    private data class AssetRow(val id: String, val title: String, val kind: AssetKind, val currency: DisplayCurrency, val balance: BigDecimal, val price: BigDecimal, val symbol: String, val updatedAt: Long)

    fun encode(data: PortfolioData): ByteArray {
        val rows = mutableListOf(columns)
        fun row(type: String, vararg fields: Pair<String, String>) {
            val values = mapOf("schema_version" to "1", "record_type" to type) + fields
            rows += columns.map { values[it].orEmpty() }
        }
        row("settings", "cny_to_hkd" to data.rates.cnyToHkd.toPlainString(), "usd_to_hkd" to data.rates.usdToHkd.toPlainString(), "display_currency" to data.displayCurrency.name, "dark_mode" to data.darkMode.toString())
        data.assets.forEach { a ->
            row("asset", "id" to a.id, "name" to a.title, "kind" to a.kind.name, "currency" to a.currency.name, "amount" to a.balance.toPlainString(), "price" to a.currentPrice.toPlainString(), "symbol" to a.symbol, "created_at" to a.updatedAt.toString())
            a.purchases.forEach { p -> row("purchase", "id" to p.id, "parent_id" to a.id, "price" to p.price.toPlainString(), "shares" to p.shares.toPlainString(), "created_at" to p.recordedAt.toString()) }
        }
        data.bills.forEach { b -> row("bill", "id" to b.id, "parent_id" to b.accountId, "kind" to b.kind.name, "currency" to b.currency.name, "amount" to b.amount.toPlainString(), "date" to b.date.toString(), "name" to b.category, "note" to b.note, "created_at" to b.createdAt.toString()) }
        data.snapshots.forEach { s -> row("snapshot", "date" to s.date.toString(), "amount" to s.valueHkd.toPlainString(), "cny_to_hkd" to s.rates.cnyToHkd.toPlainString(), "usd_to_hkd" to s.rates.usdToHkd.toPlainString()) }
        return ("\uFEFF" + rows.joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { value ->
            val safe = if (value.firstOrNull() in dangerous) "'$value" else value
            "\"${safe.replace("\"", "\"\"")}\""
        } }).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "导出数据超过 8 MB" } }
    }

    fun decode(bytes: ByteArray): PortfolioData {
        require(bytes.size <= MAX_BYTES) { "CSV 文件不能超过 8 MB" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        val rows = parse(text)
        require(rows.firstOrNull() == columns) { "表头不符合 Folio CSV 格式，请使用导出的文件或模板" }
        var settings: List<String>? = null
        val assets = mutableListOf<AssetRow>()
        val buys = mutableMapOf<String, MutableList<Purchase>>()
        val bills = mutableListOf<Bill>()
        val snapshots = mutableListOf<AssetSnapshot>()
        rows.drop(1).forEachIndexed { index, raw ->
            try {
                require(raw.size == columns.size) { "列数不正确" }
                val row = raw.map { if (it.length >= 2 && it[0] == '\'' && it[1] in dangerous) it.substring(1) else it }
                fun v(key: String) = row[columns.indexOf(key)]
                fun required(key: String) = v(key).also { require(it.isNotBlank()) { "$key 不能为空" } }
                fun number(key: String, scale: Int = 8) = requireNotNull(parseDecimal(required(key), scale)) { "$key 数值无效" }
                fun time() = requireNotNull(required("created_at").toLongOrNull()) { "created_at 必须是毫秒时间戳" }
                fun date() = LocalDate.parse(required("date"))
                fun only(vararg keys: String) {
                    val allowed = keys.toSet() + setOf("schema_version", "record_type")
                    require(columns.all { it in allowed || v(it).isEmpty() }) { "该记录包含不适用的列" }
                }
                require(v("schema_version") == "1") { "不支持的 CSV 版本" }
                when (required("record_type")) {
                    "settings" -> {
                        only("cny_to_hkd", "usd_to_hkd", "display_currency", "dark_mode")
                        require(settings == null) { "settings 只能有一条" }
                        settings = row
                    }
                    "asset" -> {
                        only("id", "name", "kind", "currency", "amount", "price", "symbol", "created_at")
                        // Investments are constructed after their purchase rows have been read.
                        assets += AssetRow(required("id"), required("name"), AssetKind.valueOf(required("kind")), DisplayCurrency.valueOf(required("currency")), number("amount"), number("price"), v("symbol"), time())
                    }
                    "purchase" -> {
                        only("id", "parent_id", "price", "shares", "created_at")
                        buys.getOrPut(required("parent_id")) { mutableListOf() } += Purchase(required("id"), number("price"), number("shares"), time())
                    }
                    "bill" -> {
                        only("id", "parent_id", "kind", "currency", "amount", "date", "name", "note", "created_at")
                        bills += Bill(required("id"), required("parent_id"), BillKind.valueOf(required("kind")), DisplayCurrency.valueOf(required("currency")), number("amount", 2), date(), required("name"), v("note"), time())
                    }
                    "snapshot" -> {
                        only("date", "amount", "cny_to_hkd", "usd_to_hkd")
                        val value = required("amount").also { require(it.length <= 128 && it.matches(Regex("[0-9]+(?:\\.[0-9]+)?"))) }.toBigDecimal()
                        require(value.scale() in 0..32 && value.precision() <= 96)
                        snapshots += AssetSnapshot(date(), value, ExchangeRates(number("cny_to_hkd"), number("usd_to_hkd")))
                    }
                    else -> error("未知 record_type")
                }
            } catch (e: Exception) { throw IllegalArgumentException("第 ${index + 2} 条记录：${e.message ?: "格式不正确"}", e) }
        }
        val config = requireNotNull(settings) { "缺少 settings 记录" }
        fun setting(key: String) = config[columns.indexOf(key)]
        val completed = assets.map { a ->
            Asset(a.id, a.title, a.kind, a.currency, a.balance, buys.remove(a.id).orEmpty(), a.price, a.symbol, a.updatedAt)
        }
        require(buys.isEmpty()) { "买入记录关联的资产不存在" }
        val data = PortfolioData(completed, ExchangeRates(requireNotNull(parseDecimal(setting("cny_to_hkd"))), requireNotNull(parseDecimal(setting("usd_to_hkd")))), snapshots, DisplayCurrency.valueOf(setting("display_currency")), requireNotNull(setting("dark_mode").toBooleanStrictOrNull()) { "dark_mode 必须是 true 或 false" }, bills)
        // Also enforce the limits of the on-device backup format before showing an import preview.
        PortfolioCodec.encode(data)
        return data
    }

    /** RFC 4180 quoting, including embedded newlines; malformed input is never partially accepted. */
    private fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        fun finishCell() { row += cell.toString(); cell.setLength(0); closed = false }
        fun finishRow() { finishCell(); rows += row; row = mutableListOf(); require(rows.size <= 100000) { "CSV 记录过多" } }
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted -> if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else cell.append(ch)
                ch == ',' -> finishCell()
                ch == '\r' || ch == '\n' -> { finishRow(); if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++ }
                closed -> error("引号结束后只能接逗号或换行")
                ch == '"' -> { require(cell.isEmpty()) { "引号必须位于单元格开头" }; quoted = true }
                else -> cell.append(ch)
            }
            require(cell.length <= 32768 && row.size <= columns.size) { "CSV 单元格或列数过多" }
            i++
        }
        require(!quoted) { "CSV 引号未闭合" }
        if (cell.isNotEmpty() || row.isNotEmpty() || closed) finishRow()
        return rows
    }
}
