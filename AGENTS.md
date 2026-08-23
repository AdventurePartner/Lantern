# AGENTS.md

本文档为 AI 助手提供在本代码库中工作的必要信息。

## 项目概述

**Lantern** 是一个 Gradle 多项目，包含共享协议、四个客户端模组和一个服务端插件：

1. **共享协议** (`common-core/`) - 定义 Bukkit 与各客户端共用的字节协议和解码逻辑
2. **客户端模组** (`fabric-1.21.1/`、`forge-1.21.1/`、`forge-1.20.1/`、`neoforge-1.21.10/`) - 复用根目录 `src/main` 的客户端源码，并提供各平台适配
3. **Bukkit 插件** (`bukkit/`) - Spigot/Bukkit/Paper 服务端插件，提供配置管理和客户端通信

> 服务端架构约束：生产环境是 Bukkit/Paper + LanternPlugin，服务端不安装 Fabric、Forge、NeoForge 或 Lantern 服务端模组。所有客户端模块必须能连接没有对应模组加载器的服务端，不得把“要求服务端安装 Mod”作为解决方案。

> 重要协作规则：功能实现必须覆盖所有当前有效客户端模块，不能只改 Fabric/root 代码。涉及 Mixin、资源加载、平台 API 或网络协议时，必须同时检查共享 `src/main`、`fabric-1.21.1`、`forge-1.21.1`、`forge-1.20.1`、`neoforge-1.21.10`、`common-core` 和 `bukkit` 中受影响的路径，并运行全模块构建。

### 技术栈

| 组件 | 客户端模组 | 服务端插件 |
|------|------------|------------|
| 平台 | Fabric 1.21.1、Forge 1.21.1、Forge 1.20.1、NeoForge 1.21.10 | Bukkit/Paper (Spigot API 1.20.1) |
| Java | 1.20.1 使用 Java 17，其余客户端使用 Java 21 | Java 8 |
| 构建工具 | Gradle 8.x + Loom/ModDev | Gradle 8.x + Shadow |
| 关键库 | Fabric API、GeckoLib 4.7/5.3 | AyCore、PlaceholderAPI |

## 构建命令

### 全模块 (根项目)

```bash
# 清理并构建所有客户端模块、共享协议和 Bukkit 插件
./gradlew clean build --no-daemon

# 生成 IntelliJ IDEA 配置
./gradlew idea

# 运行 Minecraft 客户端 (开发环境)
./gradlew runClient
```

构建产物统一同步到 `object/`。

### Bukkit 插件 (bukkit 子项目)

```bash
# 构建插件
./gradlew :bukkit:build

# 或进入 bukkit 目录后
cd bukkit && ../gradlew build
```

构建产物: `bukkit/build/libs/LanternPlugin-1.0.0-BETA.jar`

## 代码结构

### 共享客户端源码结构

```
src/main/
├── kotlin/org/lantern/
│   ├── Lantern.kt              # 主对象，定义 MOD_ID 和日志
│   ├── LanternFabric.kt        # Fabric 入口点 (ClientModInitializer)
│   ├── model/                  # 自定义实体模型系统
│   │   ├── handler/RendererHandler.kt    # 渲染器管理
│   │   ├── renderer/GenericGeoRenderer.kt # GeckoLib 渲染器
│   │   └── wrapper/CustomModelWrapper.kt  # 模型数据封装
│   ├── ui/                     # UI 系统
│   │   ├── layer/              # 层级系统 (HUD, GUI)
│   │   ├── components/         # UI 组件 (按钮, 图片, HUD)
│   │   └── handler/LayerHandler.kt
│   ├── uix/                    # UI 扩展框架
│   │   ├── BaseComponent.kt    # 组件基类
│   │   ├── widget/             # 控件实现
│   │   └── canvas/             # 画布系统
│   └── internal/               # 内部实现
│       ├── network/            # 网络包处理
│       ├── mixin/              # Mixin 注入 (Java)
│       ├── handler/            # 事件处理器
│       └── listen/             # Fabric 事件监听
├── java/org/lantern/internal/mixin/  # Mixin 类 (Java)
└── resources/
    ├── fabric.mod.json         # Fabric 模组元数据
    ├── lantern.mixins.json     # Mixin 配置
    └── assets/lantern/         # 资源文件
```

### Bukkit 插件结构

```
bukkit/src/main/
├── kotlin/org/lantern/
│   ├── LanternPlugin.kt        # 插件主类 (继承 AyPlugin)
│   ├── config/Configurations.kt # 配置加载
│   ├── network/NetworkHandler.kt # 网络通信
│   ├── channel/                # 消息通道监听
│   ├── listen/PlayerListener.kt # 玩家事件监听
│   └── cache/KeyCache.kt       # 缓存系统
└── resources/
    ├── plugin.yml              # 插件元数据
    ├── config.yml              # 主配置
    ├── characters.yml          # 角色配置
    ├── entityModels.yml        # 实体模型配置
    ├── keys.yml                # 按键绑定配置
    └── itemIcons.yml           # 物品图标配置
```

## 命名约定

### Kotlin

- **对象声明 (单例)**: 使用 `object` 关键字，如 `object Lantern`, `object RendererHandler`
- **接口**: `I` 前缀，如 `IComponent`, `IWidget`, `ICanvas`
- **抽象类**: `Base` 前缀，如 `BaseComponent`, `BaseLayer`
- **实现类**: `Impl` 后缀，如 `HudLayerImpl`, `ImageWidgetImpl`
- **包名**: 全小写，如 `org.lantern.ui.layer`

### Java (Mixin)

- **Mixin 类**: `Mixin` 后缀，如 `EntityMixin`, `ModelBakeryMixin`
- **包名**: `org.lantern.internal.mixin`

### 资源位置

- 资源路径格式: `lantern:textures/example/image.png`
- 使用 `ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, path)` 创建

## 关键模式

### 网络通信

各平台客户端与 Bukkit 服务端通过自定义协议通信：

1. **通道**: `lantern:main` (插件通道), `lantern:data` (Fabric API)
2. **方向**:
   - S2C (Server to Client): 服务端推送配置数据
   - C2S (Client to Server): 客户端发送键盘事件
3. **数据包类**: `LanternMainPacket`, `LanternDataPacket`, `KeyboardPacket`

> 网络互通约束：`lantern:main` 是 Bukkit 与客户端之间的 wire contract。Forge/NeoForge 的 channel 或 payload 注册必须允许远端缺失；对于 NeoForge，应使用 `PayloadRegistrar.optional()`。运行时依赖注册的 payload 也必须检查，不能只检查 Lantern 自身代码。

> GeckoLib 兼容约束：NeoForge 1.21.10 使用的 GeckoLib `5.3-alpha-3` 会把自身 payload 注册为 mandatory，项目通过 `GeckoLibNetworkingNeoForgeMixin` 将其 registrar 改为 optional。升级 GeckoLib 时必须重新核对上游注册行为和该 Mixin 的注入目标，并连接真实 Bukkit/Paper 服务端验证登录、S2C 初始同步和 C2S 键盘消息。

### 自定义实体模型

使用 GeckoLib 渲染自定义模型：

1. 服务端 `entityModels.yml` 定义模型配置 (geo, texture, animation 路径)
2. 客户端 `RendererHandler` 注册和管理渲染器
3. Mixin `EntityMixin` 修改实体碰撞箱
4. Mixin `EntityRendererMixin` 注入自定义渲染逻辑

### UI 组件系统

1. **组件基类**: `BaseComponent` - 提供挂载点计算和渲染基础
2. **层级系统**: `BaseLayer` -> `HudLayerImpl`, `GuiLayerImpl`
3. **组件类型**: `HudComponent`, `ImageComponent`, `ButtonComponent`
4. **渲染回调**: 使用 `HudRenderCallback` 注册 HUD 渲染

### Mixin 使用

Mixin 类使用 Java 编写，位于共享 `src/main/java/org/lantern/internal/mixin/` 或对应平台模块的同名路径：

- 所有客户端 Mixin 注册在共享或对应平台 Mixin 配置的 `client` 数组中
- 使用 `@Unique` 注解添加私有方法，命名约定: `lantern$methodName`
- 兼容级别: JAVA_21

## 配置文件

### Bukkit 插件配置

| 文件 | 用途 |
|------|------|
| `characters.yml` | 角色定义 |
| `entityModels.yml` | 自定义实体模型 (geo/texture/animation 路径, scale, height, width) |
| `keys.yml` | 按键绑定配置 |
| `itemIcons.yml` | 物品图标映射 |
| `huds/` | HUD 配置文件目录 |

### 重载配置

服务端执行 `/lantern` 命令重载配置并推送到所有在线玩家。

## 注意事项

1. **Java 版本差异**: Fabric 1.21.1、Forge 1.21.1 和 NeoForge 1.21.10 使用 Java 21，Forge 1.20.1 使用 Java 17，Bukkit 插件使用 Java 8
2. **Mixin 仅客户端**: 当前所有 Mixin 都在 `client` 数组中，仅客户端运行
3. **GeckoLib 4.7+**: 不需要手动初始化，自动注册
4. **资源重载**: 客户端通过 `ResourceManagerHelper` 监听资源重载事件
5. **全模块验证**: 代码修改完成后从仓库根目录执行 `./gradlew clean build --no-daemon`
