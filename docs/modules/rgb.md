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

前缀：`%arcartrgb_*%`

```
%arcartrgb_welcome%       # 渲染 entries.welcome.text
%arcartrgb_<entryId>%     # 任意条目
```

## 命令

```
/AXS rgb status
/AXS rgb reload
```
