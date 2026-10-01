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
| `hide-vanilla` | 只给 `hud` 用，要隐藏的原版 HUD 元素列表，如 `[hotbar, health]` |
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
- 完整的快捷栏替换示例见服务端 `plugins/Lantern/huds/hotbar.yml.example`。

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

和 CSS 一样，所有组件（panel、button、input、slot、text、image、hotbar-selection）
都用同一个 `background`，写法与效果完全一致：

| 写法 | 效果 |
|---|---|
| 不写 | 组件自己的默认背景（见下表） |
| `none`、`transparent` 或 `false` | 不画背景 |
| `"#RRGGBB"`、`"#AARRGGBB"` | 纯色铺满组件区域 |
| `"url(lantern:textures/ui/bg.png)"` | 贴图拉伸铺满组件区域，也支持 http(s) 地址 |

写错的值会回退到默认背景，并在客户端日志里提示一次。

| 组件 | 默认背景 |
|---|---|
| panel、text、image、hotbar-selection | 无 |
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
