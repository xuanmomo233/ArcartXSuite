# 安装

## 环境要求

| 项 | 要求 |
| --- | --- |
| 服务端 | Spigot / Paper / 其分支，**MC 1.20.1** |
| Java | **17** 或更高 |
| ArcartX 服务端插件 | 必装，版本与客户端 MOD 对齐 |
| 客户端 MOD | 玩家必须安装 ArcartX 客户端 MOD |
| 数据库 | 默认 SQLite；如需 MySQL，准备好访问凭证 |

## 步骤

### 1. 安装 ArcartX

按 [ArcartX 官方文档](https://wiki.arcartx.com/docs) 安装服务端插件，并向玩家分发客户端 MOD。

### 2. 放入 AXS jar

```
plugins/
├── ArcartX-x.x.x.jar
└── ArcartXSuite-4.0.0.jar
```

### 3. 启动一次，生成默认资源

启动 / 重启服务端。AXS 会：

- 把 jar 内 `.axb` 解密、解压成默认 YAML 释放到 `plugins/ArcartXSuite/`
- 生成 `config.yml`（总开关 + 各模块密码）和各模块配置
- 生成 `arcartx/ui/*.yml` UI 模板
- 全部模块**默认 `enabled: false`**

### 4. 验证

```
/AXS status
```

所有行都是 `disabled / locked` 是正常的 — 因为还没填密码、没开模块。

::: info 首次启动后不需要继续做什么
建议保持服务端运行，**直接编辑 `plugins/ArcartXSuite/config.yml`** 进入下一步。
:::

## 升级 / 替换 jar

- 直接覆盖 jar 然后重启；**不会丢已有数据库 / 已编辑过的 YAML**。
- 新版本可能新增配置键；升级后建议备份后重启，让 `YamlConfigSynchronizer` 合并缺失字段。

## 卸载

- 停服 → 删除 jar。
- 数据保留在 `plugins/ArcartXSuite/` 不会自动清理。
