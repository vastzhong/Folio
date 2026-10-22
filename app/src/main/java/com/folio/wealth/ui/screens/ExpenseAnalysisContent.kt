package com.folio.wealth.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.folio.wealth.model.*
import com.folio.wealth.ui.components.CompactCard
import com.folio.wealth.ui.format.nativeMoney
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

internal fun LazyListScope.expenseAnalysisItems(analysis: ExpenseAnalysis, currency: DisplayCurrency, onCategory: (String) -> Unit) {
    item(key = "expense_summary") {
        CompactCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("支出 · ${currency.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(nativeMoney(analysis.total, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("expense_total"))
                val previousLabel = if (analysis.samePeriod) "上月同期" else "上月"
                Text("$previousLabel ${nativeMoney(analysis.previousTotal, currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("expense_previous"))
                val percent = analysis.changePercent
                val comparison = when {
                    analysis.difference.signum() == 0 -> "较$previousLabel 无变化"
                    percent == null -> "$previousLabel 无支出"
                    else -> "较$previousLabel ${if (percent.signum() > 0) "增加" else "减少"} ${percent.abs().toPlainString()}%"
                }
                Text(comparison, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("expense_change"))
            }
        }
    }
    if (analysis.total.signum() == 0) {
        item { Text("暂无支出", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("analysis_empty")) }
    } else {
        item(key = "daily_chart") {
            CompactCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("每日开销", style = MaterialTheme.typography.titleMedium)
                    DailyExpenseChart(analysis.days, currency)
                }
            }
        }
        item { Text("分类占比", style = MaterialTheme.typography.titleMedium) }
        items(analysis.categories, key = { "category_${it.category}" }) { entry ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("expense_category_${entry.category}").clickable { onCategory(entry.category) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.category, style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(nativeMoney(entry.amount, currency), modifier = Modifier.testTag("category_amount_${entry.category}"))
                        Text("${entry.percent.toPlainString()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("category_percent_${entry.category}"))
                    }
                    LinearProgressIndicator(progress = { entry.percent.divide(BigDecimal(100)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
                }
            }
        }
    }
}

@Composable
private fun DailyExpenseChart(days: List<DailyExpense>, currency: DisplayCurrency) {
    if (days.isEmpty()) return
    var selected by rememberSaveable(days.first().date.toString()) { mutableStateOf(days.indexOfLast { it.amount.signum() > 0 }.coerceAtLeast(0)) }
    val index = selected.coerceIn(days.indices)
    val point = days[index]
    val label = "${point.date.format(DateTimeFormatter.ofPattern("M月d日"))} · ${nativeMoney(point.amount, currency)}"
    val peak = days.maxOf { it.amount }
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp).testTag("daily_expense_chart")
        .pointerInput(days) { detectTapGestures { offset -> selected = (offset.x / (size.width.toFloat() / days.size)).toInt().coerceIn(days.indices) } }
        .semantics {
            contentDescription = "每日开销"
            stateDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..days.lastIndex.toFloat(), (days.size - 2).coerceAtLeast(0))
            setProgress { selected = it.toInt().coerceIn(days.indices); true }
        }) {
        val slot = size.width / days.size
        val width = (slot * .65f).coerceAtLeast(1f)
        drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        days.forEachIndexed { dayIndex, day ->
            val ratio = if (peak.signum() == 0) 0f else day.amount.divide(peak, 8, RoundingMode.HALF_UP).toFloat()
            val height = (size.height - 4.dp.toPx()) * ratio
            if (height > 0f) drawRoundRect(if (dayIndex == index) primary else primary.copy(alpha = .45f), Offset(slot * dayIndex + (slot - width) / 2, size.height - height), Size(width, height), CornerRadius(2.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("1日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${days.last().date.dayOfMonth}日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("daily_expense_selected"))
}
