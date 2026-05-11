# Tab 在线列表

## 功能定位

通过 ArcartX TAB UI 渲染自定义在线列表，支持排序、分组、PAPI 变量。

## 依赖

- 必需：ArcartX、**PlaceholderAPI**

## 启用步骤

```yaml
modules:
  tab:
    enabled: true
    password: "AXS-Tab@2026#Ready"
```

## 关键配置（`ArcartXTab.yml`）

```yaml
settings:
  debug: false
  ui-id: "AXS:tab_list"
  register-ui-on-enable: true
  refresh-interval-ticks: 20
```

## 命令

> 权限：`arcartxsuite.admin`

| 命令 | 说明 |
| --- | --- |
| `/AXS tab status` | 查看 Tab 模块状态 |
| `/AXS tab reload` | 重载 Tab 配置并刷新在线玩家显示 |
