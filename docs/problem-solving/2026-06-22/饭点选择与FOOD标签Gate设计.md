# 饭点选择与 FOOD 标签 Gate 设计

本文定义路线生成请求中“是否安排午餐/晚餐”与 `FOOD_*` 兴趣标签的关系。该契约当前已在后端请求参数、校验、候选召回、LLM prompt payload、Route X 特征和路线偏好模拟器中落地；历史样本兼容以 `featureSchemaVersion` 为边界处理。

## 1. 问题背景

当前后端主要用时间窗口隐式判断饭点需求：

```text
路线时间覆盖 11:30-13:30 -> 推断需要午餐
路线时间覆盖 17:30-20:00 -> 推断需要晚餐
```

同时，在 Amap 候选召回中还有一条逻辑：

```text
如果用户没有 FOOD 兴趣，且路线覆盖午餐/晚餐窗口
  -> 自动追加 MEAL_LUNCH / MEAL_DINNER 系统召回计划
```

这会导致一个问题：用户只是 13:00 出发、并不想吃饭，前端即使不传 `FOOD_*`，后端仍可能自动召回并提示 LLM 安排饭点。最后 Route X / judge 也会把“没有饭点”视作缺失。

所以饭点需求不能只由 `departureTime + durationMinutes` 隐式决定，必须让用户选择是否安排午餐/晚餐。

## 2. 决策

新增独立的饭点选择字段：

```json
{
  "mealWindows": ["LUNCH", "DINNER"]
}
```

取值：

```text
LUNCH
DINNER
```

语义：

- `mealWindows` 表示用户明确希望本次路线安排哪些正餐饭点。
- 空数组 `[]` 表示用户明确不希望系统安排午餐或晚餐。
- 字段缺失或 `null` 时，后端统一兜底为 `[]`。
- `departureTime + durationMinutes` 只决定哪些饭点是合法可选项；用户最终选择决定是否真的要求安排饭点。

需要区分两个集合：

```text
feasibleMealWindows = 根据路线时间窗可覆盖的饭点集合
mealWindows = 用户在 feasibleMealWindows 中最终选择的饭点集合
```

约束：

```text
mealWindows 必须是 feasibleMealWindows 的子集。
```

例如：

```text
09:00-12:00 -> feasibleMealWindows = [LUNCH]，可以选择 LUNCH，也可以选择 []
17:30-20:30 -> feasibleMealWindows = [DINNER]，可以选择 DINNER，也可以选择 []
09:00-12:00 + mealWindows=[DINNER] -> 非法请求，后端应返回 400
```

这不是恢复“时间覆盖就强制安排饭点”的旧逻辑。时间窗只做合法性边界，用户仍然可以取消任意可选饭点。

当前实现入口：

```text
RouteGenerateParam.mealWindows
MealWindowSupport.feasibleMealWindows(...)
ValidateRouteRequestStep.validateMealWindows(...)
ValidateRouteRequestStep.validateFoodInterestRequiresMealWindow(...)
```

## 3. FOOD 标签 Gate

`FOOD_*` 兴趣标签只在用户选择至少一个饭点时开放。

规则：

```text
mealWindows 非空:
  允许选择 FOOD 子标签

mealWindows 为空:
  前端禁用并清空所有 FOOD 子标签
  request.interestTags 禁止出现 FOOD_* 标签
```

原因：

- `FOOD_*` 表达“这次正餐想吃什么类型”，例如川菜、火锅、清真、西餐。
- 如果用户明确不安排午餐/晚餐，就不应该再选择 FOOD 子标签。
- 不再用 FOOD 兴趣去反向推断“是否需要吃饭”；饭点需求由 `mealWindows` 表达。

非 FOOD 兴趣不受影响：

```text
SCENIC / CULTURE / MUSEUM / COFFEE / SHOPPING / LOCAL / NIGHT / PHOTO / ENTERTAINMENT / EVENT
```

其中 `COFFEE` 仍可独立选择。咖啡/休息不是正餐饭点，不受 `mealWindows` gate 限制。

## 4. 前端行为

前端根据出发时间和路线时长计算 `feasibleMealWindows`，只展示其中可选的饭点；用户可以关闭任意可选饭点。

建议规则：

```text
路线明显覆盖午饭窗口 -> LUNCH 可选，默认勾选 LUNCH
路线明显覆盖晚饭窗口 -> DINNER 可选，默认勾选 DINNER
边界时间，如 13:00 出发 -> 可以默认提示 LUNCH，但必须允许用户取消
路线不覆盖某个饭点窗口 -> 不展示该饭点选项，request 也不能传该饭点
```

交互口径：

```text
这段路线接近午饭时间，是否加入午餐点？
[加入午餐] [不安排]
```

当用户取消所有饭点：

- 清空所有 `FOOD_*` 选择。
- 禁用 FOOD 子标签 UI。
- request 传 `mealWindows: []`。

前端不应允许用户自由组合不可达饭点。例如 09:00-12:00 的路线不能选择 `DINNER`。

## 5. 后端影响

### 5.1 请求校验

后端需要校验：

```text
mealWindows 缺失或 null 时，统一按 [] 处理。
mealWindows 只能包含 LUNCH / DINNER。
mealWindows 必须是 feasibleMealWindows 的子集；否则返回 400。
mealWindows 为空时，interestTags 不允许包含 FOOD_*。
mealWindows 非空时，FOOD 子树仍遵守既有规则：
  不允许 FOOD 根标签
  FOOD 内部最多显式选择 3 个标签
  FOOD 同一父子链互斥
  FOOD 子树整体只算一个全局大类
```

### 5.2 候选召回

饭点召回必须以 `mealWindows` 为准：

```text
mealWindows 包含 LUNCH，且 interestTags 不包含 FOOD_*:
  允许追加 MEAL_LUNCH 系统计划

mealWindows 包含 DINNER，且 interestTags 不包含 FOOD_*:
  允许追加 MEAL_DINNER 系统计划

mealWindows 非空，且 interestTags 包含 FOOD_*:
  饭点候选由 FOOD 兴趣召回承担，不再重复追加 MEAL_LUNCH / MEAL_DINNER 系统计划

mealWindows 不包含对应窗口:
  禁止因为时间覆盖而自动追加该饭点计划
```

如果 `interestTags` 包含 `FOOD_*`，也意味着 `mealWindows` 必须非空。`FOOD_*` 表达“本次正餐想吃什么类型”，不是独立的吃饭开关；吃饭开关只由 `mealWindows` 表达。系统饭点计划只在用户选择了 `mealWindows` 但没有 FOOD 兴趣时补充，用于保证正餐需求不被遗漏。

时间窗只参与 `feasibleMealWindows` 校验。通过校验后，召回不再单独用时间窗口重复判断，避免同一规则在多处漂移。实现上应把时间 overlap 收敛到共享 helper，供校验计算 `feasibleMealWindows` 使用；召回、Route X 和 composer fallback 只读 `mealWindows`。

### 5.3 LLM composer

composer prompt 中“如果覆盖午饭/晚饭窗口，优先安排 FOOD/MEAL stop”应改为：

```text
如果 request.mealWindows 包含 LUNCH，必须优先安排午餐 MEAL stop。
如果 request.mealWindows 包含 DINNER，必须优先安排晚餐 MEAL stop。
如果 request.mealWindows 不包含某个饭点，不要仅因为路线时间覆盖该窗口而硬塞餐厅。
```

`routeRole=MEAL` 仍需填写：

```text
intendedMealWindow = LUNCH / DINNER / OTHER
```

### 5.4 Route X

Route X 中所有 meal 派生特征都要把源头从时间窗口推断切换为读取 `mealWindows`。

```text
requiresLunchFlag = mealWindows contains LUNCH
requiresDinnerFlag = mealWindows contains DINNER
missingRequiredMealFlag = 用户要求的饭点没有被覆盖
lunchCoveredFlag / dinnerCoveredFlag = 路线是否实际安排了对应 MEAL stop
lunchRequiredMissingMeal = requiresLunchFlag * (1 - lunchCoveredFlag)
dinnerRequiredMissingMeal = requiresDinnerFlag * (1 - dinnerCoveredFlag)
```

实现要求：

- 只在源头计算 `requiresLunchFlag` / `requiresDinnerFlag` 时读取 `mealWindows`。
- 下游 `missingRequiredMealFlag`、`lunchRequiredMissingMeal`、`dinnerRequiredMissingMeal` 等字段沿用该源头结果。
- 不要在派生层再次用 `departureTime + durationMinutes` 判断饭点需求，否则会回到隐式时间推断。

如果用户没有选择午餐/晚餐：

```text
requiresLunchFlag = 0
requiresDinnerFlag = 0
missingRequiredMealFlag = 0
```

这样不会把“用户明确不要吃饭”的路线错误训练成坏样本。

当前 `RouteInputFeatureExtractor` 已按 `mealWindows` 读取 `requiresLunchFlag` / `requiresDinnerFlag`，不再用 `departureTime + durationMinutes` 隐式推断饭点需求。

### 5.5 LLM judge / 模拟用户

judge 也必须按 `mealWindows` 评价：

- 用户要求饭点，路线缺少合理 MEAL stop：可以扣分，并说明缺失饭点。
- 用户未要求饭点，路线没有 MEAL stop：不应扣分。
- 用户未要求饭点但路线硬塞餐厅：可视为时间结构或兴趣偏离问题。

## 6. 与训练数据的关系

当前没有已上线模型，也没有可训练样本，因此可以直接把该字段纳入当前 request / Route X 口径。

原则：

```text
同一 featureSchemaVersion 下不能混入不同饭点语义的样本。
```

如果本字段上线前已经产生训练样本，应提升 `featureSchemaVersion`；当前阶段没有训练数据，可以直接折入当前 schema。

## 7. 不做事项

本设计不改变：

- FOOD 子标签本身的层级和合法性规则。
- 非 FOOD 兴趣标签选择规则。
- `COFFEE` 作为独立兴趣的能力。
- scoring / rerank 是否引入饭点惩罚；这属于后续 serving 排序策略。
