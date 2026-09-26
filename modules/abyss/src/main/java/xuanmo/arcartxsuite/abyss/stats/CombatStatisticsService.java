package xuanmo.arcartxsuite.abyss.stats;

import java.util.Map;
import java.util.UUID;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;

/**
 * 战斗统计服务：伤害/承伤/治疗/击杀 记录（数据存在 AbyssInstance 上，
 * 事件由监听器/attributeBridge 转发到这里）。
 */
public final class CombatStatisticsService {

    private final AbyssModuleConfiguration.StatisticsSection cfg;

    public CombatStatisticsService(AbyssModuleConfiguration.StatisticsSection cfg) {
        this.cfg = cfg;
    }

    public boolean enabled() { return cfg.enabled(); }
    public boolean autoShow() { return cfg.autoShow(); }

    public void recordDamageDealt(AbyssInstance i, UUID player, double amount) {
        if (cfg.enabled() && cfg.trackDamage()) i.recordDamage(player, amount);
    }

    public void recordDamageTaken(AbyssInstance i, UUID player, double amount) {
        if (cfg.enabled() && cfg.trackDamageTaken()) i.recordDamageTaken(player, amount);
    }

    public void recordHealing(AbyssInstance i, UUID player, double amount) {
        if (cfg.enabled() && cfg.trackHealing()) i.recordHealing(player, amount);
    }

    /** 生成个人统计文本（结算/统计 UI 用）。 */
    public Map<String, Double> summary(AbyssInstance i, UUID player) {
        return Map.of(
            "damage", i.getDamageDealt().getOrDefault(player, 0.0),
            "damage_taken", i.getDamageTaken().getOrDefault(player, 0.0),
            "healing", i.getHealingDone().getOrDefault(player, 0.0),
            "kills", (double) i.getKillCounts().getOrDefault(player, 0)
        );
    }
}
