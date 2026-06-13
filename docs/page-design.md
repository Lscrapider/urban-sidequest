# 城市副本页面与 UI 设计文档

## 1. 文档状态

本文是当前 Android App 第一阶段 UI 设计基准，用于承接 `docs/product-design.md` 中的产品判断，并指导后续技术选型、页面拆分和逐页开发。

当前已确认的高保真 UI 方案来自 Stitch 项目：

- 项目名称：Urban Sidequest Mobile
- 项目 ID：`12129162147610428521`
- 设备类型：Mobile / Android
- 本地导出目录：`docs/ui/stitch/urban-sidequest-mobile-v2/html/`

说明：Stitch 导出的截图预览清晰度不足，已不作为本地留档；本地仅保留 HTML 设计稿。

## 2. 设计方向

当前方案采用“可信地图工具”为主方向，吸收少量“旅游行程助手”和“轻城市副本”的结构：

- 地图先行：关键结果页和执行页以地图为主画布，底部抽屉承载路线解释和操作。
- 路线 A 主推：路线 A 是默认答案，路线 B/C 只作为备选入口，不与路线 A 平级展示。
- 解释优先：POI 卡和路线结果必须解释“为什么这样安排”，而不是只展示地点信息。
- 输入克制：条件配置页以默认值、chip 和结构化选择为主，避免长表单。
- 轻打卡：执行页提供到达确认、拍照、跳过和替换，不展开等级、徽章或排行榜。
- 个人资产：我的页聚焦路线资产、私人城市地图、偏好和反馈，不做社交动态流。

## 3. 颜色与视觉规则

真实地图底图中道路已经大量使用黄色/橙黄色，因此 UI 色彩需要避免与路网冲突。

已确认的颜色规则：

- 主品牌色：`Deep Teal #0D4D4D`，用于主按钮、路线 A、当前 tab、选中态和关键图标。
- 路线 A：使用深青绿实线，必要时可用略亮的 `#0F6B63` 提高地图上可见性。
- 地图节点：使用白色填充 + 深青绿描边 + 深青绿编号文字，必要时加白色 halo 或轻阴影。
- 备选路线：路线 B/C 使用低饱和蓝灰或灰青色虚线，明显弱于路线 A。
- 风险提示：黄色只用于风险、等待、注意事项等语义状态；使用浅琥珀底，例如 `#FFF7E0`。
- 页面背景：近白或浅灰，信息层以白色 surface、浅灰边框和克制阴影区分。

禁用：

- 不用黄色作为主要路线节点或主路径。
- 不用渐变文字、玻璃拟态、过度阴影和装饰性地图效果。
- 不做卡片墙、榜单页、攻略信息流、游戏大厅和社交动态流。

## 4. 页面清单

### 4.1 登录页

- Stitch 屏幕 ID：`14abb4c2edce46728192571395b6190a`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/06-login.html`

目标：

- 让用户理解登录后可以保存路线、打卡记录和私人城市地图。
- 支持手机号验证码登录/注册，并提供微信登录入口。
- 保持克制可信，不做营销式 hero。

核心内容：

- 品牌名“城市副本”。
- 手机号输入、验证码输入、获取验证码。
- 主按钮“登录 / 注册”。
- 用户协议和隐私政策说明。
- “保存路线、打卡记录和你的私人城市地图”提示。

### 4.2 地图选区页

- Stitch 屏幕 ID：`d93625c49b054aecaf11a566add037b6`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/01-map-selection.html`

目标：

- 让用户快速确定今天要在哪一片区域玩。
- 通过地图建立空间认知，减少一开始填写条件的压力。

核心内容：

- 顶部搜索栏：搜索城市、区县、酒店或地点。
- 地图主画布：当前定位、选区边界、少量 POI。
- 底部抽屉：当前区域摘要、适合时长、推荐范围标签。
- 主按钮“下一步配置路线”。
- 次入口“手动框选区域”。

### 4.3 条件配置页

- Stitch 屏幕 ID：`a84e90c853a0408b891cf3c845fc4571`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/02-route-config.html`

目标：

- 用尽量少的结构化输入生成路线 A。
- 明确默认值，让用户知道不必填写所有高级条件。

核心内容：

- 区域摘要和起点。
- 出发时间、可用时长、起终点方式。
- 交通组合，默认步行 + 地铁。
- 路线目标，默认稳妥省心。
- 兴趣偏好多选。
- 必去点，区分“必须保证”和“尽量安排”。
- 主按钮“生成路线 A”，并说明同时生成两条备选路线。

### 4.4 路线结果页

- Stitch 屏幕 ID：`04579f90ea9b4968974e5206b1f2e214`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/03-route-result.html`

目标：

- 让用户第一眼相信路线 A 可以直接走。
- 明确路线空间关系、时间成本、预算和风险。

核心内容：

- 上半屏地图展示路线 A。
- 路线 A 用深青绿实线突出，节点使用白底青绿描边。
- 底部抽屉展示路线 A 概览、指标、推荐理由和风险提示。
- 路线 B/C 用轻入口呈现，不与路线 A 平级。
- 主按钮“开始路线”，次按钮“调整路线”。

### 4.5 POI 解释卡

- Stitch 屏幕 ID：`45a39f488fcf458db9a126fb1a832846`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/04-poi-explanation.html`

目标：

- 解释这个地点为什么适合当前路线，而不是做普通商户详情页。

核心内容：

- 地点名称、类型、距离上一站时间。
- 地图评分、营业状态、门票/人均、预计停留时间。
- “为什么安排这里”：衔接关系、时间窗口、避坑原因。
- 风险提示和替换建议。
- 外部查看入口作为辅助，不压过路线解释。
- 操作：保留这个点、替换此点、加入必去点。

### 4.6 路线执行页

- Stitch 屏幕 ID：`1a37d3b123634ef4b8459d8b781687c4`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/05-route-execution.html`

目标：

- 让用户边走边知道下一步做什么，并顺手完成打卡和反馈。

核心内容：

- 当前阶段：下一站、交通方式、预计到达时间。
- 路线进度地图。
- 当前任务：到达确认、拍照打卡、跳过、替换。
- 后续节点和时间变化。
- 轻量反馈：节奏、惊喜、是否太累。

### 4.7 我的页

- Stitch 屏幕 ID：`fc20119cfbc24a54a803e35e736069f6`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/07-profile.html`

目标：

- 聚焦用户个人路线资产和偏好，不做社交动态流。

核心内容：

- 用户信息和编辑资料入口。
- 资产摘要：已完成路线、收藏路线、私人地点。
- 我的城市地图：去过或收藏城市入口。
- 最近路线列表。
- 偏好设置：默认交通方式、路线目标、兴趣偏好。
- 反馈与数据：路线反馈、问题上报、数据来源说明。
- 底部导航：地图、生成、我的。

## 5. Stitch 导出记录

导出源：

```text
Project: Urban Sidequest Mobile
Project ID: 12129162147610428521
```

屏幕清单：

| 页面 | Stitch 屏幕 ID | 本地 HTML |
| --- | --- | --- |
| 条件配置页 - Android | `a84e90c853a0408b891cf3c845fc4571` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/02-route-config.html` |
| 地图选区页 - Android | `d93625c49b054aecaf11a566add037b6` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/01-map-selection.html` |
| 路线结果页 - 路线 A (Android) | `04579f90ea9b4968974e5206b1f2e214` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/03-route-result.html` |
| POI 解释卡 | `45a39f488fcf458db9a126fb1a832846` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/04-poi-explanation.html` |
| 路线执行页 | `1a37d3b123634ef4b8459d8b781687c4` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/05-route-execution.html` |
| 登录页 - Android | `14abb4c2edce46728192571395b6190a` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/06-login.html` |
| “我的”页 - Android | `fc20119cfbc24a54a803e35e736069f6` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/07-profile.html` |

本地导出说明：

- 使用 Stitch MCP 的 `get_screen` 获取每个屏幕的 `htmlCode.downloadUrl`。
- 使用 `curl -L` 下载 HTML 设计稿到 `docs/ui/stitch/urban-sidequest-mobile-v2/html/`。
- 不保留低清截图，后续需要图片时应从 HTML 或 Stitch 项目重新导出高分辨率素材。

## 6. 后续开发拆分建议

建议 Android App 第一阶段按以下页面顺序落地：

1. 登录页。
2. 地图选区页。
3. 条件配置页。
4. 路线生成加载态和失败态。
5. 路线结果页。
6. POI 解释卡。
7. 路线执行页。
8. 我的页。

开发前仍需要补充：

- 真实地图 SDK 选型和底图样式策略。
- 路线 A/B/C 的数据结构。
- POI 解释卡的数据来源字段。
- 打卡和反馈的数据模型。
- 登录方式、账号体系和隐私协议落地方式。
