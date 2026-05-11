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

### 管理命令（权限：`arcartxsuite.admin`）

| 命令 | 说明 |
| --- | --- |
| `/AXS mail status` | 查看邮箱模块、存储、预设和 CDK 状态 |
| `/AXS mail reload` | 重载邮箱配置、预设和 UI |
| `/AXS mail open <玩家>` | 为在线玩家打开邮箱收件箱 |
| `/AXS mail preset send <预设ID> <目标>` | 按预设派发邮件。目标可填玩家名、`all-online` 或 `all-registered` |
| `/AXS mail cdk create <预设ID> <兑换码\|auto> <最大领取数> <有效期>` | 创建 CDK 兑换码。`auto` 自动生成，有效期如 `1d`、`7d`、`permanent` |
| `/AXS mail cdk info <兑换码>` | 查看 CDK 绑定预设、已领取次数、过期时间 |
| `/AXS mail cdk list [页码]` | 分页查看所有已创建的 CDK |
| `/AXS mail cdk delete <兑换码>` | 禁用并删除指定 CDK |

### 玩家命令（权限：`arcartxsuite.mail.use`，别名 `/axmail`）

| 命令 | 说明 |
| --- | --- |
| `/mail` 或 `/mail open` | 打开邮箱收件箱 |
| `/mail compose` | 打开写信界面，可给其他玩家写信并附带物品 |
| `/mail claimall` | 一键领取所有未领取邮件的附件和奖励 |
| `/mail deleteall` | 删除所有已读邮件 |
| `/mail cdk <兑换码>` | 使用兑换码领取对应邮件奖励 |

## PAPI

前缀：`%AXSmail_*%`

| 占位符 | 返回值 | 说明 |
| --- | --- | --- |
| `%AXSmail_unread_count%` | 数字 | 未读邮件数量 |
| `%AXSmail_claimable_count%` | 数字 | 有附件但尚未领取的邮件数量 |
| `%AXSmail_total_count%` | 数字 | 收件箱邮件总数 |

## EventPacket 联动

Mail 模块在 CDK 兑换成功时自动向 EventPacket 发射信号：

| 信号名 | 触发时机 | 携带变量 |
| --- | --- | --- |
| `cdk_redeemed` | CDK 兑换成功 | `cdk_code`, `preset_id`, `preset_name` |

可在 `ArcartXEventPacket.yml` 中配置对应规则实现兑换特效、字幕播报等联动效果。
