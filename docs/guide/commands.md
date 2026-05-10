# 命令速查

源码：`xuanmo.arcartxsuite.command.ArcartXSuiteCommand`（管理）+ 各模块 `*PlayerCommand`（玩家）。

## 管理命令

主入口：`/AXS`（别名 `/arcartxsuite`），需要权限 `arcartxsuite.admin`（默认 op）。

### 全局

| 命令 | 说明 |
| --- | --- |
| `/AXS` | 等同 `/AXS status` |
| `/AXS status` | 全模块状态总览 |
| `/AXS help [module]` | 中文帮助 |
| `/AXS reload all` | 按顺序 reload 全部启用模块 |
| `/AXS reload <module>` | reload 单个模块 |
| `/AXS <module> status` | 单模块状态详情 |

合法的 `<module>` 共 17 个：

```
announcer, entitytracker, combateffect, eventpacket,
chat, conversation, loginview, mail, onlinerewards,
pickup, prop, rgb, tab, title,
map, questgps, warehouse
```

### 模块特有动作

#### EntityTracker

| 命令 | 说明 |
| --- | --- |
| `/AXS entitytracker sessions [mobId]` | 列出活跃 Boss 会话 |
| `/AXS entitytracker rank <entityUuid> [page]` | 查看实时伤害排行 |
| `/AXS entitytracker settlements [page]` | 列出结算记录 |
| `/AXS entitytracker settlement <id> [page]` | 查看结算详情 |
| `/AXS entitytracker reissue <id> <rank> [player]` | 补发奖励 |

#### EventPacket

| 命令 | 说明 |
| --- | --- |
| `/AXS eventpacket fire <signal> <player> [key=value...]` | 手动触发规则 |

#### Announcer

| 命令 | 说明 |
| --- | --- |
| `/AXS announcer subtitle list` | 列出字幕组 |
| `/AXS announcer subtitle play <player> <group>` | 播放字幕 |
| `/AXS announcer subtitle stop <player>` | 停止字幕 |

#### Title

| 命令 | 说明 |
| --- | --- |
| `/AXS title give <player> <titleId> <duration>` | 发放称号 |
| `/AXS title revoke <player> <titleId>` | 收回称号 |
| `/AXS title open <player>` | 打开称号菜单 |

#### LoginView

| 命令 | 说明 |
| --- | --- |
| `/AXS loginview open <player>` | 打开登录界面 |
| `/AXS loginview migrate-authme [dry-run]` | 迁移 AuthMe 数据 |
| `/AXS loginview migration-commands` | 查看迁移说明 |

#### Mail / Warehouse / Chat / Map

详见各模块文档页。

## 玩家命令

| 命令 | 别名 | 权限 | 说明 |
| --- | --- | --- | --- |
| `/title open/equip/unequip/hide/unhide` | — | `arcartxsuite.title.use` | 称号菜单 |
| `/warehouse` | `wh` | `arcartxsuite.warehouse.use` | 打开仓库 |
| `/mail open/compose/claimall/cdk` | `axmail` | `arcartxsuite.mail.use` | 邮箱 |
| `/chat channel/toggle/ignore` | — | `arcartxsuite.chat.use` | 频道控制 |
| `/msg <player> <message>` | — | `arcartxsuite.chat.msg` | 私聊 |
| `/onlinerewards open/signin/top` | `signin` | `arcartxsuite.onlinerewards.use` | 在线奖励 |
| `/questgps open/cleartrack/hud` | — | `arcartxsuite.questgps.use` | 任务 GPS |
| `/map open/hud/cleartrack` | `axmap` | `arcartxsuite.map.use` | 地图 |
