# 聊天频道与聊天样式

聊天相关配置都在 `plugins/Lantern/config.yml`，改完执行 `/lantern reload` 生效。

## 频道按钮

在 `config.yml` 配置服务端同步给客户端的聊天频道：

```yaml
chat-channels:
  - id: all
    display-name: "全部"
    prefixes: []
    filter:
      - system
  - id: announcement
    display-name: "公告"
    prefixes:
      - "[公告]"
  - id: system
    display-name: "系统"
    prefixes:
      - "[系统]"
```

执行 `/lantern reload` 后，玩家打开聊天栏会在输入框上方看到每个频道的按钮。

- `prefixes` 匹配客户端实际看到的纯文本开头，不写 `&` 或 `§` 颜色代码；`prefixes: []` 表示该频道显示所有已缓存聊天消息。
- `filter` 填频道 `id` 列表，会从当前频道里排除这些频道的非空 `prefixes` 命中的消息，例如上面的 `all` 不显示 `[系统]` 消息。
- 切换按钮会替换原版聊天栏当前显示的消息队列，不再显示额外独立面板。
- 非 `all` 频道收到新消息时：玩家没打开聊天栏会在左下角看到「收到新消息」；玩家正在聊天栏内时，对应频道按钮右上角会显示红点，点击该频道后清除。
- `display-name` 可以写 `&` 颜色代码，例如 `"&6公告"`。

## 聊天样式

同样在 `config.yml` 里加 `chat-style`，可以改原版聊天窗口、频道按钮和「收到新消息」提示的外观。每一项都可以不写，不写的项保持原来的样子：

```yaml
chat-style:
  window:                    # 原版聊天窗口
    width: 320               # 宽度（像素）。不写 = 跟随玩家自己的“聊天宽度”设置
    height: 180              # 打开聊天栏时的高度
    height-unfocused: 90     # 没打开聊天栏时的高度
    scale: 1.0               # 文字大小，0.25 到 4。不写 = 跟随玩家设置
    offset-x: 0              # 整个聊天窗口左右移动，向右为正
    offset-y: -20            # 上下移动，向下为正，负数向上
    background: "#202040"    # 每行消息的背景色；none = 不画背景
    text-color: "#E0E0E0"    # 消息文字颜色，只影响没有自带颜色的文字
    input-background: "#80000000"   # 底部输入框的背景色；none = 透明
  tabs:                      # 频道按钮
    offset-x: 0              # 整组按钮的移动量
    offset-y: 0
    height: 12               # 按钮高度
    gap: 2                   # 按钮之间的空隙
    min-width: 24            # 按钮最小宽度
    padding-x: 6             # 文字离按钮左边的距离
    text-align: left         # left 靠左 / center 居中
    text-shadow: false       # 文字阴影
    background: "#80000000"           # 普通按钮背景。不写 = 跟随玩家的聊天背景不透明度
    selected-background: "#C04A90D9"  # 当前频道的按钮背景
    hover-background: "#A0FFFFFF"     # 鼠标指着时的背景。不写 = 不变
    hover-outline: "#FFFFFFFF"        # 鼠标指着时的边框；none = 不画
    text-color: "#FFFFFF"
    selected-text-color: "#FFFF55"    # 当前频道的文字颜色。不写 = 同 text-color
    unread-color: "#FFFF3333"         # 未读红点颜色
    unread-size: 4                    # 红点大小；0 = 不显示
  notification:              # 没打开聊天栏时左下角的“收到新消息”
    enabled: true            # false = 不显示
    text: "&e收到新消息"      # 可以写 & 颜色代码
    text-color: "#FFFFFF"
    text-shadow: false
    background: "#80000000"  # 不写 = 跟随玩家的聊天背景不透明度
    padding-x: 4
    padding-y: 2
    offset-x: 0              # 以屏幕左下角为起点移动
    offset-y: 0
```

单个频道的按钮还可以在 `chat-channels` 里单独写 `style`，没写的项沿用上面 `tabs` 的设置：

```yaml
chat-channels:
  - id: announcement
    display-name: "&6公告"
    prefixes:
      - "[公告]"
    style:
      width: 40                          # 固定按钮宽度
      background: "#80402000"
      selected-background: "#C0D9904A"
      hover-background: "#A0FFD080"
      text-color: "#FFD080"
      selected-text-color: "#FFFFFF"
```

## 注意事项

- 颜色一定要加引号，因为 `#` 在配置文件里表示注释。颜色写 6 位 `#RRGGBB`（不透明）或 8 位 `#AARRGGBB`（前两位是透明度，`00` 全透明、`FF` 不透明）。
- 按钮和提示的 `background` 还可以写贴图 `"url(lantern:textures/chat/tab.png)"`，写法和[界面配置](../ui/styling.md#背景-background)里的 `background` 一样，贴图会拉伸填满按钮。聊天窗口的 `background` 只能写颜色。
- 聊天窗口的背景透明度会再乘上玩家自己的「文字背景不透明度」设置。消息过一会儿淡出的效果也会保留。玩家把这个设置调到 0 时，背景就不显示。
- 写了 `width`、`height`、`scale` 后，玩家在设置里调的聊天宽度、高度、文字大小就不起作用了。不写则仍由玩家自己决定。
- 移动聊天窗口后，点击消息里的链接、鼠标悬停提示也会跟着新位置走。
- 改完执行 `/lantern reload` 即可生效。玩家退出服务器后，聊天窗口会恢复原版外观。
