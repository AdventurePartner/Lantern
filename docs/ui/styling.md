# 样式写法

所有组件的位置、大小、颜色都写在 `style` 里。本页集中说明通用样式：常用设置、背景、贴屏幕边缘、自动排列、裁切、复用和变量文字。

## 常用设置

| 设置名 | 说明 |
|---|---|
| `x`、`y` | 离左上角的距离 |
| `width`、`height` | 宽和高 |
| `color` | 文字颜色 |
| `background` | 背景，所有组件通用，写法见下 |
| `border-color` | 输入框边框颜色 |
| `placeholder-color` | 输入框提示文字颜色 |
| `visible` | 是否显示，`true` 或 `false` |
| `overflow` | 写 `hidden` 时裁掉超出自己区域的部分，见[裁掉超出部分](#裁掉超出部分) |
| `white-space` | 写 `nowrap` 时文字不按 `width` 自动折行（换行符照常生效），见[多行文字](components.md#多行文字) |

鼠标提示 `tooltip` 不是样式，要和 `type` 写在同一层，见[鼠标提示](components.md#鼠标提示-tooltip)。

颜色可以写成 `#ffffff`，也可以写成带透明度的 `#80ffffff`。配置里颜色值要加引号（`#` 在 YAML 里是注释）。

## 背景 `background`

和 CSS 一样，所有组件（panel、div、button、input、slot、text、image、hotbar-selection、
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
| panel、div、text、image、hotbar-selection、vanilla、icon-bar、progress-bar | 无 |
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

注意：`gap` 单独写不触发自动排列，需要 `display: flex`；flex 子项的 `x`/`y` 不生效，要用 `margin-top` / `margin-left` 微调。

## 裁掉超出部分

和 CSS 的 `overflow: hidden` 一样：组件写了 `overflow: hidden` 后，自己和里面的内容
只在自己的区域内显示，超出的部分被裁掉。适合固定大小的列表框、只露出一部分的贴图、
限定宽度的长文字。

```yml
- type: div
  style:
    x: 20
    y: 20
    width: 100
    height: 40
    overflow: hidden
  children:
    - type: text
      text: "这一行很长，超过 100 宽度的部分不会显示"
    - type: image
      texture: "lantern:textures/ui/banner.png"
      style:
        y: 20
        width: 160
        height: 40
```

- 区域就是组件的 `x`、`y`、`width`、`height`；在[自动排列](#自动排列)里按排好的位置和大小算。
- 没写宽高时按组件的自动大小算：文字是文字宽度，面板和区块是里面内容撑开的大小，
  所以不写宽高不会把内容全部裁掉。
- 可以多层嵌套，内层只会在外层区域和自己区域重叠的部分显示。
- 被裁掉的部分不能点击，鼠标放上去也不会显示 `tooltip`。提示文字本身不会被裁。
- 宽或高为 0 时整个组件不显示。
- 以下内容不受裁切影响：`vanilla` 节点（原版元素由原版自己画）、容器 `overlay` 里的
  `slot` 节点（移动的是原版槽位）。`hotbar-selection` 自己写 `overflow` 无效，
  但放在写了 `overflow: hidden` 的容器里时会被容器正常裁掉。

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

- `update-interval` 是变量刷新间隔（毫秒），不填时 2000。
- 如果没有安装 PlaceholderAPI，变量文字不会自动变成真实数值。
- 血量、饥饿等客户端本地状态不用 PlaceholderAPI，直接写[实时数值](status-widgets.md#实时数值)（`{health}` 等）。
