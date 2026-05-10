# Announcer 播报系统

## 功能定位

服务器信息播报一站式方案，包含两大能力：

- **HUD 公告** — 常驻 / 轮播文字公告，可点击执行后台命令
- **打字机字幕** — 按帧播放文本动画，适用于剧情对白、任务提示、登录欢迎

**几乎零依赖，适合作为第一个验证模块。**

## 依赖

- 必需：ArcartX
- 可选：PlaceholderAPI（解析文本中的 `%...%`）

## 启用步骤

```yaml
modules:
  announcer:
    enabled: true
    password: "AXS-Announcer@2026#Ready"
```

## 配置

Announcer 的配置分为公告和字幕两部分。

### 公告（`ArcartXAnnouncer.yml`）

```yaml
settings:
  debug: false
  ui-id: "AXS:announcer_hud"
  register-ui-on-enable: true
  overwrite-ui-file: false

entries:
  welcome:
    enabled: true
    text: "欢迎来到服务器 — 你好，%player_name%。"
    click-command: "say <player> 点了公告"
```

### 字幕

打字机字幕动画配置，位于同一配置文件的 `groups` 节：

```yaml
settings:
  debug: false
  ui-id: "AXS:subtitle_hud"
  register-ui-on-enable: true

groups:
  welcome_cinematic:
    frames:
      - text: "欢迎来到..."
        length: 0
        time: 1000
        keep: 0.5
      - text: "冒险开始！"
        length: 0
        time: 800
        keep: 1.0
```

## 命令

```
/AXS announcer status
/AXS announcer reload
/AXS announcer subtitle list              # 列出已加载的字幕组
/AXS announcer subtitle play <p> <group>  # 对玩家播放字幕
/AXS announcer subtitle stop <player>     # 停止播放
```

## UI / Packet

| 功能 | UI ID | 说明 |
| --- | --- | --- |
| 公告 HUD | `AXS:announcer_hud` | 服务端推 `init`，客户端点击推 `click` 回包 |
| 字幕 HUD | `AXS:subtitle_hud` | 服务端按帧推 `play`，组结束推 `close` |
