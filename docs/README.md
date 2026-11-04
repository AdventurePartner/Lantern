# Lantern 使用文档

Lantern 是一套「Bukkit 服务端插件 + 玩家客户端模组」组合，让 Paper/Spigot 服务器不需要在服务端装任何模组加载器，就能给玩家推送自定义界面、模型、相机演出等内容。

- **服务端插件**：读取配置、下发数据、处理指令。服务器只需要 AyCore 前置。
- **客户端模组**：显示界面、模型、图片文字和按键效果。支持 Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1、NeoForge 1.21.10、NeoForge 1.21.11 五个版本，玩家装对应版本即可，功能一致。

> 文档里的英文设置名需要照抄；说明文字会尽量用日常语言解释。

## 快速开始

1. 服务器放入 Lantern 插件与 AyCore，启动一次生成 `plugins/Lantern/` 文件夹。
2. 玩家客户端放入对应版本的 Lantern 模组与前置。
3. 玩家进服后自动接收配置，修改配置后执行 `/lantern reload` 推送。

详见[安装与文件结构](getting-started/installation.md)。

## 功能一览

| 分类 | 内容 |
|---|---|
| [开始使用](getting-started/installation.md) | 安装、文件结构、全部命令与权限、聊天频道与聊天样式 |
| [界面（uix）](ui/screens.md) | HUD / 弹出界面 / 原版界面叠加层，覆盖或摆放原版 HUD，自绘血量、进度条等状态控件 |
| [世界内容](world/custom-blocks.md) | 自定义方块、生物自定义模型、世界图片（伤害数字、贴图与文字面片） |
| [玩家装扮与动作](player/costumes.md) | 玩家外观、衣柜、翻滚闪避与连招、按武器换动作组 |
| [相机](camera/camera.md) | 越肩视角、演出指令（锁定 / 震屏 / FOV）、打点运镜 |
| [交互](interaction/bind-and-input-lock.md) | 载体绑定、输入锁、按键绑定 |
| [资源](assets/assets-and-icons.md) | 图片文字、物品图标、加密资源包与进服后台加载 |
| [联动](integrations/mythic-mechanics.md) | MythicMobs 五个自定义机制（动画播控、molang 变量、相机、输入锁、载体绑定） |

## 常用入口

- [命令与权限](getting-started/commands.md) —— `/lantern` 全部子命令和权限节点
- [界面组件速查](ui/components.md) —— panel / div / text / button / image / input / slot
- [常见问题](assets/assets-and-icons.md#常见问题) —— 各页末尾都有「常见问题」表
