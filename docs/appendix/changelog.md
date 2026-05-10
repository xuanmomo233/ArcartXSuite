# 变更日志

按版本倒序记录对**外部接口契约**的破坏性变更与重要新增。

---

## 4.0.0（当前）

- **架构** — 模块整合为 17 个主模块，EntityTracker（实体追踪）、CombatEffect（战斗特效）、Announcer（播报系统）、EventPacket（事件引擎）功能扩展
- **新增** — LoginView 登录界面模块（独立/AuthMe 兼容双模式）
- **文档** — 迁移至 VitePress，全新可视化文档站

## 3.3.8

- **架构** — 资源加密协议固定为 `AES/GCM/NoPadding` + GZIP + magic `AXR1` + 12-byte IV
- **架构** — `ClientPacketGuard` 引入 `mode: silent / notify / punish`
- **Mail** — `attachment-tax-rates.<currency>` 替代旧 `vault-tax-rate`
- **EventPacket** — 引入 `rules.<id>` 模型（trigger + actions 链）
- **Title** — 新增 `craneattribute.enabled` 桥
- **OnlineRewards** — 管理命令固定为 `onlinereward`（单数）
- **EntityTracker** — rewards actions 支持四类；`inventory-full` 策略

---

## 升级注意事项

1. **备份** `plugins/ArcartXSuite/`
2. 启动新版本，让 `YamlConfigSynchronizer` 合并新增字段
3. `/AXS status` 检查模块状态
4. `/AXS reload all` 确认无报错
