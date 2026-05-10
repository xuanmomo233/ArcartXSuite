# 模块密码门控

AXS 的 `config.yml` 里每个模块**都有两个开关同时控制是否启动**：

```yaml
modules:
  announcer:
    enabled: true
    password: "AXS-Announcer@2026#Ready"
```

- `enabled: true`：**意图开启**这个模块
- `password`：**模块发行密码**，必须与当前 AXS 版本约定的字符串完全一致

**两者必须同时通过**才会启动模块。

## 为什么要密码？

- 防止未授权的服务器直接复制使用
- 让作者在每个版本独立校验模块开放状态
- 给服主一个**显式的双保险开关**

## 密码从哪里来

默认 `config.yml` 里**已经填好了**合法密码：

```yaml
modules:
  announcer:
    enabled: false
    password: "AXS-Announcer@2026#Ready"   # ← 默认就是这个，不要改
  entitytracker:
    enabled: false
    password: "AXS-EntityTracker@2026#Ready"
  # ... 共 17 个主模块
```

**你只需要把 `enabled` 改成 `true`，密码字段保持不动即可**。

::: warning 不要把密码写在公共仓库
建议把 `plugins/ArcartXSuite/config.yml` 加进 `.gitignore`。
:::

## 校验流程

源码：`xuanmo.arcartxsuite.security.ModulePasswordAuthenticator`

1. AXS 启动 / `reload` 时逐模块读取 `enabled` 与 `password`
2. 内部用 SHA-256 对比产出 `ValidationResult`（`OK / DISABLED / LOCKED`）
3. 非 `OK` 则跳过模块启用

## 常见错误

| `/AXS status` 输出 | 原因 | 处理 |
| --- | --- | --- |
| `disabled` | `enabled: false` | 改为 `true`，然后 reload |
| `password locked` | 密码不匹配 | 比对最新 jar 中的 `config.yml` |
| `bridge missing` | ArcartX 反射桥未就绪 | 确认 ArcartX 插件已正确加载 |
