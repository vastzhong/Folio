package com.folio.wealth.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.folio.wealth.model.Asset
import com.folio.wealth.model.AssetKind
import com.folio.wealth.model.DisplayCurrency
import com.folio.wealth.model.ExchangeRates
import com.folio.wealth.model.PortfolioMath
import com.folio.wealth.model.SamplePortfolio
import com.folio.wealth.ui.components.AssetIcon
import com.folio.wealth.ui.components.AssetRow
import com.folio.wealth.ui.components.BreakdownBar
import com.folio.wealth.ui.components.CompactCard
import com.folio.wealth.ui.components.ScreenTitle
import com.folio.wealth.ui.format.money
import com.folio.wealth.ui.format.nativeMoney
import com.folio.wealth.ui.format.priceMoney
import com.folio.wealth.ui.format.signedPercentage
import com.folio.wealth.ui.theme.FolioTheme

@Composable
fun AssetsScreen(
    assets: List<Asset>, currency: DisplayCurrency,
    filter: AssetKind?, onFilterChanged: (AssetKind?) -> Unit,
    onAssetClick: (String) -> Unit,
    rates: ExchangeRates = ExchangeRates(),
) {
    val visible = assets.filter { filter == null || it.kind == filter }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("assets_scroll"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") { ScreenTitle("我的资产") }
        item(key = "summary") {
            CompactCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                        "总资产",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CurrencyLabel(currency)
                    }
                    AssetValuation(money(PortfolioMath.totalHkd(assets, rates), currency, rates))
                    if (assets.isNotEmpty()) BreakdownBar(assets, rates)
                    listOf(DisplayCurrency.CNY, DisplayCurrency.HKD, DisplayCurrency.USD).forEach { original ->
                        Text("原币 ${original.name} · ${nativeMoney(PortfolioMath.nativeTotal(assets, original), original)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") {
                    AssetFilter(
                        label = "全部 ${assets.size}",
                        selected = filter == null,
                        onClick = { onFilterChanged(null) },
                        modifier = Modifier.testTag("filter_all"),
                    )
                }
                items(AssetKind.entries, key = { it.name }) { kind ->
                    AssetFilter(
                        label = "${kind.label} ${assets.count { it.kind == kind }}",
                        selected = filter == kind,
                        onClick = { onFilterChanged(kind) },
                        modifier = Modifier.testTag("filter_${kind.name}"),
                    )
                }
            }
        }
        item(key = "section_summary") {
            Column(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${filter?.label ?: "全部持仓"} · ${visible.size} 项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "合计 ${money(PortfolioMath.totalHkd(visible, rates), currency, rates)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                CompactCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("暂无${filter?.label ?: "资产"}持仓", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (assets.isEmpty()) "点击右下角 +，添加储蓄或股票。" else "选择其他分类，查看已有资产。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        items(visible, key = { it.id }, contentType = { "asset" }) { asset ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                AssetRow(
                    asset = asset,
                    currency = currency,
                    onClick = { onAssetClick(asset.id) },
                    modifier = Modifier.testTag("asset_${asset.id}"),
                    rates = rates,
                )
            }
        }
    }
}

@Composable
private fun AssetFilter(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier,
    )
}

@Composable
private fun CurrencyLabel(currency: DisplayCurrency) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            currency.name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AssetValuation(value: String) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Text(
            value,
            style = if (maxWidth < 310.dp || fontScale > 1.2f) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.headlineLarge
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun AssetDetailScreen(asset: Asset, currency: DisplayCurrency, onBack: () -> Unit, rates: ExchangeRates = ExchangeRates(), onEdit: () -> Unit = {}, onDelete: () -> Unit = {}, onAddPurchase: () -> Unit = {}, onUpdatePrice: () -> Unit = {}) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("asset_detail"),
    ) {
        item(key = "navigation") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back_assets")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回资产列表")
                }
                Text(
                    "资产详情",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item(key = "valuation") {
            CompactCard {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssetIcon(asset.kind)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(asset.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(asset.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${if (asset.isInvestment) "当前持仓市值" else "储蓄余额"} · ${asset.currency.name}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        AssetValuation(nativeMoney(asset.nativeValue, asset.currency))
                        Text("折合 ${money(asset.estimatedHkd(rates), currency, rates)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (asset.isInvestment) {
                            Text(
                                "未实现盈亏 ${nativeMoney(asset.unrealizedPnl, asset.currency)} · ${asset.pnlPercent?.let { signedPercentage(it) } ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (asset.unrealizedPnl.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("holding_pnl"),
                            )
                        }
                    }
                }
            }
        }
        item(key = "details") {
            CompactCard {
                Text("持仓信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DetailRow("资产类别", asset.kind.label)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow(if (asset.kind == AssetKind.SAVINGS) "余额类型" else "持有数量", asset.unitsLabel)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (asset.isInvestment) {
                    DetailRow("平均买入成本（加权）", priceMoney(asset.buyPrice, asset.currency))
                    DetailRow("当前价格（手动）", priceMoney(asset.currentPrice, asset.currency))
                    DetailRow("当前持股数", asset.currentShares.stripTrailingZeros().toPlainString())
                    DetailRow("当前持仓成本", nativeMoney(asset.holdingCost, asset.currency))
                } else DetailRow("原币余额", nativeMoney(asset.balance, asset.currency))
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (asset.isInvestment) {
                    Button(onClick = onAddPurchase, modifier = Modifier.fillMaxWidth().testTag("add_purchase")) { Text("追加买入") }
                    Button(onClick = onUpdatePrice, modifier = Modifier.fillMaxWidth().testTag("update_price")) { Text("更新当前价") }
                }
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().testTag("edit_asset")) { Text(if (asset.isInvestment) "编辑名称 / 代码" else "编辑储蓄余额") }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().testTag("delete_asset")) { Text("删除这项资产", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (asset.isInvestment) {
            item(key = "purchase_title") { Text("买入记录 · ${asset.purchases.size} 笔", style = MaterialTheme.typography.titleMedium) }
            items(asset.purchases.asReversed(), key = { "purchase_${it.id}" }) { buy ->
                CompactCard {
                    Text("${buy.shares.stripTrailingZeros().toPlainString()} 股 × ${priceMoney(buy.price, asset.currency)}", style = MaterialTheme.typography.titleSmall)
                    Text("买入金额 ${nativeMoney(buy.price.multiply(buy.shares), asset.currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (buy.recordedAt > 0) Text("录入时间 " + java.time.Instant.ofEpochMilli(buy.recordedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "note") {
            Text(
                (if (asset.isInvestment) "股数和成本按买入记录累计。未实现盈亏 = 当前市值 − 累计买入成本；当前仅支持买入，不含卖出、手续费和分红。行情价由你手动更新。" else "储蓄按录入的原币余额保存，汇率变化只影响折算估值。") +
                    if (asset.updatedAt > 0) "\n最近保存：" + java.time.Instant.ofEpochMilli(asset.updatedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        if (maxWidth < 300.dp || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    modifier = Modifier.weight(1.1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Preview(name = "Assets · Light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AssetsLightPreview() {
    FolioTheme(isDark = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AssetsScreen(SamplePortfolio.assets, DisplayCurrency.HKD, null, {}, {})
        }
    }
}

@Preview(name = "Asset detail · Dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AssetDetailDarkPreview() {
    FolioTheme(isDark = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AssetDetailScreen(SamplePortfolio.assets.first { it.id == "qqqm" }, DisplayCurrency.HKD, {})
        }
    }
}
