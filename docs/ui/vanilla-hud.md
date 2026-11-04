# 覆盖原版 HUD

`screen-type: hud` 的界面可以写 `hide-vanilla`，把指定的原版 HUD 元素整个隐藏，
再用 `root` 里的内容自己画替代品。多个 HUD 文件的隐藏列表会合并（并集）。

不想隐藏、只想挪位置时，用 [`type: vanilla`](#摆放原版元素-type-vanilla) 节点。

## 隐藏原版元素 `hide-vanilla`

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
| `experience_bar` | 经验条（1.21.10 起坐骑跳跃条、定位栏与它共用一个位置，会一起隐藏） |
| `experience_level` | 经验等级数字 |
| `jump_bar` | 坐骑跳跃蓄力条（同上，1.21.10 起共用） |
| `crosshair` | 准星 |
| `effects` | 状态效果图标 |
| `all` | 以上全部 |

注意：

- 只有服务端下发了配置才会隐藏。删掉文件执行 `/lantern reload`、或断开服务器，
  原版元素立即恢复。
- Forge 1.20.1 和 NeoForge（1.21.1、1.21.10、1.21.11）上，血量/护甲/饥饿等左右两侧的元素会向上堆叠；
  隐藏其中一个，上面的剩余元素会往下补位。Fabric 1.21.1 的位置是固定的。
- 完整的快捷栏替换示例见服务端 `plugins/Lantern/huds/hotbar.yml.example`
  （`huds` 目录第一次生成时释放；老服务器可从插件 jar 里取）。

## 摆放原版元素 `type: vanilla`

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
| `fixed`（默认） | 元素永远画在节点处，各客户端一致。原版的互相避让不再生效：心变两行（血量上限超过 20 或有吸收）会向上长、可能压到摆在上方的护甲 |
| `vanilla` | 节点对应元素在原版默认布局（一行心、元素齐全）里的位置，相当于整体平移；原版的动态避让照常（多行心时护甲、物品名上移）。整组挪动时用它 |

注意：

- 节点本身不画东西。调位置时写 `background: "#40ff0000"` 可以看到占位框。
- 只在 `screen-type: hud` 里生效，`index` 正负都行；打开背包等界面时原版元素仍在节点位置。
- 同一个元素被多个 vanilla 节点摆放时，只有 index 最小的 HUD 里的第一个生效，客户端日志会提示。
- 同一个元素也写在 `hide-vanilla` 里时，隐藏优先。
- 1.21.10 起经验条、坐骑跳跃条、定位栏共用一个位置：骑可跳跃的坐骑时跟随 `jump_bar` 的节点，
  其余情况（含定位栏）跟随 `experience_bar` 的节点。
- 旁观模式的快捷栏菜单不会被移动。
- 整组上移的完整示例见 `plugins/Lantern/huds/vanilla_layout.yml.example`。

## 自绘血量、饥饿、经验条

想完全换外观时，用 `hide-vanilla` 隐藏原版元素，再用[图标条 `icon-bar`](status-widgets.md#图标条-icon-bar)、
[进度条 `progress-bar`](status-widgets.md#进度条-progress-bar) 和文字里的[实时数值](status-widgets.md#实时数值)自己画。
完整示例见 `plugins/Lantern/huds/custom_status.yml.example`。
