# Warehouse 仓库银行

::: warning WIP
本模块仍在开发中，接口契约可能调整。
:::

## 功能定位

个人仓库（物品存取）+ 多货币银行（活期/定期存款）。

## 依赖

- 必需：ArcartX
- 可选：Vault / PlayerPoints

## 启用步骤

```yaml
modules:
  warehouse:
    enabled: true
    password: "AXS-Warehouse@2026#Ready"
```

## 命令

### 管理命令（权限：`arcartxsuite.admin`）

| 命令 | 说明 |
| --- | --- |
| `/AXS warehouse status` | 查看仓库模块、数据库和配置状态 |
| `/AXS warehouse reload` | 重载仓库配置 |
| `/AXS warehouse open <玩家>` | 为在线玩家打开仓库界面 |
| `/AXS warehouse info <玩家>` | 查看玩家仓库概览（使用量、共享仓库数等） |
| `/AXS warehouse password <玩家> clear` | 清除玩家的二级密码（玩家忘记密码时使用） |
| `/AXS warehouse bank <玩家> <货币ID> <set\|add\|take> <金额>` | 管理玩家银行余额。`set` 设定、`add` 增加、`take` 扣除 |

### 玩家命令（权限：`arcartxsuite.warehouse.use`，别名 `/wh`）

| 命令 | 说明 |
| --- | --- |
| `/warehouse` 或 `/wh` | 打开仓库界面，所有操作（存/取/共享/银行）在 AXUI 中完成 |

## PAPI

前缀：`%AXSwarehouse_*%`

| 占位符 | 说明 |
| --- | --- |
| `%AXSwarehouse_total_items%` | 仓库物品总数 |
| `%AXSwarehouse_personal_used%` | 个人仓库已使用格子数 |
| `%AXSwarehouse_personal_capacity%` | 个人仓库总容量 |
| `%AXSwarehouse_shared_owned_count%` | 拥有的共享仓库数量 |
| `%AXSwarehouse_shared_joined_count%` | 加入的他人共享仓库数量 |
| `%AXSwarehouse_category_<分类ID>_amount%` | 指定分类的物品数量 |
| `%AXSwarehouse_bank_balance_<货币ID>%` | 指定货币的银行余额 |
| `%AXSwarehouse_bank_fixed_active_<货币ID>%` | 活跃定期存款笔数 |
| `%AXSwarehouse_bank_fixed_matured_<货币ID>%` | 已到期定期存款笔数 |
