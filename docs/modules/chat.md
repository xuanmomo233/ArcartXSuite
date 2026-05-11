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

### 管理命令（权限：`arcartxsuite.admin`）

| 命令 | 说明 |
| --- | --- |
| `/AXS chat status` | 查看聊天模块状态、存储、Redis 和 Proxy 连接信息 |
| `/AXS chat reload` | 重载聊天频道、过滤和跨服配置 |
| `/AXS chat mute <玩家> <时长> [原因]` | 禁言玩家。时长如 `30m`、`12h`、`7d`、`permanent`（永久） |
| `/AXS chat unmute <玩家>` | 解除玩家禁言 |
| `/AXS chat spy <玩家> <on\|off>` | 开启/关闭对指定玩家的私聊监听 |

### 玩家命令（权限：`arcartxsuite.chat.use`）

| 命令 | 说明 |
| --- | --- |
| `/chat` | 查看当前聊天状态（频道、私聊开关、忽略列表等） |
| `/chat channel <频道ID>` | 切换聊天频道，之后发送的消息进入该频道 |
| `/chat toggle private [on\|off]` | 开启/关闭私聊接收 |
| `/chat toggle mentions [on\|off]` | 开启/关闭 @提及通知 |
| `/chat ignore <玩家>` | 屏蔽指定玩家，不再看到对方消息 |
| `/chat unignore <玩家>` | 取消屏蔽 |
| `/chat socialspy [on\|off]` | 开启/关闭社交监听（可查看他人私聊） |
| `/msg <玩家> <消息>` | 向指定在线玩家发送私聊（权限：`arcartxsuite.chat.msg`） |
| `/reply <消息>` | 快速回复最近一次私聊你的玩家 |

## PAPI

前缀：`%AXSchat_*%`

| 占位符 | 返回值 | 说明 |
| --- | --- | --- |
| `%AXSchat_current_channel%` | 文本 | 当前所在频道的 ID |
| `%AXSchat_current_channel_display%` | 文本 | 当前频道的显示名称 |
| `%AXSchat_reply_target%` | 文本 | 最近私聊对象的玩家名，没有时返回空 |
| `%AXSchat_spy_enabled%` | `true`/`false` | 社交监听是否开启 |
| `%AXSchat_ignore_count%` | 数字 | 已屏蔽的玩家数量 |
| `%AXSchat_muted%` | `true`/`false` | 是否处于被禁言状态 |
