package com.folio.wealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.roundToInt

/** Asset values stay decimal until normalization, preserving small changes in large balances. */
@Composable
fun TrendChart(
    values: List<BigDecimal>,
    onPointSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueDescription: (BigDecimal) -> String = { "${it.stripTrailingZeros().toPlainString()} 港币" },
) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    val surface = MaterialTheme.colorScheme.surface
    val normalized = remember(values) {
        val minimum = values.minOrNull()
        val maximum = values.maxOrNull()
        if (minimum == null || maximum == null) {
            emptyList()
        } else {
            val span = maximum.subtract(minimum)
            if (span.signum() == 0) {
                values.map { 0.5f }
            } else {
                values.map { value ->
                    value.subtract(minimum).divide(span, MathContext.DECIMAL64).toFloat()
                }
            }
        }
    }
    var chosen by remember(values) { mutableIntStateOf(values.lastIndex) }
    val selected = if (values.isEmpty()) -1 else chosen.coerceIn(values.indices)
    val latestOnPointSelected by rememberUpdatedState(onPointSelected)
    val selectPoint: (Int) -> Boolean = { index ->
        if (values.isEmpty()) {
            false
        } else {
            chosen = index.coerceIn(values.indices)
            latestOnPointSelected(chosen)
            true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "资产价值趋势"
                stateDescription = if (selected < 0) {
                    "暂无历史数据"
                } else {
                    "第 ${selected + 1} 个时间点，共 ${values.size} 个，资产价值 ${valueDescription(values[selected])}"
                }
                customActions = buildList {
                    if (selected > 0) {
                        add(CustomAccessibilityAction("上一个时间点") { selectPoint(selected - 1) })
                    }
                    if (selected >= 0 && selected < values.lastIndex) {
                        add(CustomAccessibilityAction("下一个时间点") { selectPoint(selected + 1) })
                    }
                }
                if (values.size > 1) {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = selected.toFloat(),
                        range = 0f..values.lastIndex.toFloat(),
                        steps = (values.size - 2).coerceAtLeast(0),
                    )
                    setProgress { index ->
                        index.isFinite() && selectPoint(index.roundToInt())
                    }
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || selected < 0) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionDown -> selectPoint(selected - 1)
                        Key.DirectionRight, Key.DirectionUp -> selectPoint(selected + 1)
                        else -> false
                    }
                }
            }
            .focusable(enabled = values.isNotEmpty())
            .pointerInput(values) {
                detectTapGestures { position ->
                    if (values.isNotEmpty()) {
                        val inset = 8.dp.toPx()
                        val plotWidth = (size.width.toFloat() - inset * 2).coerceAtLeast(1f)
                        val fraction = ((position.x - inset) / plotWidth).coerceIn(0f, 1f)
                        selectPoint((fraction * values.lastIndex).roundToInt())
                    }
                }
            },
    ) {
        if (normalized.isEmpty()) {
            Text(
                text = "暂无历史数据",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Canvas(Modifier.matchParentSize()) {
                val left = 8.dp.toPx()
                val right = (size.width - left).coerceAtLeast(left)
                val top = 8.dp.toPx()
                val bottom = (size.height - 10.dp.toPx()).coerceAtLeast(top + 1f)
                val plotHeight = bottom - top
                val points = normalized.mapIndexed { index, value ->
                    Offset(
                        x = if (normalized.size == 1) {
                            (left + right) / 2f
                        } else {
                            left + index.toFloat() / normalized.lastIndex * (right - left)
                        },
                        y = top + (0.12f + (1f - value) * 0.76f) * plotHeight,
                    )
                }
                val dashes = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
                listOf(0.12f, 0.5f, 0.88f).forEach { fraction ->
                    val y = top + plotHeight * fraction
                    drawLine(
                        color = grid,
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 0.7.dp.toPx(),
                        pathEffect = dashes,
                    )
                }
                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(points.last().x, bottom)
                    lineTo(points.first().x, bottom)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0f)),
                        startY = top,
                        endY = bottom,
                    ),
                )
                drawPath(
                    path = linePath,
                    color = accent,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                val point = points[selected.coerceIn(points.indices)]
                drawLine(
                    color = accent.copy(alpha = 0.22f),
                    start = Offset(point.x, top),
                    end = Offset(point.x, bottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashes,
                )
                drawCircle(accent.copy(alpha = 0.1f), 10.dp.toPx(), center = point)
                drawCircle(surface, 6.dp.toPx(), center = point)
                drawCircle(accent, 3.6.dp.toPx(), center = point)
            }
        }
    }
}
