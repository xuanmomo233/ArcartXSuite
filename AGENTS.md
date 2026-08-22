# ArcartXSuite 重构计划

## 已完成的重构（批次 1-7 第 5 项）

| 批次 | 内容 | 状态 |
|---|---|---|
| 1 | 清理死代码 + 安全（StorageDescriptor.toString 密码脱敏、删除重复文件、reloading 死代码、ApiStability.@Deprecated 重命名） | ✅ |
| 2 | CurrencyBridgeManager 异常日志 + 命令注入校验 | ✅ |
| 3 | AxsScheduler Folia 反射缓存 + 单位注释 + wrap(null) 空操作 | ✅ |
| 4 | MessageProvider hex 颜色支持 + {0} 占位符修复 | ✅ |
| 5 | ScriptCondition 别名收敛 warning + 可读序列化格式 + SyncPolicy ** 递归通配 | ✅ |
| 6 | MigrationService 抽离 + deletePlayerData/deleteAllPlayerData 事务 | ✅ |
| 7-1 | ModuleDescriptor 改 record | ✅ |
| 7-2 | EventBus payload 类型安全（新增 publish(String,Player,Object)，BusEvent 加 typedPayload()） | ✅ |
| 7-3 | ModuleContext 按 ISP 拆 6 个子接口（InfrastructureContext/BridgeContext/SecurityContext/StorageContext/ResourceContext/RegistrationContext） | ✅ |
| 7-4 | AbstractAXSModule 合并配置钩子为对象（19→12 钩子，新建 ModuleConfig/ModuleUiSpec/PacketHandlerSpec record） | ✅ |
| 7-5 | AbstractModuleRepository 重构（方案 D-1：分离连接池管理和数据访问，消除 null 哨兵双模式） | ✅ |
| 8 | 锐评-02 事实核查 + 全档修复（A/B/C 三档 + ModuleRegistry 拆分） | ✅ |

## 批次 7-5 完成详情 — AbstractModuleRepository 重构（方案 D-1）

### 实际改动

| 改动 | 文件数 | 说明 |
|---|---|---|
| StorageDescriptor 加 withTablePrefix(String) | 1 | 共享模式下用本体描述符拷贝出带模块前缀的副本 |
| StorageManager 接口加 resolveModuleDataSource | 1 | 统一数据源解析入口（共享/自建），取代旧双模式构造 |
| AbstractModuleRepository 重构 | 1 | 单构造方法 (poolName, DataSource, descriptor, dataFolder, logger)，删除 isSharedMode()/双构造/双字段 |
| StorageManagerImpl 实现 resolveModuleDataSource | 1 | 自建池缓存到 selfManagedSources，closeModuleDataSource 统一关闭 |
| SecondaryPasswordService + SuiteCoreImpl | 2 | 适配新构造，通过 resolveModuleDataSource 获取数据源 |
| 18 个子类简化 | 18 | 删双构造/删 config 字段/删 isSharedMode() 调用，统一单构造方法 |
| 18 个 Module.java 适配 | 18 | startService() 改用 resolveModuleDataSource + 新构造方法，stopService() 改用 closeModuleDataSource |

总计约 42 个文件。

### 设计决策

1. **tablePrefix 携带在 descriptor 中**：共享模式用 `storageManager.getDescriptor().withTablePrefix(模块前缀)` 构造带正确前缀的描述符。Group A 模块（market/lottery/loginview）自建模式也用 `withTablePrefix(config.tablePrefix())`；Group B 模块自建模式用 `config.toDescriptor()` 原样。
2. **dataFolder 保留在 repo 中**：migrateData() 内部传给 MigrationService 用于 SQLite 迁移目标文件路径，构造方法加 dataFolder 参数，migrateData 签名不变。
3. **Aubade 不受影响**：Aubade 的 Repository 是独立泛型接口，不继承 AbstractModuleRepository，从工作量估算中移除。
4. **shutdown() 改为空操作**：连接池生命周期由 StorageManager 统一管控，Module.stopService() 调用 `storageManager.closeModuleDataSource(moduleId)` 释放 SQLite 文件锁。

### 注意事项

- `MigrationService.migrate()` 已抽离，不受影响
- `deletePlayerData` / `deleteAllPlayerData` 事务已加，不受影响
- `migrateData()` 方法委托给 MigrationService，重构后仍保留
- `isSharedMode()` 方法已删除，外部调用方（market/lottery）已改为直接用 `tablePrefix()`
- `descriptor` 字段保留（`isMysql()` / `autoIncrement()` / `currentDescriptor()` 等方法依赖它）
- Service 层的 `repository.shutdown()` 调用保留（现在是空操作，安全无害），SQLite 文件锁释放由 Module 层 `closeModuleDataSource` 接管

## 批次 8 完成详情 — 锐评-02 事实核查 + 全档修复

### 事实核查结论

对锐评-02-axs-core 的 48 条问题逐条核实：41 条属实（85%）、6 条部分属实、1 条已过时（SecondaryPasswordService 共享池问题已被批次 7-5 修复）、0 条误判。锐评"安全工程满分、软件工程不及格"的总结判断成立。

### A 档：低风险纯重构（5 项）

| 改动 | 文件 | 说明 |
|---|---|---|
| A1 启动步骤编号重排 | SuiteCoreImpl.java | 0/2/3/4/5/7.5/7.6/8/7/9/7/8/10/11 → 0-14 顺序 |
| A2 日志双路径统一 | SuiteCoreImpl.java | consoleInfo/Warn/Error 改走 getLogger()，删除 CONSOLE_PREFIX 常量 |
| A3 注释乱码修复 | JvmAntiDebug.java | line 89/104 的 `????` 乱码替换为中文注释 |
| A4 SQLite 池注释修正 | StorageManagerImpl.java | 类注释说 maximumPoolSize=1 但代码写 2，修正注释为 2（WAL 模式支持并发读） |
| A5 tacz 字段注释 | BridgeLifecycleManager.java | 加注释说明 taczCombatBridge 非 final 的原因（可选桥接，tryInitialize 可能返回 null） |

### B 档：中风险功能修复（6 项）

| 改动 | 文件 | 说明 |
|---|---|---|
| B1 JDBC 参数可配化 | StorageManagerImpl.java + config.yml | 新增 JdbcParams 内部类，use-ssl/allow-public-key-retrieval/server-timezone/character-encoding/use-unicode/jdbc-params 均可配置 |
| B2 玩家名注入校验 | ClientPacketGuard.java | 新增 sanitizePlayerName()，仅允许 [A-Za-z0-9_]，防止离线模式特殊字符名导致 RCE |
| B3 二级密码安全加固 | SecondaryPasswordService.java | verify() 加空密码拒绝；synchronized 从实例级改为按 UUID 粒度（playerLocks map） |
| B4 缓存 TTL | AccountTypeServiceImpl.java | officialUuidCache 加 CacheEntry<T> 包装（value + cachedAt），TTL 24 小时，过期后重新查询 |
| B5 签名空密钥拒绝 | CrossServerService.java | shouldSign=true 但 secret 为空时，publishEnvelope 拒绝发送（fail-closed） |
| B6 签名 payload v2 | ModuleSignatureVerifier.java | payload 从 `id:version:mainClass` 扩展为换行分隔的 6 字段（加 depends/softDepends/externalDepends），防止依赖声明篡改 |

### C 档：高风险安全层调整（4 项）

| 改动 | 文件 | 说明 |
|---|---|---|
| C1 Unsafe 注入加固 | ProtectionInit.java | 哨兵探针扫描范围 256→512 字节（覆盖 Java 8~21 布局）；加 Java 21 兼容性警告注释 |
| C2 ClassLoader 注释修正 | ProtectedClassLoader.java | 类注释说 parent-first 但代码是 child-first，修正注释为 child-first 并说明风险 |
| C3 Attach API 误杀优化 | JvmAntiDebug.java | isAttachApiActive() 加 10 秒文件年龄检测，仅活跃 attach 才视为威胁，避免误杀 Spark/async-profiler |
| C4 IV 限制注释 | NativeBridge.java | 加注释说明 IV 从文件头读取的安全限制及 native 层改造方向 |

### D 档：ModuleRegistry 上帝类拆分

| 改动 | 文件 | 说明 |
|---|---|---|
| CapabilityRegistry | 新建 166 行 | 管理 capabilities/purgeables/migratables 注册表，从 ModuleRegistry 提取 |
| ClientPacketRouter | 新建 116 行 | 管理客户端包处理器和初始化处理器的注册与路由，从 ModuleRegistry 提取 |
| GlobalBridgeFactory | 新建 270 行 | 管理所有全局桥接单例的创建、初始化和查询，从 ModuleRegistry 提取 |
| ModuleRegistry 重构 | 1523→1092 行（-28%） | 委托给三个组件，公共 API 不变 |

### 验证

- 编译通过：`.\gradlew.bat :axs-core:compileJava`
- 针对性测试全部通过（7 个测试用例）：sanitizePlayerName 合法/注入名、payload v2 字段、CacheEntry TTL、JdbcParams 默认/自定义参数、拆分组件类存在

### 设计选择（保持现状，不改）

以下属于设计选择而非 bug，经确认保持现状：
- ModuleSignatureVerifier 默认不验签（公钥未配置时 return true，向后兼容）
- ConfigDiagnosticEngine modules 节不清理废弃键（用户自定义模块开关不被误删）
- suppressPlaceholderApiNoise 改 PAPI logger（控制台输出干净）



```bash
# 编译全部
.\gradlew.bat compileJava compileKotlin --no-daemon

# 编译 axs-api
.\gradlew.bat :axs-api:compileJava --no-daemon

# 编译 axs-core
.\gradlew.bat :axs-core:compileJava --no-daemon
```

