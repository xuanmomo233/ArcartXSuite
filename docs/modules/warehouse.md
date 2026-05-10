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

管理：
```
/AXS warehouse status
/AXS warehouse reload
/AXS warehouse open <player>
/AXS warehouse info <player>
/AXS warehouse password <player> clear
/AXS warehouse bank <player> <currency> <set|add|take> <amount>
```

玩家：
```
/warehouse
/wh
```

## PAPI

前缀：`%AXSwarehouse_*%`

```
%AXSwarehouse_total_items%
%AXSwarehouse_personal_used%
%AXSwarehouse_personal_capacity%
%AXSwarehouse_bank_balance_<currency>%
```
