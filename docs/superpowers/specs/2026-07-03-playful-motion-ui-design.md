# 城市副本动画 UI 与轻副本感样式设计

## 背景

当前 `PRODUCT.md` 与 `DESIGN.md` 将城市副本定义为可信、清晰、克制的城市路线生成工具。这个方向保证了路线可读性和移动端执行效率，但界面容易偏静态，缺少“城市副本”这个产品名天然带来的轻任务感和完成反馈。

本设计文档只处理 UI 动画、视觉样式和前端交互反馈，不引入新的路线难度算法、隐藏点机制、排行榜、复杂等级或重社交。所有“副本感”都必须基于现有路线数据和状态表达，不能伪造业务能力。

## 目标

- 让产品从“静态路线工具”变成“清爽、灵动、有任务反馈的城市探索 App”。
- 保留真实地图、路线可读性、路线 A 主推和 Android 原生体验。
- 增强生成中、地图选区、路线执行、完成反馈、底部导航和发现页分享流的动态表现。
- 用 Compose 原生动画优先落地，允许少量新技术或依赖作为第二阶段增强。
- 所有循环动效低干扰，并提供 reduced motion 友好方案。

## 非目标

- 不设计真实游戏系统，不新增后端玩法规则。
- 不引入复杂等级、排行榜、徽章墙或游戏大厅页面。
- 不把路线结果包装成虚假的隐藏点、随机宝箱或不可解释奖励。
- 不使用大面积渐变、发光边框、玻璃拟态、重阴影或过度装饰插画。
- 不优先使用 image generation 生成动画素材。动画应由 Compose、Canvas 或可控矢量动画实现。

## 新视觉北极星

**城市副本装载感。**

界面应该像在真实城市地图上装载一场轻量探索：路线生成有进度，地图选区有锁定反馈，路线执行有章节推进，完成后有结算感。它仍然是路线工具，但每个关键状态都有明确反馈。

关键词：

- 清爽
- 灵动
- 轻副本感
- 城市探索
- 真实地图优先
- 反馈明确
- 可执行

## 设计原则

### 1. 地图仍然是主角

动效不能遮挡地图判断。地图选区、路线结果、路线执行页上的动效只用于确认状态、引导当前目标和表达进度。

### 2. 副本感来自状态，不来自虚构玩法

可以使用“装载”“章节”“完成”“今日路线”等 UI 语言，但必须保留真实路线信息：时间、距离、风险、站点、交通方式和解释。

### 3. 动效服务动作

每个动效必须对应一个用户可感知状态：

- 已选范围
- 正在生成
- 路线已生成
- 当前站点
- 已打卡
- 路线完成
- 已分享
- tab 已切换

### 4. 控制强度

产品可以更有趣，但不能变成高噪音界面。默认动效保持 150-300ms；生成中循环可以更长，但透明度要低、面积要小。

### 5. 先局部增强，再考虑新依赖

第一阶段优先用 Compose 原生能力完成。只有当完成反馈或分享图确实需要更丰富的矢量动画时，再引入 Lottie Compose 等依赖。

## 视觉样式方向

### 色彩

保留现有主色：

- `DeepTeal`：主行动、底部导航选中态、路线 A 关键元素。
- `RouteTeal`：路线轨迹、当前进度、生成中流动线。
- `AreaGreen` / `InfoCyan`：完成、已走过、城市图鉴、分享成功等正向状态。
- `WarningAmber`：仅用于风险、等待、注意事项。
- `ErrorRed`：错误。

建议新增或派生少量 UI 语义色，不需要新增大设计系统：

- `QuestSurfaceBlue`: `DeepTeal.copy(alpha = 0.06f)`，用于轻副本面板底色。
- `QuestBorderActive`: `RouteTeal.copy(alpha = 0.42f)`，用于生成中或当前任务边框。
- `QuestSuccessSurface`: `InfoCyanSurface` 或 `AreaGreenSurface`，用于完成反馈。
- `QuestProgressTrack`: `RouteTeal.copy(alpha = 0.18f)`，用于细进度轨。

禁止：

- 不用紫蓝大渐变作为页面背景。
- 不用发光描边。
- 不用黄色表达主路线或奖励。

### 圆角与层级

延续当前设计：

- 普通内容卡片：12dp。
- 地图底部 sheet：顶部 18dp，底部 12dp。
- 通知浮层与完成结算卡：16-18dp。
- chip / 状态点：pill 或圆形。

层级语言：

- 普通列表卡片：边框优先，阴影极少。
- 地图浮层：可使用轻 shadow，避免地图上信息混在一起。
- 完成结算卡：可以比普通卡片更有层级，但 shadow blur 不应过重。

### 字体与文案

字体继续使用现有 Material 3 typography。文案可以更有任务感，但不要牺牲解释性。

推荐替换方向：

- “路线生成” -> “生成今日路线”
- “正在生成路线” -> “正在装载路线”
- “地图选区” -> “探索范围”
- “已选：当前位置附近” -> “已锁定探索范围”
- “路线已生成” -> “今日路线已生成”
- “路线完成” -> “今日副本完成”

避免：

- “隐藏点已解锁”，除非后端真实返回可解释的可选点。
- “难度 S 级”，除非有真实强度计算。
- “奖励”“金币”“排行”等强游戏化词。

## 动效系统

### 时长

| 场景 | 时长 |
| --- | --- |
| 点击反馈、icon scale | 100-150ms |
| tab、chip、按钮状态变化 | 150-200ms |
| 卡片进入、通知进入 | 220-250ms |
| bottom sheet、弹层进入 | 250-300ms |
| 页面级共享元素或大容器变换 | 400-500ms |
| 生成中循环动效 | 1200-1800ms |
| 完成反馈 stagger | 每项间隔 50-70ms |

### Easing

优先使用 Material 3 motion：

- 进入：`MotionTokens.EasingEmphasizedDecelerateCubicBezier`
- 退出：`MotionTokens.EasingEmphasizedAccelerateCubicBezier`
- 状态变化：`MotionTokens.EasingEmphasizedCubicBezier`
- 循环流动：`MotionTokens.EasingLinearCubicBezier`

如果项目当前 Material 3 版本没有开放 `MotionTokens`，可以先在 UI 模块定义小范围私有 motion 常量，后续再切换。

### Compose API 选择

- 单一属性变化：`animateColorAsState`、`animateFloatAsState`。
- 多属性同步：`updateTransition`。
- 出入场：`AnimatedVisibility`。
- 文案或状态切换：`AnimatedContent`。
- 循环生成中动效：`rememberInfiniteTransition`。
- 位移、缩放、透明度：优先 `graphicsLayer`，减少 layout 抖动。
- 扫描线、状态点、进度流动：优先 `Canvas` 或 draw modifier。

### Reduced motion

需要提供一个统一判断策略。第一阶段可以用轻量方案：

- 若系统动画缩放关闭或应用内 reduced motion 开启，则关闭循环呼吸、扫描线和 shimmer。
- 保留静态状态色、静态进度和短 crossfade。
- 不让内容依赖动画才可见。

后续可以抽象为：

```kotlin
data class UrbanMotionPreference(
    val reducedMotion: Boolean
)
```

## 核心组件设计

### 1. UrbanQuestLoadingCard

用于路线库中的 `PENDING` / `GENERATING` 状态，也可用于路线提交后反馈。

UI 行为：

- 卡片边框低透明呼吸，周期 1600ms。
- 卡片底部 2dp 流动线，周期 1400ms。
- 状态点 alpha 轻微变化。
- 阶段文案使用 `AnimatedContent` 上滑 8dp + fade。

样式：

- 背景：白色或 `QuestSurfaceBlue`。
- 边框：`RouteTeal.copy(alpha = 0.24f..0.44f)`。
- 文案：标题清楚，阶段说明保持一行或两行。

涉及文件：

- `android-app/app/src/main/java/com/urbansidequest/app/feature/routes/RoutesScreen.kt`
- `android-app/app/src/main/java/com/urbansidequest/app/ui/components/UrbanStaticComponents.kt`

实现边界：

- 只在 `generationStatus == "PENDING"` 或 `"GENERATING"` 时启用。
- 列表中多个生成中卡片时，只做轻量 Canvas，不使用重型动画资源。

### 2. UrbanAnimatedBottomNavigationBar

增强现有 `UrbanBottomNavigationBar`，不改变导航结构。

UI 行为：

- 选中胶囊颜色 180ms 过渡。
- icon scale 从 1.0 到 1.08。
- label color 180ms 过渡。
- 可选短线指示器 200ms 移动。

样式：

- 胶囊保持浅蓝，不做高饱和背景。
- 图标仍使用 Material Icons。
- tab 点击反馈固定一致，不做弹跳。

涉及文件：

- `android-app/app/src/main/java/com/urbansidequest/app/ui/components/UrbanNavigation.kt`

实现边界：

- 保留 `Role.Tab` 和 `selected` semantics。
- 保留 48dp 以上触控区域。
- 第一阶段可先不做全栏滑动短线，只做 item 内动效。

### 3. UrbanQuestNoticeOverlay

替代当前分散的生成结果 Dialog 和分享 Toast 风格，实现统一轻量通知。

UI 行为：

- 顶部浮层从上方 12dp 进入，fade + slide。
- 成功、失败、提交中、分享中统一样式。
- 提交中不自动消失；成功/失败 2200-3000ms 后自动消失。
- 可选主动作，如“查看路线”“去路线库”。

样式：

- 白色 surface。
- 左侧状态圆形标记。
- 成功用 `RouteTeal` 或 `InfoCyan`，失败用 `ErrorRed`。
- 不用系统 Toast。

涉及文件：

- `android-app/app/src/main/java/com/urbansidequest/app/MainActivity.kt`
- `android-app/app/src/main/java/com/urbansidequest/app/ui/components/UrbanStaticComponents.kt`

实现边界：

- 生成成功如果需要立即跳转决策，可以保留主动作，但不默认强迫用户二次选择。
- live region 使用 `LiveRegionMode.Polite`；错误可视情况使用 assertive。

### 4. UrbanMapSelectionMotion

地图选区的轻副本感增强。

UI 行为：

- 从地图首页进入选区 sheet 时，sheet 上浮 250ms。
- 锁定中心点后，中心点出现一次外扩圆环，周期 600ms，只播放一次。
- `MapSelectionSheet` 顶部出现一条 600ms 扫描线，表示范围已锁定。
- “下一步配置路线”点击时按钮轻微压缩到 0.98 后恢复。

样式：

- sheet 文案改为“已锁定探索范围”。
- chip 文案保留“自动范围”“按时长计算”。
- 扫描线颜色使用 `RouteTeal.copy(alpha = 0.32f)`。

涉及文件：

- `android-app/app/src/main/java/com/urbansidequest/app/feature/mapselect/MapSelectScreen.kt`

实现边界：

- 不改变地图 SDK 渲染逻辑。
- 不在地图上做持续动画，避免遮挡真实地图。

### 5. UrbanRouteChapterProgress

路线执行页的章节进度感。

UI 行为：

- 当前 POI 节点有低透明 pulse。
- 打卡后节点从空心变实心，连线进度向下一站推进。
- 当前目标切换时，站点标题用 `AnimatedContent` 短滑动。
- 完成最后一站后触发完成结算卡。

样式：

- 当前节点：蓝底白心或蓝描边加实心点。
- 已完成节点：青绿或蓝绿，不用黄色。
- 未完成节点：白底灰边。

涉及文件：

- `android-app/app/src/main/java/com/urbansidequest/app/feature/mapselect/MapSelectScreen.kt`
- `android-app/app/src/main/java/com/urbansidequest/app/feature/execution/RouteExecutionScreen.kt`

实现边界：

- 不改变打卡半径和打卡逻辑。
- 所有进度只由当前 `completedStopIds` 和路线 stop 列表驱动。

### 6. UrbanRouteCompletionCard

完成反馈是最适合提升可玩感的地方。

UI 行为：

- 路线完成后从底部上浮一张结算卡。
- 展示路线标题、完成站点数、总距离、总时长、完成区域。
- 2-3 个数据 chip 依次出现，每项间隔 60ms。
- 主动作是“生成分享图”或“分享路线”，次动作是“返回路线库”。

样式：

- 标题：“今日副本完成”。
- 可使用真实地图缩略图或路线预览图作为顶部区域。
- 不能使用金币、宝箱、排行榜、徽章雨。

涉及文件：

- `android-app/app/src/main/java/com/urbansidequest/app/MainActivity.kt`
- `android-app/app/src/main/java/com/urbansidequest/app/feature/routes/RoutesScreen.kt`
- `android-app/app/src/main/java/com/urbansidequest/app/feature/mapselect/MapSelectScreen.kt`

实现边界：

- 第一阶段可以仅在完成主动路线后显示本地浮层。
- 分享图仍复用现有分享接口。

### 7. Discover Share Motion

发现页分享流保持真实地图缩略图，但增加轻微动态与错位感。

UI 行为：

- 双列保留轻微错位，右列顶部偏移 10-14dp。
- 地图图像加载时使用地图色 skeleton，不用普通灰块。
- tile 按下时图片 scale 到 0.98，释放恢复。
- 打开路线时可后续加入共享元素过渡。

样式：

- 图片圆角 12dp。
- 文案最多 2-3 行。
- 不做完整重卡片墙，避免点评/攻略信息流感。

涉及文件：

- `android-app/app/src/main/java/com/urbansidequest/app/feature/discover/DiscoverScreen.kt`

实现边界：

- 当前手写 URL 图片加载可以先保留。
- 如果分享数量增长，第二阶段考虑引入 Coil Compose，替代手写 Bitmap 加载并支持 crossfade、placeholder、缓存。

## 可选新技术与依赖

用户允许使用新的技术或框架，但应分阶段引入。

### 第一阶段：不新增依赖

优先使用：

- Compose Animation
- Material 3 motion tokens 或本地 motion 常量
- Canvas / draw modifier
- `graphicsLayer`
- `AnimatedVisibility`
- `AnimatedContent`
- `rememberInfiniteTransition`

原因：

- 改动可控。
- 与现有 Kotlin Compose 代码一致。
- 不增加包体和依赖风险。
- 更容易支持 reduced motion。

### 第二阶段：按需引入 Lottie Compose

适用场景：

- 完成结算卡的轻量成功动效。
- 分享图生成成功的标记动画。
- 空状态或加载状态需要更精细的矢量动画。

限制：

- 每个动画 JSON 要本地托管，不能运行时远程加载。
- 只允许 1-2 个关键资产，不做全 App Lottie 化。
- 必须有静态 fallback。

建议依赖：

```kotlin
implementation("com.airbnb.android:lottie-compose:<version>")
```

版本需在实施前根据项目 Gradle 与 Compose 版本确认，不能在设计文档中写死。

### 第二阶段：按需引入 Coil Compose

适用场景：

- 发现页真实地图缩略图加载。
- 分享图列表需要缓存、placeholder、crossfade。

限制：

- 只替换图片加载，不改变业务数据结构。
- placeholder 必须保持稳定尺寸，避免瀑布流跳动。

### 暂不建议

- Rive：能力强，但当前需求过重。
- MotionLayout：对当前局部微交互收益不高。
- Navigation Compose 重构：现有屏幕栈手写，切换成本大，不作为动画 UI 第一阶段前置条件。
- image generation：适合静态视觉资产和分享图模板，不适合实时 UI 动画。

## 分阶段落地计划

### 阶段 1：轻副本感基础动效

目标：最小改动获得明显动感。

内容：

1. 增强底部导航选中态动画。
2. 增强路线库生成中卡片。
3. 抽出统一 motion 常量。
4. 抽出轻量状态点和流动线组件。

主要文件：

- `UrbanNavigation.kt`
- `UrbanStaticComponents.kt`
- `RoutesScreen.kt`

验收：

- tab 切换有稳定反馈。
- 生成中状态一眼可见但不影响阅读。
- 无新增依赖。

### 阶段 2：通知与地图选区反馈

目标：让关键操作有明确状态反馈。

内容：

1. 新增 `UrbanQuestNoticeOverlay`。
2. 替换分享 Toast 样式。
3. 将路线生成提交/成功/失败从强 Dialog 调整为轻浮层，必要时保留动作按钮。
4. 地图选区 sheet 加锁定反馈和一次性扫描线。

主要文件：

- `MainActivity.kt`
- `UrbanStaticComponents.kt`
- `MapSelectScreen.kt`

验收：

- 不使用系统 Toast。
- 成功/失败反馈不强迫用户二次选择。
- 地图选区反馈清晰但不遮挡地图。

### 阶段 3：路线执行章节感与完成结算

目标：让“走路线”变成有进度、有完成感的体验。

内容：

1. 当前 POI 节点 pulse。
2. 打卡节点状态过渡。
3. 当前目标 `AnimatedContent` 切换。
4. 完成后展示结算卡。

主要文件：

- `MapSelectScreen.kt`
- `RouteExecutionScreen.kt`
- `RoutesScreen.kt`

验收：

- 路线执行进度比当前更明确。
- 完成后有清晰成就反馈。
- 不新增后端机制。

### 阶段 4：发现页分享流质感

目标：让分享页更像城市探索成果，而不是静态列表。

内容：

1. 双列轻微错位。
2. 地图色 skeleton。
3. tile 按压 scale。
4. 可选引入 Coil Compose。

主要文件：

- `DiscoverScreen.kt`

验收：

- 真实地图缩略图仍是主视觉。
- 列表更有节奏但不混乱。
- 图片失败、加载中、长文案不会导致布局跳动。

## 验收标准

### 视觉

- 页面仍然是 Android 原生移动端体验，不像 Web 后台或游戏大厅。
- 地图相关页面不被动效遮挡。
- 生成中、执行中、完成、错误、分享成功状态容易区分。
- 整体比当前更灵动，但没有高噪音装饰。

### 工程

- 新组件优先放在现有 `ui/components` 或对应 feature 文件中。
- 动画状态由 UI state 或参数驱动，不在 Composable 中写复杂业务逻辑。
- 可复用动效抽小组件，不创建大而全设计系统。
- 不修改已有业务默认值、阈值、枚举含义。
- 新依赖必须单独评估版本、包体和必要性。

### 无障碍

- 不仅靠颜色表达状态。
- 通知使用 live region。
- 所有可点击元素保持至少 48dp 触控区域。
- reduced motion 下关闭循环和扫描动效。

### 性能

- 列表中循环动画只在必要状态启用。
- 位移、缩放、透明度优先使用 `graphicsLayer`。
- 动画不触发不必要的布局重排。
- 发现页图片占位尺寸稳定。

## 风险与处理

### 风险 1：可玩感变成游戏大厅

处理：

- 文案保持“路线、探索、完成”，不使用金币、排行、抽奖、宝箱等表达。
- 完成反馈以真实路线数据为主。

### 风险 2：动效影响户外阅读

处理：

- 循环动效面积小、透明度低。
- 关键文字不动或只短暂切换。
- 地图页不做持续大面积动画。

### 风险 3：新依赖扩大维护成本

处理：

- 第一阶段不新增依赖。
- Lottie / Coil 只作为第二阶段按需引入。
- 每个新依赖要有明确替代收益。

### 风险 4：与现有文档冲突

处理：

- 本文档作为新方向草案，不直接覆盖 `PRODUCT.md` 和 `DESIGN.md`。
- 后续确认后，再同步更新正式产品和设计文档。

## 推荐优先级

优先做：

1. 底部导航动效。
2. 路线库生成中卡片动效。
3. 统一轻量通知浮层。
4. 地图选区锁定反馈。
5. 完成结算卡。

后置做：

1. 发现页图片加载体系升级。
2. Lottie 完成动画。
3. 共享元素过渡。
4. 更完整的 motion preference 设置。

## 实施前需要确认

当前建议先按第一阶段实施，不新增依赖。如果第一阶段效果仍不够有趣，再评估 Lottie Compose 和 Coil Compose。

第一阶段预计只涉及 UI 层，不需要后端改动，不需要新增单元测试；可通过 Android 构建和人工走查验证。
