# EntityTracker 实体追踪

## 功能定位

全方位的实体追踪方案，包含两大能力：

- **Boss 追踪** — 跟踪 MythicMob，把血量、距离、存活时间、实时伤害排行推给 HUD/聊天卡片。Boss 死亡时按伤害排行自动结算并发奖
- **攻击目标 HUD** — 实时显示玩家最近命中的活体目标：名称、生命、距离、坐标、实体类型、MythicMob ID

## 依赖

- 必需：ArcartX、**MythicMobs / MythicBukkit**
- 可选：PlaceholderAPI、NeigeItems、MythicLib / MMOItems

## 启用步骤

```yaml
modules:
  entitytracker:
    enabled: true
    password: "AXS-EntityTracker@2026#Ready"
```

## 配置

EntityTracker 的配置分为 Boss 追踪和攻击目标两部分。

### Boss 追踪（`ArcartXEntityTracker.yml`）

```yaml
settings:
  debug: false
  refresh-interval-ticks: 5
  viewer-range: 48.0

bosses:
  ExampleBoss:
    enabled: true
    priority: 100
    mob-id: "ExampleBoss"
    title-format: "&c{display_name}"
    subtitle-format: "&7HP &c{health}/{max_health}"
    damage-ranking:
      enabled: true
      max-entries: 10
      rewards:
        enabled: true
        ranks:
          1:
            actions:
              - type: command
                command: "points give {player} 100"
              - type: mail
                preset-id: "boss_first_reward"
              - type: signal
                signal: "boss_first_cleared"
```

### 奖励动作类型

| `type` | 参数 | 说明 |
| --- | --- | --- |
| `command` | `command` | 以控制台身份执行命令，支持 `{player}`、`{rank}` 等占位符 |
| `message` | `text`, `target` | 发送消息给玩家或全服 |
| `neigeitems` | `item-id`, `amount` | 发放 NeigeItems 物品 |
| `mythicitems` | `item-id`, `amount` | 发放 MythicMobs 物品 |
| `mail` | `preset-id` | 通过 Mail 模块发送预设邮件（需 Mail 模块） |
| `signal` | `signal` | 向 EventPacket 发射自定义信号，可串联更多动作 |

### 攻击目标 HUD

玩家命中任意活体后实时展示目标信息 HUD。已配置追踪的 Boss 默认不触发此 HUD，避免重复显示。

```yaml
attack-target:
  debug: false
  refresh-interval-ticks: 5
  target-timeout-ms: 3000
  max-view-distance: 48.0
  ui-id: "AXS:attack_target_hud"
```

## 命令

```
/AXS entitytracker status
/AXS entitytracker reload
/AXS entitytracker sessions [mobId]
/AXS entitytracker rank <entityUuid> [page]
/AXS entitytracker settlements [page]
/AXS entitytracker settlement <settlementId> [page]
/AXS entitytracker reissue <settlementId> <rank> [player]
```

## PAPI

前缀：`%AXSentitytracker_*%`

```
%AXSentitytracker_current_display_name%
%AXSentitytracker_current_health_percent%
%AXSentitytracker_current_viewer_rank_text%
%AXSentitytracker_slot_2_top_1_name%
%AXSentitytracker_last_rank%
```

## EventPacket 联动

EntityTracker 在 Boss 击杀结算时自动向 EventPacket 发射信号（每位参与玩家各一次）：

| 信号名 | 触发时机 | 携带变量 |
| --- | --- | --- |
| `boss_settlement` | Boss 死亡结算 | `boss_id`, `boss_name`, `settlement_id`, `rank`, `damage`, `total_damage`, `participant_count` |

可在 `ArcartXEventPacket.yml` 中配置对应规则实现击杀庆祝字幕、额外邮件奖励、称号授予等联动效果。

## UI / Packet

| 功能 | UI ID | 说明 |
| --- | --- | --- |
| Boss 追踪 | 配置中指定 | 服务端周期推 `init` / `update`，Boss 死亡推 `close` |
| 攻击目标 | `AXS:attack_target_hud` | 命中时推 `init` / `update`，超时推 `close` |
