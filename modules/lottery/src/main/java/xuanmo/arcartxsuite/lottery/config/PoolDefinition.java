package xuanmo.arcartxsuite.lottery.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 单个奖池的定义。
 * <p>
 * 描述奖池的基本元数据（ID、类型、显示名等）、消耗配置，
 * 以及根据类型持有的 {@link GachaPoolConfig} 或 {@link CasePoolConfig}。
 *
 * @param id          奖池唯一标识
 * @param type        奖池类型（GACHA 或 CASE）
 * @param enabled     是否启用
 * @param displayName 显示名称
 * @param description 描述文本
 * @param upItemIcon  UP 物品图标定义（结构化，支持 custom-model-data / NBT 贴图等）
 * @param cost        消耗配置
 * @param gacha       GACHA 奖池配置，仅 {@code type == GACHA} 时非空
 * @param caseConfig  CASE 奖池配置，仅 {@code type == CASE} 时非空
 */
public record PoolDefinition(
    @NotNull String id,
    @NotNull PoolType type,
    boolean enabled,
    @NotNull String displayName,
    @NotNull String description,
    @Nullable IconDefinition upItemIcon,
    @NotNull CostConfig cost,
    @Nullable GachaPoolConfig gacha,
    @Nullable CasePoolConfig caseConfig
) {

    /**
     * 从 YAML 配置节加载奖池定义。
     *
     * @param section 配置节
     * @param logger  日志记录器
     * @return 解析后的 {@link PoolDefinition} 实例，缺少 id 时返回 {@code null}
     * @throws IllegalArgumentException 当 type 字段非合法枚举值时抛出
     */
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
        String description = section.getString("description", "");
        IconDefinition upItemIcon = IconDefinition.load(section.getConfigurationSection("up-item-icon"));
        CostConfig cost = CostConfig.load(section.getConfigurationSection("cost"));

        GachaPoolConfig gacha = null;
        CasePoolConfig caseConfig = null;

        if (type == PoolType.GACHA) {
            gacha = GachaPoolConfig.load(section.getConfigurationSection("gacha"), logger);
        } else {
            caseConfig = CasePoolConfig.load(section.getConfigurationSection("case"), logger);
        }

        return new PoolDefinition(id, type, enabled, displayName, description, upItemIcon, cost, gacha, caseConfig);
    }
}
