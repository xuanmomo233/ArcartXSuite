# Pickup 拾取提示

## 功能定位

物品拾取时在 ArcartX HUD 上弹出提示动画，可自定义外观和持续时间。

## 依赖

- 必需：ArcartX

## 启用步骤

```yaml
modules:
  pickup:
    enabled: true
    password: "AXS-Pickup@2026#Ready"
```

## 关键配置（`ArcartXPickup.yml`）

```yaml
settings:
  debug: false
  ui-id: "AXS:pickup_hud"
  register-ui-on-enable: true
  overwrite-ui-file: false
  display-duration-ticks: 60
```

## 命令

```
/AXS pickup status
/AXS pickup reload
```
