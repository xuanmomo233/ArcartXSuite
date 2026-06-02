# Tab 模块扩展路线图

> 本文件用于内部记录 Tab 模块的功能扩展计划，不直接进入 wiki。每项功能列出：用途、配置开关（默认值）、实现要点。**所有新功能默认关闭**，保留现有行为不被破坏。

## 0. 当前已有能力（基线）

- 三种排序模式：`name` / `prem` / `papi`
- pack 三态：字符串 / 列表 / 字典
- 服务端周期 diff 推送 + 客户端 `Packet.send("TAB","update")` 强制重发（带限流）
- 跨服：Redis Pub/Sub 或 BungeeCord/Velocity 插件消息
- `max-entries` / `omit-blank-values` / 称号 PAPI 前后缀

---

## 1. 多 Tab 视图切换（优先级 ★★★★★）

让同一玩家在 `default / arena / dungeon` 等多套 Tab UI 间切换。

```yaml
settings:
  multi-view:
    enabled: false          # 总开关
    default-view: "default"
    auto-switch:
      enabled: false        # 进入特定世界/区域自动切换
      rules:
        - when-world: "arena_world"
          view: "arena"
        - when-papi: "%mythicmobs_in_combat%"
          equals: "true"
          view: "combat"
```

- 新增字段 `tabs.<id>.view: <viewName>`，TabSyncService 按玩家当前 view 推送对应 pack。
- 命令：`/axs tab view <name>` 玩家自切；`/axs tab view <player> <name>` 管理员强制切。
- 切换时下发一次 `clear` 包后再 `init`，避免残留。

## 2. 玩家头像（优先级 ★★★★★）✅ 已就绪

使用 ArcartX 自带 **纹理表达式 `PlayerSkin:<UUID>`** 由客户端 UI 渲染，**服务端不抓 Mojang，零 Java 改动**。

`TabSyncService.buildValues()` 已经原生注入 `{player_uuid}` 占位符，pack 直接写即可：

```yaml
tabs:
  online-tab:
    pack:
      name: "%player_name%"
      uuid: "{player_uuid}"
      health: "%player_health%"
```

UI YAML 端 Texture 控件用 `normal: "PlayerSkin:{var.uuid}"` 渲染头颅。

- 跨服：远程快照中已经包含 UUID（`renderedPack`），跨服节点同样可渲染，零额外网络代价。
- 不要自己拉 Mojang / 缓存皮肤，避免速率限制与离线服兼容问题。
- 已同步到 `docs/modules/tab.md` 的「玩家头像（PlayerSkin 渲染）」章节，与 `ArcartXTab.yml` pack 注释。
- **结论**：M1 玩家头像无需新增配置开关，文档化即可。

## 3. 复合排序 / 过滤器 / 置顶置底（优先级 ★★★★）✅ 阶段 1 完成

```yaml
tabs:
  online-tab:
    sort-keys:
      - { mode: prem, prem-group: [admin, vip3, vip2, vip1, default] }
      - { mode: papi, key: "%player_level%", numeric: true, order: desc }
      - { mode: name }
    filters:
      hide-vanished: false                # 内置隐身集成（Essentials/SuperVanish/PremiumVanish/Bukkit metadata）
      include:
        - { papi: "%player_world%", equals: "world" }
      exclude:
        - { permission: "axs.tab.hide" }
    pinned:
      top:
        - { permission: "axs.tab.pin-top" }
      bottom:
        - { permission: "axs.tab.pin-bottom" }
```

- 实现位置：`TabSortKey` / `TabFilterRule` / `TabDefinition.sortKeys/includeFilters/excludeFilters/hideVanished/pinnedTop/pinnedBottom` / `TabSyncService.applyFilters/composedComparator/sortPlayers`。
- 旧字段 `sort-mode` / `sort-papi-*` / `sort-prem-group` / `sort-descending` 在 `sort-keys` 缺失时仍兼容。
- **跨服限制**：`TabRemoteEntry` 仍是单 sortValue，跨服节点按首个 sortKey 投递。完整跨服多键将在阶段 3 协议升级。
- 同步：`docs/modules/tab.md`「排序、过滤与置顶」章节、`ArcartXTab.yml` 注释示例。

## 4. 多视图 / 分组 / 分页（优先级 ★★★★）✅ 阶段 2 完成

- **多视图**：`tabs.<id>.view`（默认 `"default"`）+ 玩家命令 `/axstab view <name>`。切换时旧 view 一次性发空 payload 清空 UI，新 view 重发数据。状态保存于 `TabSyncService.viewerCurrentView`。
- **分组**：`tabs.<id>.grouping.{enabled, group-by-papi, group-order, header-pack, include-unordered}`。仅 list / string pack 支持，map pack 退化警告。实现位置：`TabSyncService.appendGroupedEntries / emitGroup / renderGroupHeader`。
- **分页**：`tabs.<id>.pagination.{enabled, page-size, packet-id, next-action, prev-action, set-action}`。玩家命令 `/axstab page <id> <next|prev|N>` 与客户端 `Packet.send("TAB_PAGE", "next/prev/set", N)` 等价。状态保存于 `TabSyncService.viewerPages`。
- **限制**：跨服 + grouping/pagination 当前不组合生效（cross-server 路径走旧 builder），下一步可扩展 buildCrossServerPayload 接收 viewer 上下文。

## 4-legacy（已并入上方）分组分页（优先级 ★★★★）

```yaml
tabs:
  online-tab:
    grouping:
      enabled: false
      group-by-papi: "%vault_group%"      # 按权限组分组
      group-order: ["admin", "vip3", "vip2", "vip1", "default"]
      header-pack: "&6=== {group} ===("   # 每组组首行 pack（可选）
    pagination:
      enabled: false
      page-size: 80                       # 单页玩家数；超过自动分页
      switch-key: "TAB_PAGE"              # 客户端 Packet.send("TAB_PAGE","next/prev")
```

## 5. 行为反馈 / 悬浮 / 点击（优先级 ★★★★）

```yaml
tabs:
  online-tab:
    interactions:
      enabled: false
      click:
        packet-id: "TAB_CLICK"            # 客户端点击行回包，服务端转给 EventPacket
        cooldown-ms: 500
      tooltip:
        enabled: false
        pack:                             # 服务端为每个玩家额外渲染 tooltip pack
          level: "%player_level%"
          ping: "%player_ping%"
```

- 点击事件直接走 `EventPacket` 的 `ui-packet → packet-handler` 链路，不需要 Tab 模块自己实现菜单。

## 6. 跨服增强（优先级 ★★★）✅ 阶段 3 完成

- **聚合模式 aggregate**：`tabs.<id>.aggregate.{enabled, line-pack}`，仅 `cross-server: true` 生效。每节点（含本服）渲染一行，占位符 `{server-id}` / `{server-display}` / `{server-online}` + 完整 PAPI。实现位置：`TabSyncService.buildAggregatePayload / renderAggregateLine`。
- **批节流 batch**：`settings.batch.window-ticks` 控制 `broadcastLocalSnapshots` 的最小广播间隔。状态保存于 `lastBroadcastTimestamps`。
- **退服宽限 leave-grace-ms**：`settings.leave-grace-ms` 玩家退服后保留虚拟条目；缓存在 `leaveGraceCache`，每次 `cleanupGraceCache` 时按时间淘汰；`broadcastLocalSnapshots` 把宽限内的玩家合并进快照。
- **遗留 TODO**：`TabRemoteEntry` 仍是单 sortValue（首键），跨服多键完整传递需要协议升级（向后兼容字段 + 版本号）。

## 6-legacy（已并入上方）跨服增强（优先级 ★★★）

```yaml
transport:
  mysql:
    enabled: false                        # 第三种通道：MySQL 兜底快照
    table: "axs_tab_snapshots"
    ttl-seconds: 60
  aggregate:
    enabled: false                        # 聚合模式：跨服只发"服务器一行"
    format: "{server-id} ({count}/{max})"
  batch:
    enabled: true                         # 默认开：合批多 tick 内的 diff
    window-ticks: 2
  leave-grace-ms: 1500                    # 玩家退服后延迟移除，避免跨服跳传闪烁
```

## 7. PAPI 输出（优先级 ★★★）✅ 阶段 4-A 完成

新增 PAPI 前缀 `AXStab_*`，对外可供 Scoreboard / Title / 广播复用：

- 实现位置：`TabPlaceholderExpansion`（`onRequest` 解析 defId + metric / 全局 metric）+ `TabModule.createPlaceholderExpansion` 受 `settings.papi.enabled` 开关守护。
- 支撑 API：`TabSyncService.localVisibleCount / totalVisibleCount / rankOf / currentView / currentPage / definitionIds`。
- 同步：`docs/modules/tab.md` 的「PAPI 输出」章节、`ArcartXTab.yml` `settings.papi.*` 注释。

| 占位符 | 含义 |
| --- | --- |
| `%AXStab_<defId>_count%` | 当前定义可见玩家数 |
| `%AXStab_<defId>_total%` | 包含跨服玩家在内的总数 |
| `%AXStab_<defId>_rank%` | 自己在排序中的位次（1 起） |
| `%AXStab_<defId>_view%` | 自己当前所在 view（多视图） |
| `%AXStab_<defId>_page%` | 自己当前页码 |

```yaml
settings:
  papi:
    enabled: false                        # 注册 PAPI 扩展，默认关闭以减少占位符冲突
    expansion-id: "AXStab"
```

## 8. 视觉增强（优先级 ★★★）✅ 阶段 4-C 完成（PAPI 暴露形态）

```yaml
tabs:
  online-tab:
    style:
      pvp-highlight:
        enabled: false
        papi: "%mythicmobs_in_combat%"
        when-equals: "true"
        color-override: "&c"
      vanish-grey:
        enabled: false
        papi: "%essentials_vanished%"
        when-equals: "true"
        color-override: "&8"
      ping-icon:
        enabled: false
        thresholds: { good: 80, warn: 150 }    # 客户端 UI 端转 icon
```

- 客户端 UI 端按颜色字段切换 icon / texture，服务端只做语义标签注入。
- **当前形态**：style 不修改 pack 渲染流程，改为通过 `%AXStab_pvp%` / `%AXStab_pvp_color%` / `%AXStab_vanished%` / `%AXStab_vanish_color%` / `%AXStab_ping%` / `%AXStab_ping_icon%` 占位符暴露，pack 主动消费。
- 实现位置：`TabStyleConfiguration` + `TabSyncService.recordPvpEvent / isPvpActive / isVanishedPublic / pingOf / pingIcon` + `TabPvpListener`（EntityDamageByEntityEvent / Projectile 双向记录）+ `TabPlaceholderExpansion.resolveGlobalMetric`。
- 同步：`docs/modules/tab.md` 的「视觉风格」章节、`ArcartXTab.yml` `settings.style.*` 注释。

## 9. 运营 / 调试（优先级 ★★）✅ 阶段 4-D 完成

```yaml
settings:
  debug-tools:
    dry-run: false                        # 渲染流程跑全，但不真正发包
    snapshot-dir: "data/tab/snapshots"
```

- 新增命令：
  - `/axs tab debug <player>` — 打印某玩家的输入、排序、过滤、最终 pack。
  - `/axs tab snapshot save <name>` — 落盘当前在线列表，便于复现 BUG。
  - `/axs tab snapshot load <name>` — 加载并以模拟玩家方式回放（仅 dev 用）。
- **`/axstab debug <player> [definitionId]`**（阶段 4-C）：权限 `axstab.debug` 或 OP，控制台亦可；打印玩家多键排序值、`group-key`、pinned 命中、view / page / rank、vanished / pvp / ping、`local-visible-count` / `total-visible-count`。实现：`TabPlayerCommand.handleDebug` + `TabSyncService.debugSnapshot`。
- **dry-run**（阶段 4-D）：`settings.debug-tools.dry-run: true` 后 `dispatchRefresh` 跳过 `bridge.sendPacket(...)` 与 `deliveredPayloads` 缓存更新，仅记 `ArcartXTab[dry-run] skip send ...` 日志。不影响初始计算、不会造成客户端闪烁。
- **snapshot save / load / unload / list / delete**（阶段 4-D）：
  - 落盘路径：`plugins/ArcartXSuite/data/tab/snapshots/<name>.json`（`TabSnapshotStore` + Gson），格式 `version: 2`，含 `localEntries` + `remoteSnapshots`。
  - `load` 将存档注入为 `snapshot:<name>:local` + `snapshot:<name>:<原 nodeId>` 虚拟跨服节点，在 `cleanupStaleSnapshots` 中豁免清理，必须 `unload` 显式移除。
  - 实现：`TabSnapshotStore` + `TabSyncService.snapshotLocalEntries / snapshotRemoteEntries / installSnapshotPayload / uninstallSnapshotPayload / installedSnapshotNodeIds`。`TabPlayerCommand.handleSnapshot` 提供子命令与 tab-complete。
  - **限制**：load 不重新解析 PAPI（使用落盘时的 `renderedPack`）。

## 10. 合规 / 安全（优先级 ★★）✅ 阶段 4-C 完成

```yaml
settings:
  privacy:
    hide-uuid: false                      # PAPI/pack 输出 UUID 时返回脱敏值
    hide-ip: true
```

- 实现位置：`TabPrivacyConfiguration` + `TabPlaceholderExpansion.resolveGlobalMetric`（仅在占位符层生效，建议 pack 改用 `%AXStab_uuid%` / `%AXStab_ip%` 替代 `%player_uuid%` / `%player_ip%`）。
- `{player_uuid}` 花括号占位符**不受 `hide-uuid` 影响**，因为它用于客户端 `PlayerSkin:` 头像渲染，需要保留真实 UUID。

---

## 实施分期

| 阶段 | 包含项 | 状态 |
| --- | --- | --- |
| 阶段 1 | §3 复合排序 + 过滤器 + 置顶置底 | ✅ |
| 阶段 2 | §1 多视图 + §4 分组 / 分页 | ✅ |
| 阶段 3 | §6 跨服增强（aggregate / batch / leave-grace） | ✅ |
| 阶段 4-A | §7 PAPI 输出 | ✅ |
| 阶段 4-B | 跨服协议 v2（`TabRemoteEntry` 多键 + `groupKey` + cross-server grouping/pagination） | ✅ |
| 阶段 4-C | §8 视觉增强（PAPI 暴露） + §9 `/axstab debug` + §10 隐私脱敏 | ✅ |
| 阶段 4-D | §9 `dry-run` 模式 + `/axstab snapshot save/load/unload/list/delete` | ✅ |
| 后续 | §5 点击 / 悬浮（**已确认不实现**，HUD 不支持长期菜单态） | 🟡 |

## 阶段 4-B 跨服协议 v2 实现要点

- **`TabRemoteEntry` v2**：新增 `sortValues: List<Double>` / `sortStringValues: List<String>` / `groupKey: String`，保留旧 `sortValue` / `sortStringValue` 字段作为首键投影。`TabSnapshotCodec` 编解码识别版本号字段，未携带 v2 字段时回退首键单列。
- **`broadcastLocalSnapshots`**：对每个在线玩家调用 `computeSortValuesPerKey / computeSortStringValuesPerKey / computeGroupKey`；退服宽限缓存仅保留首键值，远端 grouping 按 `""` 兜底。
- **`buildCrossServerPayload(definition, onlinePlayers, viewer)`**：合并本地与远程 `SortableEntry` 后按 `composedSortableComparator(sortKeys)` 排序；pinned 分桶仅识别本地玩家（远程一律入中段）；`maxEntries` 截断后写入 `lastSortedByDef` 供 PAPI rank/count；最后按 viewer 维度切片 + 可选 grouping 渲染。
- **`buildPayloadForViewer`**：cross-server 路径接收 viewer 上下文，与单服路径在 grouping / pagination 行为上保持一致。

## 通用约束

- **全部默认 `enabled: false`** 或保持旧默认行为，避免升级时炸现网。
- 涉及 PAPI 注册（§7）、跨服新通道（§6）、客户端额外包（§5）时，必须由对应 `enabled` 开关守护，缺少依赖时打 fine 级日志降级。
- 头像（§2）严格走 ArcartX `PlayerSkin:<uuid>` 表达式，服务端**不抓 Mojang**，不缓存皮肤纹理。
- 任何新字段需要在 `TabModuleConfiguration` / `TabDefinition` 中提供默认值，老配置文件无需修改即可继续工作。
