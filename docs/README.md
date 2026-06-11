# Lantern 使用总览

Lantern 分为两部分：

- 服务端插件：放在服务器的 `plugins` 文件夹里，负责读取配置、发放自定义方块、打开界面。
- 玩家客户端模组：放在玩家的 `mods` 文件夹里，负责显示界面、模型、图片文字和按键效果。

> 下面文档里的英文设置名需要照抄；说明文字会尽量用日常语言解释。

## 需要准备

| 位置 | 需要的内容 |
|---|---|
| 服务器 | Minecraft 1.20.1 服务端、Lantern 插件、AyCore |
| 玩家客户端 | Minecraft 1.21.1、Fabric、Fabric API、Fabric Kotlin、GeckoLib、Lantern 模组 |
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
| `plugins/Lantern/itemIcons.yml` | 物品图标替换 |
| `plugins/Lantern/characters.yml` | 用一个文字显示一张小图片 |
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

## 聊天频道按钮

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

执行 `/lantern reload` 后，玩家打开聊天栏会在输入框上方看到每个频道的按钮。`prefixes` 匹配客户端实际看到的纯文本开头，不写 `&` 或 `§` 颜色代码；`prefixes: []` 表示该频道显示所有已缓存聊天消息。`filter` 填频道 `id` 列表，会从当前频道里排除这些频道的非空 `prefixes` 命中的消息，例如上面的 `all` 不显示 `[系统]` 消息。切换按钮会替换原版聊天栏当前显示的消息队列，不再显示额外独立面板。非 `all` 频道收到新消息时，玩家没打开聊天栏会在左下角看到“收到新消息”；玩家正在聊天栏内时，对应频道按钮右上角会显示红点，点击该频道后清除。

## 功能文档

- [界面配置](ui-config.md)
- [自定义方块](custom-blocks.md)
- [生物模型](entity-models.md)
- [玩家外观](player-costumes.md)
- [按键绑定](keys.md)
- [图片文字、物品图标和资源包](assets-and-icons.md)
