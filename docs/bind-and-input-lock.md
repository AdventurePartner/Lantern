# 载体绑定与输入锁

两个给技能演出用的原语：

| 能力 | 做什么 |
|---|---|
| 载体绑定 | 把一只实体画到另一只实体身上，跟着走跟着转 |
| 输入锁 | 一段时间内压住玩家的移动、跳跃、视角等输入 |

两者都不需要玩家装额外的东西，客户端有 Lantern 模组就行。

---

## 载体绑定

### 能做什么

手上端着的杯子、头顶转的光环、觉醒时身上浮起的符文——这些都是一只**单独的实体**，被画到了玩家身上。

绑定只改渲染。载体实体本身停在原地不动，服务端的坐标、碰撞箱、AI 全都没变，只是客户端在画它的时候把它挪到了宿主身上。所以：

- 不会挤到玩家，也不会被玩家撞开
- 不占移动同步的带宽（不是每 tick teleport）
- 玩家用 F3 看实体列表，载体的坐标是召唤点的坐标，这是正常的

### 怎么用

召唤载体，再用 `summon` 的 `onsummonskill` 回调把它绑上去：

```yml
挂个杯子:
  Skills:
  - summon{type=杯子载体;amount=1;r=0;onsummonskill=杯子绑定} @Self

杯子绑定:
  Skills:
  - lanternbind{as=host;offset=0.4,1.3,0.2;rotate=true;duration=0}
```

`onsummonskill` 会把**刚召唤出来的那只实体**直接设成技能目标，caster 仍是召唤者。
所以绑定这一行不用写 targeter（沿用已有目标），也就没有半径搜索、没有时序窗口。

`as=host` 表示 caster 是宿主、target 是被挂上去的载体。

| 参数 | 说明 |
|---|---|
| `as` | `follower`（缺省）= caster 是载体、target 是宿主；`host` = 反过来 |
| `offset` | `x,y,z`。`rotate=true` 时是宿主朝向的局部坐标（x=右 y=上 z=前） |
| `rotate` | 缺省 `true`，载体整体跟着宿主转身 |
| `visible` | 缺省 `false`，`true` = 宿主转出视野也照渲染 |
| `duration` | 毫秒，缺省 `0` = 一直绑着，直到载体或宿主消失。特效载体自己会 `remove`，用 `0` 跟随它消失即可，不要再配一个和 `remove` 的 delay 掐着算的毫秒数——两个时钟总有先后 |

### 不要用命令加占位符

下面这种写法能跑，但很脆：

```yml
  - cmd{c="lantern bind <caster.uuid> <target.uuid> 0 1.5 0 true false"} @MIR{r=25;t=杯子载体}
```

`<target.uuid>` 只有在这一行**确实选中了实体**时才会被替换。targeter 一旦落空，
MythicMobs 会把整条命令按「无目标」再执行一次，占位符原样发到服务端，日志里出现：

```
[Lantern] 载体 UUID 格式不合法: <target.uuid>
```

`lanternbind` 机制拿到的是实体本身，不经字符串替换；targeter 落空时会明确打一条
「没有拿到实体目标」的警告，而不是绑一个错的或者什么都不做。

### 命令

```
/lantern bind <宿主UUID> <载体UUID> [x y z] [rotate] [visible] [持续毫秒]
/lantern unbind <载体UUID>
/lantern binds
```

**宿主在前，载体在后。** 命令主要用来手工排查（`/lantern binds` 看当前有哪些绑定），技能里请用上面的 `lanternbind` 机制。

| 参数 | 说明 |
|---|---|
| `x y z` | 偏移。`rotate=true` 时是宿主朝向的局部坐标：**x=右，y=上，z=前**；`rotate=false` 时是世界坐标 |
| `rotate` | 缺省 `true`。载体整体跟着宿主转身 |
| `visible` | 缺省 `false`。`true` = 宿主转出视野或被墙挡住时载体照样画出来 |
| `持续毫秒` | 缺省 `0` = 一直绑着，直到宿主或载体消失 |

举例：

```
lantern bind <玩家> <杯子> 0.4 1.3 0.2 true false      右手边、胸口高度、略微靠前
lantern bind <玩家> <光环> 0 2.2 0 false true          头顶正上方，不跟着转，常驻渲染
lantern bind <玩家> <符文> 0 1 0 true false 3000       挂 3 秒自动脱落
```

### 自动解绑

四种情况会自动解绑并通知客户端：

- 配的持续毫秒到了
- 载体被杀掉或清理掉
- 宿主死亡下线
- 宿主和载体不在同一个世界了

实体在未加载区块里查不到不算消失——连续三秒找不到才判定真的没了，避免宿主路过未加载区域时绑定莫名其妙断掉。

### 中途进来的玩家

绑定按宿主位置广播给附近的人。绑定时不在场的玩家，走进范围后一秒内会补收到；登录时也会补一遍现存绑定。走出范围再回来同理。

### 特效载体别留在地上

载体的 `remove` 是靠 MythicMobs 的 `delay` 调度的，那几百毫秒里停服、崩溃、`/mm reload`，载体就留下了。它无重力、无敌、Marker，打不死也点不中，原地永远闪特效。载体定义里加两行：

```yml
  Despawn: true
  Skills:
  - remove @Self ~onLoad
```

区块落盘再加载时 `~onLoad` 立即自毁。

### 用不了的情况

| 现象 | 原因 |
|---|---|
| 载体不显示 | 载体的自定义名没对上 `entityModels.yml` 的条目名——绑定只对 Lantern 模型实体生效 |
| 日志出现「载体 UUID 格式不合法: `<target.uuid>`」 | 用了 `cmd` + 占位符且 targeter 没命中，改用 `lanternbind` |
| 日志出现「lanternbind 没有拿到实体目标」 | targeter 没选中任何实体，检查 `onsummonskill` 或 targeter 的类型名 |
| 载体离玩家很远时消失 | 载体自己的区块没加载，客户端根本没这只实体。让载体跟宿主同点召唤 |

### 给其他插件用

```java
BindRegistry.bind(hostUuid, followerUuid, x, y, z, rotate, visible, durationMs);  // 返回失败原因，成功是 null
BindRegistry.unbind(followerUuid);
BindRegistry.followersOf(hostUuid);
```

事件：`LanternBindEvent`、`LanternUnbindEvent`（带解绑来源 command / api / expired / gone）。

两个方法都必须在主线程调用。

---

## 输入锁

### 能做什么

受击硬直、施法前摇、剧情演出期间不许乱跑，都靠它。

压制发生在客户端，玩家按了等于没按——不是服务端把人拽回来，所以没有橡皮筋。

### 动作集

| 动作 | 压住什么 |
|---|---|
| `move` | 前后左右 |
| `jump` | 跳跃 |
| `sneak` | 潜行 |
| `turn` | 视角（鼠标转不动） |
| `attack` | 攻击键 |
| `use` | 使用键 / 右键 |

逗号组合，例如 `move,jump`。

### MythicMobs

```yml
重击:
  Skills:
  - lanternanim{anim=重击;mode=once} @Self
  - lanternlock{actions=move,turn;time=1200} @Self
  - damage{a=20} @PIR{r=4}
```

```yml
击退硬直:
  Skills:
  - lanternlock{actions=move,jump;time=800} @Target
```

| 参数 | 说明 |
|---|---|
| `actions` / `a` | 动作集，缺省 `move` |
| `time` / `t` | 毫秒，缺省 `1000` |

时长的经验值 = 动画时长 + 一点余量。动画 20 tick（1 秒）就给 1100～1200。

目标必须是玩家。生物没有键盘输入可压，对生物用 `setAI` 之类的原版手段。

### 命令

```
/lantern lock <玩家> <动作,动作> <毫秒>
/lantern unlock <玩家>
```

### 多个来源叠加

受击硬直和施法前摇可能同时存在。两者各记一条，按并集生效，各自到期——先结束的那条不会把另一条一起解掉。

`/lantern unlock` 和玩家退服会清掉该玩家的全部锁。

### 和翻滚的关系

锁了 `move` 之后，翻滚读到的方向键是空的，会走 `none` 那一段。不用额外配置。

`attack` 锁和动作的 `suppress-vanilla-attack` 是两件事：前者是**按不下去**（挥击都不发生，连招也不会推进），后者是**打得出去但不结算伤害**（伤害交给动画时间线的伤害帧）。

---

## 动作交给服务端把关

默认情况下 `playerActions.yml` 里的动作是客户端按下即播，不等服务器——这是动作手感的根基，不要轻易改。

需要扣蓝、扣耐力、查冷却的招式，给它配上：

```yml
处决:
  key: "r"
  file: "animations/player/execute.animation.json"
  server-checked: true
  directions:
    none: "处决"
```

配了之后：客户端按键不再本地播放，改成把请求发给服务端 → 服务端抛 `LanternPlayerActionRequestEvent` → 没人拦就播出去。

没装任何附属插件时它照样能用，只是多一个来回的延迟。资源扣除这件事 Lantern 本身不管，它只提供「玩家想出这一招」这个信号和「准不准」这个开关：

```java
@EventHandler
public void onRequest(LanternPlayerActionRequestEvent event) {
    if (!myEnergyApi.tryConsume(event.getPlayer(), 30)) {
        event.setCancelled(true);   // 蓝不够，不播
    }
}
```

附属也可以不改配置，直接在 reload 前把某条动作标成服务端把关：

```java
PlayerActionGateway.markServerChecked("处决");
```

代价要说清楚：`server-checked: true` 的动作会有一个 RTT 的出招延迟，翻滚闪避这类吃手感的动作不要开。

---

## 相关文档

- [玩家动作](player-actions.md) —— 翻滚、连招、动作组
- [生物模型](entity-models.md) —— 载体的模型怎么配
- [MythicMobs 联动](mythic-mechanics.md) —— 全部机制一览
