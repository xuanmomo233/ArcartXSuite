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

```
/AXS tab status
/AXS tab reload
```
