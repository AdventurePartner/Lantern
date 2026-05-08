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
```

| 设置名 | 怎么填 |
|---|---|
| `character` | 要替换成图片的文字，只取第一个字 |
| `texture` | 图片位置；可以写资源包文件，也可以写图片网址 |
| `width` | 图片宽度 |
| `height` | 图片高度 |
| `wide` | 这张图片占用的文字宽度 |

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

## 修改后生效

1. 保存对应文件。
2. 执行 `/lantern reload`。
3. 如果改了资源包内容，玩家可能需要等待资源刷新；仍未显示时重新进入服务器。

## 常见问题

| 现象 | 检查 |
|---|---|
| 图片文字没显示 | `character` 是否只写了一个想替换的字，`texture` 是否正确 |
| 物品图标没变 | 物品上的模型数字是否和 `itemIcons.yml` 最上面的数字一致 |
| 图标显示紫黑方块 | 贴图是否放在资源包正确位置 |
| 加密资源包没读取 | 密码是否正确，zip 是否在玩家客户端的 `resourcePacks` 文件夹里 |
