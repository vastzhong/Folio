# Folio CSV 格式 v1

从右上角设置 →「导出 CSV 备份」生成文件，也可参考 [示例文件](examples/folio-example.csv)。示例包含虚构数据，导入会替换本机内容，请先备份真实数据。

CSV 是**完整数据快照**。导入先校验，再显示资产、买入、账单和历史数量；点击「替换并导入」后才原子保存。取消、格式不正确或保存失败都不会改动原数据。不合并文件，也不会重复执行账单。

## 文件规则

- 编码 UTF-8，支持有或没有 BOM，最大 8 MB。导出带 BOM，方便表格软件识别中文。
- 第一行表头及顺序必须如下，所有行保持 18 列；不适用的列留空。
- `schema_version` 每条记录均填 `1`。`record_type` 大小写敏感。
- 金额使用小数点，不使用货币符号、千分位、科学计数法或负数。收入和支出都填正金额。
- 日期用 `YYYY-MM-DD`，`created_at` 用 Unix 毫秒时间戳；不需要实际时间时可填 `0`。
- 文本中含逗号、引号、换行时用双引号包裹，内部的一个双引号写成两个。支持 CRLF 或 LF 换行。
- 导出时，文本若以 `= + - @`、制表符、换行或单引号开头，会额外加一个单引号，避免表格软件把它当公式；再次导入会去掉这一个保护前缀。手工填写这类文本时也按此规则处理。
- 文件必须有且只有一条 `settings`；资产 id 全局唯一，账单 id 全局唯一，同一资产的买入 id 唯一。引用不存在的资产、币种不符、重复编号及未知记录类型都会拒绝整份导入。

```csv
schema_version,record_type,id,parent_id,name,kind,currency,amount,price,shares,date,note,created_at,symbol,cny_to_hkd,usd_to_hkd,display_currency,dark_mode
```

## 各类记录

| record_type | 需要填写的列（除此以外留空） |
| --- | --- |
| `settings` | `cny_to_hkd`、`usd_to_hkd` 为大于零的手动汇率；`display_currency` 为 `CNY/HKD/USD`；`dark_mode` 为 `true/false` |
| `asset` | `id`、`name`（1–100 字）、`kind`（`SAVINGS/STOCK/FUND`）、`currency`（`CNY/HKD/USD`）、`amount`、`price`、`created_at`；`symbol` 可选，最多 32 字 |
| `purchase` | `id`、`parent_id`（持仓的 id）、`price`（买入价）、`shares`（股数）、`created_at`；价格与股数大于零 |
| `bill` | `id`、`parent_id`（储蓄账户的 id）、`name`（分类，1–40 字）、`kind`（`EXPENSE/INCOME`）、`currency`（与账户相同）、`amount`（大于零）、`date`、`created_at`；`note` 可选，最多 500 字 |
| `snapshot` | `date`、`amount`（当日全部资产的 HKD 估值）、`cny_to_hkd`、`usd_to_hkd`（当天的汇率） |

储蓄的 `amount` 是**已经包含所有账单影响的当前余额**，`price` 填 `0`，不能关联 `purchase`。股票/基金的 `amount` 填 `0`，`price` 填当前原币价格，至少有一条对应的 `purchase`。账单和买入记录不要求紧跟资产行。

例如，储蓄原来 1,000 CNY，记支出 25.50、收入 88.88，那么资产行 `amount` 应填 **1063.38**，并保留两条账单行。导入恢复 1063.38，不会再扣一次 25.50 或再加一次 88.88。

账单金额最多 2 位小数；价格、股数、汇率最多 8 位小数，单个输入最多 24 位有效数字。历史估值保留计算所得精度。资产最多 10,000 项，单持仓买入最多 10,000 笔，账单最多 20,000 笔，历史最多 400 天且日期唯一；文件还须符合 8 MB 存储上限。

导出的文件即可作为后续编辑模板。表格软件保存时选择 CSV UTF-8，注意保留 id 和 parent_id 文本，避免自动修改日期、编号和数值格式。
