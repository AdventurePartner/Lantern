# 自定义方块

自定义方块写在 `plugins/Lantern/blockModels.yml`。

玩家拿到的是一个带特殊外观的木桶物品；放下后，玩家看到的是你配置的模型，原木桶界面会被阻止打开。

## 添加一个方块

```yml
ruby_ore:
  custom_variation: 1
  custom_model_data: 10001
  display-name: "&cRuby Ore"
  geo: "geo/block/ruby_ore.geo.json"
  texture: "textures/block/ruby_ore.png"
  animation: "animations/block/ruby_ore.animation.json"
  scale: 1.0
  idle-animation: "idle"
  block-scale: 1.0
  item-offset-x: 0.0
  item-offset-y: 0.0
  item-offset-z: 0.0
  hardness: 1.5
  tool-type: "pickaxe"
  break-sound: "block.stone.break"
```

最上面的 `ruby_ore` 是方块名，指令会用到它。

| 设置名 | 怎么填 |
|---|---|
| `custom_variation` | 每个方块独有的数字，不能重复，建议从 1 开始递增 |
| `custom_model_data` | 物品外观数字，资源包里的物品也要用同一个数字 |
| `display-name` | 物品显示名，支持 `&c` 这类颜色写法 |
| `geo` | 模型文件位置，从 `assets/lantern/` 后面开始写 |
| `texture` | 贴图位置；可以写资源包文件，也可以写图片网址 |
| `animation` | 动画文件位置；没有动画时可以留空 |
| `scale` | 模型整体大小 |
| `idle-animation` | 默认循环播放的动画名 |
| `block-scale` | 放在世界里时的大小 |
| `item-offset-x` | 在物品栏里左右移动 |
| `item-offset-y` | 在物品栏里上下移动 |
| `item-offset-z` | 在物品栏里前后移动 |
| `hardness` | 挖掘耗时，越大越慢；小于 0 表示不能正常破坏 |
| `tool-type` | 哪类工具挖得更快：`pickaxe`、`axe`、`shovel`；不需要就留空 |
| `break-sound` | 破坏时播放的声音；不填则使用木桶原本的声音 |

## 给玩家方块

```text
/lantern give ruby_ore
/lantern give ruby_ore Steve 16
```

- 第一条给自己 1 个 `ruby_ore`。
- 第二条给 Steve 16 个 `ruby_ore`。
- 数量会限制在 1 到 64。

## 放置和破坏

- 玩家放下这个物品后，服务器会记住它的位置。
- 玩家重新进入服务器时，客户端会再次收到这些位置。
- 破坏后，位置记录会移除，并通知附近玩家不再显示该模型。
- 服务器会定时保存已放置方块的位置，关服时也会保存。

## 资源文件放哪里

如果 `geo` 写成：

```yml
geo: "geo/block/ruby_ore.geo.json"
```

那么资源包里应有：

```text
assets/lantern/geo/block/ruby_ore.geo.json
```

同理：

```yml
texture: "textures/block/ruby_ore.png"
animation: "animations/block/ruby_ore.animation.json"
```

对应：

```text
assets/lantern/textures/block/ruby_ore.png
assets/lantern/animations/block/ruby_ore.animation.json
```

## 修改后生效

1. 保存 `blockModels.yml`。
2. 执行 `/lantern reload`。
3. 如果换了模型或贴图，让玩家等待资源刷新；仍未显示时重新进入服务器。

## 常见问题

| 现象 | 检查 |
|---|---|
| 指令提示找不到方块 | 方块名是否和 `blockModels.yml` 最上面的名字一致 |
| 放下后还是木桶 | `custom_model_data` 是否和获得的物品一致，玩家客户端是否安装 Lantern 模组 |
| 模型不显示 | `geo`、`texture`、`animation` 的文件位置是否正确 |
| 挖掘速度不对 | `hardness` 和 `tool-type` 是否符合预期 |
