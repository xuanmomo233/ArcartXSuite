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

### 管理命令（权限：`arcartxsuite.admin`）

| 命令 | 说明 |
| --- | --- |
| `/AXS onlinerewards status` | 查看在线奖励、签到和排行榜状态 |
| `/AXS onlinerewards reload` | 重载在线奖励配置和 UI |
| `/AXS onlinerewards add\|remove\|set <时长> <玩家>` | 修改玩家在线时长。`add` 增加、`remove` 减少、`set` 设为指定值。时长如 `30m`、`2h`、`1d` |
| `/AXS onlinerewards card add\|remove\|set <数量> <玩家>` | 修改玩家的补签卡数量 |

### 玩家命令（权限：`arcartxsuite.onlinerewards.use`，别名 `/signin`）

| 命令 | 说明 |
| --- | --- |
| `/onlinerewards` 或 `/onlinerewards open` | 打开在线奖励菜单界面 |
| `/onlinerewards status` | 查看自己的在线时长统计和签到状态 |
| `/onlinerewards signin` 或 `/signin` | 进行今日签到 |
| `/onlinerewards top <范围> [页码]` | 查看排行榜。范围：`daily`（日）、`weekly`（周）、`monthly`（月）、`total`（总） |

## PAPI

前缀：`%AXSonlinerewards_*%`

### 个人数据

| 占位符 | 说明 |
| --- | --- |
| `%AXSonlinerewards_daily_minutes%` | 今日在线分钟数 |
| `%AXSonlinerewards_weekly_minutes%` | 本周在线分钟数 |
| `%AXSonlinerewards_monthly_minutes%` | 本月在线分钟数 |
| `%AXSonlinerewards_total_minutes%` | 总在线分钟数 |
| `%AXSonlinerewards_daily_time%` | 今日在线时间（格式化显示） |
| `%AXSonlinerewards_weekly_time%` | 本周在线时间（格式化） |
| `%AXSonlinerewards_monthly_time%` | 本月在线时间（格式化） |
| `%AXSonlinerewards_total_time%` | 总在线时间（格式化） |
| `%AXSonlinerewards_signin_signed_today%` | 今日是否已签到（`true`/`false`） |
| `%AXSonlinerewards_signin_streak%` | 连续签到天数 |
| `%AXSonlinerewards_signin_total%` | 累计签到天数 |

### 排行榜

格式：`%AXSonlinerewards_top_<范围>_<名次>_<字段>%`

| 参数 | 可选值 |
| --- | --- |
| `<范围>` | `daily`、`weekly`、`monthly`、`total` |
| `<名次>` | 1\~10 |
| `<字段>` | `name`（玩家名）、`minutes`（分钟数）、`time`（格式化时间） |

## EventPacket 联动

OnlineRewards 在签到成功时自动向 EventPacket 发射信号：

| 信号名 | 触发时机 | 携带变量 |
| --- | --- | --- |
| `signin_success` | 玩家签到成功 | `streak`, `total`, `date`, `day_of_month` |

可在 `ArcartXEventPacket.yml` 中配置对应规则实现连续签到里程碑奖励、邮件派发等联动效果。
