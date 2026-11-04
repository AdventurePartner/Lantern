# 背包格子与快捷栏

## 物品格 `slot`

slot 节点映射玩家背包的一个格子，三种界面都能用，不需要为它指定界面类型：

| 界面 | 行为 |
|------|------|
| `gui` | 显示格子里的物品，并且能像原版背包一样操作：左键拿起/放下、右键分一半/放一个、Shift 快移、双击收集同类、按住拖拽平均分配（右键拖拽每格放一个）、悬停按数字键或副手键交换、按丢弃键丢出（加 Ctrl 丢整组）、创造模式中键复制；悬停显示物品提示 |
| 容器 `overlay`（玩家背包、箱子等） | 槽位位置声明：把原版槽位**移动**到该节点位置，交互全部由原版处理 |
| HUD | 只显示物品（HUD 显示时鼠标被锁定，没有光标） |

**`source` 写法**

按玩家背包定位，在任何界面里都指向同一格：

| source | 格子 |
|--------|------|
| `hotbar_0` … `hotbar_8` | 快捷栏九格 |
| `inventory_9` … `inventory_35` | 主背包三行（`inventory_0` … `inventory_8` 同快捷栏） |
| `armor_head` / `armor_chest` / `armor_legs` / `armor_feet` | 盔甲四格 |
| `offhand` | 副手 |

`slot_N`（或纯数字 `N`）是**当前界面菜单**的第 N 个槽位，含义随界面变化：
生存背包里 `0` 合成结果、`1-4` 合成格、`5-8` 盔甲（头/胸/腿/靴）、`9-35` 主背包三行、`36-44` 快捷栏、`45` 副手；
箱子界面里前面是箱子的格子，玩家背包排在后面。`gui` 和 HUD 里 `slot_N` 按生存背包下标算。
要指向箱子等容器自己的格子才用 `slot_N`，玩家背包的格子用上面的名字。

```yml
- type: slot
  source: "hotbar_0"      # 快捷栏第一格
  style:
    x: 20
    y: 20
```

### `gui` 界面：可交互的背包格子

操作直接作用在玩家背包上，物品真实移动，服务端照常收到点击（Bukkit 的 `InventoryClickEvent` 能拦截）。

- 关闭界面时手上拿着的物品、合成格里的物品由服务端放回背包。
- 在箱子等容器界面上打开带 slot 的 gui，会先关掉那个容器。
- 点到格子以外的地方不会丢出手上的物品。
- width/height 控制格子尺寸（默认 18），物品在格子内居中。

### 容器 `overlay`：移动原版槽位

移动后渲染、点击、拖拽、Shift 快移、提示全部由原版在新位置处理，uix 不再画镜像、不拦截点击。

- `visible: false` 可把槽位隐藏（移出屏幕，物品逻辑仍在，Shift 仍可移入）。
- width/height 仅影响布局占位，槽位本身固定 16x16（+1px 点击容差）。

### HUD：背包镜像

实时显示玩家背包指定格子的物品，包含数量、耐久条和物品冷却；
原版层 HUD 在背包、箱子等界面打开时仍会显示。width/height 控制镜像尺寸（默认 18）。

### 格子底

格子底用通用的 `background` 控制（见[背景](styling.md#背景-background)）：不写＝原版风格灰框；
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

## 快捷栏选中框 `hotbar-selection`

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
