# RGB 渐变文本

## 功能定位

通过 PlaceholderAPI 输出渐变 / 扫光效果文本。`entries.<id>.text` 自身可以再嵌套其他 PAPI。

## 依赖

- 必需：ArcartX、**PlaceholderAPI**

## 启用步骤

```yaml
modules:
  rgb:
    enabled: true
    password: "AXS-RGB@2026#Ready"
```

## 关键配置（`ArcartXRGB.yml`）

```yaml
entries:
  welcome:
    text: "欢迎来到服务器"
    colors:
      - "#FF0000"
      - "#00FF00"
      - "#0000FF"
    mode: gradient
```

## PAPI

前缀：`%arcartrgb_*%`（**必须安装 PlaceholderAPI**）

| 占位符 | 说明 |
| --- | --- |
| `%arcartrgb_<条目ID>%` | 渲染配置中定义的渐变文本条目。条目 ID 对应 `entries.<id>` |

示例：`%arcartrgb_welcome%` → 渲染 `entries.welcome.text` 的渐变文本

## 命令

> 权限：`arcartxsuite.admin`

| 命令 | 说明 |
| --- | --- |
| `/AXS rgb status` | 查看 RGB 模块状态 |
| `/AXS rgb reload` | 重载动态渐变配置 |
