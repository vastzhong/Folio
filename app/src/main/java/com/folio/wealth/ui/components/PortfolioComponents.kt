package com.folio.wealth.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StackedBarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.wealth.model.Asset
import com.folio.wealth.model.AssetKind
import com.folio.wealth.model.DisplayCurrency
import com.folio.wealth.model.ExchangeRates
import com.folio.wealth.model.PortfolioMath
import com.folio.wealth.ui.format.money
import com.folio.wealth.ui.format.nativeMoney
import com.folio.wealth.ui.format.signedPercentage
import com.folio.wealth.ui.theme.FundAmber
import com.folio.wealth.ui.theme.SavingsGreen
import com.folio.wealth.ui.theme.StockBlue
import java.math.BigDecimal
import java.math.RoundingMode

fun colorForKind(kind: AssetKind) = when (kind) {
    AssetKind.SAVINGS -> SavingsGreen
    AssetKind.STOCK -> StockBlue
    AssetKind.FUND -> FundAmber
}

@Composable
fun CompactCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AssetIcon(kind: AssetKind) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(14.dp),
        color = colorForKind(kind).copy(alpha = .16f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when (kind) {
                    AssetKind.SAVINGS -> Icons.Outlined.AccountBalance
                    AssetKind.STOCK -> Icons.Outlined.ShowChart
                    AssetKind.FUND -> Icons.Outlined.StackedBarChart
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
fun AssetRow(asset: Asset, currency: DisplayCurrency, onClick: () -> Unit, modifier: Modifier = Modifier, rates: ExchangeRates = ExchangeRates()) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.25f
        Column(
            modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AssetIcon(asset.kind)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(asset.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(asset.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!stacked) AssetAmount(asset, currency, rates, Alignment.End, Modifier.widthIn(max = 140.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            if (stacked) {
                Box(Modifier.padding(start = 54.dp)) { AssetAmount(asset, currency, rates, Alignment.Start) }
            }
        }
    }
}

@Composable
private fun AssetAmount(asset: Asset, currency: DisplayCurrency, rates: ExchangeRates, alignment: Alignment.Horizontal, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(nativeMoney(asset.nativeValue, asset.currency), style = MaterialTheme.typography.titleSmall)
        if (asset.currency != currency) Text("折合 ${money(asset.estimatedHkd(rates), currency, rates)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val pnl = asset.pnlPercent
        Text(
            if (!asset.isInvestment) "现金余额" else pnl?.let { "${signedPercentage(it)} · 持仓盈亏" } ?: "暂无持仓盈亏",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                pnl == null -> MaterialTheme.colorScheme.onSurfaceVariant
                pnl.signum() < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
        )
    }
}

@Composable
fun BreakdownBar(assets: List<Asset>, rates: ExchangeRates = ExchangeRates()) {
    val portions = AssetKind.entries.map { it to PortfolioMath.byKindHkd(assets, it, rates) }
        .filter { (_, value) -> value.signum() > 0 }
    val total = portions.fold(BigDecimal.ZERO) { sum, (_, value) -> sum.add(value) }
    val description = if (total.signum() == 0) "暂无资产分布" else portions.joinToString("，") { (kind, _) ->
        "${kind.label} ${PortfolioMath.sharePercent(assets, kind, rates)}%"
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        portions.forEach { (kind, value) ->
            val fraction = value.divide(total, 8, RoundingMode.HALF_UP).toFloat().coerceAtLeast(.000001f)
            Spacer(Modifier.weight(fraction).height(10.dp).background(colorForKind(kind)))
        }
    }
}
