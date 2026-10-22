package com.folio.wealth.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.folio.wealth.data.PortfolioCsv
import com.folio.wealth.data.PortfolioStore
import com.folio.wealth.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class FolioUiTest {
    @get:Rule val rule = createComposeRule()
    private class MemoryStore(initial: PortfolioData) : PortfolioStore {
        @Volatile var value = initial
        @Volatile var failSaves = false
        override fun load() = value
        override fun save(data: PortfolioData) { if (failSaves) throw java.io.IOException("test failure"); value = data }
    }
    private lateinit var store: MemoryStore
    private var documentOwner: ActivityResultRegistryOwner? = null
    private var documentUri: Uri? = null
    private fun useDocumentPickerStub() {
        documentOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(documentUri)) }
                }
            }
        }
    }
    private fun start(data: PortfolioData = PortfolioData()) {
        store = MemoryStore(data)
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides requireNotNull(documentOwner ?: LocalActivityResultRegistryOwner.current)) { FolioApp(store) }
        }
        await("total_value")
    }
    private fun await(tag: String) = rule.waitUntil(10000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitGone(tag: String) = rule.waitUntil(10000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    private fun scroll(container: String, tag: String) { rule.onNodeWithTag(container).performScrollToNode(hasTestTag(tag)) }
    private fun fill(tag: String, value: String) {
        scroll("asset_editor", "input_$tag")
        rule.onNodeWithTag("input_$tag").performTextReplacement(value)
    }
    private fun save() { scroll("asset_editor", "save_asset"); rule.onNodeWithTag("save_asset").performClick(); awaitGone("asset_editor"); await("asset_detail") }
    private fun openAsset(id: String) {
        rule.onNodeWithTag("nav_assets").performClick()
        scroll("assets_scroll", "asset_$id")
        rule.onNodeWithTag("asset_$id").performClick()
    }

    @Test fun addSavingsShowsNativeAndConvertedAmounts() {
        start()
        rule.onNodeWithTag("total_value").assertTextEquals("HK$0.00")
        rule.onNodeWithTag("add_asset").performClick()
        rule.onNodeWithTag("add_savings").performClick()
        fill("title", "人民币储蓄")
        fill("balance", "100")
        save()
        assertEquals(DisplayCurrency.CNY, store.value.assets.single().currency)
        rule.onNodeWithTag("nav_overview").performClick()
        rule.onNodeWithTag("total_value").assertTextEquals("HK$109.00")
        rule.onNodeWithTag("currency_button").performClick()
        rule.onNodeWithTag("currency_USD").performClick()
        rule.waitUntil(10000) { store.value.displayCurrency == DisplayCurrency.USD }
        rule.onNodeWithTag("total_value").assertTextEquals("US$13.97")
    }

    @Test fun laterPurchaseAccumulatesSharesAndQuoteUpdatesRecalculatePnl() {
        val a = Asset("stock", "测试股票", AssetKind.STOCK, DisplayCurrency.USD, purchases = listOf(Purchase("first", BigDecimal.TEN, BigDecimal("100"), 0)), currentPrice = BigDecimal("12"))
        start(PortfolioData(assets = listOf(a)))
        openAsset("stock")
        scroll("asset_detail", "add_purchase")
        rule.onNodeWithTag("add_purchase").performClick()
        rule.onNodeWithTag("input_tradePrice").performTextReplacement("20")
        rule.onNodeWithTag("input_tradeShares").performTextReplacement("100")
        scroll("stock_action", "save_stock_action")
        rule.onNodeWithTag("save_stock_action").performClick()
        rule.waitUntil(10000) { store.value.assets.single().purchases.size == 2 }
        awaitGone("stock_action")
        scroll("asset_detail", "update_price")
        rule.onNodeWithTag("update_price").performClick()
        rule.onNodeWithTag("input_tradePrice").performTextReplacement("12")
        scroll("stock_action", "save_stock_action")
        rule.onNodeWithTag("save_stock_action").performClick()
        rule.waitUntil(10000) { store.value.assets.single().currentPrice.compareTo(BigDecimal("12")) == 0 }
        awaitGone("stock_action")
        scroll("asset_detail", "holding_pnl")
        rule.onNodeWithTag("holding_pnl").assertTextContains("-20.00%", substring = true)
        val updated = store.value.assets.single()
        assertEquals(0, BigDecimal("2400").compareTo(updated.nativeValue))
        assertEquals(0, BigDecimal("-600").compareTo(updated.unrealizedPnl))
        assertEquals(0, BigDecimal("200").compareTo(updated.currentShares))
    }

    @Test fun manualRatesRevaluePortfolioWithoutChangingNativeBalance() {
        start(PortfolioData(assets = listOf(Asset("cny", "现金", AssetKind.SAVINGS, DisplayCurrency.CNY, balance = BigDecimal("100")))))
        rule.onNodeWithTag("open_settings").performClick()
        scroll("settings_scroll", "edit_rates")
        rule.onNodeWithTag("edit_rates").performClick()
        rule.onNodeWithTag("input_cny_rate").performTextReplacement("2")
        rule.onNodeWithTag("save_rates").performClick()
        rule.waitUntil(10000) { store.value.rates.cnyToHkd.compareTo(BigDecimal("2")) == 0 }
        awaitGone("input_cny_rate")
        rule.onNodeWithTag("nav_overview").performClick()
        rule.onNodeWithTag("total_value").assertTextEquals("HK$200.00")
        assertEquals(0, BigDecimal("100").compareTo(store.value.assets.single().balance))
    }

    @Test fun invalidSavingsCannotBeSaved() {
        start()
        rule.onNodeWithTag("add_asset").performClick()
        rule.onNodeWithTag("add_savings").performClick()
        fill("title", "储蓄")
        fill("balance", "-1")
        scroll("asset_editor", "save_asset")
        rule.onNodeWithTag("save_asset").performClick()
        rule.onNodeWithTag("asset_editor").assertExists()
        assertTrue(store.value.assets.isEmpty())
    }

    @Test fun displayPreferencesAreSaved() {
        start()
        rule.onNodeWithTag("currency_button").performClick()
        rule.onNodeWithTag("currency_USD").performClick()
        rule.waitUntil(10000) { store.value.displayCurrency == DisplayCurrency.USD }
        rule.onNodeWithTag("total_value").assertTextEquals("US$0.00")
        rule.onNodeWithTag("open_settings").performClick()
        scroll("settings_scroll", "settings_light")
        rule.onNodeWithTag("settings_light").performClick()
        rule.waitUntil(10000) { !store.value.darkMode }
        rule.onNodeWithTag("settings_light").assertIsSelected()
    }

    @Test fun deleteRequiresConfirmationAndRemovesHolding() {
        start(PortfolioData(assets = listOf(Asset("cash", "港币储蓄", AssetKind.SAVINGS, DisplayCurrency.HKD, balance = BigDecimal("100")))))
        openAsset("cash")
        scroll("asset_detail", "delete_asset")
        rule.onNodeWithTag("delete_asset").performClick()
        assertEquals(1, store.value.assets.size)
        rule.onNodeWithTag("confirm_delete").performClick()
        rule.waitUntil(10000) { store.value.assets.isEmpty() }
        rule.onNodeWithTag("nav_overview").performClick()
        rule.onNodeWithTag("total_value").assertTextEquals("HK$0.00")
    }

    @Test fun firstPurchaseNeedsOnlyPriceAndShares() {
        start()
        rule.onNodeWithTag("add_asset").performClick()
        rule.onNodeWithTag("add_stock").performClick()
        fill("title", "首笔股票")
        rule.onNodeWithTag("editor_currency_USD").performClick()
        fill("buyPrice", "20.5")
        fill("boughtShares", "10")
        save()
        val a = store.value.assets.single()
        assertEquals(DisplayCurrency.USD, a.currency)
        assertEquals(1, a.purchases.size)
        assertEquals(0, BigDecimal("205").compareTo(a.nativeValue))
        assertEquals(0, a.unrealizedPnl.signum())
    }

    @Test fun failedSaveKeepsFormAndAllowsRetry() {
        start()
        store.failSaves = true
        rule.onNodeWithTag("add_asset").performClick()
        rule.onNodeWithTag("add_savings").performClick()
        fill("title", "保留录入")
        fill("balance", "123.45")
        scroll("asset_editor", "save_asset")
        rule.onNodeWithTag("save_asset").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("保存失败，原有数据已保留，请重试。").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("asset_editor").assertExists()
        assertTrue(store.value.assets.isEmpty())
        store.failSaves = false
        save()
        assertEquals(0, BigDecimal("123.45").compareTo(store.value.assets.single().balance))
    }

    private fun billField(tag: String, value: String) {
        if (tag == "bill_category" && rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
            scroll("bill_editor", "bill_custom_category")
            rule.onNodeWithTag("bill_custom_category").performClick()
        }
        scroll("bill_editor", tag)
        rule.onNodeWithTag(tag).performTextReplacement(value)
    }
    private fun saveBillForm() {
        rule.onNodeWithTag("save_bill").performClick()
        awaitGone("bill_editor")
    }

    @Test fun expenseEditDeleteAndIncomeSynchronizeSavings() {
        start(PortfolioData(assets = listOf(Asset("cash", "日常账户", AssetKind.SAVINGS, DisplayCurrency.CNY, balance = BigDecimal("1000.00")))))
        rule.onNodeWithTag("nav_settings").assertDoesNotExist()
        rule.onNodeWithTag("nav_bills").performClick()
        rule.onNodeWithTag("add_bill").performClick()
        billField("bill_amount", "25.50")
        billField("bill_category", "午餐")
        saveBillForm()
        assertEquals(0, BigDecimal("974.50").compareTo(store.value.assets.single().balance))
        val id = store.value.bills.single().id
        scroll("bills_scroll", "bill_$id")
        rule.onNodeWithTag("bill_$id").performClick()
        billField("bill_amount", "40")
        saveBillForm()
        assertEquals(0, BigDecimal("960.00").compareTo(store.value.assets.single().balance))
        scroll("bills_scroll", "bill_$id")
        rule.onNodeWithTag("bill_$id").performClick()
        scroll("bill_editor", "delete_bill")
        rule.onNodeWithTag("delete_bill").performClick()
        rule.onNodeWithTag("confirm_delete_bill").performClick()
        awaitGone("bill_editor")
        assertTrue(store.value.bills.isEmpty())
        assertEquals(0, BigDecimal("1000.00").compareTo(store.value.assets.single().balance))
        rule.onNodeWithTag("add_bill").performClick()
        rule.onNodeWithTag("bill_kind_INCOME").performClick()
        billField("bill_amount", "88.88")
        saveBillForm()
        assertEquals(0, BigDecimal("1088.88").compareTo(store.value.assets.single().balance))
        rule.onNodeWithTag("nav_overview").performClick()
        rule.onNodeWithTag("total_value").assertTextEquals("HK$1,186.88")
    }

    @Test fun insufficientBalanceKeepsBillInputAndAllowsChoosingAnotherAccount() {
        val cny = Asset("cny", "人民币", AssetKind.SAVINGS, DisplayCurrency.CNY, balance = BigDecimal("10"))
        val usd = Asset("usd", "美元", AssetKind.SAVINGS, DisplayCurrency.USD, balance = BigDecimal("100"))
        start(PortfolioData(assets = listOf(cny, usd)))
        rule.onNodeWithTag("nav_bills").performClick()
        rule.onNodeWithTag("add_bill").performClick()
        billField("bill_amount", "20")
        rule.onNodeWithTag("save_bill").performClick()
        assertTrue(store.value.bills.isEmpty())
        rule.onNodeWithTag("bill_editor").assertExists()
        scroll("bill_editor", "bill_account")
        rule.onNodeWithTag("bill_account").performClick()
        rule.onNodeWithTag("bill_account_usd").performClick()
        saveBillForm()
        assertEquals(DisplayCurrency.USD, store.value.bills.single().currency)
        assertEquals(0, BigDecimal("80").compareTo(store.value.assets.single { it.id == "usd" }.balance))
        assertEquals(cny, store.value.assets.single { it.id == "cny" })
    }

    @Test fun linkedSavingsCannotBeDeletedWhileBillReferencesIt() {
        val data = PortfolioData(assets = listOf(Asset("cash", "现金", AssetKind.SAVINGS, DisplayCurrency.CNY, balance = BigDecimal("100"))))
            .saveBill(Bill("b", "cash", BillKind.EXPENSE, DisplayCurrency.CNY, BigDecimal("10"), java.time.LocalDate.now(), "日常"), 0)
        start(data)
        openAsset("cash")
        scroll("asset_detail", "delete_asset")
        rule.onNodeWithTag("delete_asset").performClick()
        rule.onNodeWithTag("confirm_delete").assertDoesNotExist()
        assertEquals(1, store.value.assets.size)
        assertEquals(1, store.value.bills.size)
    }

    @Test fun csvImportPreviewsThenReplacesAndExportWritesReadableBackup() {
        useDocumentPickerStub()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        fun createFile() = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "folio-test-${java.util.UUID.randomUUID()}.csv")
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
        }))
        val input = createFile()
        val output = createFile()
        try {
            val candidate = PortfolioData(assets = listOf(Asset("imported", "导入账户", AssetKind.SAVINGS, DisplayCurrency.CNY, balance = BigDecimal("100.00"))))
                .saveBill(Bill("imported_bill", "imported", BillKind.EXPENSE, DisplayCurrency.CNY, BigDecimal("12.34"), java.time.LocalDate.now(), "早餐"), 0).recordToday()
            resolver.openOutputStream(input)!!.use { it.write(PortfolioCsv.encode(candidate)) }
            start()
            rule.onNodeWithTag("open_settings").performClick()
            documentUri = input
            scroll("settings_scroll", "import_csv")
            rule.onNodeWithTag("import_csv").performClick()
            await("confirm_import")
            assertTrue(store.value.assets.isEmpty())
            rule.onNodeWithTag("confirm_import").performClick()
            awaitGone("confirm_import")
            assertEquals(candidate, store.value)
            documentUri = output
            scroll("settings_scroll", "export_csv")
            rule.onNodeWithTag("export_csv").performClick()
            rule.waitUntil(10000) {
                runCatching { resolver.openInputStream(output)!!.use { PortfolioCsv.decode(it.readBytes()) } == candidate }.getOrDefault(false)
            }
            // A malformed import never opens a confirmation and never changes current data.
            resolver.openOutputStream(input, "wt")!!.use { it.write("wrong,header\n1,2".toByteArray()) }
            documentUri = input
            scroll("settings_scroll", "import_csv")
            rule.onNodeWithTag("import_csv").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithText("导入失败，现有数据已保留", substring = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("confirm_import").assertDoesNotExist()
            assertEquals(candidate, store.value)
        } finally { resolver.delete(input, null, null); resolver.delete(output, null, null) }
    }

    @Test fun quickCategoryCalendarTodayAndLastAccountReduceEntrySteps() {
        val cny = Asset("cny", "人民币", AssetKind.SAVINGS, DisplayCurrency.CNY, balance = BigDecimal("100"))
        val usd = Asset("usd", "美元", AssetKind.SAVINGS, DisplayCurrency.USD, balance = BigDecimal("100"))
        start(PortfolioData(assets = listOf(cny, usd)))
        rule.onNodeWithTag("nav_bills").performClick()
        rule.onNodeWithTag("add_bill").performClick()
        billField("bill_amount", "5")
        scroll("bill_editor", "quick_category_交通")
        rule.onNodeWithTag("quick_category_交通").performClick()
        scroll("bill_editor", "bill_account")
        rule.onNodeWithTag("bill_account").performClick()
        rule.onNodeWithTag("bill_account_usd").performClick()
        scroll("bill_editor", "bill_date")
        rule.onNodeWithTag("bill_date").performClick()
        await("bill_calendar")
        val today = java.time.LocalDate.now()
        val chosenDay = (today.dayOfMonth - 1).coerceAtLeast(1)
        val dayPattern = Regex("(?<![0-9])$chosenDay(?![0-9])")
        val monthName = today.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        val calendarDay = SemanticsMatcher("calendar day $chosenDay") { node ->
            val labels = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { it.text }
            labels.any {
                dayPattern.containsMatchIn(it) && it.contains(monthName, ignoreCase = true)
            }
        }
        rule.onAllNodes(calendarDay and hasClickAction() and isEnabled()).onFirst().performClick()
        rule.onNodeWithTag("date_picker_confirm").performClick()
        awaitGone("bill_calendar")
        rule.onNodeWithTag("bill_date").assertTextContains("${today.year}年${today.monthValue}月${chosenDay}日", substring = true)
        saveBillForm()
        assertEquals("交通", store.value.bills.single().category)
        assertEquals(today.withDayOfMonth(chosenDay), store.value.bills.single().date)
        rule.onNodeWithTag("add_bill").performClick()
        scroll("bill_editor", "bill_account")
        rule.onNodeWithTag("bill_account").assertTextContains("美元", substring = true)
        rule.onNodeWithTag("bill_today").performClick()
        rule.onNodeWithTag("bill_date").assertTextContains("${today.year}年${today.monthValue}月${today.dayOfMonth}日", substring = true)
        scroll("bill_editor", "bill_kind_INCOME")
        rule.onNodeWithTag("bill_kind_INCOME").performClick()
        scroll("bill_editor", "quick_category_工资")
        rule.onNodeWithTag("quick_category_工资").assertIsSelected()
    }

    @Test fun expenseAnalysisShowsSharesComparisonDailyValuesAndFilteredDetails() {
        val today = java.time.LocalDate.now()
        val month = java.time.YearMonth.from(today)
        val cny = Asset("cny", "人民币", AssetKind.SAVINGS, DisplayCurrency.CNY, balance = BigDecimal("100"))
        val usd = cny.copy(id = "usd", currency = DisplayCurrency.USD)
        fun entry(id: String, value: String, category: String, currency: DisplayCurrency = DisplayCurrency.CNY, kind: BillKind = BillKind.EXPENSE, date: java.time.LocalDate = month.atDay(1), time: Long = 100) = Bill(id, if (currency == DisplayCurrency.USD) "usd" else "cny", kind, currency, BigDecimal(value), date, category, createdAt = time)
        start(PortfolioData(assets = listOf(cny, usd), bills = listOf(entry("lunch", "30", "餐饮"), entry("traffic", "10", "交通"), entry("usd_lunch", "200", "餐饮", DisplayCurrency.USD, time = 200), entry("income", "1000", "餐饮", kind = BillKind.INCOME, time = 300), entry("previous", "20", "餐饮", date = month.minusMonths(1).atDay(1)))))
        rule.onNodeWithTag("nav_bills").performClick()
        rule.onNodeWithTag("bill_tab_analysis").performClick()
        rule.onNodeWithTag("analysis_currency_CNY").performClick()
        rule.onNodeWithTag("add_bill").assertDoesNotExist()
        scroll("bills_scroll", "expense_total")
        rule.onNodeWithTag("expense_total").assertTextEquals("¥40.00")
        rule.onNodeWithTag("expense_previous").assertTextContains("¥20.00", substring = true)
        rule.onNodeWithTag("expense_change").assertTextContains("100.0%", substring = true)
        scroll("bills_scroll", "daily_expense_selected")
        rule.onNodeWithTag("daily_expense_selected").assertTextContains("¥40.00", substring = true)
        scroll("bills_scroll", "expense_category_餐饮")
        rule.onNodeWithTag("category_percent_餐饮", useUnmergedTree = true).assertTextEquals("75.0%")
        rule.onNodeWithTag("expense_category_餐饮").performClick()
        rule.onNodeWithTag("add_bill").assertExists()
        scroll("bills_scroll", "bill_lunch")
        rule.onNodeWithTag("bill_lunch").assertExists()
        rule.onNodeWithTag("bill_usd_lunch").assertDoesNotExist()
        rule.onNodeWithTag("bill_income").assertDoesNotExist()
        rule.onNodeWithTag("bill_traffic").assertDoesNotExist()
    }

    @Test fun analysisHasSafeEmptyStateAndHistoricalMonthNavigation() {
        start()
        rule.onNodeWithTag("nav_bills").performClick()
        rule.onNodeWithTag("bill_tab_analysis").performClick()
        scroll("bills_scroll", "analysis_empty")
        rule.onNodeWithTag("expense_total").assertTextEquals("¥0.00")
        scroll("bills_scroll", "bill_previous_month")
        rule.onNodeWithTag("bill_previous_month").performClick()
        scroll("bills_scroll", "expense_previous")
        rule.onNodeWithTag("expense_previous").assertTextEquals("上月 ¥0.00")
        rule.onNodeWithTag("expense_change").assertTextEquals("较上月 无变化")
    }
}
