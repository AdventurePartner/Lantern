# 衣柜

衣柜是一个游戏内的原版箱子界面，玩家在里面自己挑选、更换[玩家外观](costumes.md)，不需要服管手动发命令。

## 打开衣柜

```
/lantern wardrobe            # 给自己打开（需要 lantern.wardrobe 权限）
/lantern wardrobe Steve      # 给 Steve 打开
```

权限节点 `lantern.wardrobe` 默认只对 OP 开放。想让玩家随时自己换装，用权限插件授予这个节点，再让玩家绑定按键（见[按键绑定](../interaction/keys.md)）或界面按钮（`action: "command:lantern wardrobe"`）打开。

其他插件也可以直接调用 `WardrobeGui.openMain(player)` 打开。

## 界面怎么用

1. 主界面里每个外观槽位（全身 / 背部 / 尾巴 / 头部 / 特效）占一格：空槽显示灰色玻璃占位，已装备的显示外观名。
2. 点一个槽位进入选择界面，里面列出 `costumes.yml` 中登记在该槽位的所有外观（按外观自己的 `slot` 字段过滤）。
3. 点一件外观立即穿上并回到主界面；点右下角的「卸下」移除该槽位外观。

选择立即生效并同步给周围玩家，无需重连。界面只响应点击选择，不能把真实物品放进去（拖拽会被拦下）。

## 配置界面布局

衣柜界面外观写在 `plugins/Lantern/gui/wardrobe.yml`，改完执行 `/lantern reload`：

```yml
title: '&8衣柜'
size: 54
slots:
  - name: full_body
    index: 11
  - name: back
    index: 13
  - name: tail
    index: 15
  - name: head
    index: 29
  - name: effect
    index: 31
empty-slot:
  material: GRAY_STAINED_GLASS_PANE
  name: '&7空槽位'
border:
  material: BLACK_STAINED_GLASS_PANE
  name: ' '
  slots: '0-8,45-53'
```

| 设置名 | 怎么填 |
|---|---|
| `title` | 主界面标题，支持 `&` 颜色代码 |
| `size` | 主界面格数，9 到 54 之间、9 的倍数 |
| `slots` | 槽位列表。`name` 必须是五个外观槽位之一（`full_body` / `back` / `tail` / `head` / `effect`，见[玩家外观](costumes.md#可用位置)）；`index` 是该槽位在界面里的格子下标（0 起，从左到右、从上到下），不能重复 |
| `empty-slot` | 没穿外观的槽位显示的占位物品，`material` + `name` |
| `border` | 装饰边框物品。`slots` 支持区间写法（如 `0-8,45-53`）和单格（如 `13`），不要和槽位 `index` 重叠 |

选择界面的标题固定为「选择: <槽位>」，卸下按钮固定在右下角，这两个目前不可配置。

## 常见问题

| 现象 | 检查 |
|---|---|
| 选择界面里没有某件外观 | 外观的 `slot` 字段是否和点进的槽位一致；`costumes.yml` 保存后是否执行了 `/lantern reload` |
| 点击槽位没反应 | `slots` 的 `name` 是否写成了五个槽位之外的值 |
| 界面打不开 | 执行者是否有 `lantern.wardrobe` 权限；控制台执行必须带玩家名 |
| 换装后其他玩家看不到 | 正常情况会立即广播；让该玩家重新进入服务器可强制刷新 |
