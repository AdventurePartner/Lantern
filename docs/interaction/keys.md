# 按键绑定

按键绑定写在 `plugins/Lantern/keys.yml`。

玩家按下或松开指定按键后，服务器可以让玩家或后台执行指令。

## 基本示例

```yml
O:
  press: true
  in-gui: false
  commands:
    - "console:say %player% KeyPress"
```

最上面的 `O` 是按键名。

| 设置名 | 怎么填 |
|---|---|
| `press` | `true` 表示按下时触发；`false` 表示松开时触发 |
| `in-gui` | `true` 表示玩家打开聊天栏、背包或菜单时也触发；不填时为 `false` |
| `commands` | 要执行的指令列表 |

## 可用按键名

常用写法：

```text
A B C ... Z
0 1 2 ... 9
F1 F2 ... F25
Space Enter Tab Escape Esc Backspace Delete Insert Home End PageUp PageDown
Up Down Left Right
Ctrl Shift Alt Super Win Meta Cmd
```

组合键用 `+` 连接：

```yml
Ctrl+O:
  press: true
  commands:
    - "player:spawn"
```

`Ctrl` 也可以写成 `Control`，`Win`、`Meta`、`Cmd` 会按同一类按键处理。

## 指令写法

| 开头 | 谁来执行 |
|---|---|
| `player:` | 玩家自己执行 |
| `console:` | 服务器后台执行 |
| `op:` | 临时按管理员身份让玩家执行，执行完会恢复原状态 |

`%player%` 会替换成玩家名。

```yml
G:
  press: true
  commands:
    - "player:spawn"
    - "console:say %player% 回到了出生点"
```

```yml
Shift+R:
  press: false
  commands:
    - "op:warp vip"
```

## 在界面里是否触发

默认情况下，玩家打开聊天栏、背包或菜单时不会触发按键。如果你希望打开界面时也能触发，写：

```yml
H:
  press: true
  in-gui: true
  commands:
    - "player:help"
```

## 修改后生效

1. 保存 `keys.yml`。
2. 执行 `/lantern reload`。
3. 在线玩家会收到新的按键设置。

## 常见问题

| 现象 | 检查 |
|---|---|
| 按键没反应 | 玩家客户端是否安装 Lantern 模组，按键名是否写对 |
| 打开聊天栏时不触发 | 是否把 `in-gui` 写成 `true` |
| 指令没有执行 | `commands` 是否写了 `player:`、`console:` 或 `op:` 开头 |
| 组合键不触发 | `Ctrl+O` 这种写法中间不要加空格 |
