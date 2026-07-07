# ArcartX-Suite

> **Suite — 组曲，亦是套装。**
>
> 在音乐中，**Suite（组曲）** 是由多首独立小曲串联而成的成套器乐作品。
> 一部组曲有四段——序曲启程，变奏展开，华彩交织，终章回响。
> 每一段风格、节奏各不相同，**分开能单独演奏，合起来是一部统一于同一调性的完整作品**。
>
> **ArcartX-Suite** 正是这样的理念：**26+ 个模块如同 26+ 个乐章**，每一个独立运作、各具音色、按需启用，共享 ArcartX 的统一调性与 UI 主题，组合成一部属于你自己的**服务器组曲**。
>
> 「 从登录到击杀，每一帧都是一个音符 」——不只是插件，是服务器玩法的交响组曲。

---

## 🎵 品牌寓意 · Suite 组曲

在音乐中，**Suite（组曲）** 是由多首独立小曲串联而成的成套器乐作品。
每一段风格、节奏各不相同，**分开能单独演奏，合起来是一部统一于同一调性的完整作品**。

**ArcartX-Suite** 正是这样的理念：**26+ 个模块如同 26+ 个乐章**，每一个独立运作、各具音色、按需启用，共享 ArcartX 的统一调性与 UI 主题，组合成一部属于你自己的**服务器组曲**。

---

## 💡 项目定位

**ArcartX-Suite** 是专为 **ArcartX** 生态构建的下一代全场景核心套件与玩法基础设施。一个插件覆盖 **25+ 个功能领域**，统一 ArcartX UI 体验，模块间深度联动，消除适配烦恼。

---

## 📦 模块一览（25+ 个）

### 免费模块

| 模块 | 说明 |
|------|------|
| **RGB** | 渐变文本渲染 |
| **Pickup** | 自定义拾取动画与效果 |
| **Announcer** | 公告 / 字幕轮播系统 |
| **LoginView** | ArcartX UI 登录界面 |
| **OnlineRewards** | 在线奖励 / 签到系统 |
| **CombatEffect** | 战斗特效包（击中/暴击/击杀） |
| **EventPacket** | 事件包分发引擎 |
| **Prop** | 快捷道具栏 |
| **Essentials** | 基础工具（传送/管控/一键砍树） |
| **Regions** | 区域保护（40+ 标志） |
| **AFKReward** | 挂机奖励 |

### 付费模块

| 模块 | 说明 |
|------|------|
| **title** | 称号系统（属性联动） |
| **conversation** | NPC 对话引擎 |
| **Mail** | 邮件系统 |
| **Warehouse** | 仓库系统 |
| **QuestGPS** | 任务导航系统 |
| **Map** | 大地图 / 锚点系统 |
| **Market** | 全球市场（商店 + 拍卖行 + 回收） |
| **QQBot** | QQ 群服互联（OneBot 11 协议） |
| **Menu** | 通用菜单框架 |
| **Fishing** | 钓鱼小游戏 |
| **Lottery** | 抽奖系统 |
| **BattlePass** | 战斗通行证 |

### 福利模块

| 模块 | 说明 |
|------|------|
| **Chat** | 全频道聊天系统 |
| **Tab** | 自定义 Tab 列表面板 |
| **EntityTracker** | Boss / 目标追踪面板 |

---

## 🛠️ 核心设计

### ❖ 模块化
全部 25+ 个模块独立 jar，支持热加载 / 热卸载，`config.yml` 一键开关，按需启用。

### ❖ API 接口隔离
模块仅通过 `axs-api` 中的接口（如 `PacketBridgeAPI`、`ModuleContext`）与宿主通信，核心实现类可被 ProGuard 完全混淆，防止模块直接依赖内部类。

### ❖ 生命周期管理
`ArcartXSuitePlugin` 将桥接与客户端事件管理拆分为 `BridgeLifecycleManager` 和 `ClientEventLifecycleManager`，核心入口更精简、职责更清晰。

### ❖ 跨模块联动
原生 Capability 桥接，模块间零配置联动。事件包、称号、邮件、聊天卡片、QQ 推送等能力可自由组合。

### ❖ ArcartX UI 原生
所有模块统一使用 ArcartX 客户端 UI 渲染，实现游戏级沉浸式交互体验。

### ❖ 智能配置体检
内置 `ConfigDiagnosticEngine`，自动检测配置结构差异、类型错误、字段迁移，一键修复。

---

## 🔧 环境要求

- **Java** 17+
- **Minecraft** 1.20.1+（Spigot / Paper）
- **ArcartX** 客户端 MOD

---

## 🌐 相关链接

- 📖 **[官方文档](https://arcartx-suite.github.io/ArcartXSuite-Wiki/)** — 配置教程与 API 参考
- 💻 **[文档仓库（ArcartXSuite-Wiki）](https://github.com/ArcartX-Suite/ArcartXSuite-Wiki)** — Wiki 源码与 Issue 反馈
- 🏰 **[ArcartX 社区](https://arcartx.com)** — 连接服主与开发者
