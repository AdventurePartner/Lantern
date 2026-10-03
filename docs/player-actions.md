# 玩家动作

玩家动作分三块，写在三个文件里：

| 文件 | 管什么 |
|---|---|
| `plugins/Lantern/playerActions.yml` | 按键触发的翻滚闪避、挥击触发的连招 |
| `plugins/Lantern/animationGroups.yml` | 按条件自动换整套动作（换武器换动作组） |
| `plugins/Lantern/animations.yml` | 动作的时间线打点（伤害帧、特效帧、音效、镜头） |

这三个文件默认都是空的（只有注释），不配就等于没有这些功能，玩家动作维持基础的走跑跳。

所有动作播放都在玩家客户端本地完成，按下按键立刻出招，不等服务器往返。服务器只负责下发"有哪些动作"和"打到谁身上"。

---

## 准备动画

动作动画放在客户端资源包里：

```
LanternPackLocal/assets/lantern/animations/player/你的动画.animation.json
```

两条规则：

- **文件名只能用英文小写、数字和 `_ . - /`**。中文文件名会被游戏拒绝，动作不会播。
- **动画名可以用中文**。那是文件内部的名字，不受限制。

配置里引用时写相对 `assets/lantern/` 的路径：

```yml
file: "animations/player/roll.animation.json"
```

---

## 翻滚闪避

按一个键，朝移动方向翻滚。

```yml
翻滚:
  key: "left_alt"
  file: "animations/player/roll.animation.json"
  cooldown: 800
  layer: motion
  exclusive: true
  invulnerable: 500
  distance: 4.0
  airborne: true
  directions:
    forward: "前翻滚"
    backward: "后翻滚"
    left: "左翻滚"
    right: "右翻滚"
    forward_left: "前翻滚"
    forward_right: "前翻滚"
    backward_left: "后翻滚"
    backward_right: "后翻滚"
    none: "前翻滚"
```

### 方向怎么定

看玩家按的移动键，八个方向各播一段：

| 按键 | 方向名 |
|---|---|
| W | `forward` |
| S | `backward` |
| A | `left` |
| D | `right` |
| W + A | `forward_left` |
| W + D | `forward_right` |
| S + A | `backward_left` |
| S + D | `backward_right` |
| 没按方向键 | `none` |

只有前后左右四段动画时，斜向不用配——会自动用最接近的那段。以后补了斜向动画，把名字填上即可。

### 常用设置

| 设置 | 说明 |
|---|---|
| `key` | 触发键，写法和 `keys.yml` 一样（`left_alt`、`space`、`ctrl+q`）。单独用 `alt` / `ctrl` / `shift` 也可以 |
| `cooldown` | 冷却毫秒，防止连按刷翻滚 |
| `distance` | 翻多远（格）。配几格就走几格，不受地面摩擦影响。写 0 就只播动画不移动 |
| `invulnerable` | 无敌帧毫秒。翻滚期间免疫伤害，闪避靠它才有意义 |
| `airborne` | 空中能不能翻（跳跃中翻滚） |
| `exclusive` | 建议开。翻滚时压住上半身动作，否则会一边翻滚一边挥刀 |
| `layer` | `motion` = 全身动作（压住走路）；`combat` = 只用上半身（腿继续跑） |

### 调手感

| 想要 | 改什么 |
|---|---|
| 翻得更远 | `distance` 调大，6.0 约两格半疾跑距离 |
| 翻得更快 | `dash-duration` 填个比动画短的毫秒数，如 500 |
| 起手更快 | `transition` 降到 1 |
| 收尾有跳变 | `exit-transition` 加大到 5-8 |
| 带腾空感 | `vertical: 0.2` |

---

## 连招

连续攻击时按顺序播不同的招式。

```yml
连击:
  trigger: attack
  costume: player_default
  file: "animations/player/player_default.animation.json"
  layer: combat
  steps:
    - animation: "左砍"
      cancel-at: 0.7
      window: 700
    - animation: "右砍"
      cancel-at: 0.7
      window: 700
    - animation: "蓄力砍"
      cancel-at: 0.9
      window: 800
```

`trigger: attack` 表示用原版的挥击触发，所以攻击冷却、命中判定都还是原版那一套。

### 两个关键数值

**`cancel-at`** —— 这一段播到百分之多少就允许接下一段。

给 0.7 表示打到七成就能接，不用等整段播完。这是连招不顿挫的关键：给太大（比如 1.0）每一刀都要播完才能接，手感发黏；给太小招式会被腰斩成快进。主要攻击段给 0.7，收招长的给 0.9。

**`window`** —— 距上次出手多久内还算同一套连招。

给 700 表示 0.7 秒内继续攻击就接下一段，超过就从第一段重新开始。

### 不配连招会怎样

攻击回落到左右交替，读外观状态表里的 `attack_left` / `attack_right`，和不装这个功能时一样。

---

## 按武器换动作组

拿起大剑换一套待机、行走、攻击，放下换回来。

```yml
重型武器:
  costume: player_heavy
  priority: 20
  condition:
    item-lore-contains: "重型"
```

`costume` 填 `costumes.yml` 里真实存在的外观条目名。一套外观就是一组动作——模型、动画库、状态表、连招全都跟着换。

### 条件

同一条规则里的条件要全部满足才算命中：

| 条件 | 说明 |
|---|---|
| `item-lore-contains` | 主手物品的说明文字里包含这段话 |
| `item-name-contains` | 主手物品的显示名包含这段话 |
| `item-type` | 主手物品类型，如 `DIAMOND_SWORD` |
| `permission` | 玩家有这个权限 |

写多条规则时，`priority` 大的先匹配，第一个命中的生效。都不命中就回到 `costumes.yml` 里 `player-default.costume` 指定的默认外观。

判定每秒跑一次，只有结果变了才通知客户端。

### 让连招也跟着换

在 `playerActions.yml` 里给连招加 `costume`：

```yml
重武器连招:
  trigger: attack
  costume: player_heavy
  file: "animations/player/heavy_weapon.animation.json"
  layer: combat
  steps:
    - animation: "重武器劈砍"
      cancel-at: 0.8
      window: 900
```

配了 `costume` 的连招只在那套外观下生效；留空的作为其他外观的通用连招。

---

## 技能动画不要绑在外观库上

`animationGroups.yml` 会按手里的物品换外观，外观一换动画库就换了。技能动画如果只放在某套外观的库里，玩家切个物品再放技能，客户端就找不到这段——技能空放，日志里是：

```
[Lantern] 动画 '剑仙神剑极阵' 不在动画库 lantern:animations/player/player_default.animation.json 里，播控被丢弃
```

给 `lanternanim` 配 `file=` 指向剪辑所在的库，技能就和外观彻底脱钩：

```yml
- lanternanim{anim=剑仙神剑极阵;m=once;t=3;file=animations/player/sword_immortal.animation.json} @self
```

---

## 伤害帧与特效帧

动作播出来了，但伤害、音效、粒子要自己挂，写在 `animations.yml`：

```yml
player_default:
  左砍:
    actions:
      - {at: 5,  sound: {s: minecraft:entity.player.attack.sweep}}
      - {at: 8,  mm-skill: SwordDamage}
      - {at: 10, camera: {action: shake, amplitude: 0.3, radius: 16}}
    next: 右砍
```

- 最外层是**外观条目名**（玩家）或**生物模型条目名**（怪物）
- 第二层是动画名
- `at` 是动画开始后第几 tick（20 tick = 1 秒）
- `mm-skill` 触发 MythicMobs 技能，伤害判定写在技能里
- `next` 播完自动接下一个动画

这样伤害发生在动画挥到位的那一帧，而不是点击的瞬间。

想彻底关掉原版的即时伤害，在动作里加：

```yml
  suppress-vanilla-attack: 600
```

这样出招期间玩家打出去的原版伤害被取消，全部交给时间线上的伤害帧。

---

## 头随视角

让模型的头跟着玩家视角转，在外观的状态表里加一条：

```yml
player_default:
  animations:
    file: "animations/player/player_default.animation.json"
    states:
      idle: 待机
      walk: 行走
      head: {animation: 头颅, mode: loop}
```

对应的动画只需要控制头这一根骨头，内容就一行：

```json
"head": { "rotation": ["query.pitch", "query.yaw", 0] }
```

`query.pitch` 是抬头低头，`query.yaw` 是左右转头，每帧自动取玩家的视角。

这一层是**叠加**上去的，所以走路时头部原本的摆动不会消失，而是"走路摆动 + 视角偏转"一起生效。

---

## 上半身与下半身

出招时腿在做什么，由 `layer` 决定：

| `layer` | 效果 |
|---|---|
| `motion` | 全身动作，压住走路。翻滚、扑倒这类用 |
| `combat` | 只用上半身，腿继续跑。挥砍、施法这类用 |

配 `combat` 的动作，站着不动时会播完整动画（包括腿部动作），跑动时腿自动交还给跑步——不会出现"上身挥刀下身僵直"。

哪些骨头算"上半身"默认按标准人模判断。骨架命名不一样的外观可以自己声明：

```yml
    animations:
      upper-body-bones: [body, head, rightArm, rightForeArm, leftArm, leftForeArm]
```

---

## 常见问题

**改了配置没反应**

改 `.yml` 执行 `/lantern reload` 即可，但玩家客户端要重新连接才能收到新配置。换了插件 jar 必须重启服务器，`reload` 不会加载新的插件代码。

**动作组切了但没换动作**

看服务器日志有没有这条：

```
[Lantern] 动画组 '重型武器' 指向的外观 'player_heavy' 不在 costumes.yml 中
```

多半是 `costume` 填了不存在的外观名。

**动作完全不播**

依次检查：动画文件名是不是用了中文、动画名和文件里的是不是一致、该外观有没有开 `host-driven`。

**翻滚只有动画没有位移**

`distance` 默认是 0，要显式配。

---

## 交给服务端把关

默认按键即播、不等服务器，这是手感的根基。需要扣蓝扣耐力的招式给它加一行：

```yml
  server-checked: true
```

之后按键改为上报给服务端，服务端抛 `LanternPlayerActionRequestEvent` 让附属决定放不放，没装附属也照常播出。代价是多一个来回的延迟，翻滚闪避这类吃手感的动作不要开。详见[载体绑定与输入锁](bind-and-input-lock.md)。

---

## 相关文档

- [玩家外观](player-costumes.md) —— 外观条目怎么写
- [载体绑定与输入锁](bind-and-input-lock.md) —— 挂件跟随、硬直、服务端把关
- [按键绑定](keys.md) —— 触发键的写法
- [MythicMobs 联动](mythic-mechanics.md) —— 伤害帧里调用技能
