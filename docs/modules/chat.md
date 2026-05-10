# Chat 聊天

## 功能定位

多频道聊天系统：频道切换、私聊、@提及、SocialSpy、禁言、聊天卡片推送。支持 Redis 跨服转发。

## 依赖

- 必需：ArcartX
- 可选：PlaceholderAPI、Redis（跨服）、Vault

## 启用步骤

```yaml
modules:
  chat:
    enabled: true
    password: "AXS-Chat@2026#Ready"
```

## 关键配置（`ArcartXChat.yml`）

```yaml
settings:
  debug: false
  default-channel: "global"

channels:
  global:
    enabled: true
    format: "&7[全服] &f{player}: {message}"
    range: -1
  local:
    enabled: true
    format: "&a[本地] &f{player}: {message}"
    range: 100
```

## 命令

管理：
```
/AXS chat status
/AXS chat reload
/AXS chat mute <player> <duration> [reason]
/AXS chat unmute <player>
/AXS chat spy <player> <on|off>
```

玩家：
```
/chat channel <channelId>
/chat toggle <channelId>
/chat ignore <player>
/msg <player> <message>
/reply <message>
```

## PAPI

前缀：`%AXSchat_*%`

```
%AXSchat_current_channel%
%AXSchat_reply_target%
%AXSchat_spy_enabled%
%AXSchat_muted%
```
