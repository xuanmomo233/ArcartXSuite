package xuanmo.arcartxsuite.abyss.stamina;

import java.util.UUID;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;

/**
 * 体力系统：进本消耗，按间隔自动回复。
 */
public final class StaminaManager {

    private final AbyssModuleConfiguration.StaminaSection cfg;
    private final PlayerDataManager data;

    public StaminaManager(AbyssModuleConfiguration.StaminaSection cfg, PlayerDataManager data) {
        this.cfg = cfg;
        this.data = data;
    }

    public boolean enabled() { return cfg.enabled(); }
    public int max() { return cfg.max(); }
    public boolean ready(UUID player) { return data.ready(player); }

    /** 结算离线回复后取当前体力。 */
    public int getStamina(UUID player) {
        if (!cfg.enabled()) return cfg.max();
        int stamina = data.getStamina(player, cfg.max());
        // 离线回复结算
        long last = data.getStaminaLastRegen(player);
        if (last > 0 && cfg.regenIntervalSeconds() > 0) {
            long elapsed = (System.currentTimeMillis() - last) / 1000;
            int ticks = (int) (elapsed / cfg.regenIntervalSeconds());
            if (ticks > 0 && stamina < cfg.max()) {
                stamina = Math.min(cfg.max(), stamina + ticks * cfg.regenAmount());
                data.setStamina(player, stamina,
                    last + ticks * cfg.regenIntervalSeconds() * 1000L);
            }
        }
        return stamina;
    }

    /** 消耗体力。 */
    public boolean consume(UUID player, int cost) {
        if (!ready(player)) return false;
        if (!cfg.enabled() || cost <= 0) return true;
        int cur = getStamina(player);
        if (cur < cost) return false;
        data.setStamina(player, cur - cost, System.currentTimeMillis());
        return true;
    }

    /** 增加体力（命令/奖励）。 */
    public void add(UUID player, int amount) {
        if (!cfg.enabled()) return;
        int cur = getStamina(player);
        data.setStamina(player, Math.min(cfg.max(), cur + amount), System.currentTimeMillis());
    }

    public void set(UUID player, int value) {
        data.setStamina(player, Math.min(cfg.max(), Math.max(0, value)), System.currentTimeMillis());
    }
}
