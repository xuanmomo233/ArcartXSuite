package xuanmo.arcartxsuite.lottery.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PoolDefinition(
    @NotNull String id,
    @NotNull PoolType type,
    boolean enabled,
    @NotNull String displayName,
    @NotNull CostConfig cost,
    @Nullable GachaPoolConfig gacha,
    @Nullable CasePoolConfig caseConfig
) {

    public static PoolDefinition load(ConfigurationSection section, Logger logger) {
        String id = section.getString("id");
        if (id == null || id.isBlank()) {
            logger.warning("奖池配置缺少 id 字段，已跳过");
            return null;
        }

        String typeStr = section.getString("type", "GACHA");
        PoolType type = PoolType.valueOf(typeStr.toUpperCase());
        boolean enabled = section.getBoolean("enabled", true);
        String displayName = section.getString("display-name", id);
        CostConfig cost = CostConfig.load(section.getConfigurationSection("cost"));

        GachaPoolConfig gacha = null;
        CasePoolConfig caseConfig = null;

        if (type == PoolType.GACHA) {
            gacha = GachaPoolConfig.load(section.getConfigurationSection("gacha"), logger);
        } else {
            caseConfig = CasePoolConfig.load(section.getConfigurationSection("case"), logger);
        }

        return new PoolDefinition(id, type, enabled, displayName, cost, gacha, caseConfig);
    }
}
