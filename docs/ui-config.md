# 界面配置

界面文件放在服务器的 `plugins/Lantern/` 里：

| 文件夹 | 适合放什么 |
|---|---|
| `huds/` | 常驻提示，例如血量条、状态栏、图标提示 |
| `screens/` | 弹出界面，例如菜单、确认框、背包旁的小面板 |

保存后执行 `/lantern reload`，在线玩家会收到新的界面。

## 一个界面文件的基本样子

```yml
id: top_status
screen-type: hud

root:
  type: panel
  style:
    x: 10
    y: 10
    width: 180
    height: 40
    background: "#80000000"
  children:
    - type: text
      text: "欢迎回来"
      style:
        x: 8
        y: 8
        color: "#ffffff"
```

| 设置名 | 怎么填 |
|---|---|
| `id` | 这个界面的名字，指令和按钮会用到 |
| `screen-type` | `hud` 常驻提示，`gui` 弹出界面，`overlay` 贴在已有界面上 |
| `match-screen` | 只给 `overlay` 用，按界面类型匹配，如 `player_inventory`（生存背包），不受客户端语言影响 |
| `match-title` | 只给 `overlay` 用，按界面标题匹配，必须完全一样（优先级低于 `match-screen`） |
| `index` | z 轴深度，越大越靠前；`overlay` 里负值＝画在物品之下，`hud` 里负值＝原版层（见下） |
| `hide-vanilla` | 只给 `hud` 用，要隐藏的原版 HUD 元素列表，如 `[hotbar, health]`；只想挪位置用 [`type: vanilla`](#摆放原版元素-type-vanilla) 节点 |
| `cancel-vanilla-bg` | 只给容器 `overlay` 用，`true` 时不画原版面板背景 |
| `placeholders` | 要使用的变量插件文字，例如 `%player_name%` |
| `update-interval` | 变量刷新间隔，单位是毫秒；不填时是 2000 |
| `styles` | 复用外观，避免一段颜色和尺寸反复写 |
| `root` | 最外层内容 |

## 三种界面

### 常驻提示 `hud`

玩家没有打开聊天栏、背包或菜单时显示。适合血量、等级、服务器状态等轻量内容。

`index` 为负数（如 `index: -10`）时该 HUD 进入**原版层**：画进原版 HUD 的图层栈里
（暗角、南瓜头之上，准星、快捷栏、聊天之下），可见性跟随原版——打开背包、箱子、
聊天时依然显示，按 F1 才隐藏，也不响应点击。要替代原版快捷栏、血条这类元素的
配置应放在这一层。默认（`index: 0` 或不填）是**顶层**：画在所有原版 HUD 之上，
打开任何界面即隐藏，可点击。

```yml
id: status_hud
screen-type: hud
root:
  type: panel
  style:
    position: absolute
    anchor: top-center
    margin-top: 8
    width: 260
    height: 26
    background: "#80000000"
  children:
    - type: text
      text: "等级 %player_level%"
      style:
        x: 90
        y: 8
        color: "#ffffff"
```

## 覆盖原版 HUD（快捷栏、血量、饱食度……）

`screen-type: hud` 的界面可以写 `hide-vanilla`，把指定的原版 HUD 元素整个隐藏，
再用 `root` 里的内容自己画替代品。多个 HUD 文件的隐藏列表会合并（并集）。

```yml
id: custom_hotbar
screen-type: hud
index: -10                      # 原版层，替代原版元素放这里
hide-vanilla: [hotbar, selected_item_name]
root:
  type: panel
  # ...自己的快捷栏贴图与 slot 节点
```

可隐藏的元素：

| 值 | 隐藏什么 |
|---|---|
| `hotbar` | 底部快捷栏（旁观模式的菜单不受影响） |
| `selected_item_name` | 切换物品时显示的物品名 |
| `health` | 血量（心形） |
| `armor` | 护甲 |
| `food` | 饥饿 |
| `air` | 氧气（水下气泡） |
| `vehicle_health` | 坐骑血量 |
| `experience_bar` | 经验条（1.21.10 里坐骑跳跃条、定位栏与它共用一个位置，会一起隐藏） |
| `experience_level` | 经验等级数字 |
| `jump_bar` | 坐骑跳跃蓄力条（同上，1.21.10 共用） |
| `crosshair` | 准星 |
| `effects` | 状态效果图标 |
| `all` | 以上全部 |

注意：

- 只有服务端下发了配置才会隐藏。删掉文件执行 `/lantern reload`、或断开服务器，
  原版元素立即恢复。
- Forge 1.20.1 和 NeoForge 上，血量/护甲/饥饿等左右两侧的元素会向上堆叠；
  隐藏其中一个，上面的剩余元素会往下补位。1.21.1 的位置是固定的。
- 完整的快捷栏替换示例见服务端 `plugins/Lantern/huds/hotbar.yml.example`
  （`huds` 目录第一次生成时释放；老服务器可从插件 jar 里取）。

### 摆放原版元素 `type: vanilla`

不想隐藏、只想挪位置时（比如自定义快捷栏比原版高，血条挡住了它），在 HUD 里放
`type: vanilla` 节点。元素仍由原版绘制、只是画到节点处，所以受伤闪烁、吸收心、
中毒/凋零心、饥饿抖动等表现全部保留。

```yml
- type: vanilla
  source: health          # 取值同 hide-vanilla（不含 all）
  style: { x: 0, y: -6 }
```

位置写法和其它节点完全一样（`style` 里的 x/y、anchor、flex 都能用）；不写 width/height
时用元素的原生尺寸。节点框与元素的对齐方式：

| source | 原生尺寸 | 对齐方式 |
|---|---|---|
| `hotbar` | 182×22 | 左上角（副手格、选中框照原版向外伸出） |
| `selected_item_name` | 182×9 | 在节点宽度内水平居中，顶部对齐 |
| `health` | 81×9 | 左上角＝最下面一行心，多行时向上叠 |
| `armor` | 81×9 | 左上角 |
| `food`、`air` | 81×9 | 右上角（图标从右往左排，同原版） |
| `vehicle_health` | 81×9 | 右上角＝第一行，多行时向上叠 |
| `experience_bar`、`jump_bar` | 182×5 | 左上角 |
| `experience_level` | 182×9 | 水平居中，顶部对齐 |
| `crosshair` | 15×15 | 左上角（15×15 的节点 `anchor: center` 即与原版重合） |
| `effects` | 25×51 | 右上角（图标从节点右缘向左排） |

`stacking`（写在 style 里）决定原版元素之间的避让是否保留：

| 值 | 效果 |
|---|---|
| `fixed`（默认） | 元素永远画在节点处，四个客户端一致。原版的互相避让不再生效：心变两行（血量上限超过 20 或有吸收）会向上长、可能压到摆在上方的护甲 |
| `vanilla` | 节点对应元素在原版默认布局（一行心、元素齐全）里的位置，相当于整体平移；原版的动态避让照常（多行心时护甲、物品名上移）。整组挪动时用它 |

注意：

- 节点本身不画东西。调位置时写 `background: "#40ff0000"` 可以看到占位框。
- 只在 `screen-type: hud` 里生效，`index` 正负都行；打开背包等界面时原版元素仍在节点位置。
- 同一个元素被多个 vanilla 节点摆放时，只有 index 最小的 HUD 里的第一个生效，客户端日志会提示。
- 同一个元素也写在 `hide-vanilla` 里时，隐藏优先。
- 1.21.10 的经验条、坐骑跳跃条、定位栏共用一个位置：骑可跳跃的坐骑时跟随 `jump_bar` 的节点，
  其余情况（含定位栏）跟随 `experience_bar` 的节点。
- 旁观模式的快捷栏菜单不会被移动。
- 整组上移的完整示例见 `plugins/Lantern/huds/vanilla_layout.yml.example`。

### 自绘血量、饥饿、经验条

想完全换外观时，用 `hide-vanilla` 隐藏原版元素，再用 [`icon-bar`](#图标条-icon-bar)、
[`progress-bar`](#进度条-progress-bar) 和文字里的[实时数值](#实时数值)自己画。
完整示例见 `plugins/Lantern/huds/custom_status.yml.example`。

### 弹出界面 `gui`

用 `/lantern open <界面名>` 打开，也可以由按钮打开。适合菜单、确认框、操作面板。

```yml
id: main_menu
screen-type: gui
root:
  type: panel
  style:
    x: 80
    y: 60
    width: 220
    height: 120
    background: "#1a1a2e"
  children:
    - type: button
      text: "回到出生点"
      action: "command:spawn"
      style:
        x: 55
        y: 70
        width: 110
        height: 22
        background: "#4a90d9"
        color: "#ffffff"
```

### 贴在已有界面上 `overlay`

只在指定标题的界面上显示，例如某个箱子、菜单或背包界面。

提示：`overlay` 上的点击不会拦住原来的界面，按钮不要放在容易误点原界面格子的位置。

```yml
id: chest_hint
screen-type: overlay
match-title: "Large Chest"
root:
  type: panel
  style:
    position: absolute
    anchor: top-right
    margin-top: 10
    margin-right: 10
    width: 120
    height: 34
    background: "#80000000"
  children:
    - type: text
      text: "这里可以放任务物品"
      style:
        x: 8
        y: 12
        color: "#ffffff"
```

## 可用内容

### 面板 `panel`

用来装其它内容，也可以显示背景色。

```yml
- type: panel
  style:
    x: 10
    y: 10
    width: 200
    height: 80
    background: "#80000000"
  children:
    - type: text
      text: "面板里的文字"
      style:
        x: 8
        y: 8
```

### 文字 `text`

显示一段文字，可直接写变量插件文字。

```yml
- type: text
  text: "玩家：%player_name%"
  tooltip: "鼠标放上来时显示"
  style:
    x: 8
    y: 8
    color: "#ffffff"
```

### 按钮 `button`

玩家点击后执行一个动作。

```yml
- type: button
  text: "打开菜单"
  action: "open:main_menu"
  style:
    x: 20
    y: 40
    width: 90
    height: 22
    background: "#4a90d9"
    color: "#ffffff"
```

按钮动作：

| 写法 | 效果 |
|---|---|
| `close` | 关闭当前弹出界面 |
| `command:spawn` | 让玩家执行 `spawn` 指令，前面不用写 `/` |
| `open:main_menu` | 打开另一个弹出界面 |

### 图片 `image`

显示资源包里的图片，也可以填写网址图片。

```yml
- type: image
  texture: "lantern:textures/example/logo.png"
  style:
    x: 8
    y: 8
    width: 32
    height: 32
```

网址示例：

```yml
texture: "https://example.com/icon.png"
```

### 输入框 `input`

适合放在弹出界面里。玩家点进去后可以输入文字，按回车会执行 `on-change`。

```yml
- type: input
  value: ""
  placeholder: "输入名字"
  max-length: 16
  on-change: "command:say 已提交"
  style:
    x: 20
    y: 40
    width: 140
    height: 20
    background: "#1a1a1a"
    color: "#ffffff"
    placeholder-color: "#888888"
    border-color: "#555555"
```

### 物品格 `slot`

**容器界面上（如 match-screen: player_inventory 的 overlay）＝ 槽位位置声明**：
把原版界面的第 N 个槽位**移动**到该节点位置（容器锚定坐标，支持嵌套 panel 偏移累加）。
移动后渲染、点击、拖拽、shift 快移、tooltip 全部由原版在新位置处理，uix 不再画镜像、不拦截点击。
`visible: false` 可把槽位隐藏（移出屏幕，物品逻辑仍在，shift 仍可移入）。
width/height 仅影响布局占位，槽位本身固定 16x16（+1px 点击容差）。

生存背包（player_inventory）的槽位下标：
`0` 合成结果、`1-4` 合成格、`5-8` 盔甲（头/胸/腿/靴）、`9-35` 主背包三行、`36-44` 快捷栏、`45` 副手。

`source` 支持别名：`hotbar_0` 到 `hotbar_8`＝快捷栏九格，`offhand`＝副手，
与 `slot_36`…`slot_44`、`slot_45` 等价，在容器 overlay 和 HUD 里含义一致。

```yml
- type: slot
  source: "hotbar_0"      # 快捷栏第一格（等价 slot_36）
  style:
    x: 20
    y: 20
```

**HUD 等非容器场景＝ 物品镜像**：实时显示玩家背包指定格子的物品，
包含数量、耐久条和物品冷却；原版层 HUD 在背包、箱子等界面打开时仍会显示。
此时 width/height 控制镜像尺寸（默认 18），物品在格子内居中。

格子底用通用的 `background` 控制（见[外观写法](#外观写法)）：不写＝原版风格灰框；
`none`＝不画底（适合底图自带格子的场景）；颜色或 `url(...)` 同其它组件。
容器界面上格子底是原版容器贴图的一部分，`background` 对它无效。

多个格子共用时写成命名样式，用 `style-ref` 引用：

```yml
styles:
  bare-slot:
    background: none

# children 里：
- type: slot
  source: "hotbar_0"
  style-ref: bare-slot
  style:
    x: 3
    y: 3
    width: 16
    height: 16
```

旧写法 slot 节点上的 `texture: none` / `texture: <贴图路径>` 仍然兼容，
等同于 `background: none` / `background: "url(<贴图路径>)"`；两者都写时以 style 里的 `background` 为准。

### 快捷栏选中框 `hotbar-selection`

自动跟随当前选中的快捷栏格（滚轮、数字键切换时实时移动）。
它会找到同一 `root` 里 `source` 对应选中格的 slot 节点，把 `texture`
画在该格子上；style 的 `x`/`y` 是相对该格左上角的偏移，`width`/`height`
是选中框尺寸（默认 24）。找不到对应的 slot 节点时不显示。

```yml
- type: hotbar-selection
  texture: "lantern:textures/ui/hotbar_selection.png"
  style:
    x: -3
    y: -3
    width: 22
    height: 22
```

### 原版元素 `vanilla`

把一个原版 HUD 元素（血量、饥饿、快捷栏……）摆到这个节点的位置，见
[摆放原版元素](#摆放原版元素-type-vanilla)。

### 图标条 `icon-bar`

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

### 进度条 `progress-bar`

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

## 外观写法

常用设置：

| 设置名 | 说明 |
|---|---|
| `x`、`y` | 离左上角的距离 |
| `width`、`height` | 宽和高 |
| `color` | 文字颜色 |
| `background` | 背景，所有组件通用，写法见下 |
| `border-color` | 输入框边框颜色 |
| `placeholder-color` | 输入框提示文字颜色 |
| `visible` | 是否显示，`true` 或 `false` |
| `tooltip` | 鼠标放上去时显示的提示文字 |

颜色可以写成 `#ffffff`，也可以写成带透明度的 `#80ffffff`。

### 背景 `background`

和 CSS 一样，所有组件（panel、button、input、slot、text、image、hotbar-selection、
vanilla、icon-bar、progress-bar）都用同一个 `background`，写法与效果完全一致：

| 写法 | 效果 |
|---|---|
| 不写 | 组件自己的默认背景（见下表） |
| `none`、`transparent` 或 `false` | 不画背景 |
| `"#RRGGBB"`、`"#AARRGGBB"` | 纯色铺满组件区域 |
| `"url(lantern:textures/ui/bg.png)"` | 贴图拉伸铺满组件区域，也支持 http(s) 地址 |

写错的值会回退到默认背景，并在客户端日志里提示一次。

| 组件 | 默认背景 |
|---|---|
| panel、text、image、hotbar-selection、vanilla、icon-bar、progress-bar | 无 |
| button | `#333333` |
| input | `#1a1a1a`（边框另由 `border-color` 控制） |
| slot | 原版风格灰框 |

image、hotbar-selection 的背景画在它们的 `texture` 之下。

## 贴到屏幕边缘

把 `position` 写成 `absolute`，再用 `anchor` 选择位置。

```yml
style:
  position: absolute
  anchor: bottom-right
  margin-right: 10
  margin-bottom: 10
  width: 140
  height: 30
```

可选位置：

`top-left`、`top-center`、`top-right`、`center-left`、`center`、`center-right`、`bottom-left`、`bottom-center`、`bottom-right`

## 自动排列

当一个面板里有多个内容时，可以让它们自动横排或竖排。

```yml
style:
  display: flex
  flex-direction: row
  justify-content: center
  align-items: center
  gap: 8
  padding-left: 6
  padding-right: 6
```

| 设置名 | 可选值 |
|---|---|
| `flex-direction` | `row` 横排，`column` 竖排 |
| `justify-content` | `start`、`center`、`end`、`space-between`、`space-evenly` |
| `align-items` | `start`、`center`、`end`、`stretch` |
| `gap` | 内容之间的间距 |
| `padding`、`padding-left` 等 | 内容离面板边缘的距离 |
| `margin`、`margin-top` 等 | 自己离外面内容的距离 |

## 复用外观

如果多个按钮或文字长得一样，可以先在 `styles` 里写一次，再用 `style-ref` 引用。

```yml
styles:
  blue-button:
    width: 90
    height: 22
    background: "#4a90d9"
    color: "#ffffff"

root:
  type: panel
  children:
    - type: button
      text: "确认"
      style-ref: blue-button
      style:
        x: 20
        y: 80
      action: "close"
```

同一个设置同时出现在 `style-ref` 和 `style` 里时，以 `style` 里的为准。

## 变量文字

安装 PlaceholderAPI 后，可以在文字里写变量。先在界面顶部声明要用哪些变量：

```yml
id: status_hud
screen-type: hud
placeholders:
  - "%player_name%"
  - "%player_health%"
update-interval: 1000

root:
  type: panel
  children:
    - type: text
      text: "%player_name% 当前体力 %player_health%"
      style:
        x: 8
        y: 8
        color: "#ffffff"
```

如果没有安装 PlaceholderAPI，变量文字不会自动变成真实数值。

### 实时数值

文字里还可以写下面这些 `{...}`，由客户端每帧取值，不需要 PlaceholderAPI，
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
