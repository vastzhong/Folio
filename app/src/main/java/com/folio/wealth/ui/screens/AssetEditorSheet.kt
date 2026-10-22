package com.folio.wealth.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.folio.wealth.model.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetEditorSheet(asset: Asset?, kind: AssetKind, saving: Boolean, onSave: (Asset) -> Unit, onDismiss: () -> Unit, errorMessage: String? = null, currencyLocked: Boolean = false) {
    val seed = asset?.id ?: kind.name
    val id = rememberSaveable(seed) { asset?.id ?: UUID.randomUUID().toString() }
    var title by rememberSaveable(seed) { mutableStateOf(asset?.title.orEmpty()) }
    var symbol by rememberSaveable(seed) { mutableStateOf(asset?.symbol.orEmpty()) }
    var currencyName by rememberSaveable(seed) { mutableStateOf((asset?.currency ?: DisplayCurrency.CNY).name) }
    var balance by rememberSaveable(seed) { mutableStateOf(asset?.balance?.toPlainString().orEmpty()) }
    var buyPrice by rememberSaveable(seed) { mutableStateOf(asset?.buyPrice?.toPlainString().orEmpty()) }
    var boughtShares by rememberSaveable(seed) { mutableStateOf("") }
    var submitted by rememberSaveable(seed) { mutableStateOf(false) }
    val input = AssetInput(title, kind, DisplayCurrency.valueOf(currencyName), balance, buyPrice, boughtShares, symbol)
    val validation = input.errors().filterKeys { asset?.isInvestment != true || it in listOf("title", "symbol") }
    val errors = if (submitted) validation else emptyMap()

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding().testTag("asset_editor"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("${if (asset == null) "新增" else "编辑"}${if (kind == AssetKind.SAVINGS) "储蓄" else "股票 / ETF"}", style = MaterialTheme.typography.titleLarge) }
            item { EditorField(title, { title = it }, "资产名称", "title", errors, saving) }
            if (kind != AssetKind.SAVINGS) {
                item { EditorField(symbol, { symbol = it }, "股票代码（选填）", "symbol", errors, saving) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (kind == AssetKind.SAVINGS) "余额币种" else "股票计价币种", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DisplayCurrency.entries.forEach { currency ->
                            item(key = currency.name) {
                                FilterChip(selected = currencyName == currency.name, onClick = { currencyName = currency.name }, enabled = !saving && asset?.isInvestment != true && !currencyLocked, label = { Text(currency.name) }, modifier = Modifier.testTag("editor_currency_${currency.name}"))
                            }
                        }
                    }
                }
            }
            if (kind == AssetKind.SAVINGS) {
                if (currencyLocked) item { Text("该账户已关联账单，币种保持一致。修改余额相当于手动校准当前余额，不会重算旧账单。", style = MaterialTheme.typography.bodySmall) }
                item { EditorField(balance, { balance = it }, "储蓄余额", "balance", errors, saving, numeric = true) }
            } else if (asset == null) {
                item {
                    EditorField(buyPrice, { buyPrice = it }, "本次买入股价", "buyPrice", errors, saving, numeric = true)
                }
                item {
                    EditorField(boughtShares, { boughtShares = it }, "本次买入股数", "boughtShares", errors, saving, numeric = true)
                }
                item {
                    Text("首次买入价作为当前价；买入不自动扣减储蓄。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (errorMessage != null) item { Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item {
                Button(
                    onClick = { submitted = true; if (validation.isEmpty()) onSave(if (asset?.isInvestment == true) asset.copy(title = title.trim(), symbol = symbol.trim()) else input.toAsset(id)) },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save_asset"),
                ) { Text(if (saving) "正在保存…" else "保存资产") }
            }
            item { TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("取消") } }
        }
    }
}

@Composable
private fun EditorField(
    value: String, onChange: (String) -> Unit, label: String, field: String,
    errors: Map<String, String>, saving: Boolean, numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        enabled = !saving, singleLine = true, isError = errors.containsKey(field),
        supportingText = errors[field]?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag("input_$field"),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockActionSheet(asset: Asset, buying: Boolean, saving: Boolean, onSave: (Asset) -> Unit, onDismiss: () -> Unit, errorMessage: String? = null) {
    var price by rememberSaveable(asset.id, buying) { mutableStateOf(if (buying) "" else asset.currentPrice.toPlainString()) }
    var shares by rememberSaveable(asset.id, buying) { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val parsedPrice = parseDecimal(price)?.takeIf { !buying || it.signum() > 0 }
    val parsedShares = parseDecimal(shares)?.takeIf { it.signum() > 0 }
    val errors = buildMap {
        if (submitted && parsedPrice == null) put("tradePrice", if (buying) "请输入大于 0 的买入价" else "请输入非负的当前价格")
        if (submitted && buying && parsedShares == null) put("tradeShares", "请输入大于 0 的新增股数")
    }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).imePadding().testTag("stock_action"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(if (buying) "追加买入" else "更新当前价", style = MaterialTheme.typography.titleLarge) }
            item { Text("${asset.title} · ${asset.currency.name}", style = MaterialTheme.typography.bodyMedium) }
            item { EditorField(price, { price = it }, if (buying) "本次买入股价" else "当前价格（手动）", "tradePrice", errors, saving, true) }
            if (buying) item { EditorField(shares, { shares = it }, "本次新增股数", "tradeShares", errors, saving, true) }
            item {
                Text(if (buying) "累计股数与成本，更新当前价；储蓄需手动调整。" else "仅更新市值，不改变持股或成本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (errorMessage != null) item { Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item {
                Button(onClick = {
                    submitted = true
                    if (parsedPrice != null && (!buying || parsedShares != null)) {
                        val now = System.currentTimeMillis()
                        onSave(if (buying) asset.recordPurchase(Purchase(UUID.randomUUID().toString(), parsedPrice, requireNotNull(parsedShares), now)) else asset.copy(currentPrice = parsedPrice, updatedAt = now))
                    }
                }, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("save_stock_action")) { Text(if (saving) "正在保存…" else if (buying) "保存这笔买入" else "保存当前价") }
            }
            item { TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("取消") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeRateSheet(rates: ExchangeRates, saving: Boolean, onSave: (ExchangeRates) -> Unit, onDismiss: () -> Unit, errorMessage: String? = null) {
    var cny by rememberSaveable { mutableStateOf(rates.cnyToHkd.toPlainString()) }
    var usd by rememberSaveable { mutableStateOf(rates.usdToHkd.toPlainString()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val cnyRate = parseDecimal(cny)?.takeIf { it.signum() > 0 }
    val usdRate = parseDecimal(usd)?.takeIf { it.signum() > 0 }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("手动汇率", style = MaterialTheme.typography.titleLarge)
            Text("填写 1 单位外币折合多少港币。修改后，折算总额会更新，原币金额保持不变。", style = MaterialTheme.typography.bodySmall)
            EditorField(cny, { cny = it }, "1 CNY = 多少 HKD", "cny_rate", if (submitted && cnyRate == null) mapOf("cny_rate" to "请输入大于 0 的汇率，最多 8 位小数") else emptyMap(), saving, true)
            EditorField(usd, { usd = it }, "1 USD = 多少 HKD", "usd_rate", if (submitted && usdRate == null) mapOf("usd_rate" to "请输入大于 0 的汇率，最多 8 位小数") else emptyMap(), saving, true)
            if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { submitted = true; if (cnyRate != null && usdRate != null) onSave(ExchangeRates(cnyRate, usdRate)) }, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("save_rates")) { Text(if (saving) "正在保存…" else "保存汇率") }
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}
