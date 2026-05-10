# PlaceholderAPI 速查

AXS 中**会对外输出 PAPI 的模块**有 7 个。

| 模块 | 前缀 | 必装 PAPI? | 典型字段 |
| --- | --- | --- | --- |
| [EntityTracker](/modules/entitytracker) | `%AXSentitytracker_*%` | 可选 | `current_*`, `slot_<n>_*`, `top_<rank>_*`, `last_*` |
| [Title](/modules/title) | `%AXStitle_*%` | 可选 | `chat_<group>_prefix`, `equipped_<group>_id` |
| [RGB](/modules/rgb) | `%arcartrgb_*%` | **必装** | `%arcartrgb_<entryId>%` |
| [OnlineRewards](/modules/onlinerewards) | `%AXSonlinerewards_*%` | 可选 | `daily_minutes`, `signin_streak` |
| [Mail](/modules/mail) | `%AXSmail_*%` | 可选 | `unread_count`, `claimable_count` |
| [Chat](/modules/chat) | `%AXSchat_*%` | 可选 | `current_channel`, `muted` |
| [Warehouse](/modules/warehouse) | `%AXSwarehouse_*%` | 可选 | `total_items`, `bank_balance_<currency>` |

## EntityTracker 占位符要点

```
%AXSentitytracker_current_display_name%
%AXSentitytracker_current_health_percent%
%AXSentitytracker_current_viewer_rank_text%
%AXSentitytracker_slot_2_top_1_name%
%AXSentitytracker_last_rank%
```

## Title 占位符要点

```
%AXStitle_chat_<groupId>_prefix%
%AXStitle_tab_<groupId>_prefix%
%AXStitle_equipped_<groupId>_id%
%AXStitle_owned_<titleId>%
%AXStitle_remaining_<titleId>%
```

## 反向消费 PAPI

很多模块会**消费 PAPI 字符串**作为排序键、阈值条件等：

| 模块 | 字段 | 用途 |
| --- | --- | --- |
| Tab | `tabs.<id>.pack` / `sort-papi-key` | 每个玩家渲染一行 |
| EventPacket | `rules.<id>.placeholder` | 触发器阈值监控 |
| Mail | `currencies.<id>.balance-placeholder` | 货币余额查询 |
| EntityTracker | `bosses.<id>.title-format` | 先替换内置变量，再走 PAPI |
