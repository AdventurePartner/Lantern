# 玩家外观

玩家外观写在 `plugins/Lantern/costumes.yml`。

这个文件负责登记可用外观。当前 `/lantern` 指令包含重载、打开界面、给予自定义方块；没有外观发放指令。外观是否显示，需要由服务器已有玩法安排。

## 添加一个外观

```yml
angel_wings:
  display-name: "Angel Wings"
  geo: "geo/costume/angel_wings.geo.json"
  texture: "textures/costume/angel_wings.png"
  animations:
    file: "animations/costume/angel_wings.animation.json"
    states:
      idle: "animation.angel_wings.idle"
      walk: "animation.angel_wings.walk"
  scale: 1.0
  offset:
    x: 0.0
    y: 0.0
    z: 0.0
  slot: "back"
  bone-sync: true
  bone-mapping:
    head: "head"
    body: "body"
    left_arm: "left_arm"
    right_arm: "right_arm"
    left_leg: "left_leg"
    right_leg: "right_leg"
```

最上面的 `angel_wings` 是外观名，给玩家安排外观时会用到它。

| 设置名 | 怎么填 |
|---|---|
| `display-name` | 外观显示名 |
| `geo` | 模型文件位置，从 `assets/lantern/` 后面开始写 |
| `texture` | 贴图位置；可以写资源包文件，也可以写图片网址 |
| `animations.file` | 动画文件位置 |
| `animations.states.idle` | 站立或空闲时播放的动画 |
| `animations.states.walk` | 移动时播放的动画 |
| `scale` | 外观整体大小 |
| `offset.x` | 左右移动 |
| `offset.y` | 上下移动 |
| `offset.z` | 前后移动 |
| `slot` | 外观放在哪个位置 |
| `bone-sync` | 是否跟随玩家身体动作，通常保持 `true` |
| `bone-mapping` | 模型里的部位名字；不需要特殊处理时保持默认 |

## 可用位置

`slot` 可以填写：

| 写法 | 用途 |
|---|---|
| `full_body` | 全身外观 |
| `back` | 背部，例如翅膀、背饰 |
| `tail` | 尾巴 |
| `head` | 头部外观 |
| `effect` | 特效类外观 |

同一位置一般只显示一个外观。

## 资源文件放哪里

```text
assets/lantern/geo/costume/angel_wings.geo.json
assets/lantern/textures/costume/angel_wings.png
assets/lantern/animations/costume/angel_wings.animation.json
```

`geo`、`texture`、`animations.file` 都是从 `assets/lantern/` 后面开始写。

## 修改后生效

1. 保存 `costumes.yml`。
2. 执行 `/lantern reload`。
3. 如果玩家身上的外观没有变化，让玩家重新进入服务器，或让负责发放外观的玩法重新安排一次。

## 常见问题

| 现象 | 检查 |
|---|---|
| 外观不显示 | 外观名是否和服务器安排给玩家的名字一致 |
| 外观位置不对 | 调整 `offset.x`、`offset.y`、`offset.z` |
| 外观动作不跟随玩家 | `bone-sync` 是否为 `true`，模型里的部位名字是否和 `bone-mapping` 一致 |
| 外观太大或太小 | 调整 `scale` |
