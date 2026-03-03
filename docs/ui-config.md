# Lantern UI 配置说明

本文档说明如何通过 YAML 配置文件为 Lantern 插件定义 HUD 和 GUI 界面。

---

## 目录结构

```
plugins/Lantern/
├── huds/          # 常驻 HUD 配置（叠加在游戏画面上，始终可见）
│   └── example.yml
└── screens/       # GUI Screen 配置（通过命令打开，类似弹窗）
    └── example.yml
```

- `huds/` 中的配置在玩家连接时自动加载并渲染，无需手动触发。
- `screens/` 中的配置在服务端主动调用 `/lantern open <id>` 时才打开。

执行 `/lantern reload` 可重载所有配置并推送给在线玩家。

---

## 顶层字段

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | 界面唯一标识符，用于命令引用 |
| `screen-type` | 否 | `hud`（默认）或 `gui` |
| `styles` | 否 | 命名样式集合（见下文） |
| `root` | 是 | 根组件节点 |

```yaml
id: my_hud
screen-type: hud   # 或 gui
styles:
  my-style:
    color: "#ffffff"
root:
  type: panel
  ...
```

---

## 组件类型（type）

### panel

容器组件，可嵌套子组件。子组件的 `x`/`y` 坐标相对于本 panel 的左上角。

| 字段 | 说明 |
|---|---|
| `type` | `panel` |
| `style` | 位置、尺寸、背景色（见样式属性） |
| `children` | 子组件列表 |

```yaml
type: panel
style:
  x: 10
  y: 10
  width: 200
  height: 100
  background: "#1a1a2e"
children:
  - type: text
    text: "Hello"
    style: { x: 5, y: 5 }
```

---

### text

显示一段文本。

| 字段 | 说明 |
|---|---|
| `type` | `text` |
| `text` | 显示内容 |
| `style` | 位置、颜色、字体大小 |

```yaml
type: text
text: "玩家信息"
style:
  x: 10
  y: 5
  color: "#ffffff"
  font-size: 12
```

---

### button

可点击的按钮，点击时触发 `action`。

| 字段 | 说明 |
|---|---|
| `type` | `button` |
| `text` | 按钮文字 |
| `action` | 点击动作（见动作格式） |
| `style` | 位置、尺寸、颜色、背景色 |

```yaml
type: button
text: "确认"
action: "command:my_command"
style:
  x: 50
  y: 80
  width: 80
  height: 20
  background: "#4a90d9"
  color: "#ffffff"
```

---

### image

显示一张纹理图片。

| 字段 | 说明 |
|---|---|
| `type` | `image` |
| `texture` | 资源路径，格式 `namespace:path/to/image.png` |
| `style` | 位置、宽高 |

```yaml
type: image
texture: "lantern:textures/example/logo.png"
style:
  x: 0
  y: 0
  width: 64
  height: 64
```

---

## 样式属性（style）

所有样式属性写在组件的 `style` 块内，或通过 `style-ref` 引用命名样式。

| 属性 | 类型 | 说明 |
|---|---|---|
| `x` | 整数 | 横向位置（相对于父容器左上角） |
| `y` | 整数 | 纵向位置（相对于父容器左上角） |
| `width` | 整数 | 宽度（px） |
| `height` | 整数 | 高度（px） |
| `color` | 十六进制颜色 | 文字颜色，如 `"#ffffff"` |
| `background` | 十六进制颜色 | 背景填充色，如 `"#1a1a2e"` |
| `font-size` | 整数 | 字体大小（目前作为参考，实际由 MC 字体决定） |
| `border-radius` | 整数 | 圆角半径（预留，CSS 样式扩展用） |
| `opacity` | 小数 | 透明度 0.0 ~ 1.0（预留） |
| `visible` | 布尔 | 是否可见，`true`（默认）或 `false` |

> 颜色格式支持 `#RRGGBB`（自动补全 FF alpha）和 `#AARRGGBB`。

---

## 命名样式（styles + style-ref）

在顶层 `styles` 块中定义可复用的样式规则，在组件上用 `style-ref` 引用。  
`style-ref` 作为基础，组件自身的 `style` 块会覆盖其中的同名属性。

```yaml
styles:
  btn-primary:
    background: "#4a90d9"
    color: "#ffffff"
    width: 80
    height: 20

root:
  type: panel
  children:
    - type: button
      text: "提交"
      style-ref: btn-primary     # 应用命名样式
      style:
        x: 10                    # 仅覆盖位置
        y: 50
```

---

## 动作字符串（action）

`button` 的 `action` 字段支持以下格式：

| 格式 | 效果 |
|---|---|
| `close` | 关闭当前 GUI screen |
| `command:<cmd>` | 关闭 screen 后以玩家身份执行指令（不需要 `/`） |
| `open:<screen-id>` | 打开另一个已加载的 GUI screen |

```yaml
action: "close"
action: "command:spawn"
action: "open:confirm_dialog"
```

---

## 服务端命令

| 命令 | 说明 |
|---|---|
| `/lantern reload` | 重载所有配置，推送给所有在线玩家 |
| `/lantern open <screen-id>` | 向执行者打开指定 GUI screen |
| `/lantern open <screen-id> <player>` | 向指定玩家打开 GUI screen |

Tab 补全：第二参数自动补全已加载的 screen id，第三参数补全在线玩家名。

---

## 完整示例

### HUD（`huds/status.yml`）

```yaml
id: status_hud
screen-type: hud

root:
  type: panel
  style:
    x: 5
    y: 5
    width: 120
    height: 40
    background: "#aa000000"
  children:
    - type: text
      text: "状态: 在线"
      style:
        x: 5
        y: 5
        color: "#00ff00"
    - type: button
      text: "菜单"
      action: "open:main_menu"
      style:
        x: 30
        y: 18
        width: 60
        height: 16
        background: "#334455"
        color: "#ffffff"
```

### GUI Screen（`screens/main_menu.yml`）

```yaml
id: main_menu
screen-type: gui

styles:
  dialog:
    background: "#1a1a2e"
    width: 200
    height: 120
  btn-red:
    background: "#c0392b"
    color: "#ffffff"
    width: 80
    height: 20

root:
  type: panel
  style-ref: dialog
  style:
    x: 60
    y: 50
  children:
    - type: text
      text: "主菜单"
      style:
        x: 80
        y: 10
        color: "#ffffff"
    - type: button
      text: "回到出生点"
      action: "command:spawn"
      style:
        x: 10
        y: 50
        width: 80
        height: 20
        background: "#27ae60"
        color: "#ffffff"
    - type: button
      text: "关闭"
      action: "close"
      style-ref: btn-red
      style:
        x: 110
        y: 50
```
