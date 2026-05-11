# Title 称号

## 功能定位

分组称号系统：有效期/永久、属性加成、聊天/TAB 前缀、ArcartX UI 菜单、PAPI 全量输出。

## 依赖

- 必需：ArcartX
- 可选：PlaceholderAPI、AttributePlus / CraneAttribute / MythicLib

## 启用步骤

```yaml
modules:
  title:
    enabled: true
    password: "AXS-Title@2026#Ready"
```

## 命令

### 管理命令（权限：`arcartxsuite.admin`）

| 命令 | 说明 |
| --- | --- |
| `/AXS title status` | 查看称号模块、数据库和缓存状态 |
| `/AXS title reload` | 重载称号配置、UI 和玩家状态 |
| `/AXS title give <玩家> <称号ID> <时长>` | 向玩家发放称号。时长如 `permanent`（永久）、`7d`、`12h`、`30m` |
| `/AXS title revoke <玩家> <称号ID>` | 收回玩家的指定称号 |
| `/AXS title open <玩家>` | 为在线玩家打开称号管理界面 |

### 玩家命令（权限：`arcartxsuite.title.use`）

| 命令 | 说明 |
| --- | --- |
| `/title` 或 `/title open` | 打开称号管理菜单 |
| `/title equip <称号ID>` | 装备指定称号，前缀/后缀/属性立即生效 |
| `/title unequip <组ID\|all>` | 卸下某个组的已装备称号，`all` 卸下全部 |
| `/title hide <称号ID>` | 隐藏指定称号（不在菜单显示但仍拥有） |
| `/title unhide <称号ID>` | 取消隐藏 |

## PAPI

前缀：`%AXStitle_*%`

### 统计

| 占位符 | 说明 |
| --- | --- |
| `%AXStitle_owned_count%` | 拥有的称号总数 |
| `%AXStitle_hidden_count%` | 已隐藏的称号数 |

### 聊天 / Tab 前后缀

| 占位符 | 说明 |
| --- | --- |
| `%AXStitle_chat_<组ID>_prefix%` | 该组已装备称号的聊天前缀 |
| `%AXStitle_chat_<组ID>_suffix%` | 该组已装备称号的聊天后缀 |
| `%AXStitle_tab_<组ID>_prefix%` | 该组已装备称号的 Tab 前缀 |
| `%AXStitle_tab_<组ID>_suffix%` | 该组已装备称号的 Tab 后缀 |

### 装备状态

| 占位符 | 说明 |
| --- | --- |
| `%AXStitle_equipped_<组ID>_id%` | 该组已装备称号的 ID |
| `%AXStitle_equipped_<组ID>_name%` | 该组已装备称号的显示名称 |
| `%AXStitle_equipped_<组ID>_group%` | 该组已装备称号所属组的显示名 |
| `%AXStitle_equipped_<组ID>_quality%` | 该组已装备称号的品质名 |

### 称号查询

| 占位符 | 说明 |
| --- | --- |
| `%AXStitle_owned_<称号ID>%` | 是否拥有指定称号（`true`/`false`） |
| `%AXStitle_hidden_<称号ID>%` | 是否隐藏了指定称号 |
| `%AXStitle_remaining_<称号ID>%` | 剩余有效时间（毫秒），永久返回 `永久` |

### 属性加成

| 占位符 | 说明 |
| --- | --- |
| `%AXStitle_display_attr_<属性键>%` | 当前展示中称号的属性加成值 |
| `%AXStitle_collection_attr_<属性键>%` | 收藏图鉴属性加成 |
| `%AXStitle_total_attr_<属性键>%` | 展示 + 收藏的总属性加成 |
