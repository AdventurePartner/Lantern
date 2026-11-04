# 状态控件

两个自绘状态组件（[图标条](#图标条-icon-bar)、[进度条](#进度条-progress-bar)）读的都是客户端本地状态（`HudStat`），不需要服务端额外配置；配合[覆盖原版 HUD](vanilla-hud.md) 可以完全替换原版血量、饥饿、经验条的外观。

## 图标条 `icon-bar`

像原版的心、鸡腿那样，按数值画一排满/半/空图标。`source` 选数据来源，
`textures` 给贴图：

```yml
- type: icon-bar
  source: health
  textures:
    empty: "lantern:textures/hud/heart_empty.png"   # 空槽，每个图标位都会先画
    full: "lantern:textures/hud/heart_full.png"
    half: "lantern:textures/hud/heart_half.png"
    poison-full: "lantern:textures/hud/heart_poison_full.png"   # 可选：状态变体
  style:
    x: 0
    y: 0
    animate: true
```

| source | 数值 / 上限 | 默认每个图标代表 | 默认方向 | 何时显示（`auto-hide`） | 可用状态 |
|---|---|---|---|---|---|
| `health` | 血量 / 最大血量 | 2 | 从左往右 | 可受伤的模式 | `poison` > `wither` > `frozen` |
| `absorption` | 吸收值 / 最大血量 | 2 | 从左往右 | 吸收值大于 0 | — |
| `food` | 饥饿值 / 20 | 2 | 从右往左 | 没骑有血量的坐骑 | `hunger` |
| `saturation` | 饱和度 / 20 | 2 | 从右往左 | 同 food | `hunger` |
| `armor` | 护甲值 / 20 | 2 | 从左往右 | 护甲大于 0 | — |
| `armor_toughness` | 盔甲韧性 / 20 | 2 | 从左往右 | 韧性大于 0 | — |
| `air` | 氧气 / 最大氧气 | 上限的 1/10 | 从右往左 | 在水下或氧气不满 | — |
| `vehicle_health` | 坐骑血量 / 坐骑最大血量 | 2 | 从右往左 | 骑着有血量的坐骑 | — |
| `experience` | 本级经验进度 / 1 | 0.1 | 从左往右 | 当前模式有经验 | — |
| `jump` | 坐骑跳跃蓄力 / 1 | 0.1 | 从左往右 | 骑着可跳跃的坐骑 | — |

创造、旁观模式下不显示生存类数值（血量、饥饿、护甲、氧气）。

`textures` 的写法：

| 键 | 说明 |
|---|---|
| `empty`、`half`、`full` | 空槽、半格、满格。缺 `half` 时用 `full`，缺 `empty` 时不画空槽 |
| `<状态>-<部位>` | 状态变体，如 `poison-full`、`hunger-empty`；没写的部位回退到常规贴图 |
| `absorption-full`、`absorption-half` | 吸收心，接在普通心后面（只对 `source: health` 生效） |
| `blink-empty`、`blink-full`、`blink-half` | 受伤闪烁，同原版：掉的那几颗心闪烁、空槽换成 `blink-empty`（只对 `source: health` 生效，不写就不闪） |

style 里的设置：

| 设置名 | 默认值 | 说明 |
|---|---|---|
| `icon-width`、`icon-height` | 9 | 单个图标的尺寸 |
| `icon-spacing` | 8 | 相邻图标的间距（原版图标重叠 1 像素） |
| `icons-per-row` | 10 | 每行几个，多的另起一行 |
| `row-spacing` | 10 | 行距 |
| `row-direction` | `up` | 多行时往上叠（`up`）还是往下排（`down`） |
| `fill-direction` | 见上表 | `right` 从左往右，`left` 从右往左（以整行宽度的右缘为起点） |
| `value-per-icon` | 见上表 | 每个图标代表的数值，半格＝一半 |
| `auto-hide` | `true` | 按上表条件自动隐藏；`false` 时一直显示 |
| `animate` | `false` | 原版的动态效果：血量 ≤ 4 时心抖动、生命恢复时的波浪、饱和度为 0 时鸡腿抖动 |

不写 width/height 时，尺寸是一行排满的宽度 × 图标高度（默认 81×9）。

## 进度条 `progress-bar`

像经验条那样按比例显示。`background` 整张画，`fill` 按「数值 ÷ 上限」裁切后画在上面：

```yml
- type: progress-bar
  source: experience
  textures:
    background: "lantern:textures/hud/xp_bg.png"
    fill: "lantern:textures/hud/xp_fill.png"
  style:
    x: 0
    y: 10
    width: 182
    height: 5
    fill-direction: right
```

- `source` 与图标条相同，`auto-hide` 规则也相同。
- `fill-direction`：`right`（从左往右填，默认）、`left`、`up`（从下往上）、`down`。
- `textures` 同样支持状态变体，如 `poison-fill`、`hunger-background`。
- 不写 width/height 时默认 182×5。

## 实时数值

文字里可以写下面这些 `{...}`，由客户端每帧取值，不需要 PlaceholderAPI，
也不受 `update-interval` 限制。不认识的 `{...}` 原样显示。

| 写法 | 含义 |
|---|---|
| `{health}`、`{max_health}` | 血量、最大血量（保留一位小数） |
| `{absorption}` | 吸收值 |
| `{food}`、`{saturation}` | 饥饿值、饱和度 |
| `{armor}`、`{armor_toughness}` | 护甲值、盔甲韧性 |
| `{air}`、`{max_air}` | 氧气、最大氧气 |
| `{level}`、`{exp_progress}` | 经验等级、本级经验进度（0–100） |
| `{vehicle_health}`、`{vehicle_max_health}` | 坐骑血量、坐骑最大血量 |

```yml
- type: text
  text: "{health}/{max_health}"
  style:
    x: 0
    y: -20
    color: "#FF5555"
```

在自动排列（flex）的面板里，含 `{...}` 的文字宽度按原文计算，建议写明 `width`。
