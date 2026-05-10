# Mail 邮箱

## 功能定位

完整的游戏内邮箱系统：玩家写信、管理员预设派发、CDK 兑换、物品附件、货币手续费、跨服广播。

## 依赖

- 必需：ArcartX
- 可选：Vault / PlayerPoints、NeigeItems / MythicMobs items、Redis（跨服）

## 启用步骤

```yaml
modules:
  mail:
    enabled: true
    password: "AXS-Mail@2026#Ready"
```

## 命令

管理：
```
/AXS mail status
/AXS mail reload
/AXS mail open <player>
/AXS mail preset send <presetId> <player|all-online|all-registered>
/AXS mail cdk create <presetId> <code|auto> <maxClaims> <ttl>
/AXS mail cdk info <code>
/AXS mail cdk list [page]
/AXS mail cdk delete <code>
```

玩家：
```
/mail open
/mail compose
/mail claimall
/mail deleteall
/mail cdk <code>
```

## PAPI

前缀：`%AXSmail_*%`

```
%AXSmail_unread_count%
%AXSmail_claimable_count%
%AXSmail_total_count%
```

## EventPacket 联动

Mail 模块在 CDK 兑换成功时自动向 EventPacket 发射信号：

| 信号名 | 触发时机 | 携带变量 |
| --- | --- | --- |
| `cdk_redeemed` | CDK 兑换成功 | `cdk_code`, `preset_id`, `preset_name` |

可在 `ArcartXEventPacket.yml` 中配置对应规则实现兑换特效、字幕播报等联动效果。
