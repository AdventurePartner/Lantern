# 命令与权限

所有功能都从 `/lantern` 一个入口进。游戏里执行 `/lantern help` 可以看到按板块分组、按权限过滤后的命令总览，每条都可以点击直接填入聊天框。

## 权限节点

除 `help` 外每个子命令都有独立权限节点，默认全部只对 OP 开放。给玩家开放某个功能时授对应节点即可：

| 权限 | 管哪些命令 |
|---|---|
| `lantern.reload` | `/lantern reload` |
| `lantern.open` | `/lantern open` |
| `lantern.give` | `/lantern give` |
| `lantern.anim` | `/lantern anim`、`/lantern var` |
| `lantern.skill` | `/lantern skill` |
| `lantern.camera` | `/lantern cam`、`/lantern campath` |
| `lantern.costume` | `/lantern costume` |
| `lantern.wardrobe` | `/lantern wardrobe` |
| `lantern.bind` | `/lantern bind`、`unbind`、`binds` |
| `lantern.lock` | `/lantern lock`、`unlock` |
| `lantern.image` | `/lantern image` |
| `lantern.*` | 以上全部（父节点） |

没权限的子命令不会出现在 Tab 补全和 `help` 列表里。

## 基础

| 命令 | 效果 |
|---|---|
| `/lantern reload` | 重新读取所有 Lantern 配置，并推送给在线玩家 |
| `/lantern open <界面名> [玩家]` | 给自己（或指定玩家）打开一个弹出界面 |
| `/lantern give <方块名> [玩家] [数量]` | 给自己（或指定玩家）自定义方块物品，数量限制在 1 到 64 |
| `/lantern wardrobe [玩家]` | 打开衣柜界面，玩家在里面自己挑外观（见[衣柜](../player/wardrobe.md)） |

## 动画与变量

`anim`、`var` 都作用于**执行者 8 格内最近的、已命名（有自定义名）的生物**——和生物模型的匹配方式一致。控制台不能用这两条，请走 MythicMobs 机制或其他插件 API。

| 命令 | 效果 |
|---|---|
| `/lantern anim play <动画名> [过渡tick] [loop\|once] [速度]` | 播放动画；过渡默认 5 tick，默认循环，`once` 播一遍，速度默认 1.0 |
| `/lantern anim stop <动画名> [过渡tick]` | 停止动画 |
| `/lantern anim pause <动画名>` | 暂停，冻结时间轴 |
| `/lantern anim resume <动画名>` | 恢复暂停的动画 |
| `/lantern anim seek <动画名> <秒数>` | 跳转时间轴；loop 取模、once 钳制在时长内 |
| `/lantern var <键名> <值\|del>` | 设置 / 删除附近实体的 molang 变量，值可以是数字或 molang 表达式 |
| `/lantern skill <玩家> <技能名>` | 对玩家施放 MythicMobs 技能（需装 MythicMobs） |

## 相机

| 命令 | 效果 |
|---|---|
| `/lantern cam ...` | 越肩管理与演出指令（锁定、震屏、FOV、运镜等） |
| `/lantern campath ...` | 打点运镜的录制与预演 |

完整参数见[相机系统](../camera/camera.md)。

## 装扮

| 命令 | 效果 |
|---|---|
| `/lantern costume equip <玩家> <槽位> <装扮ID>` | 给玩家装备外观；槽位 `full_body` / `back` / `tail` / `head` / `effect` |
| `/lantern costume unequip <玩家> [槽位]` | 卸下外观，不带槽位 = 全部 |

玩家自己挑选外观用[衣柜](../player/wardrobe.md)更方便。

## 载体与输入

| 命令 | 效果 |
|---|---|
| `/lantern bind <宿主UUID> <载体UUID> [x y z] [rotate] [visible] [毫秒]` | 把载体渲染到宿主身上（宿主在前） |
| `/lantern unbind <载体UUID>` | 解除绑定 |
| `/lantern binds` | 列出当前全部绑定 |
| `/lantern lock <玩家> <动作,动作> <毫秒>` | 压住玩家输入（`move`/`jump`/`sneak`/`turn`/`attack`/`use`） |
| `/lantern unlock <玩家>` | 解除玩家的全部输入锁 |

这两组主要用来手工排查；技能里请用对应的 MythicMobs 机制，见[载体绑定与输入锁](../interaction/bind-and-input-lock.md)。

## 世界图片

| 命令 | 效果 |
|---|---|
| `/lantern image text <内容> [缩放]` | 在自己头顶 1 格生成文字图片，3 秒后消失 |
| `/lantern image texture <贴图路径> [边长]` | 在自己头顶生成贴图图片，3 秒后消失 |
| `/lantern image spawn <模板名>` | 按 `worldImages.yml` 的模板生成，3 秒后消失 |
| `/lantern image clear` | 清空自己客户端上的全部世界图片 |

生成类子命令只发给执行者本人，不影响其他玩家。详见[世界图片](../world/world-images.md)。
