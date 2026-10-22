package com.folio.wealth.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.folio.wealth.model.*
import com.folio.wealth.ui.components.CompactCard
import com.folio.wealth.ui.components.ScreenTitle
import com.folio.wealth.ui.format.nativeMoney
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun BillsScreen(bills: List<Bill>, accounts: List<Asset>, onEdit: (Bill) -> Unit, onAddSavings: () -> Unit, analysisMode: Boolean, onAnalysisModeChanged: (Boolean) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var analysisCurrencyName by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthText)
    val today = LocalDate.now()
    val listState = rememberLazyListState()
    LaunchedEffect(analysisMode, month, selectedCategory) { listState.scrollToItem(0) }
    val monthly = remember(bills, month) { bills.filter { YearMonth.from(it.date) == month } }
    val visible = remember(monthly, filter, selectedCategory) {
        monthly.filter { (filter == "ALL" || it.kind.name == filter) && (selectedCategory == null || it.category.trim() == selectedCategory) }
            .sortedWith(compareByDescending<Bill> { it.date }.thenByDescending { it.createdAt }.thenBy { it.id })
    }
    val analysisCurrency = analysisCurrencyName?.let(DisplayCurrency::valueOf)
        ?: monthly.filter { it.kind == BillKind.EXPENSE }.maxByOrNull { it.createdAt }?.currency
        ?: accounts.firstOrNull()?.currency ?: DisplayCurrency.CNY
    val analysis = remember(bills, month, analysisCurrency, today) { analyzeExpenses(bills, month, analysisCurrency, today) }
    // A category drill-down retains the analyzed currency as well as the category.
    val detailCurrency = if (selectedCategory != null) analysisCurrency else null
    val records = visible.filter { detailCurrency == null || (it.currency == detailCurrency && !it.date.isAfter(today)) }
    LazyColumn(Modifier.fillMaxSize().testTag("bills_scroll"), state = listState, contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenTitle("账单") }
        item {
            CompactCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { monthText = month.minusMonths(1).toString(); selectedCategory = null }, modifier = Modifier.testTag("bill_previous_month")) { Icon(Icons.Default.ChevronLeft, "上个月") }
                        Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("bill_month"))
                        IconButton(onClick = { monthText = month.plusMonths(1).toString(); selectedCategory = null }, enabled = month < YearMonth.now(), modifier = Modifier.testTag("bill_next_month")) { Icon(Icons.Default.ChevronRight, "下个月") }
                    }
                    if (!analysisMode) {
                        val currencies = DisplayCurrency.entries.filter { c -> monthly.any { it.currency == c } }
                        if (currencies.isEmpty()) Text("暂无收支", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        currencies.forEach { c ->
                            val income = monthly.filter { it.currency == c && it.kind == BillKind.INCOME }.fold(BigDecimal.ZERO) { sum, b -> sum + b.amount }
                            val expense = monthly.filter { it.currency == c && it.kind == BillKind.EXPENSE }.fold(BigDecimal.ZERO) { sum, b -> sum + b.amount }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(c.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Text("支出 ${nativeMoney(expense, c)}", modifier = Modifier.testTag("bill_expense_${c.name}"))
                                    Text("收入 ${nativeMoney(income, c)}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("bill_income_${c.name}"))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = !analysisMode, onClick = { onAnalysisModeChanged(false) }, label = { Text("明细") }, modifier = Modifier.testTag("bill_tab_details"))
                FilterChip(selected = analysisMode, onClick = { onAnalysisModeChanged(true); selectedCategory = null }, label = { Text("分析") }, modifier = Modifier.testTag("bill_tab_analysis"))
            }
        }
        if (analysisMode) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayCurrency.entries.forEach { c -> FilterChip(selected = analysisCurrency == c, onClick = { analysisCurrencyName = c.name }, label = { Text(c.name) }, modifier = Modifier.testTag("analysis_currency_${c.name}")) }
                }
            }
            expenseAnalysisItems(analysis, analysisCurrency, onCategory = {
                analysisCurrencyName = analysisCurrency.name
                selectedCategory = it; filter = "EXPENSE"; onAnalysisModeChanged(false)
            })
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("ALL" to "全部", "EXPENSE" to "支出", "INCOME" to "收入")) { (key, label) ->
                        FilterChip(selected = filter == key, onClick = { filter = key; selectedCategory = null }, label = { Text(label) }, modifier = Modifier.testTag("bill_filter_$key"))
                    }
                    selectedCategory?.let { category -> item { InputChip(selected = true, onClick = { selectedCategory = null }, label = { Text("$category · ${analysisCurrency.name} ×") }, modifier = Modifier.testTag("bill_category_filter")) } }
                }
            }
            if (accounts.isEmpty()) item {
                CompactCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("先添加储蓄账户", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onAddSavings, modifier = Modifier.testTag("bill_add_savings")) { Text("添加储蓄") }
                    }
                }
            }
            if (records.isEmpty() && accounts.isNotEmpty()) item { Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(records, key = { it.id }) { bill ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("bill_${bill.id}").clickable { onEdit(bill) }) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(bill.category, style = MaterialTheme.typography.titleMedium)
                        Text("${if (bill.kind == BillKind.INCOME) "+" else "−"}${nativeMoney(bill.amount, bill.currency)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = if (bill.kind == BillKind.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text("${bill.date} · ${accounts.find { it.id == bill.accountId }?.title.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (bill.note.isNotBlank()) Text(bill.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditorSheet(bill: Bill?, accounts: List<Asset>, saving: Boolean, errorMessage: String?, onSave: (Bill) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit, defaultAccountId: String? = null) {
    val focus = LocalFocusManager.current
    val seed = bill?.id ?: "new"
    val id = rememberSaveable(seed) { bill?.id ?: UUID.randomUUID().toString() }
    var kindText by rememberSaveable(seed) { mutableStateOf((bill?.kind ?: BillKind.EXPENSE).name) }
    var accountId by rememberSaveable(seed) { mutableStateOf(bill?.accountId ?: defaultAccountId ?: accounts.firstOrNull()?.id) }
    var amount by rememberSaveable(seed) { mutableStateOf(bill?.amount?.toPlainString().orEmpty()) }
    var category by rememberSaveable(seed) { mutableStateOf(bill?.category ?: "餐饮") }
    var customCategory by rememberSaveable(seed) { mutableStateOf(bill != null && bill.category !in commonBillCategories(bill.kind)) }
    var note by rememberSaveable(seed) { mutableStateOf(bill?.note.orEmpty()) }
    var dateText by rememberSaveable(seed) { mutableStateOf((bill?.date ?: LocalDate.now()).toString()) }
    var submitted by rememberSaveable(seed) { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var calendar by rememberSaveable(seed) { mutableStateOf(false) }
    val account = accounts.find { it.id == accountId }
    val number = parseDecimal(amount, 2)?.takeIf { it.signum() > 0 }
    val today = LocalDate.now()
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()?.takeIf { !it.isAfter(today) }
    val valid = number != null && account != null && date != null && category.trim().length in 1..40 && note.length <= 500
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).imePadding()) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("bill_editor"), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(if (bill == null) "记一笔" else "修改账单", style = MaterialTheme.typography.titleLarge) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BillKind.entries.forEach { kind -> FilterChip(selected = kindText == kind.name, onClick = {
                            if (kindText != kind.name) { kindText = kind.name; category = commonBillCategories(kind).first(); customCategory = false }
                        }, enabled = !saving, label = { Text(kind.label) }, modifier = Modifier.testTag("bill_kind_${kind.name}")) }
                    }
                }
                item {
                    OutlinedTextField(amount, { amount = it }, label = { Text("金额 · ${account?.currency?.name.orEmpty()}") }, singleLine = true, enabled = !saving, isError = submitted && number == null, supportingText = if (submitted && number == null) ({ Text("金额须大于 0，最多两位小数") }) else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("bill_amount"))
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        commonBillCategories(BillKind.valueOf(kindText)).forEach { item ->
                            FilterChip(selected = category == item && !customCategory, onClick = { category = item; customCategory = false; focus.clearFocus() }, enabled = !saving, label = { Text(item) }, modifier = Modifier.testTag("quick_category_$item"))
                        }
                        FilterChip(selected = customCategory, onClick = { if (!customCategory) category = ""; customCategory = true }, enabled = !saving, label = { Text("自定义") }, modifier = Modifier.testTag("bill_custom_category"))
                    }
                }
                if (customCategory) item {
                    OutlinedTextField(category, { category = it }, label = { Text("分类") }, enabled = !saving, singleLine = true, isError = submitted && category.trim().length !in 1..40, modifier = Modifier.fillMaxWidth().testTag("bill_category"))
                }
                item {
                    Box {
                        OutlinedButton(onClick = { focus.clearFocus(); expanded = true }, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("bill_account")) {
                            Text(account?.let { "${it.title} · ${nativeMoney(it.balance, it.currency)}" } ?: "选择账户")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            accounts.forEach { a -> DropdownMenuItem(text = { Text("${a.title} · ${a.currency.name}") }, onClick = { accountId = a.id; expanded = false }, modifier = Modifier.testTag("bill_account_${a.id}")) }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { focus.clearFocus(); calendar = true }, enabled = !saving, modifier = Modifier.weight(1f).testTag("bill_date")) {
                            Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(date?.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) ?: "选择日期")
                        }
                        TextButton(onClick = { dateText = today.toString() }, enabled = !saving, modifier = Modifier.testTag("bill_today")) { Text("今天") }
                    }
                }
                item {
                    OutlinedTextField(note, { note = it }, label = { Text("备注（选填）") }, enabled = !saving, maxLines = 3, isError = submitted && note.length > 500, modifier = Modifier.fillMaxWidth().testTag("bill_note"))
                }
                if (submitted && !valid) item { Text("请检查金额、分类和日期。", color = MaterialTheme.colorScheme.error) }
                if (errorMessage != null) item { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                if (bill != null) item { TextButton(onClick = onDelete, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("delete_bill")) { Text("删除账单", color = MaterialTheme.colorScheme.error) } }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                Button(onClick = {
                    submitted = true
                    if (valid) onSave(Bill(id, requireNotNull(account).id, BillKind.valueOf(kindText), account.currency, requireNotNull(number), requireNotNull(date), category.trim(), note, bill?.createdAt ?: System.currentTimeMillis()))
                }, enabled = !saving, modifier = Modifier.weight(1f).testTag("save_bill")) { Text(if (saving) "正在保存…" else "保存") }
            }
        }
    }
    if (calendar) {
        val selectedDate = date ?: today
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toEpochDay() * 86400000L,
            yearRange = minOf(1900, selectedDate.year)..maxOf(2100, today.year),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, modifier = Modifier.testTag("date_picker_confirm"), onClick = {
                picker.selectedDateMillis?.let { dateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                calendar = false
            }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { calendar = false }) { Text("取消") } }) {
            DatePicker(state = picker, modifier = Modifier.testTag("bill_calendar"), title = null, showModeToggle = false)
        }
    }
}
