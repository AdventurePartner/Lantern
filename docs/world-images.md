# 世界图片

世界图片写在 `plugins/Lantern/worldImages.yml`。

世界图片是在游戏世界里画的贴图面片或文字：可以跟随某个玩家、跟随某个实体，
也可以钉在固定坐标；支持始终面向观察者或固定朝向（yaw/pitch）、缩放、
第一人称是否可见、穿墙显示，以及关键帧动画。内置的三个动画开箱即用，
服主也可以在同一个文件里写自己的动画。

修改配置后在游戏里执行 `/lantern reload`，会推送给所有在线玩家。

## 内置应用：伤害数值

`damage` 段不用做任何事就有效果：所有对生物的最终伤害会在受害者头顶
弹出文字数字（弹出放大、上浮、淡出）。不想用就把 `enabled` 改成 `false`。

```yml
damage:
  enabled: true
  format: "%.0f"
  color: "#FF5555"
  min-damage: 0.5
  merge-ms: 250
  radius: 48
  first-person: false
  scale: 1.0
  size: 0.4
  animation: damage_pop
```

| 设置名 | 怎么填 |
|---|---|
| `enabled` | 总开关 |
| `format` | 数值格式，Java 格式串。`"%.0f"` 是整数，`"%.1f"` 保留一位小数 |
| `color` | 文字颜色，`#RRGGBB` 或 `#AARRGGBB` |
| `min-damage` | 低于这个值的伤害不显示，用来滤掉火焰余烬之类的碎数 |
| `merge-ms` | 同一实体的刷新间隔（毫秒）。窗口内的后续伤害不另起数字，跨过窗口的下一跳把数字刷新成最新值并重播动画；调大可以进一步防跳字刷屏 |
| `radius` | 数字下发给受害者周围多少格内的玩家，格外的玩家看不到 |
| `first-person` | 自己受伤的数字在自己第一人称视角下是否显示。`false` 是不挡自己视线 |
| `scale` | 附加缩放 |
| `size` | 字符行高，单位格。0.4 大约是名牌文字的一半高 |
| `animation` | 用的动画名，填内置三个之一，或 `animations` 段里自定义的名字 |

文字用的是客户端的原版字体。服务器资源包如果替换了字体，伤害数字会跟着变。

## 图片模板（images）

`images` 段登记模板，供 `/lantern image spawn` 调试命令（以及后续的联动入口）
按名字引用。条目写法：

```yml
images:
  soul_ring:
    texture: "image/soul_ring.png"
    size: 1.0
    scale: 1.0
    offset: [0, 2.0, 0]
    facing: billboard
    rot: [0, 0]
    first-person: true
    see-through: false
```

最上面的 `soul_ring` 是模板名。也可以写 `type: text` 加 `text: "内容"` 做纯文字模板。

| 设置名 | 怎么填 |
|---|---|
| `type` | `texture`（贴图面片，默认）或 `text`（原版字体文字） |
| `texture` | `texture` 型必填，贴图位置，从 `assets/lantern/` 后面开始写，或填 `http(s)://` 直链 |
| `text` | `text` 型必填，文字内容 |
| `color` | `text` 型的文字颜色，`#RRGGBB` 或 `#AARRGGBB`，默认白色 |
| `size` | 尺寸，单位格。`texture` 型是面片边长（默认 1.0），`text` 型是字符行高（默认 0.4） |
| `scale` | 附加缩放，最终尺寸 = size 乘 scale 再乘动画 scale，默认 1.0 |
| `offset` | 相对绑定点的基础偏移 `[x, y, z]` |
| `rot` | 固定朝向 `[yaw, pitch]`（度），只对 `facing: fixed` 生效，语义与实体 yaw/pitch 相同 |
| `facing` | `billboard`（每帧面向观察者，默认）或 `fixed`（固定朝向，配 `rot`） |
| `first-person` | 绑定目标是自己时，第一人称视角下是否显示，默认 `true` |
| `see-through` | `true` 时穿墙可见（与名牌穿墙同一渲染方式），默认 `false` |

贴图资产的到达方式与外观、方块模型一致：放进客户端加密资源包或
`resourcepacks/LanternPackLocal/` 本地包的 `assets/lantern/` 下；
`http(s)://` 直链由客户端异步下载，动图 GIF 会自动逐帧播放。

## 自定义动画（animations）

`animations` 段写服主自己的动画，名字与内置同名时会覆盖内置版本。
一条动画是一个关键帧时间轴，由若干条轨道组成：

```yml
animations:
  my_float:
    duration: 40
    loop: false
    tracks:
      offset-y:
        - {t: 0, v: 0, ease: out}
        - {t: 40, v: 1.5}
      alpha:
        - {t: 30, v: 1.0}
        - {t: 40, v: 0.0, ease: in}
      scale:
        - {t: 0, v: 0.5}
        - {t: 5, v: 1.2, ease: out}
        - {t: 8, v: 1.0}
```

| 设置名 | 怎么填 |
|---|---|
| `duration` | 时长，单位 tick（1 秒 = 20 tick），最小 1 |
| `loop` | `true` 时循环播放（实例需要带 age 定寿命）；默认 `false`，播完实例即消失 |
| `tracks` | 轨道表，每条是一个关键帧列表 |

| 轨道名 | 控制什么 |
|---|---|
| `offset-x` / `offset-y` / `offset-z` | 附加偏移，叠在实例 offset 上，单位格 |
| `scale` | 尺寸倍率，乘在实例 scale 上 |
| `alpha` | 透明度，1.0 不透明、0.0 全透明 |
| `rot-yaw` / `rot-pitch` | 附加旋转（度）。billboard 朝向下就是"面向观察者的同时自旋" |

关键帧 `{t: 0, v: 0, ease: out}`：`t` 是时间（tick），`v` 是数值，`ease` 写在
目标帧上、描述以什么方式到达这一帧。缓动有 `linear`（匀速，默认）、`in`
（起步慢）、`out`（收尾慢）、`in_out`（两端慢）。首帧之前取首帧值，
末帧之后取末帧值。轨道里至少要有一条帧，`t` 相同的帧取后写的。

内置动画有三个，名字可以直接填进 `damage.animation` 或引用覆盖：

| 名字 | 时长 | 效果 |
|---|---|---|
| `damage_pop` | 20t | 先放大过冲再回稳，整体上浮，最后 5t 淡出（伤害数字默认） |
| `float_up` | 40t | 上浮 1.5 格，尾段淡出 |
| `drop_spin` | 30t | 从头顶 1.8 格降到绑定点并自旋两圈（魂环式入场） |

## 调试命令

`/lantern image` 需要 `lantern.image` 权限（默认 OP），生成类子命令只发给
执行者本人，不影响其他玩家：

| 命令 | 作用 |
|---|---|
| `/lantern image text <内容> [缩放]` | 在自己头顶 1 格生成文字图片，3 秒后消失 |
| `/lantern image texture <贴图路径> [边长]` | 在自己头顶生成贴图图片，3 秒后消失 |
| `/lantern image spawn <模板名>` | 按 `images` 段的模板生成，3 秒后消失 |
| `/lantern image clear` | 清空自己客户端上的全部世界图片 |

验收新配置的推荐顺序：先 `/lantern image text 测试 1.0` 确认渲染与朝向，
再放开 `animations` 里的示例动画用 `damage.animation` 引用，
`/lantern reload` 后打怪看效果。
