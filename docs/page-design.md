# 城市副本页面与 UI 设计文档

## 1. 文档状态

本文是当前 Android App 第一阶段 UI 设计基准，用于承接 `docs/product-design.md` 中的产品判断，并指导后续技术选型、页面拆分和逐页开发。

当前已确认的高保真 UI 方案来自 Stitch 项目：

- 项目名称：Urban Sidequest Mobile
- 项目 ID：`12129162147610428521`
- 设备类型：Mobile / Android
- 本地 HTML 导出目录：`docs/ui/stitch/urban-sidequest-mobile-v2/html/`
- 本地图片导出目录：`docs/ui/stitch/urban-sidequest-mobile-v2/images/`

说明：本地实现仍以 HTML 设计稿为准；图片仅作为 Stitch 导出的快速预览，不作为最终切图或高保真验收来源。

## 2. 设计方向

当前方案采用“可信地图工具”为主方向，吸收少量“旅游行程助手”和“轻城市副本”的结构：

- 地图先行：关键结果页和执行页以地图为主画布，底部抽屉承载路线解释和操作。
- 首屏克制：登录后先进入地图首页默认态，不直接展示路线配置或大面积选区面板。
- 路线 A 主推：路线 A 是默认答案，路线 B/C 只作为备选入口，不与路线 A 平级展示。
- 解释优先：POI 卡和路线结果必须解释“为什么这样安排”，而不是只展示地点信息。
- 输入克制：条件配置页以默认值、chip 和结构化选择为主，避免长表单。
- 轻打卡：执行页提供到达确认、拍照、跳过和替换，不展开等级、徽章或排行榜。
- 个人资产：我的页聚焦路线资产、私人城市地图、偏好和反馈，不做社交动态流。

## 3. 全局导航与交互逻辑

### 3.1 一级导航

Android App 登录后的一级导航固定为：

- 地图：默认首页，承载地图查看、搜索、定位和发起生成路线。
- 路线：承载已经生成过的路线、正在进行的路线和继续执行入口。
- 我的：承载个人资料、路线资产、偏好和反馈入口。

底部导航文案统一使用“地图 / 路线 / 我的”，不再使用“生成”作为一级 tab。生成路线是地图页内的动作，不是一级导航目的地。

### 3.2 主流程

```text
登录页
  -> 地图首页默认态
  -> 点击“生成路线”
  -> 地图选区展开态
  -> 点击“下一步配置路线”
  -> 条件配置页
  -> 点击“生成路线 A”
  -> 路线结果页
  -> 点击“开始路线”
  -> 路线 tab 当前路线
  -> 进入路线执行页
```

调整路径：

- 路线结果页点击“调整路线”返回条件配置页，并保留当前区域和已选条件。
- 条件配置页返回时回到地图选区展开态，而不是回到地图首页默认态。
- 地图首页默认态点击“我的”进入我的页；点击“路线”进入路线页。
- 地图选区展开态仍属于地图 tab，不切换一级导航。

### 3.3 地图页状态原则

地图页至少有两个状态：

1. 默认态：用户刚登录或从底部 tab 回到地图。地图是主体，只展示轻量顶部工具区、定位按钮、底部导航和一个“生成路线”入口。
2. 展开态：用户点击“生成路线”后进入。隐藏默认态的生成入口，展示原地图选区页的底部选择面板，用于确认区域并进入配置路线。

一级标题不能遮挡地图视野。地图页标题只允许作为顶部工具区中的小标题出现，不能做大字号 hero，也不能用大白卡覆盖地图。

## 4. 颜色与视觉规则

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
- 不在地图首页默认态展示大面积配置面板。
- 不用大字号一级标题遮挡地图视野。

## 5. 页面清单

### 5.1 登录页

- Stitch 屏幕 ID：`14abb4c2edce46728192571395b6190a`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/07-login.html`
- 本地图片：`docs/ui/stitch/urban-sidequest-mobile-v2/images/07-login.png`

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

入口与跳转：

- 登录成功后进入地图首页默认态。

### 5.2 地图首页默认态

- Stitch 屏幕 ID：`3e9178c5c723490183ee537027e57973`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/01-map-home.html`
- 本地图片：`docs/ui/stitch/urban-sidequest-mobile-v2/images/01-map-home.png`

目标：

- 作为用户登录后的首屏，让用户先看到完整地图和当前位置。
- 给用户一个明确但克制的“生成路线”入口。
- 避免一进来就出现大面积选区面板和配置文案。

核心内容：

- 顶部轻量工具区：小标题“地图”、头像/我的入口、搜索栏。
- 地图主画布：当前定位、少量地图 POI、定位 FAB。
- 底部轻量生成入口：主按钮“生成路线”，辅助文案“选择区域后生成今天的城市副本”。
- 底部导航：地图、路线、我的，其中地图为选中态。

交互：

- 点击“生成路线”：隐藏默认态生成入口，进入地图选区展开态。
- 点击搜索栏：进入地点搜索或城市/区县搜索状态。
- 点击头像或“我的”tab：进入我的页。
- 点击“路线”tab：进入路线页。

视觉约束：

- 地图必须是主体，不能被标题或卡片遮挡。
- 一级标题“地图”只能是顶部工具区里的小标题。
- 不展示“今天在某地走一圈”、区域 chips 和“下一步配置路线”，这些属于展开态。

### 5.3 地图选区展开态

- Stitch 屏幕 ID：`d93625c49b054aecaf11a566add037b6`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/01-map-selection.html`

目标：

- 让用户快速确定今天要在哪一片区域玩。
- 通过地图建立空间认知，减少一开始填写条件的压力。
- 承接地图首页默认态的“生成路线”按钮，而不是作为登录后的首屏。

核心内容：

- 顶部轻量工具区：小标题“地图”、搜索栏、必要的个人入口。
- 地图主画布：当前定位、选区边界、少量 POI。
- 底部抽屉：当前区域摘要、适合时长、推荐范围标签。
- 主按钮“下一步配置路线”。
- 次入口“手动框选区域”。
- 底部导航：地图、路线、我的，其中地图为选中态。

交互：

- 从地图首页默认态点击“生成路线”进入。
- 点击“下一步配置路线”进入条件配置页。
- 点击“手动框选区域”进入地图框选/拖拽状态。
- 返回时优先收起到地图首页默认态，除非来自条件配置页返回，此时保留展开态和用户已选区域。

视觉约束：

- 底部抽屉必须放在底部导航上方，不能压住 tab。
- 展开态保留原地图选区页的视觉质感，但不得用大标题或大白卡遮挡地图。
- 该页不再承担一级首页的默认展示责任。

### 5.4 条件配置页

- Stitch 屏幕 ID：`a84e90c853a0408b891cf3c845fc4571`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/02-route-config.html`
- 本地图片：`docs/ui/stitch/urban-sidequest-mobile-v2/images/02-route-config.png`

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

入口与跳转：

- 从地图选区展开态点击“下一步配置路线”进入。
- 点击返回回到地图选区展开态。
- 点击“生成路线 A”提交生成任务，成功后进入路线结果页。

### 5.5 路线结果页

- Stitch 屏幕 ID：`783bc7bb299e48aeb12b461ec7ded454`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/03-route-result.html`
- 本地图片：`docs/ui/stitch/urban-sidequest-mobile-v2/images/03-route-result.png`

目标：

- 让用户第一眼相信路线 A 可以直接走。
- 明确路线空间关系、时间成本、预算和风险。

核心内容：

- 上半屏地图展示路线 A。
- 路线 A 用深青绿实线突出，节点使用白底青绿描边。
- 底部抽屉展示路线 A 概览、指标、推荐理由和风险提示。
- 路线 B/C 用轻入口呈现，不与路线 A 平级。
- 主按钮“开始路线”，次按钮“调整路线”。
- 底部一级导航：地图、路线、我的，其中路线为选中态。主操作按钮必须在一级导航上方，不能与导航重叠。

入口与跳转：

- 从条件配置页生成成功后进入。
- 点击“开始路线”进入路线 tab 的当前路线状态，再进入或继续路线执行页。
- 点击“调整路线”返回条件配置页，并保留区域、时间、交通和偏好条件。
- 点击地图节点或时间线节点弹出 POI 解释卡。

### 5.6 POI 解释卡

- Stitch 屏幕 ID：`3051c3242aae47439ceb63b8fd3a709a`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/04-poi-explanation.html`
- 本地图片：`docs/ui/stitch/urban-sidequest-mobile-v2/images/04-poi-explanation.png`

目标：

- 解释这个地点为什么适合当前路线，而不是做普通商户详情页。

核心内容：

- 地点名称、类型、距离上一站时间。
- 地图评分、营业状态、门票/人均、预计停留时间。
- “为什么安排这里”：衔接关系、时间窗口、避坑原因。
- 风险提示和替换建议。
- 外部查看入口作为辅助，不压过路线解释。
- 操作：保留这个点、替换此点、加入必去点。
- 底部一级导航：地图、路线、我的，其中路线为选中态。POI 卡片和底部操作区必须避让一级导航。

入口与跳转：

- 从路线结果页、路线 tab 或路线执行页中的节点详情进入。
- 关闭后回到触发它的页面状态。

### 5.7 路线页

- Stitch 屏幕 ID：`4ce1683f428b43819ea0502fdf02d845`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/05-routes.html`
- 本地图片：`docs/ui/stitch/urban-sidequest-mobile-v2/images/05-routes.png`

目标：

- 作为一级 tab，集中展示已经生成过的路线和正在进行的路线。
- 承接路线结果页点击“开始路线”后的去向。
- 让用户之后从底部 tab 回来时能继续当前路线，而不是重新生成。

核心内容：

- 顶部轻量标题“路线”，不使用遮挡内容的大标题。
- 当前路线模块：展示路线 A、进度、下一站、剩余时间和“继续路线”。
- 已生成路线列表：展示路线 A/B/C 或历史路线的状态、时长、距离、预算和操作。
- 空状态：提示用户去地图页生成新路线。
- 底部导航：地图、路线、我的，其中路线为选中态。

交互：

- 从路线结果页点击“开始路线”进入，并将刚开始的路线置顶为当前路线。
- 点击“继续路线”进入路线执行页。
- 点击某条历史路线进入路线结果页或路线详情。
- 点击“地图”tab 回到地图首页默认态。

### 5.8 路线执行页

- Stitch 屏幕 ID：`0cbff40a6b03447bbff53ccd785cdd68`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/06-route-execution.html`
- 本地图片：`docs/ui/stitch/urban-sidequest-mobile-v2/images/06-route-execution.png`

目标：

- 让用户边走边知道下一步做什么，并顺手完成打卡和反馈。

核心内容：

- 当前阶段：下一站、交通方式、预计到达时间。
- 路线进度地图。
- 当前任务：到达确认、拍照打卡、跳过、替换。
- 后续节点和时间变化。
- 轻量反馈：节奏、惊喜、是否太累。
- 底部一级导航：地图、路线、我的，其中路线为选中态。执行按钮和反馈入口必须位于一级导航上方。

入口与跳转：

- 从路线 tab 的当前路线模块进入。
- 执行中查看节点详情时弹出 POI 解释卡。
- 退出执行页回到路线 tab。

### 5.9 我的页

- Stitch 屏幕 ID：`70c23edb1d3d41238b9faf3f5b5eef48`
- 本地 HTML：`docs/ui/stitch/urban-sidequest-mobile-v2/html/08-profile.html`
- 本地图片：`docs/ui/stitch/urban-sidequest-mobile-v2/images/08-profile.png`

目标：

- 聚焦用户个人路线资产和偏好，不做社交动态流。

核心内容：

- 用户信息和编辑资料入口。
- 资产摘要：已完成路线、收藏路线、私人地点。
- 我的城市地图：去过或收藏城市入口。
- 最近路线列表。
- 偏好设置：默认交通方式、路线目标、兴趣偏好。
- 反馈与数据：路线反馈、问题上报、数据来源说明。
- 底部导航：地图、路线、我的，其中我的为选中态。

## 6. Stitch 导出记录

导出源：

```text
Project: Urban Sidequest Mobile
Project ID: 12129162147610428521
```

最终屏幕清单：

| 顺序 | 页面 | Stitch 屏幕 ID | 本地 HTML | 本地图片 |
| --- | --- | --- | --- | --- |
| 1 | 地图首页 - Android (默认状态) | `3e9178c5c723490183ee537027e57973` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/01-map-home.html` | `docs/ui/stitch/urban-sidequest-mobile-v2/images/01-map-home.png` |
| 2 | 条件配置页 - Android | `a84e90c853a0408b891cf3c845fc4571` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/02-route-config.html` | `docs/ui/stitch/urban-sidequest-mobile-v2/images/02-route-config.png` |
| 3 | 路线结果页 - 路线 A (Android) - 统一导航 | `783bc7bb299e48aeb12b461ec7ded454` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/03-route-result.html` | `docs/ui/stitch/urban-sidequest-mobile-v2/images/03-route-result.png` |
| 4 | POI 解释卡 - 统一导航 | `3051c3242aae47439ceb63b8fd3a709a` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/04-poi-explanation.html` | `docs/ui/stitch/urban-sidequest-mobile-v2/images/04-poi-explanation.png` |
| 5 | 路线页 - Android | `4ce1683f428b43819ea0502fdf02d845` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/05-routes.html` | `docs/ui/stitch/urban-sidequest-mobile-v2/images/05-routes.png` |
| 6 | 路线执行页 - 统一导航 | `0cbff40a6b03447bbff53ccd785cdd68` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/06-route-execution.html` | `docs/ui/stitch/urban-sidequest-mobile-v2/images/06-route-execution.png` |
| 7 | 登录页 - Android | `14abb4c2edce46728192571395b6190a` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/07-login.html` | `docs/ui/stitch/urban-sidequest-mobile-v2/images/07-login.png` |
| 8 | “我的”页 - Android (修正导航) | `70c23edb1d3d41238b9faf3f5b5eef48` | `docs/ui/stitch/urban-sidequest-mobile-v2/html/08-profile.html` | `docs/ui/stitch/urban-sidequest-mobile-v2/images/08-profile.png` |

本地导出说明：

- 使用 Stitch MCP 的 `get_screen` 获取每个屏幕的 `htmlCode.downloadUrl`。
- 使用 `curl -L` 下载 HTML 设计稿到 `docs/ui/stitch/urban-sidequest-mobile-v2/html/`。
- 使用 `curl -L` 下载 Stitch 截图预览到 `docs/ui/stitch/urban-sidequest-mobile-v2/images/`。
- 图片仅用于快速确认，开发和验收以 HTML 和 Stitch 项目源为准。
- 本节最终屏幕清单是当前实现基准；Stitch 项目里的其他临时版本不作为实现参考。

## 7. 后续开发拆分建议

建议 Android App 第一阶段按以下页面顺序落地：

1. 登录页。
2. 地图首页默认态。
3. 地图选区展开态。
4. 条件配置页。
5. 路线生成加载态和失败态。
6. 路线结果页。
7. 路线一级 tab。
8. 路线执行页。
9. POI 解释卡。
10. 我的页。

开发前仍需要补充：

- 真实地图 SDK 选型和底图样式策略。
- 路线 A/B/C 的数据结构。
- POI 解释卡的数据来源字段。
- 打卡和反馈的数据模型。
- 登录方式、账号体系和隐私协议落地方式。
