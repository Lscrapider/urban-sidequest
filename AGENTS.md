# Agent Instructions

每次开始处理本项目任务时，必须先阅读本文件，并按以下要求选择和使用 skill / tool。

## 优先级

- 当前用户明确指令优先。
- 本文件作为本项目的开发规范和执行约束。
- 如果 skill / tool 的官方使用规则与本文件冲突，优先遵循对应 skill / tool 的官方规则。
- 不确定时先说明假设，再执行。
- 不要为了使用某个 skill / tool 而强行扩大任务范围。
- 不要在未理解项目结构、技术栈和上下文的情况下直接修改代码。

## Skill / Tool 分工

- Superpowers：用于任务拆解、方案设计、实现、修 bug、调试、代码审查、收尾验证和多 agent 协作。
- Compose Expert Skill：用于 Jetpack Compose / Compose Multiplatform 工程正确性、Material 3、主题系统、动画、状态管理、性能优化、Navigation、Paging、可访问性和设计到代码落地。
- impeccable：用于 UI 审美、视觉打磨、信息层级、交互细节、可访问性、信息架构和设计一致性。
- scrapider-guidelines：用于约束实现逻辑、最小改动、验证标准和项目现有分层规范。
- codegraph_*：用于分析“这个功能怎么流转”“这个方法谁调用”“改这里影响哪里”等代码结构、调用链和影响范围问题。

## 写代码 / 改代码

- 使用 Superpowers 插件及其适用 skill：做新功能、改行为、实现功能、修 bug、调试、计划、头脑风暴、并行 agent 协作、代码审查和收尾验证时，按 Superpowers 的流程执行。
- 修改前先理解现有项目结构、技术栈、代码约定和上下文。
- 优先做最小必要改动，避免无关重构。
- 不要为了完成当前任务而扩大改动范围。
- 不强制新增单元测试，也不主动启动前端服务或打开浏览器查看页面；如需新增单元测试，先询问确认。
- 可以直接运行项目已有测试、类型检查、静态检查或手动验证。
- 使用 scrapider-guidelines 时，遵循实现逻辑、最小改动、验证标准和项目现有分层规范。
- 如果当前项目不是 Spring Boot，不要强行套用 Spring Boot 分层规范。
- 使用 codegraph_* 分析调用链、影响范围、依赖关系和功能流转。
- 修改后进行必要的测试、类型检查、静态检查或手动验证。
- 如果无法验证，必须说明未验证的原因和潜在风险。

## 默认值 / 参数 / 业务契约

- 已有默认值、阈值、枚举和策略参数均视为业务契约。
- 新功能必须优先复用已有常量、配置项或枚举。
- 禁止重新定义同义参数。
- 禁止写死 magic number。
- 未经用户明确确认，不得修改已有默认值、阈值、枚举含义或策略参数。
- 如确实需要不同值，先说明原因、影响范围和替代方案，并等待用户确认。
- 修改前应搜索项目中是否已有同义参数，例如 `defaultLimit`、`DEFAULT_LIMIT`、`limit`、`pageSize`、`windowSize`、`radius`、`timeout`、`retryCount` 等。
- 不要为了通过当前功能而局部覆盖已有默认值。

## Android / Jetpack Compose 开发

- 当前项目为 Android Kotlin + Jetpack Compose 应用时，必须按 Android 原生应用思路开发。
- 严厉禁止手画 icon，必须使用 image generator 插件生成
- 不要套用 Web / Vue / 后台管理系统的页面结构和交互习惯。
- UI 默认基于 Material 3 和 Compose Material3 组件体系实现。
- 优先使用已有主题、颜色、字体、Shape、Spacing 和组件封装。
- 编写、重构或审查 Compose UI 时，优先使用 Compose Expert Skill。
- Compose Expert Skill 负责 Compose 工程正确性，包括：
  - `@Composable` 设计
  - state hoisting
  - `remember` / `derivedStateOf` / `LaunchedEffect` / `SideEffect` 使用
  - `Modifier` 链顺序
  - recomposition 性能
  - `LazyColumn` / `LazyRow` / Paging 3 性能
  - Navigation / NavHost
  - Material 3 组件使用
  - Material 3 motion
  - Theme / Color / Typography / Shape
  - accessibility
  - animation
  - atomic design systems
  - design-to-code / design-to-compose 落地
  - 避免已废弃或不推荐的 Compose 写法
- 写 Compose UI 时注意：
  - 优先组件拆分，避免单个 Composable 过大。
  - 遵循 state hoisting，状态尽量由上层或 ViewModel 管理。
  - UI 层只负责展示和交互，不在 Composable 中写复杂业务逻辑。
  - 避免重复定义颜色、尺寸、圆角、阴影等设计参数。
  - 避免硬编码 magic number，优先复用主题、常量或已有组件参数。
  - 不要为了局部效果破坏全局主题一致性。
  - 不要随意引入不必要的第三方 UI 库。
  - 不要把一次性页面效果扩散成全局设计规范。
  - 不要为了视觉效果引入复杂状态、过度重组或破坏 Material 3 语义。

## Android UI / 视觉设计方向

- UI 风格保持：简约、高级、克制、城市探索、轻任务感。
- Android UI 必须优先考虑原生移动端体验，而不是 Web 后台体验。
- 设计应符合 Material 3 的基础审美和交互习惯。
- 页面应关注移动端单手操作、触控区域、信息层级、系统栏适配、深色模式和状态反馈。
- 避免以下风格：
  - 浮夸渐变
  - 过重阴影
  - 大面积玻璃拟态
  - 发光边框
  - Web SaaS Dashboard 风格
  - Vue 后台模板风格
  - 过度装饰性图标或插画
  - 信息密度过高的后台管理系统布局
- 地图、任务、地点选择相关页面应优先保证：
  - 信息层级清晰
  - 移动端单手操作友好
  - 地图内容不被 UI 过度遮挡
  - 主按钮明确
  - 卡片轻量、留白克制
  - 状态反馈清楚
  - 关键操作路径短
- 重点关注页面和组件：
  - 地图选择页面
  - 任务卡片
  - 地点详情
  - 底部导航
  - 顶部栏
  - 主要按钮
  - 空状态 / 加载状态 / 错误状态
  - 权限请求页面
  - 定位 / 地图 / POI 相关交互

## 做前端 / 移动端 UI 设计

- 使用 impeccable 处理 UI 设计、重构、视觉打磨、交互、响应式、可访问性和信息架构。
- 如果当前项目是 Android Kotlin + Jetpack Compose，必须按 Android 原生应用和 Material 3 设计习惯处理。
- 不要套用 Web 前端、Vue 后台模板或 SaaS Dashboard 风格。
- impeccable 负责设计审查、视觉 polish、信息层级和品牌感优化。
- Compose Expert Skill 负责 Compose 代码结构、状态管理、性能、Material 3 API、Android 工程实现和设计到代码落地。
- 当 impeccable 的视觉建议与 Compose 工程实践冲突时，先说明冲突点，再优先保证 Compose 工程正确性和 Android 原生体验。
- 不要只依赖 impeccable 编写 Compose 代码。
- 做 Android UI 方案时，应结合 Compose Expert Skill 的 Material 3、motion、theming、accessibility 和 design-to-compose 规则。

## Impeccable 使用规则

- 使用 impeccable 处理 UI 设计、视觉审查、信息架构、交互细节、可访问性和视觉打磨。
- 本项目优先使用已安装的 impeccable skill 或 `/impeccable` 命令。
- 不要假设项目内一定存在 impeccable 脚本目录。
- 如果项目内没有 `.agents/skills/impeccable/scripts`、`.impeccable/scripts` 或 `.impeccable/live/config.json`，这是正常情况，不要视为错误。
- 不要反复提示“impeccable 的项目内脚本路径不存在”。
- 只有在 impeccable 完全不可用、无法执行或无法读取设计上下文时，才提示安装或路径问题。
- 当前项目是 Android Kotlin + Jetpack Compose 时，不需要配置浏览器 live mode，除非用户明确引入 Web 前端。
- impeccable 主要负责审美、设计一致性和视觉 polish。
- Compose 代码结构、状态管理和 Android 工程实现仍需遵循 Android / Compose 开发规则。

## Compose Expert Skill 使用规则

- 本项目已安装 Compose Expert Skill。
- 涉及 Jetpack Compose、Compose Multiplatform、MaterialTheme、Material 3、Modifier、recomposition、LazyColumn、NavHost、Paging 3、动画、主题、可访问性或设计到代码落地时，优先使用 Compose Expert Skill。
- Compose Expert Skill 可以用于：
  - 新增 Compose 页面
  - 重构 Compose 组件
  - 审查 Compose 代码
  - 分析重组和性能问题
  - 处理 Navigation / NavHost
  - 处理 LazyList / Paging 3
  - 优化 Material 3 主题
  - 将设计方案落地为 Compose 组件
  - 检查 accessibility
  - 检查动画和 Material 3 motion
- 如果任务是 GitHub PR、代码审查、diff 审查或用户提到 “review this PR”、“check this code”、“what's wrong with this” 等审查语义，应优先按 Compose Expert Skill 的 Review Mode 规则执行。
- 不要把 Compose Expert Skill 当成纯审美工具；它主要负责 Compose 和 Android UI 工程质量。
- 不要把 impeccable 当成 Compose 工程工具；它主要负责通用视觉审美和 polish。
- 当两个 skill 的建议冲突时，优先保证：
  1. Android 原生体验
  2. Compose 工程正确性
  3. Material 3 语义
  4. 项目现有设计规范
  5. 视觉 polish

## 项目上下文文件

- 修改 UI、产品逻辑或交互前，优先阅读：
  - `PRODUCT.md`
  - `DESIGN.md`
  - `AGENTS.md`
  - 当前页面对应的 Screen / Component / ViewModel
  - Compose theme 相关文件
- 如果 `PRODUCT.md`、`DESIGN.md` 与代码实现不一致，不要静默覆盖。
- 发现产品文档、设计文档和代码实现存在冲突时，先说明冲突点，再按用户确认的方向修改。
- 不要在未确认的情况下刷新或重写 `PRODUCT.md`、`DESIGN.md`。

## 多 Agent 协作

- 对于可以并行、相互独立的任务，可以拆分给多个 agent 分别执行。
- 对于存在强依赖关系的任务，不要盲目并行。
- Codex 主线负责整体任务拆解、方案校验、代码审核、结果整合和最终验收。
- 其他 agent 的输出必须经过 Codex 主线复核后再采用。
- 如果多个 agent 的结论冲突，Codex 主线必须说明差异，并选择更符合项目规范和当前用户指令的方案。

## 执行要求

- 修改前先理解现有项目结构、技术栈、代码约定和上下文。
- 修改前先确认相关文件、调用链和已有实现，不要凭空猜测。
- 优先做最小必要改动，避免无关重构。
- 不要为了完成当前任务而扩大改动范围。
- 不要主动启动前端服务或打开浏览器做可视化验证，除非用户明确要求。
- 修改后进行必要的测试、类型检查、静态检查或手动验证。
- 如果无法验证，必须说明未验证的原因和潜在风险。
- 输出结果时说明：
  - 修改了哪些文件
  - 为什么这样改
  - 如何验证
  - 是否存在未验证风险

## 语言要求

- 项目文档使用中文。
- 代码注释使用中文。
- Git 提交信息使用中文。
- 面向用户的说明优先使用中文。