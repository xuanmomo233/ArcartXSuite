---
trigger: always_on
description: ArcartXSuite 智能诊断的更新与文档同步规范。当涉及修改任何模块或宿主的 yml 默认配置、ValidationRule、Migration、SyncPolicy、currentVersion 时必须遵循。
---

# 智能配置诊断 — 更新与同步规范

ArcartXSuite 的 `ConfigDiagnosticEngine` 自动覆盖大多数版本变动场景，但有些改动**必须**同步更新诊断声明，否则用户会丢值或体验下降。每次改动都应同步更新 wiki 文档，根据改动大小判断修改版本号，例如较小改动应该从1.0.2升级到1.0.3，较大改动应该从1.0.2升级到1.1.0

## 何时需要更新诊断声明

下表是必须遵守的判定规则。**Cascade 在编辑 yml 默认配置或 Java 配置 spec 时必须主动检查并提示用户**。

| 改动类型 | 是否需要更新声明 | 必须做的事 |
|---|---|---|
| 仅在 jar 默认 yml 新增字段 | ❌ 不需要 | 引擎通过 `addedPaths` 自动识别为 `JAR_NEW` |
| 仅在 jar 默认 yml 删除字段 | ❌ 不需要 | 引擎通过 `removedPaths` 自动识别为 `USER_DEPRECATED` |
| 仅修改 jar 默认值 | ❌ 不需要 | 引擎通过对比识别 `USER_MODIFIED` / `JAR_DEFAULT` |
| 修改注释 / 排版 / 大小写 | ❌ 不需要 | `SyncPolicy` 不剪除即可保留 |
| **重命名字段**（`a.b` → `c.d`） | ✅ **必须** | 写 `migrations/<from>-<to>.yml` + 升 `currentVersion()` |
| **移动字段**（嵌套层级变化） | ✅ **必须** | 写 migration + 升版本号 |
| **改变字段语义**（含义/单位变了但名字不变） | ✅ **必须** | 写 migration `value-map` + 升版本号 |
| **新增/修改字段类型约束** | ✅ **必须** | 在 `mainConfigValidations()` / `additionalConfigSpecs()` 中加 `ValidationRule` |
| **新增/修改字段范围约束**（min/max） | ✅ **必须** | 加 `ValidationRule` |
| **新增/修改字段枚举约束** | ✅ **必须** | 加 `ValidationRule` |
| **新增动态节** | ✅ **必须** | 在 `defaultSyncPolicy()` 中调用 `dynamicSection(...)`，避免误删 |

## 改动时的强制流程

每次涉及上述任何"必须"项时，Cascade 必须按以下顺序执行（不允许跳过）：

1. **修改 jar 默认 yml**：在 `modules/<module>/src/main/resources/<name>.yml` 或 `src/main/resources/config.yml`
2. **如重命名/移动字段**：
   - 在 `modules/<module>/src/main/resources/migrations/<from>-<to>.yml` 添加 `rename` / `move` 操作
   - 在模块入口（继承 `AbstractAXSModule` 的类）覆写 `currentConfigVersion()`，递增数字
3. **如有新增值约束**：在模块入口覆写 `mainConfigValidations()` 或 `additionalConfigSpecs()`，新增 `ValidationRule`
4. **如有新增动态节**：在模块入口覆写 `defaultSyncPolicy()`，调用 `SyncPolicy.builder().dynamicSection("path").build()`
5. **同步更新 wiki 文档**：
   - `docs/guide/config-management.md` — 如果改动影响用户使用流程
   - `docs/architecture/config-autofix.md` — 如果改动影响诊断架构
6. **完整构建验证**：`.\gradlew.bat build`（如改动涉及共享契约还要 `.\gradlew.bat test`）

## 反模式（禁止）

- ❌ 重命名字段但不写 migration → 用户值会丢失
- ❌ 加新字段不带默认值，且没有 `ValidationRule.required()` → 用户配置不完整时无法发现
- ❌ 直接在用户运行时配置中"约定俗成"地处理某字段，而不在 `ValidationRule` 中声明 → 诊断报告无法体现
- ❌ 修改任何上述项却不更新 wiki → 后续维护者无从追溯
- ❌ 删除已发布的 migration 文件 → 老版本用户升级路径会断

## 验证清单（每次改动结束前自检）

- [ ] jar 默认 yml 的改动是否涉及"必须更新声明"的场景？
- [ ] 如果涉及，对应的 `ValidationRule` / migration 是否已添加？
- [ ] 模块的 `currentConfigVersion()` 是否需要递增？
- [ ] `docs/appendix/changelog.md` 是否记录了本次改动？
- [ ] `docs/guide/config-management.md` 或 `docs/architecture/config-autofix.md` 是否需要同步？
- [ ] `.\gradlew.bat build` 是否通过？
