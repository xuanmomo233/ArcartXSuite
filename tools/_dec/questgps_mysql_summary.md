# QuestGPS MySQL 集成 — 会话上下文摘要

## 目标
让 QuestGPS 模块内置支持 Chemdah 的 MySQL 数据库，**不依赖** `Chemdah-1.1.33-FREE-patched.jar`。

## 已完成的调研

### 1. 类存在性确认
- `Chemdah-1.1.33-FREE.jar` **不含** `DatabaseSQL`（MySQL 实现类）
- `Chemdah-1.1.33-FREE-patched.jar` 含 `DatabaseSQL`，且已适配 FREE 1.1.33 的 `Relational` 抽象方法签名
- FREE JAR **不含** `taboolib/module/database/*` 类（运行时由 TabooLib 加载）

### 2. patched JAR 中 `DatabaseSQL` 的关键签名
```java
class DatabaseSQL extends Relational<SQL, Long, Long>

String getQuestKey()          // 返回 "quest"
boolean isDuplicateKeyUpdateSupported()  // true

Long getUserId(Player)
Long getQuestId(Player, Quest)
CompletableFuture<Long> createUser(PlayerProfile, Player)
void createQuest(PlayerProfile, long userId, Quest)

PlayerProfile select(Player)
void update(Player, PlayerProfile)
Relational<SQL, Long, Long> setup()   // 额外创建 tableUser
```

### 3. 表结构（MySQL）
| 表 | 列 | 说明 |
|---|---|---|
| `{prefix}_user` | `id`(AI,PK), `name`, `uuid`, `time` | patched 特有，FREE Relational 无此表 |
| `{prefix}_user_data` | `id`(AI,PK), `user`(INT), `key`, `value`, `mode` | |
| `{prefix}_variables` | `id`(AI,PK), `name`, `data`, `mode` | |
| `{prefix}_quest` | `id`(AI,PK), `user`(INT), `quest`, `mode` | |
| `{prefix}_quest_data` | `id`(AI,PK), `quest`(INT), `key`, `value`, `mode` | |

### 4. `Relational`（FREE 1.1.33）的抽象方法
- `getHost()` / `getTableUserData()` / `getTableVariables()`
- `getQuestKey()` / `isDuplicateKeyUpdateSupported()`
- `newQuestTable(String)`
- `getUserId(Player)` / `getQuestId(Player, Quest)`
- `CompletableFuture<UserId> createUser(PlayerProfile, Player)`
- `void createQuest(PlayerProfile, UserId, Quest)`
- `select(Player)` / `update(Player, PlayerProfile)`（来自 `Database`）
- `variables()` / `selectVariable0(String)` / `updateVariable0(String, String)` / `releaseVariable0(String)`

## 待办清单（TODO）

1. **反编译完成** — 已提取 patched `DatabaseSQL` 的完整实现逻辑
2. **stub-list / stub-build** — 需创建 `taboolib/module/database` 的 stub 类供编译期使用（FREE JAR 无此类）
3. **kotlin** — 需给 `questgps` 模块添加 Kotlin 插件支持（`DatabaseMySQL` 需大量 Kotlin lambda / DSL）
4. **database-mysql** — 编写 `DatabaseMySQL`（继承 `Relational<SQL, Long, Long>`，复用 patched 逻辑）
5. **loader-config** — 改造 `ChemdahDatabaseSqlLoader` 直接实例化 `DatabaseMySQL`，扩展 MySQL 连接配置
6. **docs** — 更新 `ArcartXQuestGPS.yml` 默认配置 + wiki 文档 + 诊断声明
7. **build** — `gradlew build` 验证

## 关键决策
- **用 Kotlin 实现 `DatabaseMySQL`**：因 `Relational` 的 `update`/`updateQuest` 等通用逻辑已封装，子类只需实现 `getUserId`/`getQuestId`/`createUser`/`createQuest`/`select`/`setup` 等。这些方法在 patched `DatabaseSQL` 中大量依赖 Kotlin lambda 与 TabooLib DSL，用 Java 写将极其冗长。
- **保留 `tableUser` 表**：patched `DatabaseSQL` 额外维护 `tableUser`（`setup()` 中手动创建），`getUserId` 依赖它。为保持与 patched JAR 的数据库兼容，`DatabaseMySQL` 也需此表。
- **stub 方案**：在 `questgps` 模块内创建 `ink.ptms.chemdah.taboolib.module.database` 包的 stub Java 类，并在 `build.gradle.kts` 的 `jar` 任务中排除该包，避免与运行时 TabooLib 冲突。

## 参考文件
- 反编译产物：`D:\IDEA\project\ArcartXSuite\tools\_dec\patched\ink\ptms\chemdah\core\database\DatabaseSQL.java`
- 字节码分析：`D:\IDEA\project\ArcartXSuite\tools\_dec\bytecode\*.txt`
- 配置：`d:\IDEA\project\ArcartXSuite\modules\questgps\src\main\resources\ArcartXQuestGPS.yml`
- 现有 Loader：`d:\IDEA\project\ArcartXSuite\modules\questgps\src\main\java\xuanmo\arcartxsuite\questgps\chemdah\database\ChemdahDatabaseSqlLoader.java`

## 下一步（建议）
1. 给 `modules/questgps/build.gradle.kts` 添加 `kotlin("jvm")` 插件和 Kotlin 标准库依赖
2. 创建 stub 包：`ink.ptms.chemdah.taboolib.module.database.{Table, Host, SQL, HostSQL, ColumnTypeSQL, ColumnBuilder, ActionInsert, ActionSelect, ActionUpdate, Filter, JoinFilter, ExecutableSource, FileToHostKt}`
3. 在 `questgps/src/main/kotlin` 下创建 `DatabaseMySQL.kt`，基于 patched `DatabaseSQL` 反编译逻辑实现
4. 修改 `ChemdahDatabaseSqlLoader` 和 `QuestGpsModule` 以直接注册 `DatabaseMySQL`
5. 扩展配置并运行 `gradlew build`
