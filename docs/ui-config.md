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
| `match-title` | 只给 `overlay` 用，填写要贴上的原界面标题，必须完全一样 |
| `placeholders` | 要使用的变量插件文字，例如 `%player_name%` |
| `update-interval` | 变量刷新间隔，单位是毫秒；不填时是 2000 |
| `styles` | 复用外观，避免一段颜色和尺寸反复写 |
| `root` | 最外层内容 |

## 三种界面

### 常驻提示 `hud`

玩家没有打开聊天栏、背包或菜单时显示。适合血量、等级、服务器状态等轻量内容。

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

适合贴在已有容器界面上，显示原界面里的一个物品格。`slot_0` 表示第一个格子，`slot_1` 表示第二个格子。

```yml
- type: slot
  source: "slot_0"
  style:
    x: 20
    y: 20
    width: 18
    height: 18
```

## 外观写法

常用设置：

| 设置名 | 说明 |
|---|---|
| `x`、`y` | 离左上角的距离 |
| `width`、`height` | 宽和高 |
| `color` | 文字颜色 |
| `background` | 背景颜色 |
| `border-color` | 输入框边框颜色 |
| `placeholder-color` | 输入框提示文字颜色 |
| `visible` | 是否显示，`true` 或 `false` |
| `tooltip` | 鼠标放上去时显示的提示文字 |

颜色可以写成 `#ffffff`，也可以写成带透明度的 `#80ffffff`。

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
