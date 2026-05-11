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

### 管理命令（权限：`arcartxsuite.admin`）

| 命令 | 说明 |
| --- | --- |
| `/AXS map status` | 查看地图模块、世界、锚点和路径点状态 |
| `/AXS map reload` | 重载地图配置和 UI |
| `/AXS map open <玩家> [世界名]` | 为在线玩家打开地图界面，可选指定世界 |
| `/AXS map list` | 列出所有已配置的地图世界 |
| `/AXS map anchors [世界名]` | 列出全部或指定世界的锚点 |

### 玩家命令（权限：`arcartxsuite.map.use`，别名 `/axmap`）

| 命令 | 说明 |
| --- | --- |
| `/map` 或 `/map open [世界名]` | 打开地图界面，不指定世界时显示当前所在世界 |
| `/map hud [on\|off\|toggle]` | 控制小地图 HUD 显示。默认 `toggle` 切换 |
| `/map cleartrack` | 清除地图上正在追踪的目标点 |
