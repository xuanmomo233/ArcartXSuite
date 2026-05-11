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

### 管理命令（权限：`arcartxsuite.admin`）

| 命令 | 说明 |
| --- | --- |
| `/AXS questgps status` | 查看任务导航模块状态 |
| `/AXS questgps reload` | 重载任务导航配置和 UI |
| `/AXS questgps open <玩家>` | 为在线玩家打开任务导航界面 |

### 玩家命令（权限：`arcartxsuite.questgps.use`）

| 命令 | 说明 |
| --- | --- |
| `/questgps` 或 `/questgps open` | 打开任务导航菜单 |
| `/questgps cleartrack` | 清除当前追踪的任务目标，HUD 导航箭头消失 |
| `/questgps hud [on\|off\|toggle]` | 控制 HUD 导航显示。默认 `toggle` 切换 |
