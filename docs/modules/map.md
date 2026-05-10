# Map 地图

::: warning WIP
本模块仍在开发中，接口契约可能调整。
:::

## 功能定位

世界地图系统：锚点传送、玩家自定义路径点、小地图 HUD、世界解锁。

## 依赖

- 必需：ArcartX
- 可选：Vault（传送/解锁费用）

## 启用步骤

```yaml
modules:
  map:
    enabled: true
    password: "AXS-Map@2026#Ready"
```

## 命令

管理：
```
/AXS map status
/AXS map reload
/AXS map open <player> [world]
/AXS map list
/AXS map anchors [world]
```

玩家：
```
/map open [world]
/map hud [on|off|toggle]
/map cleartrack
```
