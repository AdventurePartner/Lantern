# 安装与文件结构

Lantern 分为两部分：

- **服务端插件**：放在服务器的 `plugins` 文件夹里，负责读取配置、发放自定义方块、打开界面。
- **玩家客户端模组**：放在玩家的 `mods` 文件夹里，负责显示界面、模型、图片文字和按键效果。

> 下面文档里的英文设置名需要照抄；说明文字会尽量用日常语言解释。

## 需要准备

| 位置 | 需要的内容 |
|---|---|
| 服务器 | Minecraft 1.20.1 服务端、Lantern 插件、AyCore |
| 玩家客户端 | Minecraft 1.21.1 + Fabric（Fabric API、Fabric Kotlin、GeckoLib），或 Minecraft 1.21.1 + NeoForge 21.1.259 以上（GeckoLib 4.9.3 以上），再加对应版本的 Lantern 模组；另有 Forge 1.20.1、NeoForge 1.21.10 与 NeoForge 1.21.11（NeoForge 21.11.45、GeckoLib 5.4.5）版本 |
| 可选 | PlaceholderAPI，用来在界面文字里显示玩家名字、血量、等级等内容；MythicMobs，用来配合生物模型与技能演出 |

客户端模组只装在玩家自己电脑上即可，**服务器不需要安装 Fabric / Forge / NeoForge**。

## 第一次安装

1. 把服务端插件放进服务器的 `plugins` 文件夹。
2. 确认 AyCore 已经安装。
3. 启动一次服务器，让 Lantern 生成 `plugins/Lantern/` 文件夹。
4. 把客户端模组和所需前置放进玩家客户端的 `mods` 文件夹。
5. 玩家进入服务器后，客户端会自动接收 Lantern 的配置。

## Lantern 文件夹

服务器启动后会出现这些文件和文件夹：

| 位置 | 用途 | 详细文档 |
|---|---|---|
| `plugins/Lantern/config.yml` | 加密资源包密码、聊天频道、动画广播半径 | [图片文字、物品图标和资源包](../assets/assets-and-icons.md)、[聊天频道与聊天样式](chat.md) |
| `plugins/Lantern/huds/` | 常驻在游戏画面上的提示条、状态栏 | [界面文件与三种界面](../ui/screens.md) |
| `plugins/Lantern/screens/` | 可以用指令或按钮打开的弹出界面 | [界面文件与三种界面](../ui/screens.md) |
| `plugins/Lantern/gui/wardrobe.yml` | 衣柜界面的槽位布局与装饰 | [衣柜](../player/wardrobe.md) |
| `plugins/Lantern/keys.yml` | 玩家按键触发指令 | [按键绑定](../interaction/keys.md) |
| `plugins/Lantern/blockModels.yml` | 自定义方块 | [自定义方块](../world/custom-blocks.md) |
| `plugins/Lantern/entityModels.yml` | 生物显示成自定义模型 | [生物模型](../world/entity-models.md) |
| `plugins/Lantern/costumes.yml` | 玩家外观 | [玩家外观](../player/costumes.md) |
| `plugins/Lantern/playerActions.yml` | 玩家主动动作（翻滚闪避、连招） | [玩家动作](../player/actions.md) |
| `plugins/Lantern/animationGroups.yml` | 按条件自动切换动作组 | [玩家动作](../player/actions.md) |
| `plugins/Lantern/animations.yml` | 动作的时间线打点（伤害帧、特效帧、音效、镜头） | [玩家动作](../player/actions.md) |
| `plugins/Lantern/camera.yml` | 越肩视角配置 | [相机系统](../camera/camera.md) |
| `plugins/Lantern/cameraPaths/` | 打点运镜存档（命令生成） | [相机系统](../camera/camera.md) |
| `plugins/Lantern/itemIcons.yml` | 物品图标替换 | [图片文字、物品图标和资源包](../assets/assets-and-icons.md) |
| `plugins/Lantern/characters.yml` | 用一个文字显示一张小图片 | [图片文字、物品图标和资源包](../assets/assets-and-icons.md) |
| `plugins/Lantern/worldImages.yml` | 世界图片（伤害数值弹出、贴图与文字图片模板） | [世界图片](../world/world-images.md) |
| `plugins/Lantern/data/blocks.yml` | 已放置自定义方块的位置记录，通常不要手动修改 | — |

## 修改配置后的生效方式

1. 保存你修改的文件。
2. 在服务器执行 `/lantern reload`。
3. 如果你改的是模型、贴图或加密资源包，玩家客户端会在后台重新读取，读完后自动生效，不会出现加载界面；资源读好之前先显示原版样子（详见[图片文字、物品图标和资源包](../assets/assets-and-icons.md#进服时的加载方式)）。

## 客户端版本兼容

- 五个客户端模块（Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1、NeoForge 1.21.10、NeoForge 1.21.11）连接的都是没有装任何模组加载器的 Bukkit/Paper 服务端，功能一致。
- 服务端插件与客户端模组**必须一起更新**：两端之间是二进制协议，版本不一致时客户端会丢弃新格式的包并在聊天栏提示。
