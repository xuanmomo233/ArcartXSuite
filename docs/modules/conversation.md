# Conversation 对话桥

## 功能定位

将 Chemdah 对话系统桥接到 ArcartX UI，配合 Adyeshach NPC 实现可视化对话面板。

## 依赖

- 必需：ArcartX、Chemdah、Adyeshach

## 启用步骤

```yaml
modules:
  conversation:
    enabled: true
    password: "AXS-Conversation@2026#Ready"
```

## 关键配置（`ArcartXConversation.yml`）

```yaml
settings:
  debug: false
  ui-id: "AXS:conversation_panel"
  register-ui-on-enable: true
  overwrite-ui-file: false
  npc-detect-range: 5.0
```

## 命令

```
/AXS conversation status
/AXS conversation reload
```

## UI / Packet

- UI ID：`AXS:conversation_panel`
- 服务端推对话帧（说话人、文本、选项列表），客户端推选项选择回包
