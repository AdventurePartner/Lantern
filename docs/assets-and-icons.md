# 图片文字、物品图标和资源包

本页说明三个和资源有关的文件：

| 文件 | 用途 |
|---|---|
| `characters.yml` | 用一个文字显示一张小图片 |
| `itemIcons.yml` | 让带指定数字的物品显示成指定图标 |
| `config.yml` | 填写加密资源包密码 |

## 图片文字

图片文字写在 `plugins/Lantern/characters.yml`。

```yml
字符例子:
  character: "帅"
  texture: "textures/example/icon.png"
  width: 8
  height: 8
  wide: 9
  offset-x: 0
  offset-y: 0
```

| 设置名 | 怎么填 |
|---|---|
| `character` | 要替换成图片的文字，只取第一个字 |
| `texture` | 图片位置；可以写资源包文件，也可以写图片网址 |
| `width` | 图片宽度 |
| `height` | 图片高度 |
| `wide` | 这张图片占用的文字宽度 |
| `offset-x` | 图片水平偏移，可填小数和负数，默认 `0`；正值向右 |
| `offset-y` | 图片垂直偏移，可填小数和负数，默认 `0`；正值向下 |

图片默认画在该字符起点、文字行顶部。想让图片在自己的占位宽度里水平居中，可以把 `offset-x` 填成 `(wide - width) / 2`；想让图片在 TAB 列表行里垂直居中，用小的 `offset-y` 微调（例如 `0.5` 或 `1`）。偏移只移动图片本身，不改变文字占位宽度。

例如配置里写 `character: "帅"` 后，游戏里显示这个字时，玩家会看到对应图片。

建议选择平时不会被聊天误用的字符，避免普通聊天被替换成图片。

资源包文件示例：

```text
assets/lantern/textures/example/icon.png
```

配置里写：

```yml
texture: "textures/example/icon.png"
```

## 物品图标

物品图标写在 `plugins/Lantern/itemIcons.yml`。

```yml
1:
  identifier: "custom_wrapper"
  texture: "lantern:item/custom_wrapper"
  type: "generated"
```

最上面的 `1` 是物品上的模型数字。服务器里有同样数字的物品，会在玩家客户端显示成这里设置的图标。

| 设置名 | 怎么填 |
|---|---|
| 数字，例如 `1` | 物品上的模型数字 |
| `identifier` | 图标名字，建议只用小写字母、数字和下划线 |
| `texture` | 资源包里的贴图位置 |
| `type` | `generated` 普通平面图标，`handheld` 手持物品样式；不填时为 `generated` |

如果写：

```yml
texture: "lantern:item/custom_wrapper"
```

资源包里应有：

```text
assets/lantern/textures/item/custom_wrapper.png
```

图标由玩家客户端直接读取这张贴图生成，不需要重新加载资源包；贴图读好之前，物品先显示原来的样子。动画贴图（竖排多帧）只显示第一帧。

## 加密资源包

如果你把资源包做成带密码的 zip，可以在 `plugins/Lantern/config.yml` 里填写密码：

```yml
resource-pack-key: "这里填写密码"
```

玩家客户端会在自己的 `resourcePacks` 文件夹里查找 zip 文件，并用这个密码读取其中 `assets/` 里的资源。

注意：

- `resource-pack-key` 为空时，不会读取加密 zip。
- zip 文件需要已经在玩家客户端的 `resourcePacks` 文件夹里。
- zip 里资源仍然要放在 `assets/命名/文件位置` 这种结构下。
- 普通未加密 zip 会被跳过。

### 进服时的加载方式

玩家进服时，加密资源包在后台读取，不会出现加载界面，也不用等资源包读完才能进世界。读取完成前：

| 内容 | 显示 |
|---|---|
| 自定义生物模型 | 原版生物 |
| 玩家外观 | 原版玩家 |
| 已放置的自定义方块 | 暂不显示 |
| 背包里的自定义方块物品 | 原版木桶 |
| 物品图标、手持外观物品 | 原来的物品 |
| 界面图片、图片文字、世界图片 | 暂不显示 |

读取完成后会自动换成自定义内容。同一次游戏里重新进入同一个服务器、且 zip 没有换过时，不会再重新读取。

后台加载只覆盖 Lantern 自己用到的资源。下面这些内容放进加密包后，需要完整重新加载资源包才会生效：

- 声音（`sounds.json`）、语言文件、字体
- 方块状态、原版物品和方块的模型或贴图覆盖、界面图标覆盖

如果加密包里有这些内容，在 `config.yml` 打开：

```yml
client-full-reload: true
```

打开后，玩家每次进服和执行 `/lantern reload` 时都会完整重新加载一次资源包（和按 F3+T 一样，会出现加载界面）。

## 修改后生效

1. 保存对应文件。
2. 执行 `/lantern reload`。
3. 如果改了资源包内容，玩家客户端会在后台重新读取，读取完成后自动生效；玩家替换了 zip 文件时，重新进入服务器即可。

## 常见问题

| 现象 | 检查 |
|---|---|
| 图片文字没显示 | `character` 是否只写了一个想替换的字，`texture` 是否正确 |
| 物品图标没变 | 物品上的模型数字是否和 `itemIcons.yml` 最上面的数字一致 |
| 图标显示紫黑方块 | 贴图是否放在资源包正确位置 |
| 加密资源包没读取 | 密码是否正确，zip 是否在玩家客户端的 `resourcePacks` 文件夹里 |
| 进服后一小段时间显示原版样子 | 正常，资源还在后台读取，读完会自动换成自定义内容 |
| 加密包里的声音或语言没生效 | 这类资源需要完整重新加载，见上方 `client-full-reload` |
