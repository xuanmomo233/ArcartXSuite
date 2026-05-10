# 客户端包守卫 + 模块密码

AXS 的安全由两部分组成：

- **`ClientPacketGuard`** — 防止伪造 / 高频回包 DoS
- **`ModulePasswordAuthenticator`** — 防止模块被未授权启用

## ClientPacketGuard

每条客户端→服务端的包被抽象成 `(player, module, action)` 三元组。

### 配置

```yaml
client-packet-guard:
  enabled: true
  cleanup-interval-ticks: 200
  defaults:
    window-ms: 1000
    max-hits: 20
    mode: "silent"
    notify-message: "&c操作过快，请稍后再试。"
    notify-cooldown-ms: 3000
    punish-command: ""
  modules:
    title:
      window-ms: 1000
      max-hits: 4
      mode: "silent"
      actions:
        equip:
          window-ms: 1500
          max-hits: 1
          mode: "notify"
```

### 字段含义

| 字段 | 说明 |
| --- | --- |
| `window-ms` | 时间窗（毫秒） |
| `max-hits` | 时间窗内最多允许的回包数 |
| `mode` | `silent` 静默丢弃 / `notify` 丢弃并提示 / `punish` 执行命令 |

## ModulePasswordAuthenticator

### 三态

- **OK**：`enabled: true` 且密码匹配
- **DISABLED**：`enabled: false`
- **LOCKED**：`enabled: true` 但密码不对

| 现象 | 排查 |
| --- | --- |
| 玩家 UI 动作没反应 | guard `mode: silent`，临时改 `notify` 排查 |
| 被提示"操作过快" | `max-hits` 太小 |
| `password locked` | 升级后同步密码 |
