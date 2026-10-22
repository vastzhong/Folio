package com.folio.wealth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.folio.wealth.model.DisplayCurrency
import com.folio.wealth.model.ExchangeRates
import com.folio.wealth.ui.components.CompactCard
import com.folio.wealth.ui.components.ScreenTitle
import com.folio.wealth.ui.theme.FolioTheme

@Composable
fun SettingsScreen(
    currency: DisplayCurrency,
    onCurrencyChanged: (DisplayCurrency) -> Unit,
    isDark: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    rates: ExchangeRates = ExchangeRates(),
    onEditRates: () -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    busy: Boolean = false,
) {
    var showCsvHelp by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_scroll"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "title") { ScreenTitle("设置") }
        item(key = "currency") {
            CompactCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSectionTitle("显示币种")
                    Column(modifier = Modifier.selectableGroup()) {
                        DisplayCurrency.entries.forEachIndexed { index, item ->
                            SettingOption(
                                title = "${currencyName(item)} · ${item.name}",
                                selected = currency == item,
                                onClick = { onCurrencyChanged(item) },
                                modifier = Modifier.testTag("settings_currency_${item.name}"),
                            )
                            if (index != DisplayCurrency.entries.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
        item(key = "theme") {
            CompactCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSectionTitle("外观")
                    Column(modifier = Modifier.selectableGroup()) {
                        SettingOption(
                            title = "浅色模式",
                            selected = !isDark,
                            onClick = { onThemeChanged(false) },
                            modifier = Modifier.testTag("settings_light"),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingOption(
                            title = "深色模式",
                            selected = isDark,
                            onClick = { onThemeChanged(true) },
                            modifier = Modifier.testTag("settings_dark"),
                        )
                    }
                }
            }
        }
        item(key = "data") {
            CompactCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsSectionTitle("手动汇率")
                    SettingsKeyValue("1 USD", "${rates.usdToHkd.toPlainString()} HKD")
                    SettingsKeyValue("1 CNY", "${rates.cnyToHkd.toPlainString()} HKD")
                    OutlinedButton(onClick = onEditRates, modifier = Modifier.fillMaxWidth().testTag("edit_rates")) { Text("修改汇率") }
                }
            }
        }
        item(key = "backup") {
            CompactCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSectionTitle("数据备份")
                    OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("export_csv")) { Text("导出 CSV 备份") }
                    OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("import_csv")) { Text("导入 CSV 文件") }
                    Text("导入前预览，确认后替换全部数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showCsvHelp = !showCsvHelp }, modifier = Modifier.testTag("csv_format_help")) { Text(if (showCsvHelp) "收起格式说明" else "CSV 格式说明") }
                    if (showCsvHelp) Text("UTF-8 编码，保留导出文件的英文表头。日期 YYYY-MM-DD，金额使用小数点。账单 parent_id 对应储蓄 id，currency 必须相同；amount 填正数，kind 用 EXPENSE（支出）或 INCOME（收入）。储蓄 amount 是已计入账单后的当前余额。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "about") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Folio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("0.4.0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun currencyName(currency: DisplayCurrency): String = when (currency) {
    DisplayCurrency.HKD -> "港币"
    DisplayCurrency.CNY -> "人民币"
    DisplayCurrency.USD -> "美元"
}

@Composable
private fun SettingsSectionTitle(title: String, description: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingOption(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun SettingsKeyValue(left: String, right: String) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(left, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(right, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(left, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(right, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
            }
        }
    }
}

@Preview(name = "Settings · Light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SettingsLightPreview() {
    FolioTheme(isDark = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(DisplayCurrency.HKD, {}, false, {})
        }
    }
}

@Preview(name = "Settings · Dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SettingsDarkPreview() {
    FolioTheme(isDark = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(DisplayCurrency.HKD, {}, true, {})
        }
    }
}
