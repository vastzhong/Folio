package com.folio.wealth.data

import com.folio.wealth.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.LocalDate

/** Versioned decimal-string storage: no amounts are round-tripped through Double. */
object PortfolioCodec {
    fun encode(data: PortfolioData): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x464F4C49)
            out.writeInt(3)
            out.writeUTF(data.displayCurrency.name)
            out.writeBoolean(data.darkMode)
            out.writeUTF(data.rates.cnyToHkd.toPlainString())
            out.writeUTF(data.rates.usdToHkd.toPlainString())
            out.writeInt(data.assets.size)
            data.assets.forEach { a ->
                out.writeUTF(a.id); out.writeUTF(a.title); out.writeUTF(a.kind.name); out.writeUTF(a.currency.name)
                out.writeUTF(a.balance.toPlainString()); out.writeUTF(a.currentPrice.toPlainString())
                out.writeInt(a.purchases.size)
                a.purchases.forEach { buy -> out.writeUTF(buy.id); out.writeUTF(buy.price.toPlainString()); out.writeUTF(buy.shares.toPlainString()); out.writeLong(buy.recordedAt) }
                out.writeUTF(a.symbol); out.writeLong(a.updatedAt)
            }
            out.writeInt(data.snapshots.size)
            data.snapshots.forEach { out.writeUTF(it.date.toString()); out.writeUTF(it.valueHkd.toPlainString()); out.writeUTF(it.rates.cnyToHkd.toPlainString()); out.writeUTF(it.rates.usdToHkd.toPlainString()) }
            out.writeInt(data.bills.size)
            data.bills.forEach { b ->
                out.writeUTF(b.id); out.writeUTF(b.accountId); out.writeUTF(b.kind.name); out.writeUTF(b.currency.name)
                out.writeUTF(b.amount.toPlainString()); out.writeUTF(b.date.toString()); out.writeUTF(b.category); out.writeUTF(b.note); out.writeLong(b.createdAt)
            }
        }
        bytes.toByteArray().also { require(it.size <= 8 * 1024 * 1024) { "资产记录过多，无法保存" } }
    }

    fun decode(bytes: ByteArray): PortfolioData {
        require(bytes.size <= 8 * 1024 * 1024) { "资产文件过大" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == 0x464F4C49) { "无法识别资产文件" }
            val version = input.readInt()
            require(version in 2..3) { "无法识别资产文件版本" }
            fun decimal() = input.readUTF().also { require(it.length <= 128) }.toBigDecimal()
            val currency = DisplayCurrency.valueOf(input.readUTF())
            val dark = input.readBoolean()
            val rates = ExchangeRates(decimal(), decimal())
            val assetCount = input.readInt().also { require(it in 0..10000) }
            val assets = List(assetCount) {
                val id = input.readUTF()
                val title = input.readUTF()
                val kind = AssetKind.valueOf(input.readUTF())
                val nativeCurrency = DisplayCurrency.valueOf(input.readUTF())
                val balance = decimal()
                val price = decimal()
                val buys = input.readInt().also { require(it in 0..10000) }
                val purchases = List(buys) { Purchase(input.readUTF(), decimal(), decimal(), input.readLong()) }
                Asset(id, title, kind, nativeCurrency, balance, purchases, price, input.readUTF(), input.readLong())
            }
            val count = input.readInt().also { require(it in 0..400) }
            val snapshots = List(count) { AssetSnapshot(LocalDate.parse(input.readUTF()), decimal(), ExchangeRates(decimal(), decimal())) }
            val bills = if (version == 2) emptyList() else List(input.readInt().also { require(it in 0..20000) }) {
                Bill(input.readUTF(), input.readUTF(), BillKind.valueOf(input.readUTF()), DisplayCurrency.valueOf(input.readUTF()), decimal(), LocalDate.parse(input.readUTF()), input.readUTF(), input.readUTF(), input.readLong())
            }
            require(input.available() == 0) { "资产文件存在无法识别的数据" }
            PortfolioData(assets, rates, snapshots, currency, dark, bills)
        }
    }
}
