# 元气试炼之地 · 外部依赖配套文件

example 副本的 **NPC 对话** 与 **讨伐委托** 由 Conversation(Chemdah)
与 QuestGPS(Chemdah) 承担——它们的定义文件在各自插件的配置目录。

## 自动部署（推荐）

abyss 在每次加载地牢模板（`/axs abyss reload`、模块启动）时自动把
本目录文件部署到对应插件目录并触发重载——**无需手动复制**：

| 本目录文件 | 部署目标 |
|---|---|
| `chemdah/conversation/sk_guide.yml` | `plugins/Chemdah/core/conversation/` |
| `chemdah/quest/sk_slayer.yml` | `plugins/Chemdah/core/quest/` |
| `questgps/sk_slayer.yml` | `plugins/ArcartXSuite/data/questgps/quests/` |

规则：目标插件/模块缺席则整组跳过；目标文件已存在则跳过
（管理员自行修改不会被覆盖）。部署成功后自动执行
`chemdah reload` 与 questgps 模块重载。

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

## 复用到你自己的地牢

任何地牢都可以建同名 `external/` 目录（结构如上）——abyss 部署器
对所有模板目录生效，不只是 example。
