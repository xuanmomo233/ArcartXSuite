# 元气试炼之地 · 外部依赖配套文件

example 副本的 **NPC 对话** 与 **讨伐委托** 由 Conversation(Chemdah)
与 QuestGPS(Chemdah) 承担——它们的定义文件在各自插件的配置目录，
不在 abyss 地牢目录内。把下面文件复制到对应位置：

| 本目录文件 | 部署目标 | 作用 |
|---|---|---|
| `chemdah/conversation/sk_guide.yml` | `plugins/Chemdah/conversation/` | 向导会话（绑定 `adyeshach guide`） |
| `chemdah/conversation/sk_merchant.yml` | `plugins/Chemdah/conversation/` | 商人会话（绑定 `adyeshach merchant`） |
| `chemdah/quest/sk_slayer.yml` | `plugins/Chemdah/quest/` | 讨伐委托任务模板 |
| `questgps/sk_slayer.yml` | `plugins/ArcartXSuite/data/questgps/quests/` | 任务菜单呈现层 |

> Chemdah 会话/任务目录名随版本可能为 `conversation(s)`/`quest(s)`，
> 以实际插件目录为准；放好后 `/chemdah reload`（或重启）。

## 模块分工（本示例演示的边界）

- **abyss**：副本编排——NPC 生命周期/站位/click-script、房间/波次/
  祭坛商店/机关/金币经济/guide_point 导航、通关判定。
- **Adyeshach**（宿主桥 `spawnPublicTemporaryNpc`）：NPC 实体呈现，
  临时公共实体不入持久库，副本销毁即清理。
- **Conversation + Chemdah**：NPC 右键 → Chemdah 会话（ArcartX 对话 UI）；
  未装 conversation 时降级为 npcs.yml `dialogs` 聊天栏逐句。
- **QuestGPS + Chemdah**：委托接取/进度追踪/完成判定；
  完成事件经 EventBus `axs.questgps.quest_completed` 路由回
  abyss `quests.yml` 的 `on-complete` 副本脚本发奖。
- **abyss tasks.yml**：副本内定时/周期/事件任务（金币掉落等，无外部对应）。

## 缺依赖时的行为（全部静默降级，副本照常可玩）

- 无 Adyeshach → NPC 退回 Bukkit LivingEntity（无 PLAYER 型/皮肤）。
- 无 conversation/Chemdah → 右键 NPC 走 `dialogs` 聊天栏对话。
- 无 questgps/Chemdah → `quest_step` 等动作静默跳过；委托线不存在。
- 有 questgps 但想在进本处卡主线：在 QuestGPS 配置
  `gate.blocked-module-entries` 加入 `abyss.example`。
