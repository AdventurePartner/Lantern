# Lantern 相机系统操作指南

> 面向服管与玩家视角的完整操作手册：越肩视角、演出指令（锁定/震屏/FOV/偏移）、打点运镜与动作轨道编排。
> 版本要求：服务端 LanternPlugin + 客户端 Lantern mod（NeoForge 1.21.10）；纯客户端安装时相机功能静默不生效（无服务端下发）。

---

## 1. 玩家操作（越肩视角）

### 1.1 F5 四视角循环

越肩是 F5 循环里的**第四视角**，不覆盖原版任何视角：

```
一人称 → 越肩 → 第三人称背后（原版） → 第三人称正面（原版） → 一人称 ...
```

- 越肩激活时相机从右肩看向准星方向，**带准星**，攻击/挖矿/交互与屏幕中心一致（射程不变）；
- 第三人称背后是原版形态（居中、无准星），随时可切回；
- 旁观模式下 F5 走原版三视角循环；
- 越肩开关状态不跨服保留（重进服务端恢复默认），原版视角选择正常保存在 options.txt。

### 1.2 微调键位（默认值，可在 camera.yml 改）

| 按键 | 效果 | 步进 |
|---|---|---|
| ← / → | 画面左移 / 右移（相机反向平移） | 0.05 格 |
| ↑ / ↓ | 画面上移 / 下移（取景抬高 / 压低） | 0.05 格 |
| Shift + ↑ / Shift + ↓ | 拉远 / 拉近 | 0.25 格 |
| O | 换肩（左肩 / 右肩互换） | — |

- 方向语义 = **画面跟随按键**：按 ← 画面内容向左滑，人物在画面中右移；
- 调整结果实时同步服务端会话，掉线重进后恢复默认值（V1 不持久化）；
- 打开任何 GUI（聊天栏、背包）时微调键不触发；
- 调整范围受 camera.yml 限幅约束（见 §2）。

---

## 2. 服务端配置（camera.yml）

位置 `plugins/Lantern/camera.yml`，`/lantern reload` 生效：

```yaml
shoulder:
  enabled: true            # 全服总开关；false 时调节键不下发、F5 完全原版
  default-offset-x: 0.75   # 默认横向偏移，正 = 右肩
  default-offset-y: 0.0    # 默认垂直偏移，正 = 抬升
  default-distance: 4.0    # 默认相机后向距离（格），4.0 与原版第三人称同距
  max-offset-x: 1.5        # |offset-x| 上限（按键微调与 /lantern cam set 都受限）
  max-offset-y: 1.0        # |offset-y| 上限
  min-distance: 1.5        # 距离下限
  max-distance: 8.0        # 距离上限
  adjust:
    enabled: true          # 是否允许玩家按键微调
    swap-key: "o"          # 换肩键
    left-key: "left"       # 画面左移
    right-key: "right"     # 画面右移
    up-key: "up"           # 画面上移
    down-key: "down"       # 画面下移
    farther-key: "shift+up"   # 拉远
    closer-key: "shift+down"  # 拉近
    step-offset-x: 0.05    # 横移步进
    step-offset-y: 0.05    # 垂直步进
    step-distance: 0.25    # 距离步进
```

要点：

- 键位 spec 语法与 keys.yml 一致（单字符 / f1-f25 / up/left 等命名键 / ctrl+ shift+ alt+ 修饰前缀）；
- 相机键与 keys.yml 键位冲突时**相机优先**（同名命令绑定不触发），启动日志会告警冲突列表；
- `enabled: false` 改完 reload：在线玩家立即回原版视角（无需重进）。

---

## 3. 命令参考

统一入口 `/lantern help`（玩家端每条命令可点击直接填入聊天框）。

### 3.1 越肩管理（/lantern cam）

| 命令 | 说明 |
|---|---|
| `/lantern cam info [玩家]` | 查看越肩状态与当前参数 |
| `/lantern cam toggle [玩家]` | 开/关目标玩家的越肩（受全服开关约束） |
| `/lantern cam set <offset-x\|offset-y\|distance> <值> [玩家]` | 设置单项参数（受限幅约束） |
| `/lantern cam reset [玩家]` | 恢复 camera.yml 默认参数 |

### 3.2 演出指令（/lantern cam，作用于执行者，可带 [玩家] 指定目标）

| 命令 | 说明 |
|---|---|
| `/lantern cam lock <x> <y> <z> [平滑秒] [持续秒] [sync] [玩家]` | 视角锁定看向坐标；持续秒 0 = 直到 unlock；sync = 同时写回真实朝向（他人可见转头） |
| `/lantern cam lockentity [平滑秒] [持续秒] [sync] [玩家]` | 视角跟随 8 格内最近的命名实体（可跟移动目标） |
| `/lantern cam unlock [玩家]` | 解除锁定（无痕恢复） |
| `/lantern cam shake [幅度] [频率] [时长秒] [玩家]` | 震屏；幅度单位格（≤0.5），默认线性衰减 |
| `/lantern cam fov [度数] [过渡秒] [玩家]` | 临时 FOV；**不带度数 = 恢复**；不改玩家设置滑条 |
| `/lantern cam offset <pitch> <yaw> [roll] [过渡秒] [玩家]` | 朝向偏移叠加（度） |
| `/lantern cam path <路径ID> [玩家] [速度]` | 播放打点运镜（见 §4） |
| `/lantern cam watch <x> <y> <z> <看向x> <看向y> <看向z> [持续秒] [平滑秒] [玩家]` | 相机飞到观察点看向目标点，到期自动回程 |
| `/lantern cam clear [玩家]` | 清除全部演出状态（lock/shake/fov/offset/watch；不影响越肩设置） |

示例：

```
/lantern cam lock 100 70 -200 0.5 5          # 0.5 秒平滑看向该坐标，持续 5 秒后自动恢复
/lantern cam shake 0.4 8 1                   # 震屏 1 秒
/lantern cam fov 110 0.5                     # 半秒过渡到 110 度广角
/lantern cam fov                             # 平滑恢复玩家自己的 FOV
/lantern cam clear                           # 一键清空所有演出效果
```

### 3.3 打点运镜（/lantern campath）

在机位间录制相机路径，三种运动方式逐段可选（含帧动画跳切）：

| 命令 | 说明 |
|---|---|
| `/lantern campath start <路径ID>` | 开始/继续编辑（已有存档自动载入） |
| `/lantern campath add [t] [linear\|smooth\|hold] [fov]` | 在当前站位打关键帧；数字按顺序是 t、fov，单词是插值方式；t 缺省 = 上一帧 +20 tick |
| `/lantern campath undo` / `clear` | 撤销上一帧 / 清空 |
| `/lantern campath list` | 列出当前关键帧 |
| `/lantern campath preview` | 粒子预览：火苗 = 关键帧，青色线 = 段（直线近似，曲率以 play 为准） |
| `/lantern campath play [速度]` | 会话内预演（不必先保存） |
| `/lantern campath save` | 保存到 `plugins/Lantern/cameraPaths/<路径ID>.yml` |
| `/lantern campath stop` | 结束编辑会话 |

**完整工作流示例**（Boss 登场运镜）：

```
/lantern campath start boss_intro
# 走到远景机位，看向 Boss：
/lantern campath add 0 smooth
# 绕到侧面近景：
/lantern campath add 60 smooth
# 贴脸特写并变焦：
/lantern campath add 100 hold 90
/lantern campath preview        # 看一眼路线
/lantern campath play           # 预演
/lantern campath save
/lantern cam path boss_intro    # 正式播放（可带 [玩家] [速度]）
```

**三种插值（每个关键帧声明"上一帧 → 本帧"怎么走）**：

| interp | 行为 | 用途 |
|---|---|---|
| `smooth` | catmullrom 曲线，位置/角度/FOV 平滑划过 | 常规运镜 |
| `linear` | 直线匀速，角度走最短路径（跨 ±180° 不翻转） | 硬朗的直线路线 |
| `hold` | 保持上一机位，到时刻**瞬间跳切**到本帧 | 场景帧转换、蒙太奇分镜 |

- 关键帧写 `fov` 才参与 FOV 轨（随所在段一起插值）；
- 播放开始有 5 tick 淡入（从当前视角滑进首帧，任意位置播放都不瞬跳）；
- 播完或 `/lantern cam clear` 打断都有 250ms 回程过渡；
- 运镜期间可以打字、移动（不改输入语义）；运镜中可叠加震屏；
- 存档是明文 yml（speed/loop/keyframes），可手工精修；tab 补全会列出已存档路径 ID。

---

## 4. 动作轨道编排（动画轨道里直接配相机）

动作轨道的 actions 支持 camera 节点——动画播到指定 tick 时，对**该实体半径内同世界玩家**广播相机指令，不需要写任何 MythicMobs 技能：

```yaml
树妖:
  踩踏:
    actions:
      - { at: 14, sound: {s: entity.ravager.attack, v: 2.0, p: 0.65},
          camera: {action: shake, amplitude: 0.35, frequency: 9, duration: 0.7, radius: 16} }
  登场:
    actions:
      - { at: 0, camera: {action: path, id: boss_intro, speed: 1, radius: 24} }
      - { at: 120, camera: {action: clear, radius: 24} }
```

- `radius`（默认 16）以**播放动画的实体**为圆心；其余字段与 §3.2 演出指令的 packet 字段同名直通（amplitude/frequency/duration/decay/value/transition/pitch/yaw/roll/smooth/sync/x/y/z/target）；
- `action: path` 用 `id` 引用 cameraPaths 存档（缺档写日志告警，不影响轨道其余动作）；
- 同一轨道可混排 sound/command/mm-skill/camera；轨道被打断时未到点的相机动作一并取消。

---

## 5. 行为边界与已知限制

| 项 | 说明 |
|---|---|
| 射程 | 越肩/演出只改射线**起点与方向**，交互距离仍按原版（服务器可验证的上限不变） |
| 背墙收缩 | 相机后向距离继承原版 8 点防穿墙收缩；**侧向偏移无收缩**（贴墙横移瞬间可能短暂入墙，与主流越肩方案一致） |
| 睡觉/开镜 | 沉睡时机位不受演出覆写；望远镜开镜时 FOV 指令自动让位 |
| 旁观模式 | F5 走原版循环；越肩残留状态不生效 |
| 持久化 | 越肩开关与微调值不跨服保存（会话态）；原版视角选择正常保存 |
| 断线 | 演出状态（lock/shake/fov/offset/watch）断线即清，不跨服残留 |
| 权限 | 当前所有子命令无权限节点限制（后续版本计划为演出类子命令加权限开关） |
| 平台 | 当前仅 NeoForge 1.21.10 客户端实现；其余平台移植后行为一致 |
