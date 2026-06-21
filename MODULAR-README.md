# ArcartXSuite 模块化架构

## 概述

ArcartXSuite 已从单体插件重构为 **宿主 + 模块 Jar** 架构。  
用户可按需放入模块 Jar，实现功能的自由组合。宿主会自动检测外部模块并跳过内置加载，  
由 `ModuleRegistry` 统一管理生命周期。

## 项目结构

```
ArcartXSuite/
├── axs-api/              # 模块 API 接口层（AXSModule, ModuleContext, bridge API 等）
├── axs-core/             # 宿主核心（ShadowJar 输出）
│   ├── lifecycle/        # BridgeLifecycleManager / ClientEventLifecycleManager
│   ├── module/           # ModuleRegistry, DefaultModuleContext
│   ├── bridge/           # ArcartX 桥接实现（仅核心内部使用）
│   └── util/             # ReflectionCache 等内部工具
├── modules/
│   ├── afkreward/        # AFK 奖励
│   ├── announcer/        # Announcer 播报 + Subtitle 字幕
│   ├── battlepass/       # BattlePass 战斗通行证
│   ├── chat/             # Chat 频道聊天
│   ├── combateffect/     # CombatEffect 战斗特效 + DigisDisplay 伤害飘字
│   ├── conversation/     # Conversation 对话桥（需要 Chemdah）
│   ├── entitytracker/    # EntityTracker 实体追踪 + AttackTarget 攻击目标
│   ├── eventpacket/      # EventPacket 事件引擎
│   ├── fishing/          # Fishing 钓鱼小游戏
│   ├── loginview/        # LoginView 登录界面
│   ├── lottery/          # Lottery 抽奖
│   ├── mail/             # Mail 邮箱
│   ├── map/              # Map 世界地图
│   ├── market/           # Market 全球市场
│   ├── menu/             # Menu 通用菜单
│   ├── onlinerewards/    # OnlineRewards 在线奖励
│   ├── pickup/           # Pickup 拾取提示
│   ├── prop/             # Prop 快捷道具
│   ├── questgps/         # QuestGPS 任务导航（需要 Chemdah）
│   ├── regions/          # Regions 区域保护
│   ├── rgb/              # RGB 渐变色文本
│   ├── tab/              # Tab 在线列表
│   ├── title/            # Title 称号
│   ├── warehouse/        # Warehouse 仓库银行
│   └── qqbot/            # QQBot 群服互联
├── proxy/                # Bungee / Velocity 代理端公共库
└── native/               # Native 安全库（可选）
```

## 构建

```bash
# 清理所有构建产物与缓存
./gradlew clean

# 构建宿主（含混淆）
./gradlew :axs-core:shadowJar

# 构建全部模块 Jar（含混淆 + 字符串加密）
./gradlew buildModules

# 构建单个模块
./gradlew :modules:rgb:jar

# 全量构建（宿主 + 所有模块 + 代理）
./gradlew buildAll

# 运行测试
./gradlew test
```

## 部署

```
plugins/
  ArcartXSuite.jar              ← axs-core/build/libs/ArcartXSuite-x.x.x.jar
  ArcartXSuite/
    config.yml                  ← 模块启用/密码配置
    modules/                    ← 按需放入模块 Jar
      AXS-RGB-x.x.x.jar
      AXS-Tab-x.x.x.jar
      AXS-Pickup-x.x.x.jar
      ...
```

### 说明

1. 将 `axs-core` 产出的 ShadowJar 重命名为 `ArcartXSuite.jar` 放入 `plugins/`
2. 在 `plugins/ArcartXSuite/modules/` 目录下放入需要的模块 Jar
3. 在 `config.yml` 中配置模块的 `enabled` 和 `password`
4. 启动服务器，宿主自动扫描 `modules/` 目录
5. 有外部 Jar 的模块由模块 Jar 接管，无外部 Jar 的模块走内置加载

## 启动流程

```
onEnable()
  ├── 初始化智能配置诊断 ConfigDiagnosticEngine
  ├── 初始化 ClientPacketGuard
  ├── BridgeLifecycleManager.initialize() → 创建并初始化所有桥接实现
  ├── KeybindService.initialize() → 注册全局按键
  ├── CrossServerService.start() → 启动跨服通道
  ├── ClientEventLifecycleManager.start() → 监听 ArcartX 客户端事件
  ├── ChatSignBypassService.initialize() → Paper 1.21+ 签名绕过
  ├── 创建 ModuleRegistry（接收桥接 API 接口）
  ├── moduleRegistry.loadAll() → 按拓扑排序加载外部模块 Jar
  └── 完成
```

> **关键设计**：
> - 桥接生命周期由 `BridgeLifecycleManager` 集中管理，模块只通过 `ModuleContext` 获取 API 接口。
> - 客户端事件（自定义包、初始化完成）由 `ClientEventLifecycleManager` 监听并路由到模块。
> - `ArcartXSuitePlugin` 不再直接持有桥接实现类，核心内部实现可安全混淆。

## 重载流程

### `axs reload all`

对每个模块：
- 如果外部 Jar 已加载 → 调用 `moduleRegistry.reloadModule(id)` → 触发模块 `onReload()`
- 如果走内置 → 调用 `plugin.reloadXxxState(true)`

### `axs reload <module>`

单模块重载遵循同样逻辑，通过 `isExternalModule()` 判断走外部还是内置路径。

### UI 注册/更新

每个有 UI 的模块在 reload 时完整执行：

| 步骤 | 操作 |
|------|------|
| 1 | `shutdownXxxModule()` → 停止 Service + `unregisterXxxUi()` |
| 2 | 加载配置 → 导出 UI 文件 |
| 3 | `prepareUiBinding()` → `packetBridge.registerOrReloadUi()` |
| 4 | 创建并启动新 Service |

`registerOrReloadUi()` 先尝试 reload（已注册则更新），再尝试 register（未注册则注册），是**幂等安全**的。  
ArcartX 现已支持 UI 自动导入，不再需要手动执行 `ax reload`。

## 模块实现模式

### 独立模式

模块自建 Service，完全不依赖宿主业务逻辑。适合逻辑简单或已完全解耦的模块。

```java
public final class RgbModule implements AXSModule {
    private RgbService service;

    @Override
    public boolean onEnable(ModuleContext context) throws Exception {
        // 自建 Service，从 context 获取 plugin/logger/bridges
        service = new RgbService(context);
        service.start();
        return true;
    }

    @Override
    public void onDisable() {
        if (service != null) service.shutdown();
    }

    @Override
    public void onReload() throws Exception {
        onDisable();
        // re-enable with saved context
    }
}
```

### 使用桥接 API 接口

模块通过 `ModuleContext` 获取 `PacketBridgeAPI` 等接口，而非直接引用核心实现类。

```java
public final class MyModule implements AXSModule {
    private ModuleContext context;
    private PacketBridgeAPI packetBridge;

    @Override
    public boolean onEnable(ModuleContext context) throws Exception {
        this.context = context;
        this.packetBridge = context.packetBridge();
        // 注册 UI、监听客户端包等
        return true;
    }

    @Override
    public void onDisable() {
        // 模块通过 context 注册的资源会自动清理
    }

    @Override
    public void onReload() throws Exception {
        onDisable();
        if (context != null) onEnable(context);
    }
}
```

> **禁止**：模块源码中不得 `import xuanmo.arcartxsuite.bridge.*` 或引用 `ArcartXSuitePlugin` 等核心实现类，否则混淆后可能无法加载。

## 模块开发指南

### 1. 实现 `AXSModule` 接口

```java
public final class MyModule implements AXSModule {
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("mymodule")
            .name("MyModule")
            .version("1.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    public boolean onEnable(ModuleContext context) throws Exception {
        // 初始化逻辑
        return true;
    }

    @Override
    public void onDisable() {
        // 清理逻辑
    }

    @Override
    public void onReload() throws Exception {
        // 重载逻辑（通常 disable + enable）
    }

    @Override
    public boolean isReady() {
        return true;
    }
}
```

### 2. 创建 `module.yml`

```yaml
id: mymodule
name: MyModule
version: 1.0.0
main: com.example.MyModule
api-version: 1.0
depends: []
softdepends: []
external-depends: []
external-softdepends: []
```

### 3. 注册命令（可选）

实现 `ModuleCommandHandler` 接口即可自动注册 `/axs mymodule ...` 子命令：

```java
public final class MyModule implements AXSModule, ModuleCommandHandler {
    @Override
    public String commandId() { return "mymodule"; }

    @Override
    public List<String> actions() { return List.of("help", "status", "reload"); }

    @Override
    public boolean onCommand(CommandSender sender, String label, String[] args) {
        // 命令处理
        return true;
    }
}
```

### 4. `ModuleContext` 可用能力

| 方法 | 说明 |
|------|------|
| `plugin()` | 宿主 JavaPlugin 实例 |
| `logger()` | 模块专用 Logger |
| `packetBridge()` | ArcartX 发包桥接 |
| `clientBridge()` | ArcartX 客户端桥接 |
| `itemStackBridge()` | ItemStack 桥接 |
| `propBridge()` | Prop / 按键绑定桥接 |
| `worldTextureBridge()` | 世界文字贴图桥接 |
| `createWaypointBridge()` | 创建独立路标桥接实例 |
| `createAdyeshachNpcBridge()` | 创建独立 Adyeshach NPC 桥接实例 |
| `packetGuard()` | 客户端包守卫 |
| `crossServer()` | 跨服通道 |
| `accountTypeService()` | 统一账号识别服务 |
| `registerCapability(Class, T)` | 注册跨模块能力 |
| `getCapability(Class)` | 查找跨模块能力 |
| `hasPlugin(String)` | 检查外部插件是否可用 |

## 核心组件

| 组件 | 说明 |
|------|------|
| `AXSModule` | 模块生命周期接口（onEnable / onDisable / onReload / isReady） |
| `ModuleContext` | 宿主暴露给模块的上下文（plugin / bridges / logger） |
| `ModuleDescriptor` | 模块元数据（id / name / version / depends） |
| `ModuleCommandHandler` | 可选命令处理接口（commandId / actions / onCommand） |
| `ModuleRegistry` | 模块扫描 / 加载 / 启用 / 禁用 / 重载 |
| `ModuleClassLoader` | 模块隔离 ClassLoader |
| `DefaultModuleContext` | ModuleContext 默认实现 |
| `BridgeLifecycleManager` | 核心桥接生命周期管理 |
| `ClientEventLifecycleManager` | ArcartX 客户端事件监听与路由 |
| `ReflectionCache` | 核心内部反射缓存工具 |
| `ClientPacketGuard` | 客户端包安全校验 |
| `ChatSignBypassService` | Paper 1.21+ 聊天签名绕过 |
| `CloudModuleService` | 云端模块同步 |

## 迁移状态

所有模块均已独立为外部 Jar，通过 `AbstractAXSModule` 实现生命周期，业务逻辑不再依赖宿主内置 Service。

| 模块 Jar | 对应功能模块 | 模式 | UI |
|----------|-------------|------|-----|
| afkreward | AFK 奖励 | ✅ 独立 | — |
| announcer | Announcer + Subtitle | ✅ 独立 | HUD |
| battlepass | BattlePass 战斗通行证 | ✅ 独立 | UI |
| chat | Chat 频道聊天 | ✅ 独立 | — |
| combateffect | CombatEffect + DigisDisplay | ✅ 独立 | — |
| conversation | Conversation 对话 | ✅ 独立 | UI+Selector |
| entitytracker | EntityTracker + AttackTarget | ✅ 独立 | HUD |
| eventpacket | EventPacket 事件引擎 | ✅ 独立 | — |
| fishing | Fishing 钓鱼 | ✅ 独立 | UI |
| loginview | LoginView 登录界面 | ✅ 独立 | UI |
| lottery | Lottery 抽奖 | ✅ 独立 | UI |
| mail | Mail 邮箱 | ✅ 独立 | UI |
| map | Map 世界地图 | ✅ 独立 | Menu+HUD |
| market | Market 全球市场 | ✅ 独立 | UI |
| menu | Menu 通用菜单 | ✅ 独立 | UI |
| onlinerewards | OnlineRewards 在线奖励 | ✅ 独立 | — |
| pickup | Pickup 拾取提示 | ✅ 独立 | HUD |
| prop | Prop 快捷道具 | ✅ 独立 | — |
| questgps | QuestGPS 任务导航 | ✅ 独立 | Menu+HUD |
| regions | Regions 区域保护 | ✅ 独立 | — |
| rgb | RGB 渐变色文本 | ✅ 独立 | — |
| tab | Tab 在线列表 | ✅ 独立 | — |
| title | Title 称号 | ✅ 独立 | — |
| warehouse | Warehouse 仓库银行 | ✅ 独立 | UI |
| qqbot | QQBot 群服互联 | ✅ 独立 | — |

> 委托模式已完全淘汰，模块与宿主之间仅通过 `axs-api` 接口与 Capability 通信。

## 近期变更记录

### v4 — Bridge API 接口化 & 核心生命周期拆分

- **Bridge API 全面接口化**：`PacketBridgeAPI` / `ClientBridgeAPI` / `ItemBridgeAPI` / `PropBridgeAPI` / `WaypointBridgeAPI` / `AdyeshachNpcBridgeAPI` / `WorldTextureBridgeAPI` 成为模块唯一可见的桥接面；模块禁止直接引用 `xuanmo.arcartxsuite.bridge.*` 实现类。
- **核心内部字段接口化**：`ArcartXSuitePlugin`、`ModuleRegistry`、`DefaultModuleContext`、`KeybindService` 字段与参数全部使用 API 接口类型。
- **生命周期拆分**：新增 `BridgeLifecycleManager` 管理桥接创建/初始化/关闭；新增 `ClientEventLifecycleManager` 管理客户端事件监听与路由；`ArcartXSuitePlugin` 从 765 行精简到约 578 行。
- **反射缓存工具**：新增 `ReflectionCache`，已应用于 `ChatSignBypassService` 和 `ClientEventLifecycleManager`，减少重复反射样板代码。
- **混淆安全**：核心 bridge 实现类可被 ProGuard 完全混淆，`modules/` 下 17 个模块 JAR 中不再出现任何原始 bridge 实现类名。
- **委托模式淘汰**：所有模块均通过 `AbstractAXSModule` 独立实现，不再依赖 `ArcartXSuitePlugin.reloadXxxState()` 等宿主方法。

### v3 — 混合登录代理重构（独立进程架构）

- **MixedYggdrasilProxy 独立进程化**：新增 `main()` 入口，支持固定端口启动与端口占用复用检测（`SO_REUSEADDR`），解决 authlib-injector `premain` 时序问题
- **启动脚本重写**：`start-mixed-auth.bat/.sh` 改为「后台启动代理进程 → 轮询端口就绪 → 启动服务器 JVM」流程，代理生命周期由脚本管理
- **宿主去耦合**：`ArcartXSuitePlugin.onEnable` 不再启动/停止代理，改为异步探测代理端口可达性并提示状态
- **命令状态同步**：`/axs auth status` 显示代理端口与连通状态（就绪/未运行）
- **配置清理**：`config.yml` 新增 `auth.mixed-proxy-port`（默认 25599），重写 `?mixed` 误导注释；删除无效死配置 `deny-offline` / `kick-offline-message`
- **ProGuard 保护**：`-keep` 保留 `MixedYggdrasilProxy` 类名与 `main()`，防止混淆导致独立进程入口丢失

### v2 — 统一启动流程 & 移除 ax reload

- **EntityTracker 流程统一**：`reloadEntityTrackerState` 签名简化为 `(boolean logSummary)`，与其他模块一致
- **移除 ArcartX 自动 reload**：不再执行 `ax reload true`，ArcartX 已支持 UI 自动导入
- **移除 Hybrid Bootstrap**：不再有延迟重试机制（`scheduleHybridBootstrap`）
- **预扫描避免双重初始化**：`ModuleRegistry.scanAvailableModuleIds()` 在内置加载前执行
- **命令感知外部模块**：`axs reload all / axs reload <module>` 自动判断走外部模块还是内置路径
- **完善模块 onDisable**：所有 16 个委托模块正确调用 `shutdownXxxModule()` 清理资源

### v1 — 模块化基础架构

- Gradle 多模块结构 + axs-api 接口定义
- ModuleRegistry + ModuleClassLoader + ModuleContext
- 4 个独立实现模块 (RGB, Pickup, Tab, CombatEffect)
- 13 个委托实现模块
- 动态命令注册 (ModuleCommandHandler)
