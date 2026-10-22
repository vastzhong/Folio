# Folio — Android 本地资产管理

**Folio 0.4.0** 使用 **Kotlin + Jetpack Compose + Material 3**，支持三币种资产、股票逐笔买入、快捷记账、按原币的支出分析，以及 CSV 备份和恢复。数据保存在本机，行情和汇率手动维护。

功能使用方法和计算口径见 [FUNCTIONALITY.md](FUNCTIONALITY.md)。此前 0.1 版的视觉研究记录见 [design/README.md](design/README.md)，其中设计示意使用的是当时的模拟数据。

## 已实现

- **总览**：总资产、7天/1月/3月/1年的历史趋势、轻触曲线查看节点估值、储蓄/股票/基金分布；点击分类进入资产页。
- **储蓄录入**：选择 CNY/HKD/USD，填写余额；支持编辑和确认删除。
- **股票买入**：首次录入买入价和股数，后续追加每笔买入，自动累计股数、总成本和加权平均成本；买入记录完整保存。
- **手动行情**：股票当前价可单独更新，不改动买入记录；市值与未实现盈亏自动计算。
- **多币种展示**：顶部切换总额折算币种；资产摘要和明细保留原币金额。已移除总览、资产页重复的「原币资产 / 全部资产折算」卡片。
- **资产**：显示全部持仓，按储蓄、股票/ETF、基金筛选；点击条目进入资产详情，支持返回。
- **日常账单**：底部新增账单页，按账户记录支出或收入，直接扣减或增加对应储蓄余额；支持修改、删除、月份切换和收支筛选，各币种分别统计。
- **快捷记账**：常用分类点选、自定义分类、上次账户、日历与「今天」，保存按钮固定可用。
- **支出分析**：按原币查看分类占比、每日开销及上月对比；本月采用上月同期，点击分类进入对应账单。
- **界面精简**：移除重复说明，CSV 格式帮助按需展开，保留必要的错误和替换确认。
- **设置**：入口移至右上角；支持主题、展示币种、手动汇率和备份。底部为总览、资产、账单。
- **CSV 备份**：通过系统文件选择器导出全部数据，导入先校验和预览，确认后完整替换；规则和可导入示例见 [CSV-FORMAT.md](CSV-FORMAT.md)。
- **响应式手机布局**：`Scaffold` 固定顶部应用栏和底部三栏导航；中部用 `LazyColumn` 独立滚动。Android 系统状态栏/导航区域通过 Compose 系统 Insets 处理。没有硬编码手机高度，也不需要屏内二次滚动。
- **本地保存**：原子文件保存资产、买入记录、账单、汇率、偏好及历史；兼容读取 0.2.0 的本地数据。正式应用首次进入为空，`SamplePortfolio` 仅供预览及测试使用。
- **自动化测试**：纯 Kotlin 的资产计算单元测试、Compose 交互 UI 测试（需要 Android 模拟器或设备）。

## 导入及运行

1. 用近期版本的 **Android Studio** 打开解压后的 `Folio` 文件夹（根目录存在 `settings.gradle.kts`）。
2. 安装 **JDK 17+** 与 **Android SDK API 37**，接入网络同步 Gradle 依赖。
3. 使用 Gradle **9.5.1**、Android Gradle Plugin **9.3.0**、Compose BOM **2026.08.00**。
4. 在 Android Studio 的设备管理器创建模拟器（推荐普通尺寸手机，Android 8.0+），或连接实体 Android 手机。
5. 运行 `app` 配置。

**关于 Wrapper**：本地源码已补齐 Gradle 9.5.1 标准 `gradle-wrapper.jar`、`gradlew` 和 `gradlew.bat`，替换了原先使用无效下载地址的引导脚本。分发配置包含官方 Gradle ZIP 的 SHA-256 校验值。首次使用时仍需下载 Gradle 分发包；已有对应缓存时会复用缓存。

### 命令行

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest  # 需已启动的安卓模拟器或真机
```

Windows 可用 `gradlew.bat` 代替 `./gradlew`。

成功编译后，Debug APK 通常在 `app/build/outputs/apk/debug/app-debug.apk`。

## 代码组织

```text
app/src/main/java/com/folio/wealth/
  MainActivity.kt
  model/
    Asset.kt                # 资产分类/币种/历史时间范围/持仓模型
    PortfolioMath.kt        # 不依赖 Android 的金额计算
    PortfolioData.kt        # 输入校验、偏好和每日历史记录
    Bill.kt                 # 账单及储蓄余额差额处理
    ExpenseAnalysis.kt      # 按原币的月度、分类、每日统计和默认账户
    SamplePortfolio.kt      # 仅用于预览及测试
  data/
    PortfolioStore.kt       # 本地原子写入
    PortfolioCodec.kt       # 有版本的精确金额序列化
    PortfolioCsv.kt         # CSV 完整快照、解析和校验
  ui/
    FolioApp.kt             # 页面导航、状态管理、Scaffold
    theme/FolioTheme.kt    # 深绿配色与 Material 3 主题
    components/
      PortfolioComponents.kt
      TrendChart.kt
    screens/
      OverviewScreen.kt
      AssetsScreen.kt
      SettingsScreen.kt
      BillsScreen.kt        # 月度账单及收支表单
      ExpenseAnalysisContent.kt # 支出统计与可选日柱状图
      AssetEditorSheet.kt   # 储蓄/首次买入、追加买入、更新行情及汇率表单
    format/Formatters.kt
app/src/test/            # 财务计算单元测试
app/src/androidTest/     # UI 仪器测试
```

## 当前设计约束

- 历史趋势显示**总资产价值的变化**，包含增减储蓄或转入资金，不能直接称为“投资收益”。
- 默认手动汇率为 `1 USD = 7.80 HKD`、`1 CNY = 1.09 HKD`，可在设置修改；并非实时汇率。原币金额单独保存，换汇只用于估值展示。
- 当前记录买入，不包含卖出、手续费、分红或拆股。买入不会自动扣减储蓄余额，储蓄余额需自行更新。
- 历史从实际录入开始，按日最多保存 400 条；同一天更新当日记录，不生成虚构历史。每条历史保留当日汇率。
- UI 不提供交易、充值或银行登录功能，也不要求账号权限。
- 资产、买入记录、账单、主题和显示币种已持久化；筛选及当前页面使用 `rememberSaveable`。CSV 为完整备份恢复，不做合并，没有云同步。
- 支出不得使储蓄余额为负；关联账单的账户不能直接删除或改币种。删除已花掉的收入可能因余额不足而被拒绝。

## 设备适配验收（在 Android Studio 实测）

- 小屏约 320dp 宽：图表可读、卡片金额有溢出保护、资产页可滚动。
- 常规 360–430dp 宽：顶部栏、底部导航可见，中部可滚动；总览按总资产与趋势、资产分布两组内容展示。
- 放大系统字体 1.3×–2×：文字可换行/必要时裁剪，主操作可点击。
- 手势导航与三键导航：不遮挡底部导航。
- 横屏或分屏：中间 LazyColumn 可用，顶部与底部持续可访问。

> 0.4.0：44 个单元测试、16 个模拟器测试通过，APK 签名验证通过。详细记录见 [BUILD-VERIFICATION.md](BUILD-VERIFICATION.md)。

## GitHub 云端构建（可选）

项目内包含 `.github/workflows/android.yml`：将工程推送到 GitHub 仓库后，可在 Actions 中启动 **Android build and unit tests**，自动执行 Gradle 调试构建与单元测试，并在成功后上传 `folio-debug-apk` 产物。上传仓库是用户自行进行的操作，本次交付尚未创建云端仓库或执行 Actions。Compose 的 `androidTest` 仪器测试仍需 Android 模拟器/真机运行。
