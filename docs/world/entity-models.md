# 生物模型

生物模型写在 `plugins/Lantern/entityModels.yml`。

当一个生物的名字和这里的 `name` 一致时，玩家客户端会把它显示成对应模型。

## 添加一个生物模型

```yml
example_pig:
  name: "TEST"
  geo: "geo/entity/example_pig.geo.json"
  texture: "textures/entity/example_pig.png"
  animations:
    file: "animations/entity/example_pig.animation.json"
    states:
      idle: "animation.example_pig.idle"
      walk: "animation.example_pig.walk"
      attack: "animation.example_pig.attack"
      hurt: "animation.example_pig.hurt"
      death: "animation.example_pig.death"
  scale: 1.0
  height: 1.0
  width: 1.0
  hidden: false
  offset-y: 0.0
```

最上面的 `example_pig` 只是这段配置的名字；真正用来匹配生物的是 `name`。

| 设置名 | 怎么填 |
|---|---|
| `name` | 生物显示名，必须和游戏里的名字一致；建议先用纯文字 |
| `geo` | 模型文件位置，从 `assets/lantern/` 后面开始写 |
| `texture` | 贴图位置；可以写资源包文件，也可以写图片网址 |
| `animations.file` | 动画文件位置 |
| `animations.states.idle` | 站立或空闲时播放的动画 |
| `animations.states.walk` | 移动时播放的动画 |
| `animations.states.attack` | 攻击时播放的动画，可不填 |
| `animations.states.hurt` | 受伤时播放的动画，可不填 |
| `animations.states.death` | 死亡时播放的动画，可不填 |
| `scale` | 模型整体大小 |
| `height` | 生物可碰到的高度 |
| `width` | 生物可碰到的宽度 |
| `hidden` | `true` 隐藏名字，`false` 显示名字 |
| `offset-y` | 名字向上移动多少 |

## 让生物使用模型

给生物改成配置里的名字即可。例如配置里写：

```yml
name: "TEST"
```

那么游戏里的生物名字也要是 `TEST`。可以用命名牌，也可以用服务器已有的召唤或改名方式。

## 旧写法

如果只需要一个默认动画，也可以这样写：

```yml
simple:
  name: "SIMPLE"
  geo: "geo/entity/simple.geo.json"
  texture: "textures/entity/simple.png"
  animation: "animations/entity/simple.animation.json"
  scale: 1.0
  height: 1.0
  width: 1.0
  hidden: false
  offset-y: 0.0
```

这种写法会把默认动画名当作 `idle`。

## 资源文件放哪里

```text
assets/lantern/geo/entity/example_pig.geo.json
assets/lantern/textures/entity/example_pig.png
assets/lantern/animations/entity/example_pig.animation.json
```

`geo`、`texture`、`animations.file` 都是从 `assets/lantern/` 后面开始写。

## 修改后生效

1. 保存 `entityModels.yml`。
2. 执行 `/lantern reload`。
3. 模型和贴图在玩家客户端后台读取，读完之前生物先显示原版样子，读完后自动换成自定义模型；一直没出现时，让玩家重新进入服务器。

## 手动播放动画

除了 `idle`/`walk` 这些自动状态，还可以随时让生物播放动画文件里的任意一个动画。动画名就是 `.animation.json` 里 `animations` 下面的那个名字。

这些命令作用于**执行者 8 格内最近的、已命名的生物**（控制台不能用，走 MythicMobs 机制或插件 API）：

```
/lantern anim play <动画名>                    # 循环播放，直到停止
/lantern anim play <动画名> 5 once             # 播一遍自动结束，5 tick 过渡
/lantern anim play <动画名> 5 loop 1.5         # 1.5 倍速循环
/lantern anim stop <动画名>                    # 停止播放
/lantern anim pause <动画名>                   # 暂停，冻结时间轴
/lantern anim resume <动画名>                  # 恢复暂停的动画
/lantern anim seek <动画名> 2.5                # 跳转到 2.5 秒处（loop 取模 / once 钳制）
```

`play` 的参数按顺序：过渡时间（tick，默认 5）、`loop` 或 `once`（默认循环）、速度倍率（默认 1.0）。

还可以给附近生物设置 molang 变量，动画表达式里用 `variable.名字` 读取：

```
/lantern var amplitude 0.5                     # 设置变量
/lantern var amplitude del                     # 删除变量
```

值支持数字或 molang 表达式（如 `math.sin(query.time_stamp/100)`）。

## 配合 MythicMobs

服务端装了 MythicMobs 插件时，Lantern 会自动注册一个技能机制 `lanternanim`，可以在 MM 的技能里直接触发动画。

绑定模型的方法：让 MM 生物的 `Display` 名字和 `entityModels.yml` 里该条目的 `name` 字段值一致（不是文件里的 key！颜色符号会自动忽略），客户端就会把这只生物渲染成对应模型。

然后在 MM 生物或技能配置里写：

```yml
Skills:
  - lanternanim{anim=skill_slash} @Self                 # 播放（循环）
  - lanternanim{anim=skill_slash;mode=once} @Self       # 播一遍
  - lanternanim{anim=skill_slash;remove=true;time=0} @Self  # 停止
```

参数（都可以只写首字母）：`anim/a` 动画名、`remove/r` 是否停止（默认 false）、`time/t` 过渡 tick（默认 5）、`mode/m` `loop` 或 `once`（默认 loop）、`speed/sp` 速度倍率、`file/lib` 动画库。完整参数见 [MythicMobs 机制](../integrations/mythic-mechanics.md#1-lanternanim动画播控别名-lanim)。

没装 MythicMobs 时这个机制不会注册，插件其他功能不受影响。

播控指令只广播给实体周围一定范围内的玩家（被播放动画的玩家本人除外，无论距离都会收到），半径在 `config.yml` 的 `animation-broadcast-radius` 里调（默认 96 格，设 0 或负数退回全服广播——不推荐，人多时每秒可达数万包）。

## 常见问题

| 现象 | 检查 |
|---|---|
| 生物没有变样 | 生物名字是否和 `name` 完全一致 |
| 只有影子或不显示 | 模型、贴图、动画文件位置是否正确 |
| 名字挡住模型 | 调整 `offset-y` 或把 `hidden` 改成 `true` |
| 碰撞范围不合适 | 调整 `height` 和 `width` |
| 命令提示附近没有已命名生物 | anim/var 命令找的是 8 格内最近的自定义名生物，先给生物命名 |
| 配置很多时报 “plugin message too large” | 同时更新服务端插件和客户端模组；新版会把大的模型配置自动拆包发送，不需要删配置 |
