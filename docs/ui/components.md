# 基础组件

节点都写在 `root`（或任意容器的 `children`）里，`type` 决定是什么组件。位置、大小、颜色等统一写在 `style` 里，见[样式写法](styling.md)。

## 面板 `panel`

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

## 区块 `div`

不画任何东西的空容器，只用来圈出一块区域，类似网页里的 `div`。可以用它占住一块空白、
在[自动排列](styling.md#自动排列)里当间隔，或者把一组内容包起来统一摆放、统一[裁掉超出部分](styling.md#裁掉超出部分)。
用法和 `panel` 完全一样，写了 `background` 也会画背景。

在一行里用固定宽度的 div 隔开两组内容：

```yml
- type: div
  style:
    display: flex
    flex-direction: row
    align-items: center
  children:
    - type: text
      text: "左边"
    - type: div
      style:
        width: 40
        height: 1
    - type: text
      text: "右边"
```

把一组内容包起来，整体挪位置时只改外层的 `x`、`y`：

```yml
- type: div
  style:
    x: 20
    y: 40
    width: 120
    height: 60
  children:
    - type: image
      texture: "lantern:textures/ui/icon.png"
    - type: text
      text: "说明"
      style:
        x: 20
        y: 4
```

## 文字 `text`

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

文字里可以写[实时数值](status-widgets.md#实时数值)（如 `{health}`），由客户端每帧取值。

### 多行文字

用 `|` 或列表写多行，每行之间自动换行：

```yml
- type: text
  text: |-
    第一行
    第二行
  style:
    x: 8
    y: 8

- type: text
  text:
    - "§e标题"
    - "说明文字"
  style:
    x: 8
    y: 40
```

- 写了 `width` 时，超过宽度的部分会自动折到下一行；不想折行就在 `style` 里写 `white-space: nowrap`。
- 没写 `height` 时，高度按行数自动撑开，背景也会跟着变高。
- `§` 颜色只对当前行有效，换行后要重新写；同一行因为太长被折开时，后半段颜色不变。

## 按钮 `button`

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

按钮文字也可以用 `|` 或列表写成多行，多行文字在按钮里上下、左右居中。按钮不会按宽度自动折行，记得把 `height` 调够。

## 图片 `image`

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

## 输入框 `input`

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

## 鼠标提示 `tooltip`

任何组件都可以写 `tooltip`，鼠标停在组件上时显示提示框。`tooltip` 和 `type` 写在同一层，不要写进 `style`。

```yml
- type: image
  texture: "lantern:textures/ui/coin.png"
  tooltip:
    - "§6金币"
    - ""
    - "§7用于在商店购买物品"
  style:
    x: 8
    y: 8
```

- 可以写一行，也可以用 `|` 或列表写多行；提示框不会自动折行，每行写多长就显示多长。
- 没写 `width`、`height` 的组件按显示出来的大小判断鼠标是否放在上面（文字按文字大小、图片 16×16、按钮 80×20、格子 18×18）。
- 只在弹出界面（gui）和贴在原版界面上的 overlay 里显示。HUD 里没有鼠标，不会显示；`index` 为负数的 overlay 是背景层，不参与鼠标，也不会显示。
- 弹出界面里，鼠标停在有物品的 `slot` 上时显示物品自己的提示。
- 组件互相重叠时，优先显示画在最上面那个组件的提示；它没写 `tooltip` 时，依次看它下面的组件和外层容器。
- `visible: false` 的组件和它里面的内容都不会显示提示。
- 提示文字里可以写界面 `placeholders` 里声明的变量（如 `%player_name%`），按 `update-interval` 刷新，见[变量文字](styling.md#变量文字)；`{health}` 这类[实时数值](status-widgets.md#实时数值)不会被替换。

## 其它节点

| 类型 | 说明 | 文档 |
|---|---|---|
| `slot` | 映射玩家背包格子；gui 里可交互、overlay 里移动原版槽位、HUD 里镜像显示 | [背包格子与快捷栏](slots.md) |
| `hotbar-selection` | 自动跟随选中快捷栏格的选中框 | [背包格子与快捷栏](slots.md#快捷栏选中框-hotbar-selection) |
| `vanilla` | 把原版 HUD 元素摆到节点位置 | [覆盖原版 HUD](vanilla-hud.md#摆放原版元素-type-vanilla) |
| `icon-bar` | 心 / 鸡腿式图标行 | [状态控件](status-widgets.md#图标条-icon-bar) |
| `progress-bar` | 按比例裁切的进度条 | [状态控件](status-widgets.md#进度条-progress-bar) |
