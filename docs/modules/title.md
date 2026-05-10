# Title 称号

## 功能定位

分组称号系统：有效期/永久、属性加成、聊天/TAB 前缀、ArcartX UI 菜单、PAPI 全量输出。

## 依赖

- 必需：ArcartX
- 可选：PlaceholderAPI、AttributePlus / CraneAttribute / MythicLib

## 启用步骤

```yaml
modules:
  title:
    enabled: true
    password: "AXS-Title@2026#Ready"
```

## 命令

管理：
```
/AXS title status
/AXS title reload
/AXS title give <player> <titleId> <duration>
/AXS title revoke <player> <titleId>
/AXS title open <player>
```

玩家：
```
/title open
/title equip <id>
/title unequip <group|all>
/title hide <id>
/title unhide <id>
```

## PAPI

前缀：`%AXStitle_*%`

```
%AXStitle_chat_<groupId>_prefix%
%AXStitle_tab_<groupId>_prefix%
%AXStitle_equipped_<groupId>_id%
%AXStitle_owned_<titleId>%
%AXStitle_remaining_<titleId>%
```
