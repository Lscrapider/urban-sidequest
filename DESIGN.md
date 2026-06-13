---
name: "城市副本 Android UI"
description: "面向城市路线生成与执行的移动端产品视觉基线，来自 Stitch 项目 Urban Sidequest Mobile 的已确认设计稿。"
colors:
  primary: "#0D4D4D"
  primary-deep: "#003535"
  route-primary: "#0F6B63"
  route-secondary: "#607D8B"
  warning: "#FFB100"
  warning-surface: "#FFF7E0"
  background: "#F8F9FA"
  surface: "#FFFFFF"
  surface-muted: "#F3F4F5"
  text: "#191C1D"
  text-muted: "#404848"
  border: "#BFC8C8"
typography:
  sans: "Inter, system-ui, -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif"
  mono: "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
  title: "22px / 30px, 700"
  section-title: "16px / 24px, 700"
  body: "14px / 22px, 400"
  label: "12px / 18px, 600"
rounded:
  small: "4px"
  medium: "8px"
  large: "12px"
  sheet: "18px"
  pill: "999px"
spacing:
  base: "4px"
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  screen-margin: "16px"
components:
  primary-button: "48px height, deep teal surface, white text, 12px radius"
  secondary-button: "48px height, white surface, teal text, 1px teal border"
  chip: "36px minimum height, pill radius, compact label"
  bottom-sheet: "white surface, 18px top radius, soft shadow, map overlay"
  route-node: "white fill, teal stroke, teal label, halo on map"
  warning-banner: "amber-tinted surface with explicit warning icon and text"
---

<!-- SEED: this design system is derived from the confirmed Stitch UI export, not from implemented frontend tokens. Re-run $impeccable document once code exists to capture the actual components. -->

## Overview

**Creative North Star: "可信路线罗盘".** 城市副本的界面必须像一个熟悉当地情况的路线规划伙伴：先给出能走的路线 A，再解释为什么这样安排，最后允许用户轻量调整。视觉服务于路线判断，不制造攻略浏览、游戏大厅或社交动态的错觉。

**The Map-First Rule.** 地图是空间判断的主画布，结果页和执行页第一眼必须看到路线空间关系；文字说明放在底部抽屉、解释卡和节点详情里。

**The Route A Rule.** 路线 A 永远是默认答案，必须在颜色、层级、按钮和文案上成为主动作；路线 B/C 只能作为备选入口，不允许平级抢注意力。

**Key Characteristics:** 可信、清晰、有探索感；移动端户外可读；输入克制；解释充分；个人资产沉淀清楚但不社交化。

## Colors

**The Teal Owns Action Rule.** Deep Teal 是唯一主行动色，用于主按钮、路线 A、当前 tab、选中态、关键图标和地图路径。它表达可靠和方向感，不用于大面积装饰背景。

**The Yellow Is Risk Rule.** 地图底图已有大量黄色道路，黄色只能用于风险、等待、注意事项等语义状态。路线节点、主路径、主按钮禁止使用黄色。

**The White Node Rule.** 地图节点必须使用白色填充、深青绿描边和深青绿编号文字，必要时加白色 halo 或轻阴影，确保在真实地图道路上仍然可读。

Primary roles:

- Primary action: `primary`, for primary buttons, selected chips, current tab, icon emphasis.
- Primary path: `route-primary`, for route A line and route progress.
- Alternative routes: `route-secondary`, for route B/C dashed lines, secondary indicators and subdued comparison.
- Warning: `warning` and `warning-surface`, only for risk, delay, closure, weather, queue and attention states.
- Surfaces: `background`, `surface`, `surface-muted`, `border`, `text`, `text-muted`, for mobile app shell, cards, inputs and bottom sheets.

## Typography

**The Outdoor Scan Rule.** Type must be readable in mobile, walking and outdoor contexts. Use one sans family for almost all UI, with strong weight contrast instead of decorative type. Do not scale text with viewport width.

**The Compact Authority Rule.** Headings are compact and factual, not marketing-style hero copy. Route names, current step, POI names and warnings must fit their containers without truncating critical meaning.

Use the sans stack for Chinese and English UI. Use the mono stack only for compact route labels, node codes, ETA-like metadata or debug-style identifiers when alignment matters. Body text defaults to 14px; section labels and chips may use 12px; major page titles use 20-22px.

## Elevation

**The Layered Map Rule.** Elevation exists to separate controls from the map, not to make decorative cards. Bottom sheets may use a soft shadow; map controls use crisp borders and small shadows; ordinary content surfaces rely on spacing and 1px borders.

Recommended shadow language:

- Bottom sheet: soft, wide shadow under a white surface.
- Floating map action: small circular shadow with high contrast icon.
- Warning banner: no heavy shadow; use tinted background plus border.
- Content blocks: border first, shadow only when overlaying map.

If a screen looks like stacked cards instead of a navigable route tool, elevation is too loud.

## Components

**Primary Button.** Full-width or high-priority action, 48px height, deep teal background, white text, 12px radius, clear active and disabled states. Use for “下一步配置路线”, “生成路线 A”, “开始路线”, “登录 / 注册”.

**Secondary Button.** Same height as primary, white or surface-muted background, teal text, 1px teal or border stroke. Use for “调整路线”, “替换此点”, “手动框选区域”.

**Chips.** Use chips for structured route choices: duration, transport, route goal, interests and filters. Selected chips use teal tint or border; default chips stay white with light border. Chips must never feel like decorative tags.

**Bottom Sheet.** The core map companion pattern. It contains current route summary, explanation, metrics, warning and primary action. It should reveal enough map above it for spatial context.

**Route Node.** White fill, teal stroke, compact label, stable size. Node labels must remain legible on dense road maps and should not visually merge with yellow roads.

**POI Explanation Card.** Explains why this POI fits the current route: time window, transition logic, risk and replacement. It is not a shop-detail page and must not center ratings or rankings.

**Warning Banner.** Amber-tinted surface with icon and concise copy. Use for closure, weather, queue, transit risk and walking fatigue. It must be semantic, not decorative.

**Navigation.** Bottom navigation has three product destinations: 地图、生成、我的. Current state uses teal; inactive state uses muted text. Navigation must not introduce social feed, ranking or game lobby patterns.

## Do's and Don'ts

Do:

- Do make the map and route spatial relationship the first visual answer on route result and execution screens.
- Do use route A as the obvious default answer, with B/C as subdued alternatives.
- Do explain POI choices through route logic, timing, risk and replacement options.
- Do use defaults, chips and structured controls before free-form input.
- Do keep personal assets focused on completed routes, saved routes, private places, preferences and feedback.
- Do preserve WCAG AA contrast and avoid color-only route distinctions.

Don't:

- Don't look like 点评 App: no merchant review feed, store ranking wall or rating-first POI page.
- Don't look like 传统旅行攻略: no long guide articles, generic recommendation lists or content waterfalls.
- Don't look like 游戏大厅: no complex levels, badges, leaderboards or heavy social mechanics in the first-stage app.
- Don't make a 炫技地图界面: no decorative map effects, glassmorphism, gradient text or motion that hides information.
- Don't make an 过度卡片化页面: route, timeline and POI information need clear hierarchy, not isolated card clutter.
- Don't use yellow for primary routes, route nodes, primary buttons or selected state.
