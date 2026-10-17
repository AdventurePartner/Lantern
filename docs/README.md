# Lantern 使用总览

Lantern 分为两部分：

- 服务端插件：放在服务器的 `plugins` 文件夹里，负责读取配置、发放自定义方块、打开界面。
- 玩家客户端模组：放在玩家的 `mods` 文件夹里，负责显示界面、模型、图片文字和按键效果。

> 下面文档里的英文设置名需要照抄；说明文字会尽量用日常语言解释。

## 需要准备

| 位置 | 需要的内容 |
|---|---|
| 服务器 | Minecraft 1.20.1 服务端、Lantern 插件、AyCore |
| 玩家客户端 | Minecraft 1.21.1 + Fabric（Fabric API、Fabric Kotlin、GeckoLib），或 Minecraft 1.21.1 + NeoForge 21.1.259 以上（GeckoLib 4.9.3 以上），再加对应版本的 Lantern 模组；另有 Forge 1.20.1 与 NeoForge 1.21.10 版本 |
| 可选 | PlaceholderAPI，用来在界面文字里显示玩家名字、血量、等级等内容 |

## 第一次安装

1. 把服务端插件放进服务器的 `plugins` 文件夹。
2. 确认 AyCore 已经安装。
3. 启动一次服务器，让 Lantern 生成 `plugins/Lantern/` 文件夹。
4. 把客户端模组和所需前置放进玩家客户端的 `mods` 文件夹。
5. 玩家进入服务器后，客户端会自动接收 Lantern 的配置。

## Lantern 文件夹

服务器启动后会出现这些文件和文件夹：

| 位置 | 用途 |
|---|---|
| `plugins/Lantern/config.yml` | 加密资源包密码、聊天频道 |
| `plugins/Lantern/huds/` | 常驻在游戏画面上的提示条、状态栏 |
| `plugins/Lantern/screens/` | 可以用指令或按钮打开的弹出界面 |
| `plugins/Lantern/keys.yml` | 玩家按键触发指令 |
| `plugins/Lantern/blockModels.yml` | 自定义方块 |
| `plugins/Lantern/entityModels.yml` | 生物显示成自定义模型 |
| `plugins/Lantern/costumes.yml` | 玩家外观 |
| `plugins/Lantern/playerActions.yml` | 玩家主动动作（翻滚闪避、连招） |
| `plugins/Lantern/animationGroups.yml` | 按条件自动切换动作组 |
| `plugins/Lantern/itemIcons.yml` | 物品图标替换 |
| `plugins/Lantern/characters.yml` | 用一个文字显示一张小图片 |
| `plugins/Lantern/worldImages.yml` | 世界图片（伤害数值弹出、贴图与文字图片模板） |
| `plugins/Lantern/data/blocks.yml` | 已放置自定义方块的位置记录，通常不要手动修改 |

## 常用指令

| 指令 | 效果 |
|---|---|
| `/lantern reload` | 重新读取所有 Lantern 配置，并推送给在线玩家 |
| `/lantern open <界面名>` | 给自己打开一个弹出界面 |
| `/lantern open <界面名> <玩家名>` | 给指定玩家打开一个弹出界面 |
| `/lantern give <方块名>` | 给自己一个自定义方块物品 |
| `/lantern give <方块名> <玩家名> <数量>` | 给指定玩家指定数量的自定义方块物品，数量会限制在 1 到 64 |

## 修改配置后的生效方式

1. 保存你修改的文件。
2. 在服务器执行 `/lantern reload`。
3. 如果你改的是模型、贴图或加密资源包，玩家客户端可能需要等待资源刷新；仍未显示时，让玩家重新进入服务器。

## 聊天频道与聊天样式

### 频道按钮

在 `plugins/Lantern/config.yml` 配置服务端同步给客户端的聊天频道：

```yaml
chat-channels:
  - id: all
    display-name: "全部"
    prefixes: []
    filter:
      - system
  - id: announcement
    display-name: "公告"
    prefixes:
      - "[公告]"
  - id: system
    display-name: "系统"
    prefixes:
      - "[系统]"
```

执行 `/lantern reload` 后，玩家打开聊天栏会在输入框上方看到每个频道的按钮。`prefixes` 匹配客户端实际看到的纯文本开头，不写 `&` 或 `§` 颜色代码；`prefixes: []` 表示该频道显示所有已缓存聊天消息。`filter` 填频道 `id` 列表，会从当前频道里排除这些频道的非空 `prefixes` 命中的消息，例如上面的 `all` 不显示 `[系统]` 消息。切换按钮会替换原版聊天栏当前显示的消息队列，不再显示额外独立面板。非 `all` 频道收到新消息时，玩家没打开聊天栏会在左下角看到“收到新消息”；玩家正在聊天栏内时，对应频道按钮右上角会显示红点，点击该频道后清除。`display-name` 可以写 `&` 颜色代码，例如 `"&6公告"`。

### 聊天样式

同样在 `config.yml` 里加 `chat-style`，可以改原版聊天窗口、频道按钮和“收到新消息”提示的外观。每一项都可以不写，不写的项保持原来的样子：

```yaml
chat-style:
  window:                    # 原版聊天窗口
    width: 320               # 宽度（像素）。不写 = 跟随玩家自己的“聊天宽度”设置
    height: 180              # 打开聊天栏时的高度
    height-unfocused: 90     # 没打开聊天栏时的高度
    scale: 1.0               # 文字大小，0.25 到 4。不写 = 跟随玩家设置
    offset-x: 0              # 整个聊天窗口左右移动，向右为正
    offset-y: -20            # 上下移动，向下为正，负数向上
    background: "#202040"    # 每行消息的背景色；none = 不画背景
    text-color: "#E0E0E0"    # 消息文字颜色，只影响没有自带颜色的文字
    input-background: "#80000000"   # 底部输入框的背景色；none = 透明
  tabs:                      # 频道按钮
    offset-x: 0              # 整组按钮的移动量
    offset-y: 0
    height: 12               # 按钮高度
    gap: 2                   # 按钮之间的空隙
    min-width: 24            # 按钮最小宽度
    padding-x: 6             # 文字离按钮左边的距离
    text-align: left         # left 靠左 / center 居中
    text-shadow: false       # 文字阴影
    background: "#80000000"           # 普通按钮背景。不写 = 跟随玩家的聊天背景不透明度
    selected-background: "#C04A90D9"  # 当前频道的按钮背景
    hover-background: "#A0FFFFFF"     # 鼠标指着时的背景。不写 = 不变
    hover-outline: "#FFFFFFFF"        # 鼠标指着时的边框；none = 不画
    text-color: "#FFFFFF"
    selected-text-color: "#FFFF55"    # 当前频道的文字颜色。不写 = 同 text-color
    unread-color: "#FFFF3333"         # 未读红点颜色
    unread-size: 4                    # 红点大小；0 = 不显示
  notification:              # 没打开聊天栏时左下角的“收到新消息”
    enabled: true            # false = 不显示
    text: "&e收到新消息"      # 可以写 & 颜色代码
    text-color: "#FFFFFF"
    text-shadow: false
    background: "#80000000"  # 不写 = 跟随玩家的聊天背景不透明度
    padding-x: 4
    padding-y: 2
    offset-x: 0              # 以屏幕左下角为起点移动
    offset-y: 0
```

单个频道的按钮还可以在 `chat-channels` 里单独写 `style`，没写的项沿用上面 `tabs` 的设置：

```yaml
chat-channels:
  - id: announcement
    display-name: "&6公告"
    prefixes:
      - "[公告]"
    style:
      width: 40                          # 固定按钮宽度
      background: "#80402000"
      selected-background: "#C0D9904A"
      hover-background: "#A0FFD080"
      text-color: "#FFD080"
      selected-text-color: "#FFFFFF"
```

注意事项：

- 颜色一定要加引号，因为 `#` 在配置文件里表示注释。颜色写 6 位 `#RRGGBB`（不透明）或 8 位 `#AARRGGBB`（前两位是透明度，`00` 全透明、`FF` 不透明）。
- 按钮和提示的 `background` 还可以写贴图 `"url(lantern:textures/chat/tab.png)"`，写法和界面配置里的 `background` 一样，贴图会拉伸填满按钮。聊天窗口的 `background` 只能写颜色。
- 聊天窗口的背景透明度会再乘上玩家自己的“文字背景不透明度”设置。消息过一会儿淡出的效果也会保留。玩家把这个设置调到 0 时，背景就不显示。
- 写了 `width`、`height`、`scale` 后，玩家在设置里调的聊天宽度、高度、文字大小就不起作用了。不写则仍由玩家自己决定。
- 移动聊天窗口后，点击消息里的链接、鼠标悬停提示也会跟着新位置走。
- 改完执行 `/lantern reload` 即可生效。玩家退出服务器后，聊天窗口会恢复原版外观。

## 功能文档

- [界面配置](ui-config.md)
- [自定义方块](custom-blocks.md)
- [生物模型](entity-models.md)
- [玩家外观](player-costumes.md)
- [玩家动作](player-actions.md)
- [载体绑定与输入锁](bind-and-input-lock.md)
- [按键绑定](keys.md)
- [世界图片](world-images.md)
- [图片文字、物品图标和资源包](assets-and-icons.md)
