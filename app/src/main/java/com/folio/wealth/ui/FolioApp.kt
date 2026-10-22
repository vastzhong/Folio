package com.folio.wealth.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.folio.wealth.model.AssetKind
import com.folio.wealth.model.DisplayCurrency
import com.folio.wealth.model.HistoryRange
import com.folio.wealth.model.SamplePortfolio
import com.folio.wealth.model.PortfolioData
import com.folio.wealth.data.PortfolioStore
import com.folio.wealth.data.LocalPortfolioStore
import com.folio.wealth.data.PortfolioCsv
import com.folio.wealth.model.Bill
import com.folio.wealth.model.saveBill
import com.folio.wealth.model.deleteBill
import com.folio.wealth.model.preferredBillAccount
import com.folio.wealth.ui.screens.BillsScreen
import com.folio.wealth.ui.screens.BillEditorSheet
import com.folio.wealth.ui.screens.AssetEditorSheet
import com.folio.wealth.ui.screens.ExchangeRateSheet
import com.folio.wealth.ui.screens.StockActionSheet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.folio.wealth.ui.screens.AssetDetailScreen
import com.folio.wealth.ui.screens.AssetsScreen
import com.folio.wealth.ui.screens.OverviewScreen
import com.folio.wealth.ui.screens.SettingsScreen
import com.folio.wealth.ui.theme.FolioTheme

private enum class Page(val label: String) {
    OVERVIEW("总览"), ASSETS("资产"), BILLS("账单"), SETTINGS("设置")
}

/** Local portfolio and ledger navigation; only committed saves update the displayed data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolioApp(store: PortfolioStore? = null) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val entryPreferences = remember(context, store, preview) { if (store == null && !preview) context.getSharedPreferences("bill-entry", Context.MODE_PRIVATE) else null }
    var lastBillAccountId by rememberSaveable { mutableStateOf(entryPreferences?.getString("last_account_id", null)) }
    val repository = remember(context, store, preview) { store ?: if (preview) null else LocalPortfolioStore(context) }
    var portfolio by remember(repository) { mutableStateOf(if (preview) PortfolioData(assets = SamplePortfolio.assets).recordToday() else PortfolioData()) }
    var loading by remember(repository) { mutableStateOf(!preview) }
    var loadError by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var transferring by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<PortfolioData?>(null) }
    var billEditor by rememberSaveable { mutableStateOf(false) }
    var billsAnalysisMode by rememberSaveable { mutableStateOf(false) }
    var editingBillId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteBill by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var addExpanded by rememberSaveable { mutableStateOf(false) }
    var editorKindName by rememberSaveable { mutableStateOf<String?>(null) }
    var editingAssetId by rememberSaveable { mutableStateOf<String?>(null) }
    var editRates by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var stockAction by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(repository, retry) {
        if (preview) return@LaunchedEffect
        loading = true
        loadError = false
        try {
            portfolio = withContext(Dispatchers.IO) {
                val loaded = requireNotNull(repository).load()
                val current = loaded.recordToday()
                if (current != loaded) repository.save(current)
                current
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            loadError = true
        } finally { loading = false }
    }

    fun persist(candidate: PortfolioData, recordSnapshot: Boolean = false, onSaved: () -> Unit = {}) {
        if (loading || saving || transferring || loadError) return
        saving = true
        saveError = null
        scope.launch {
            try {
                val updated = if (recordSnapshot) candidate.recordToday() else candidate
                withContext(Dispatchers.IO) { repository?.save(updated) }
                portfolio = updated
                onSaved()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                saveError = "保存失败，原有数据已保留，请重试。"
                scope.launch { snackbar.showSnackbar("保存失败，原有数据已保留，请重试。") }
            } finally { saving = false }
        }
    }

    val darkMode = portfolio.darkMode
    var pageName by rememberSaveable { mutableStateOf(Page.OVERVIEW.name) }
    var filterName by rememberSaveable { mutableStateOf("ALL") }
    var rangeName by rememberSaveable { mutableStateOf(HistoryRange.MONTH.name) }
    var selectedAssetId by rememberSaveable { mutableStateOf<String?>(null) }
    var currencyExpanded by remember { mutableStateOf(false) }

    val currency = portfolio.displayCurrency
    val page = Page.valueOf(pageName)
    val range = HistoryRange.valueOf(rangeName)
    val kindFilter = if (filterName == "ALL") null else AssetKind.valueOf(filterName)
    val chosen = portfolio.assets.find { it.id == selectedAssetId }
    val today = LocalDate.now()
    val snapshots = portfolio.history(range, today)
    val dateFormat = DateTimeFormatter.ofPattern("M月d日")
    val activity = LocalContext.current.folioActivity()
    val largeFont = LocalDensity.current.fontScale > 1.25f
    val accounts = portfolio.assets.filter { it.kind == AssetKind.SAVINGS }
    val chosenBill = portfolio.bills.find { it.id == editingBillId }
    val busy = saving || transferring
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            if (loading || loadError || busy) message("数据尚未就绪，请稍后重新导出。")
            else {
                val snapshot = portfolio
                transferring = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val bytes = PortfolioCsv.encode(snapshot)
                            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法打开导出文件" }.use { it.write(bytes) }
                        }
                        message("CSV 备份已导出")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        message("导出失败，请重新选择保存位置。")
                    } finally { transferring = false }
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !loading && !loadError && !busy) {
            transferring = true
            scope.launch {
                try {
                    pendingImport = withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件" }.use { input ->
                            val bytes = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(bytes.size() + count <= PortfolioCsv.MAX_BYTES) { "CSV 文件不能超过 8 MB" }
                                bytes.write(buffer, 0, count)
                            }
                            PortfolioCsv.decode(bytes.toByteArray())
                        }
                    }
                    saveError = null
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    message("导入失败，现有数据已保留：${e.message.orEmpty().take(160)}")
                } finally { transferring = false }
            }
        }
    }

    SideEffect {
        val transparent = android.graphics.Color.TRANSPARENT
        activity?.enableEdgeToEdge(
            statusBarStyle = if (darkMode) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
            navigationBarStyle = if (darkMode) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
        )
    }

    BackHandler(enabled = page != Page.OVERVIEW || chosen != null) {
        when {
            chosen != null -> selectedAssetId = null
            page != Page.OVERVIEW -> pageName = Page.OVERVIEW.name
        }
    }

    FolioTheme(isDark = darkMode) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (!loading && !loadError && page != Page.SETTINGS && chosen == null && (page != Page.BILLS || !billsAnalysisMode)) {
                    FloatingActionButton(onClick = {
                        if (!busy) {
                            saveError = null
                            if (page == Page.BILLS) {
                                if (accounts.isEmpty()) { editingAssetId = null; editorKindName = AssetKind.SAVINGS.name }
                                else { editingBillId = null; billEditor = true }
                            } else addExpanded = true
                        }
                    }, modifier = Modifier.testTag(if (page == Page.BILLS) "add_bill" else "add_asset")) {
                        Icon(Icons.Filled.Add, contentDescription = if (page == Page.BILLS) "记一笔收支" else "新增储蓄或股票")
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Folio", style = if (largeFont) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { persist(portfolio.copy(darkMode = !darkMode)) },
                            enabled = !loading && !busy && !loadError,
                            modifier = Modifier.testTag("theme_toggle"),
                        ) {
                            Icon(
                                if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (darkMode) "切换浅色模式" else "切换深色模式",
                            )
                        }
                        Box {
                            OutlinedButton(
                                onClick = { currencyExpanded = true },
                                enabled = !loading && !busy && !loadError,
                                modifier = Modifier.testTag("currency_button"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text(currency.name, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 3.dp).size(18.dp))
                            }
                            DropdownMenu(
                                expanded = currencyExpanded,
                                onDismissRequest = { currencyExpanded = false },
                            ) {
                                DisplayCurrency.entries.forEach { candidate ->
                                    DropdownMenuItem(
                                        text = { Text("${candidate.name} · ${candidate.symbol}") },
                                        onClick = {
                                            persist(portfolio.copy(displayCurrency = candidate))
                                            currencyExpanded = false
                                        },
                                        modifier = Modifier.testTag("currency_${candidate.name}"),
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { pageName = Page.SETTINGS.name; selectedAssetId = null }, enabled = !loading && !loadError, modifier = Modifier.testTag("open_settings")) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置与数据备份")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(Page.OVERVIEW, Page.ASSETS, Page.BILLS).forEach { destination ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = {
                                if (page != destination) {
                                    pageName = destination.name
                                    selectedAssetId = null
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            icon = {
                                Icon(
                                    when (destination) {
                                        Page.OVERVIEW -> Icons.Filled.Home
                                        Page.ASSETS -> Icons.Filled.AccountBalanceWallet
                                        Page.SETTINGS -> Icons.Filled.Settings
                                        Page.BILLS -> Icons.AutoMirrored.Filled.ReceiptLong
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(destination.label) },
                            modifier = Modifier.testTag("nav_${destination.name.lowercase()}"),
                        )
                    }
                }
            },
        ) { innerPadding: PaddingValues ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    loadError -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂时无法读取本地资产", style = MaterialTheme.typography.titleMedium)
                        Text("原有文件已保留。请重试；当前不会用空数据覆盖你的资产。", textAlign = TextAlign.Center)
                        Button(onClick = { retry++ }) { Text("重新读取") }
                    }
                    else -> when (page) {
                    Page.OVERVIEW -> OverviewScreen(
                        assets = portfolio.assets,
                        history = snapshots.map { com.folio.wealth.model.PortfolioMath.convertFromHkd(it.valueHkd, currency, it.rates) },
                        range = range,
                        currency = currency,
                        onRangeChanged = { rangeName = it.name },
                        onViewKind = { kind ->
                            filterName = kind.name
                            selectedAssetId = null
                            pageName = Page.ASSETS.name
                        },
                        rates = portfolio.rates,
                        snapshotLabel = "${today.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))} · 手动行情",
                        historyStartLabel = snapshots.firstOrNull()?.date?.format(dateFormat) ?: "尚无记录",
                        historyEndLabel = snapshots.lastOrNull()?.date?.format(dateFormat) ?: "今天",
                    )
                    Page.ASSETS -> {
                        if (chosen != null) {
                            AssetDetailScreen(
                                asset = chosen,
                                currency = currency,
                                onBack = { selectedAssetId = null },
                                rates = portfolio.rates,
                                onEdit = { if (!saving) { editingAssetId = chosen.id; editorKindName = chosen.kind.name } },
                                onDelete = { if (!saving) confirmDelete = true },
                                onAddPurchase = { if (!saving) stockAction = "BUY" },
                                onUpdatePrice = { if (!saving) stockAction = "QUOTE" },
                            )
                        } else {
                            AssetsScreen(
                                assets = portfolio.assets,
                                currency = currency,
                                filter = kindFilter,
                                onFilterChanged = { filterName = it?.name ?: "ALL" },
                                onAssetClick = { selectedAssetId = it },
                                rates = portfolio.rates,
                            )
                        }
                    }
                    Page.SETTINGS -> SettingsScreen(
                        currency = currency,
                        onCurrencyChanged = { persist(portfolio.copy(displayCurrency = it)) },
                        isDark = darkMode,
                        onThemeChanged = { persist(portfolio.copy(darkMode = it)) },
                        rates = portfolio.rates,
                        onEditRates = { if (!saving) editRates = true },
                        busy = busy,
                        onExport = {
                            runCatching { exportLauncher.launch("Folio-${java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.csv") }.onFailure { message("无法打开文件选择器，请重试。") }
                        },
                        onImport = {
                            runCatching { importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }.onFailure { message("无法打开文件选择器，请重试。") }
                        },
                    )
                    Page.BILLS -> BillsScreen(portfolio.bills, accounts, onEdit = { if (!busy) { editingBillId = it.id; saveError = null; billEditor = true } }, onAddSavings = { if (!busy) { editingAssetId = null; editorKindName = AssetKind.SAVINGS.name } }, analysisMode = billsAnalysisMode, onAnalysisModeChanged = { billsAnalysisMode = it })
                }
                }
            }
        }
        if (addExpanded) {
            AlertDialog(
                onDismissRequest = { addExpanded = false },
                title = { Text("新增资产") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { addExpanded = false; editingAssetId = null; editorKindName = AssetKind.SAVINGS.name }, modifier = Modifier.fillMaxWidth().testTag("add_savings")) { Text("储蓄余额") }
                        OutlinedButton(onClick = { addExpanded = false; editingAssetId = null; editorKindName = AssetKind.STOCK.name }, modifier = Modifier.fillMaxWidth().testTag("add_stock")) { Text("股票 / ETF") }
                    }
                },
                confirmButton = { TextButton(onClick = { addExpanded = false }) { Text("取消") } },
            )
        }
        if (!loading && !loadError && editorKindName != null) {
            AssetEditorSheet(
                asset = portfolio.assets.find { it.id == editingAssetId },
                kind = AssetKind.valueOf(requireNotNull(editorKindName)), saving = saving,
                onDismiss = { editorKindName = null; saveError = null },
                errorMessage = saveError,
                currencyLocked = portfolio.bills.any { it.accountId == editingAssetId },
                onSave = { asset ->
                    val assets = if (portfolio.assets.any { it.id == asset.id }) portfolio.assets.map { if (it.id == asset.id) asset else it } else portfolio.assets + asset
                    persist(portfolio.copy(assets = assets), recordSnapshot = true) {
                        editorKindName = null
                        selectedAssetId = asset.id
                        pageName = Page.ASSETS.name
                    }
                },
            )
        }
        if (editRates && !loading && !loadError) {
            ExchangeRateSheet(portfolio.rates, saving = saving, onDismiss = { editRates = false; saveError = null }, onSave = { rates -> persist(portfolio.copy(rates = rates), recordSnapshot = true) { editRates = false } }, errorMessage = saveError)
        }
        if (stockAction != null && chosen?.isInvestment == true) {
            StockActionSheet(chosen, buying = stockAction == "BUY", saving = saving, onDismiss = { stockAction = null; saveError = null }, errorMessage = saveError, onSave = { updated ->
                persist(portfolio.copy(assets = portfolio.assets.map { if (it.id == updated.id) updated else it }), recordSnapshot = true) { stockAction = null }
            })
        }
        if (confirmDelete && chosen != null) {
            AlertDialog(
                onDismissRequest = { if (!saving) confirmDelete = false },
                title = { Text("删除${chosen.title}？") },
                text = { Text(if (portfolio.bills.any { it.accountId == chosen.id }) "这个储蓄账户已关联账单。请先处理相关账单，再删除账户，以免丢失收支记录。" else "这项持仓及买入记录会被删除。历史按日记录：当天总额会更新，更早日期的记录保留。") },
                confirmButton = {
                    if (portfolio.bills.none { it.accountId == chosen.id }) TextButton(enabled = !saving, onClick = { persist(portfolio.copy(assets = portfolio.assets.filter { it.id != chosen.id }), recordSnapshot = true) { confirmDelete = false; selectedAssetId = null } }, modifier = Modifier.testTag("confirm_delete")) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    else TextButton(onClick = { confirmDelete = false }) { Text("知道了") }
                },
                dismissButton = { TextButton(enabled = !saving, onClick = { confirmDelete = false }) { Text("取消") } },
            )
        }
        if (billEditor && accounts.isNotEmpty()) {
            BillEditorSheet(chosenBill, accounts, saving, saveError, defaultAccountId = preferredBillAccount(accounts, portfolio.bills, lastBillAccountId), onSave = { bill ->
                try { persist(portfolio.saveBill(bill), recordSnapshot = true) {
                    lastBillAccountId = bill.accountId
                    entryPreferences?.edit()?.putString("last_account_id", bill.accountId)?.apply()
                    billEditor = false; editingBillId = null
                } }
                catch (e: IllegalArgumentException) { saveError = e.message ?: "无法保存账单" }
            }, onDelete = { confirmDeleteBill = true }, onDismiss = { if (!saving) { billEditor = false; saveError = null } })
        }
        if (confirmDeleteBill && chosenBill != null) {
            AlertDialog(onDismissRequest = { if (!saving) confirmDeleteBill = false }, title = { Text("删除这笔${chosenBill.kind.label}？") }, text = { Text("将撤销这笔收支对储蓄余额的影响。删除收入时，如果余额不足以撤销，操作会被取消。") }, confirmButton = {
                TextButton(enabled = !saving, modifier = Modifier.testTag("confirm_delete_bill"), onClick = {
                    try { persist(portfolio.deleteBill(chosenBill.id), recordSnapshot = true) { confirmDeleteBill = false; billEditor = false; editingBillId = null } }
                    catch (e: IllegalArgumentException) { saveError = e.message; confirmDeleteBill = false }
                }) { Text("删除并修正余额", color = MaterialTheme.colorScheme.error) }
            }, dismissButton = { TextButton(enabled = !saving, onClick = { confirmDeleteBill = false }) { Text("取消") } })
        }
        pendingImport?.let { incoming ->
            AlertDialog(onDismissRequest = { if (!saving) pendingImport = null }, title = { Text("导入 CSV 备份？") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("已校验：${incoming.assets.size} 项资产、${incoming.assets.sumOf { it.purchases.size }} 笔买入、${incoming.bills.size} 笔账单、${incoming.snapshots.size} 天历史。")
                    Text("确认后将替换本机全部数据（当前 ${portfolio.assets.size} 项资产、${portfolio.bills.size} 笔账单），包括汇率和偏好。不会合并，也不会再次扣减账单金额。")
                    if (saveError != null) Text(requireNotNull(saveError), color = MaterialTheme.colorScheme.error)
                }
            }, confirmButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("confirm_import"), onClick = { persist(incoming) { pendingImport = null; selectedAssetId = null; message("数据导入完成") } }) { Text(if (saving) "正在导入…" else "替换并导入") } }, dismissButton = { TextButton(enabled = !saving, onClick = { pendingImport = null }) { Text("取消") } })
        }
    }
}

private tailrec fun Context.folioActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.folioActivity()
    else -> null
}

@Preview(name = "Folio · Phone", showBackground = true, widthDp = 393, heightDp = 780)
@Preview(name = "Folio · Narrow", showBackground = true, widthDp = 320, heightDp = 720)
@Preview(name = "Folio · Large text", showBackground = true, widthDp = 393, heightDp = 850, fontScale = 1.5f)
@Composable
private fun FolioAppPreview() {
    FolioApp()
}
