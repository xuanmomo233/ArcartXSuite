# Prop 快捷道具

## 功能定位

把物品标记为"快捷道具"，绑定 ArcartX 客户端按键效果和临时属性加成。

## 依赖

- 必需：ArcartX
- 可选：MythicLib / AttributePlus（属性加成）

## 启用步骤

```yaml
modules:
  prop:
    enabled: true
    password: "AXS-Prop@2026#Ready"
```

## 命令

> 权限：`arcartxsuite.admin`

| 命令 | 说明 |
| --- | --- |
| `/AXS prop status` | 查看 Prop 模块状态和已加载道具列表 |
| `/AXS prop reload` | 重载 Prop 配置 |
| `/AXS prop set <道具ID>` | 把指定道具 ID 写入执行者的当前 Prop 状态，用于调试 |
