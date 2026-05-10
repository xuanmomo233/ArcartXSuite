# QuestGPS 任务导航

::: warning WIP
本模块仍在开发中，接口契约可能调整。
:::

## 功能定位

将 Chemdah 任务追踪展示为 ArcartX HUD 路径点导航。

## 依赖

- 必需：ArcartX、Chemdah

## 启用步骤

```yaml
modules:
  questgps:
    enabled: true
    password: "AXS-QuestGPS@2026#Ready"
```

## 命令

管理：
```
/AXS questgps status
/AXS questgps reload
/AXS questgps open <player>
```

玩家：
```
/questgps open
/questgps cleartrack
/questgps hud [on|off|toggle]
```
