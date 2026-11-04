# 界面文件与三种界面

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
| `hide-vanilla` | 只给 `hud` 用，要隐藏的原版 HUD 元素列表，如 `[hotbar, health]`；只想挪位置用 [`type: vanilla`](vanilla-hud.md#摆放原版元素-type-vanilla) 节点 |
| `cancel-vanilla-bg` | 只给容器 `overlay` 用，`true` 时不画原版面板背景 |
| `placeholders` | 要使用的变量插件文字，例如 `%player_name%` |
| `update-interval` | 变量刷新间隔，单位是毫秒；不填时是 2000 |
| `styles` | 复用外观，避免一段颜色和尺寸反复写，见[复用外观](styling.md#复用外观) |
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

要隐藏或挪动原版 HUD 元素（快捷栏、血量、饱食度……），见[覆盖原版 HUD](vanilla-hud.md)；
要用图标条、进度条自绘状态，见[状态控件](status-widgets.md)。

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

`gui` 里可以放 [`slot` 节点](slots.md#物品格-slot)让玩家直接操作背包格子。

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

overlay 的 z 轴层次：原版面板背景（`cancel-vanilla-bg: true` 可取消，仅容器界面）→ `index < 0` 的 overlay（背景层，画在物品/槽位之下）→ 游戏物品/槽位渲染 → `index >= 0` 的 overlay（顶层，默认 0）。同层内按 index 升序绘制。

在生存背包等容器界面上，`slot` 节点可以**移动原版槽位**的位置，见[背包格子与快捷栏](slots.md#容器-overlay移动原版槽位)。
