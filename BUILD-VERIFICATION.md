# Folio 构建验证

## 0.4.0 快捷记账与支出分析（2026-10-22）

- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`：离线构建成功。
- 44 个 JVM 单元测试通过，包含 8 个新增统计测试：分币种/收支隔离、上月同期、整月对比、闰年与月末、零基期、精确金额聚合、编辑删除后统计、默认账户回退。
- API 37.0 模拟器 16 个测试通过（15 个 Compose 交互、1 个真实文件测试），结果 `OK (16 tests)`。
- 新增交互覆盖：分类快捷选择、自定义分类、日历选日、今天按钮、上次账户、收入分类、每日开销、分类占比、上月对比、分类明细及空统计。
- 金额按原币独立统计；本月比较上月相同日号范围，历史月份比较整月；零基期不计算增长率。快捷账户偏好仅在保存成功后更新，财务数据格式及 CSV 格式保持兼容。
- APK 签名验证通过（v2），版本 `0.4.0` / versionCode `4`，最低 API `26`。
- APK 大小 19,106,487 字节，交付副本 `D:\vastz\android_app\Folio-0.4.0-debug.apk`。
- SHA-256：`715e9f03de488fff09162909b92dd7674a91876bf9eee2df595e6f30dee96b88`。
- 额外运行检查：通过系统文件选择器导入虚构账单，核对本月 CNY 支出 309.00、上月同期 400.00、减少 22.8%；检查 320dp 宽和 1.5 倍字体下的记账及分析布局。分析页隐藏新增浮动按钮，避免遮挡图表。

构建日志 `.gradle/folio-0.4-build.log`，设备日志 `.gradle/folio-0.4-instrumentation.log`。使用端口 5556 的独立只读无窗口模拟器，没有改动真机或原 AVD 数据。

## 0.3.0 账单与 CSV（2026-10-22）

- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`：成功。
- 单元测试：36 个全部通过，包括 14 个账单、CSV、v2 数据迁移及示例一致性测试。
- Android 17 / API 37.0 模拟器：13 个全部通过（12 个 Compose 交互、1 个真实原子文件测试），日志结果 `OK (13 tests)`。
- 新增设备回归：支出、收入、修改差额、删除撤销、账户切换、余额不足保留表单、关联账户删除保护、CSV 导入预览后替换、真实 ContentResolver 文件读写往返，以及无效 CSV 保留原数据。
- CSV 文件交互测试使用测试用 ActivityResultRegistry 提供文档 URI，其余走正式页面、解析器和 Android ContentResolver；文件在模拟器测试结束后删除。
- `apksigner verify --verbose` 通过，APK Signature Scheme v2。
- 版本 `0.3.0`，versionCode `3`，最低 API `26`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，19,034,118 字节；交付副本为工作区根目录 `Folio-0.3.0-debug.apk`。
- SHA-256：`2e618ebd3e99a90f2260618f150a235c42f2cc7e00e34eea0c45cbb63380a3e4`。

测试使用独立的无窗口只读模拟器实例（端口 5556），没有连接真机或写回原 AVD。通过 `adb shell am instrument` 运行测试 APK；本轮使用缓存依赖离线构建。构建日志 `.gradle/folio-0.3-build.log`，补充示例测试日志 `.gradle/folio-0.3-unit-final.log`，设备日志 `.gradle/folio-0.3-instrumentation.log`。

本地二进制版本升级至 v3，可读取 v2；保留既有 `portfolio-v2.dat` 文件位置以兼容安装升级。CSV 规范独立为 v1，导入完整恢复当前余额，不重放账单。说明见 [FUNCTIONALITY.md](FUNCTIONALITY.md) 和 [CSV-FORMAT.md](CSV-FORMAT.md)。

## 0.2.0 功能版（2026-10-22）

- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`：成功。
- 单元测试：22 个全部通过（17 个录入、逐笔成本、舍入、历史汇率及存储测试，5 个基础资产计算测试）。
- Android 17 / API 37.0 模拟器：9 个全部通过，包括 8 个 Compose 交互测试及 1 个真实原子文件保存测试。
- `apksigner verify --verbose`：通过，APK Signature Scheme v2。
- 版本 `0.2.0`，versionCode `2`，最低 API `26`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，18,878,706 字节。
- SHA-256：`062167267b4127e639204c2937e0cda68fb0dbeb06d1cc877fbea95f4e3e5330`。

设备回归覆盖：首次股票只填价格/股数、后续加仓累计成本、独立报价更新、储蓄新增、三币种原币/折算金额、手动汇率、偏好保存、无效输入、删除确认、保存失败重试，以及创建新 Store 后重新读取实际文件。

测试在 `Medium_Phone_API_37.0` 的无窗口只读实例中完成，不写回原 AVD 用户数据。`connectedDebugAndroidTest` 首次因缺少离线 UTP 主机插件未能启动，随后直接通过 `adb shell am instrument` 运行同一个已编译的测试 APK，结果 `OK (9 tests)`。

Compose 测试传递依赖的 Espresso 3.5 与新系统的 InputManager 不兼容，已显式使用官方稳定版 `espresso-core:3.7.0`；对应修复可见 [Android Test 官方提交](https://github.com/android/android-test/commit/fddcda4adc8eaba515fbf2d790cf8dfe5c1b722d)。

本轮构建日志：`.gradle/folio-0.2-build.log`；设备测试日志：`.gradle/folio-0.2-instrumentation.log`。功能与计算说明见 [FUNCTIONALITY.md](FUNCTIONALITY.md)。

## 0.1.0 历史记录

日期：2026-10-21（Asia/Shanghai）。

## 结果

- `:app:assembleDebug :app:testDebugUnitTest`：成功，用时 1 分 23 秒。
- `PortfolioMathTest`：5 个测试，0 失败、0 错误、0 跳过。
- Android Build Tools `apksigner verify --verbose`：通过，APK Signature Scheme v2。
- `aapt2 dump badging`：应用为 Folio，包名 `com.folio.wealth`，版本 `0.1.0`，入口为 `MainActivity`。
- 没有已连接设备，未运行模拟器 UI 测试或真机验收。已配置但未启动的 AVD 为 `Medium_Phone_API_37.0`。

## 安装包

- 文件：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：18,643,893 字节。
- SHA-256：`74803d545eec9d09041801d870a60189370423ddc1f459400c5d4a48909ff6e9`
- 类型：使用本机调试证书签名的 Debug APK。

## 环境及执行命令

使用 Android Studio 自带的 JBR 25.0.3，Gradle 9.5.1，AGP 9.3.0，Android SDK Platform 37.0 和 Build Tools 36.0.0。项目 Java 编译目标仍为 17，最低 API 26，目标 API 36。

```powershell
$env:JAVA_HOME = 'D:\vastz\android_studio\jbr'
& .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain --stacktrace
```

本机 SDK 位置由未纳入版本管理的 `local.properties` 指定，未改动系统全局环境变量。

## 本次修复

原项目缺少 `gradle-wrapper.jar`，自定义启动脚本使用的 JAR 下载地址返回 404。已通过本机缓存的 Gradle 9.5.1 生成标准 Wrapper，补齐 JAR 并替换两套启动脚本。`gradle-wrapper.properties` 包含官方分发 ZIP 的 SHA-256 校验值。

生成 Wrapper 时，Java 的分发地址预检失败；本次对生成任务关闭该预检，最终项目配置保留 `validateDistributionUrl=true`，并保留分发 ZIP 的 SHA-256 校验。实际构建复用了本机 Gradle 缓存。

构建日志位于 `.gradle/folio-debug-build.log`，单元测试 HTML 报告位于 `app/build/reports/tests/testDebugUnitTest/index.html`。以上目录由 `.gitignore` 排除。

## 非阻塞提示

编译器提示 `Icons.Outlined.ShowChart` 已弃用，建议未来使用对应的 AutoMirrored 图标。原生库 `libandroidx.graphics.path.so` 未剥离调试符号，按原样打包。二者均未导致本次构建或签名验证失败。
