package com.folio.wealth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.folio.wealth.model.Asset
import com.folio.wealth.model.AssetKind
import com.folio.wealth.model.DisplayCurrency
import com.folio.wealth.model.ExchangeRates
import com.folio.wealth.model.HistoryRange
import com.folio.wealth.model.PortfolioMath
import com.folio.wealth.ui.components.AssetIcon
import com.folio.wealth.ui.components.BreakdownBar
import com.folio.wealth.ui.components.CompactCard
import com.folio.wealth.ui.components.ScreenTitle
import com.folio.wealth.ui.components.TrendChart
import com.folio.wealth.ui.format.money
import com.folio.wealth.ui.format.nativeMoney
import com.folio.wealth.ui.format.signedPercentage
import java.math.BigDecimal

@Composable
fun OverviewScreen(
    assets: List<Asset>,
    history: List<BigDecimal>,
    range: HistoryRange,
    currency: DisplayCurrency,
    onRangeChanged: (HistoryRange) -> Unit,
    onViewKind: (AssetKind) -> Unit,
    rates: ExchangeRates = ExchangeRates(),
    snapshotLabel: String = "本地资产 · 行情手动更新",
    historyStartLabel: String = range.startLabel,
    historyEndLabel: String = "今天",
) {
    val total = PortfolioMath.totalHkd(assets, rates)
    val change = PortfolioMath.changeAmount(history)
    val hasChange = history.size >= 2
    val rangeCaption = when (range) {
        HistoryRange.WEEK -> "近7天"
        HistoryRange.MONTH -> "近1个月"
        HistoryRange.QUARTER -> "近3个月"
        HistoryRange.YEAR -> "近1年"
    }
    val percentage = if (hasChange && history.first().signum() != 0) {
        signedPercentage(PortfolioMath.changePercent(history))
    } else "—"
    var graphIndex by remember(range, history) { mutableIntStateOf(history.lastIndex) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("overview_scroll"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { ScreenTitle("资产总览", snapshotLabel) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("总资产估值 · ${currency.name}", style = MaterialTheme.typography.labelMedium)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val amountStyle = when {
                                LocalDensity.current.fontScale > 1.25f -> MaterialTheme.typography.headlineSmall
                                maxWidth < 280.dp -> MaterialTheme.typography.headlineMedium
                                else -> MaterialTheme.typography.displaySmall
                            }
                            Text(money(total, currency, rates), style = amountStyle, modifier = Modifier.testTag("total_value"))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .12f), shape = RoundedCornerShape(10.dp)) {
                            Text(
                                "$percentage · ${rangeCaption}资产变化",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).testTag("range_change_percent"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            when {
                                !hasChange -> "暂无区间变化数据"
                                change.signum() < 0 -> "区间减少 ${nativeMoney(change.abs(), currency)}"
                                change.signum() == 0 -> "区间资产价值持平"
                                else -> "区间增加 ${nativeMoney(change, currency)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("range_change_amount"),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .12f))
                    Text("资产价值趋势", style = MaterialTheme.typography.titleSmall)
                    RangeSelector(range, onRangeChanged)
                    TrendChart(
                        history,
                        onPointSelected = { graphIndex = it },
                        modifier = Modifier.testTag("asset_history_chart"),
                        valueDescription = { nativeMoney(it, currency) },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(historyStartLabel, style = MaterialTheme.typography.labelSmall)
                        Text(historyEndLabel, style = MaterialTheme.typography.labelSmall)
                    }
                    val selected = history.getOrNull(graphIndex)
                    Text(
                        if (selected == null) "新增资产后开始记录历史" else "记录 ${graphIndex + 1}/${history.size} · ${nativeMoney(selected, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("graph_point_value"),
                    )
                }
            }
        }
        item {
            Text("资产变化含收支及汇率影响", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            CompactCard {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("资产分布", style = MaterialTheme.typography.titleMedium)
                    }
                    BreakdownBar(assets, rates)
                    AssetKind.entries.forEachIndexed { index, kind ->
                        AllocationRow(assets, kind, currency, rates, onClick = { onViewKind(kind) })
                        if (index < AssetKind.entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(range: HistoryRange, onRangeChanged: (HistoryRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = .35f))
            .selectableGroup().padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HistoryRange.entries.forEach { candidate ->
            val selected = candidate == range
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onRangeChanged(candidate) })
                    .testTag("time_${candidate.name}").padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(candidate.label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun AllocationRow(assets: List<Asset>, kind: AssetKind, currency: DisplayCurrency, rates: ExchangeRates, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("distribution_${kind.name}").padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssetIcon(kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(kind.label, style = MaterialTheme.typography.titleSmall)
            Text(money(PortfolioMath.byKindHkd(assets, kind, rates), currency, rates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${PortfolioMath.sharePercent(assets, kind, rates)}%", style = MaterialTheme.typography.labelLarge)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
