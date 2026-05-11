# CombatEffect 战斗特效

## 功能定位

战斗视觉反馈一站式方案，包含两大能力：

- **击杀特效** — 监听玩家攻击 / 击杀活体实体，把上下文发包给 ArcartX UI 播放击杀特效或弹出击杀提示。不限 PVP，可监听任意活体
- **伤害飘字** — 把伤害 / 治疗事件桥接到 ArcartX 伤害显示，按来源拆分：原始伤害、玩家伤害、暴击、治疗等

## 依赖

- 必需：ArcartX
- 可选：MythicLib / MMOItems、CraneAttribute、AttributePlus、MythicMobs

## 启用步骤

```yaml
modules:
  combateffect:
    enabled: true
    password: "AXS-CombatEffect@2026#Ready"
```

## 配置

CombatEffect 的配置分为击杀特效和伤害飘字两部分。

### 击杀特效（`ArcartXCombatEffect.yml`）

```yaml
settings:
  debug: false
  ui-id: "AXS:kill_effect_hud"
  register-ui-on-enable: true

triggers:
  player-kill:
    enabled: true
    recipient: attacker
    pack: "{killer_name};{victim_name};{weapon}"
```

### 伤害飘字

伤害 / 治疗事件的数字显示配置：

```yaml
digis-display:
  debug: false
  damage:
    enabled: true
    default-config-id: "default_damage"
  heal:
    enabled: true
    default-config-id: "default_heal"
  source-detection:
    exact-mode: true
```

## 命令

> 权限：`arcartxsuite.admin`

| 命令 | 说明 |
| --- | --- |
| `/AXS combateffect status` | 查看战斗特效模块是否启用和配置加载状态 |
| `/AXS combateffect reload` | 重载战斗特效配置 |
