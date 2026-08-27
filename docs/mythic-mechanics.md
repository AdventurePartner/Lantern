# Lantern MythicMobs 机制指南

> Lantern 向 MythicMobs 注册的三个自定义机制：`lanternanim`（动画播控）、`lanternvar`（molang 变量）、`lanterncam`（相机演出）。
> 前置：服务端安装 MythicMobs 5.x（softdepend，未安装时插件正常加载、机制不可用）。启动日志出现
> `MythicMobs integration enabled (mechanics: lanternanim / lanim, lanternvar / lvar, lanterncam)` 即注册成功。

通用约定：

- 三个机制都是**目标实体机制**（ITargetedEntitySkill），目标由 MythicMobs targeter 决定——`@Self`、`@Trigger`、`@PlayersInRadius{r=10}`、`@NearestPlayer{r=20}` 等全部可用；
- `lanternanim`/`lanternvar` 作用于**任意 LivingEntity**；`lanterncam` 仅对**玩家**生效（非玩家目标自动跳过该目标）；
- 技能可能在异步线程执行，机制内部已调度回主线程；
- 参数写法 `参数=值`，别名见各表；未写的参数用默认值。

---

## 1. lanternanim（动画播控，别名 lanim）

对目标实体播放 / 停止 Lantern 动画（模型按 entityModels.yml 的 `name` 匹配；未匹配模型的实体会忽略该包）。

```yaml
skills:
  - skill{s=踩踏} @Self
  # 或直接内联：
  - lanternanim{anim=踩踏;t=3} @Self
```

| 参数 | 别名 | 默认 | 说明 |
|---|---|---|---|
| `anim` | `a` | （必填） | 动画名（.animation.json 里的动画 clips 名） |
| `time` | `t` | 5 | 过渡 tick（0 = 立即切入；一次性动作建议 2-3） |
| `mode` | `m` | loop | `loop` 循环直到停止；`once` 播一次自动回落并上报 finish |
| `speed` | `sp` | 1.0 | 播放速度倍率（>0.01） |
| `remove` | `r` | false | true = 停止该动画（stop 语义），此时 anim 为要停止的动画名 |

行为要点：

- **播控优先级**：播控动画覆盖行走/姿态链；死亡动画期间（濒死保护）其它 lanternanim 会被忽略，防止踩踏指令顶掉 die；
- `once` 播完客户端上报 finish → 可配合轨道 `next` 链式接续；
- 配合轨道：动画本身可以带 actions（音效/命令/mm-skill/camera），见动作轨道文档。

示例——二段连击（once 链）：

```yaml
快速挥击:
  cooldown: 3
  skills:
    - lanternanim{a=挥击1;m=once;t=2} @Self
```

（挥击1 的轨道 `next: 挥击2`，播完自动接。）

---

## 2. lanternvar（molang 变量，别名 lvar)

对目标实体设置/删除 molang 变量，客户端动画表达式可实时读取（`variable.名字`）。

```yaml
skills:
  - lanternvar{k=amplitude;v=0.5} @Self
  - lanternvar{k=rage;v=math.sin(query.time_stamp/100)} @Self
  - lanternvar{k=amplitude;v=} @Self   # 空值 = 删除该变量
```

| 参数 | 别名 | 默认 | 说明 |
|---|---|---|---|
| `k` | `key` | （必填） | 变量名（裸名，客户端按 `variable.名字` 引用） |
| `v` | `value` | （必填） | 数字字面量 **或** molang 表达式（表达式每次采样时求值，可引用 `query.*`）；空串 = 删除 |

- 变量按实体隔离，广播给所有可见玩家（packet 17）；
- 典型用法：动画关键帧写 `math.sin(query.anim_time * 360) * variable.amplitude`，Boss 技能用 lanternvar 调 amplitude 实现同一动画不同幅度。

---

## 3. lanterncam（相机演出）

对目标玩家下发相机指令。**目标选择交给 MythicMobs targeter**，常用写法：

| 写法 | 目标 |
|---|---|
| `@Trigger` | 触发者本人（被打/进入范围的玩家） |
| `@PlayersInRadius{r=10}` | 施法者周围 10 格玩家 |
| `@PlayerTargetsInRadius{r=16}` | 每个目标周围的玩家（群怪分摊） |
| `@NearestPlayer{r=20}` / `@PlayersOnServer` | 就近玩家 / 全服 |

### 3.1 通用参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `action` (`a`) | shake | 见下表 |
| `duration` (`d`) | 0 | lock/watch 的持续秒；0 = 直到 unlock/clear |
| `smooth` (`s`) | 0.3 | lock/watch 的平滑过渡秒 |
| `sync` | false | lock 时同时写回玩家真实朝向（他人可见转头） |
| `x`/`y`/`z` | 0 | 坐标；lock/watch 默认**相对目标实体**，`relative=false` 时为世界绝对坐标 |
| `amplitude` (`amp`) | 0.3 | shake 幅度（格，服务端钳 ≤0.5） |
| `frequency` (`freq`) | 8 | shake 频率（Hz，钳 0.1-30） |
| `shake-duration` (`sdur`) | 0.5 | shake 时长秒 |
| `decay` | true | shake 是否线性衰减 |
| `value` (`v`) | （无） | fov 度数；**不写 = 恢复玩家 FOV** |
| `transition` (`t`) | 1.0 | fov 过渡秒 |
| `pitch` (`p`) / `yaw` / `roll` (`r`) | 0 | offset 朝向偏移（度） |
| `id` | （必填） | path 引用的运镜存档（plugins/Lantern/cameraPaths/） |
| `speed` | 1.0 | path 播放倍速 |
| `lookentity` (`look`) | true | watch 时看向目标实体；false 时看向目标 + lookx/looky/lookz 偏移点 |

### 3.2 action 一览

```yaml
# 震屏（默认动作，可省 action=shake）
- lanterncam{amplitude=0.4;sdur=0.8} @PlayersInRadius{r=10}

# 锁定视角看向目标上方 2 格（相对坐标），3 秒后自动恢复，他人可见转头
- lanterncam{a=lock;y=2;sync=true;d=3} @Trigger

# 锁定到绝对坐标
- lanterncam{a=lock;relative=false;x=100;y=70;z=-200;d=5} @Trigger

# 视角跟随目标实体（审讯/对视演出）
- lanterncam{a=lockentity;s=0.5;d=4;sync=true} @Trigger

# 解除锁定
- lanterncam{a=unlock} @Trigger

# 临时广角 0.5 秒过渡（演出发力感）；不带 value 则恢复
- lanterncam{a=fov;v=110;t=0.5} @Trigger

# 朝向歪斜叠加
- lanterncam{a=offset;pitch=10;yaw=15;t=1} @Trigger

# 播放打点运镜（Boss 登场）
- lanterncam{a=path;id=boss_intro;speed=1} @PlayersInRadius{r=16}

# 相机飞到目标旁 (6,3,6) 观察目标 3 秒后自动回程
- lanterncam{a=watch;x=6;y=3;z=6;d=3} @Trigger

# 清空该玩家全部演出状态（不动越肩设置）
- lanterncam{a=clear} @Trigger
```

### 3.3 编排示例：Boss 技能链混排

```yaml
怒吼:
  cooldown: 12
  skills:
    - lanternanim{a=咆哮;m=once;t=3} @Self
    - lanterncam{a=fov;v=105;t=0.6} @PlayersInRadius{r=14}
    - lanterncam{a=lock;y=1.5;d=2.5;sync=true} @PlayersInRadius{r=14}
    - delay{t=10}
    - lanterncam{amp=0.35;freq=9;sdur=0.7} @PlayersInRadius{r=14}
```

（动画轨道负责音效/命中判定，lanterncam 负责玩家观感，两者按各自时间轴叠加。）

---

## 4. 机制 vs 动作轨道，怎么选

| 场景 | 推荐 |
|---|---|
| 动画播到固定 tick 必然发生（音效、震屏、命令） | 动作轨道 camera/sound/mm-skill 节点——配在动画上，任何触发源都带效果 |
| 条件/时机由 MM 技能逻辑决定（冷却、血量、随机、targeter 圈定） | lanterncam / lanternanim / lanternvar 机制 |
| 同一个震屏既要在动画轨道又在技能里 | 两边都写没冲突，效果会叠加（注意幅度别叠加过头） |

## 5. 排查

- 启动日志无 `MythicMobs integration enabled` → MM 未装或加载顺序问题（装了 MM 后首次启动需再重启一次）；
- `lanternanim` 无反应 → 实体自定义名与 entityModels.yml `name` 不一致（去色后必须完全相同）；
- `lanterncam{a=path}` 无反应 → 存档不存在或关键帧不足 2 个，看服务端日志 warning；
- 表达式不生效 → 变量名区分大小写；表达式在客户端求值，确认客户端 mod 已更新到对应版本。
