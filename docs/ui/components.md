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

## 其它节点

| 类型 | 说明 | 文档 |
|---|---|---|
| `slot` | 映射玩家背包格子；gui 里可交互、overlay 里移动原版槽位、HUD 里镜像显示 | [背包格子与快捷栏](slots.md) |
| `hotbar-selection` | 自动跟随选中快捷栏格的选中框 | [背包格子与快捷栏](slots.md#快捷栏选中框-hotbar-selection) |
| `vanilla` | 把原版 HUD 元素摆到节点位置 | [覆盖原版 HUD](vanilla-hud.md#摆放原版元素-type-vanilla) |
| `icon-bar` | 心 / 鸡腿式图标行 | [状态控件](status-widgets.md#图标条-icon-bar) |
| `progress-bar` | 按比例裁切的进度条 | [状态控件](status-widgets.md#进度条-progress-bar) |
