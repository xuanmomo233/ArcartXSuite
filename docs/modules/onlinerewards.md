# OnlineRewards 在线奖励

## 功能定位

在线时长奖励、每日签到、补签卡、四维排行榜（日/周/月/总）。

## 依赖

- 必需：ArcartX
- 可选：PlaceholderAPI、Vault

## 启用步骤

```yaml
modules:
  onlinerewards:
    enabled: true
    password: "AXS-OnlineRewards@2026#Ready"
```

## 命令

管理：
```
/AXS onlinerewards status
/AXS onlinerewards reload
/AXS onlinerewards add|remove|set <time> <player>
/AXS onlinerewards card <add|remove|set> <amount> <player>
```

玩家：
```
/onlinerewards open
/onlinerewards status
/onlinerewards signin
/onlinerewards top <daily|weekly|monthly|total> [page]
```

## PAPI

前缀：`%AXSonlinerewards_*%`

```
%AXSonlinerewards_daily_minutes%
%AXSonlinerewards_signin_streak%
%AXSonlinerewards_top_daily_1_name%
```

## EventPacket 联动

OnlineRewards 在签到成功时自动向 EventPacket 发射信号：

| 信号名 | 触发时机 | 携带变量 |
| --- | --- | --- |
| `signin_success` | 玩家签到成功 | `streak`, `total`, `date`, `day_of_month` |

可在 `ArcartXEventPacket.yml` 中配置对应规则实现连续签到里程碑奖励、邮件派发等联动效果。
