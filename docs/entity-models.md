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
3. 如果模型没有立刻出现，让玩家重新进入服务器。

## 常见问题

| 现象 | 检查 |
|---|---|
| 生物没有变样 | 生物名字是否和 `name` 完全一致 |
| 只有影子或不显示 | 模型、贴图、动画文件位置是否正确 |
| 名字挡住模型 | 调整 `offset-y` 或把 `hidden` 改成 `true` |
| 碰撞范围不合适 | 调整 `height` 和 `width` |
