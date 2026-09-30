package xuanmo.arcartxsuite.api.potion;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 药水效果名称解析器（公共）。
 * <p>
 * 将配置中的效果名解析为 {@link PotionEffectType}，支持三种写法：
 * <ul>
 *   <li>新版枚举名：{@code haste}、{@code resistance}、{@code jump_boost} 等（不区分大小写）</li>
 *   <li>旧版枚举名：{@code fast_digging}、{@code damage_resistance} 等（1.20.2+ 已改名，做兼容映射）</li>
 *   <li>中文别名：{@code 急迫}、{@code 抗性提升} 等</li>
 * </ul>
 * 新旧名称做双向回退查找：新端上旧名可解析，旧端（1.20.1 等）上新名亦可回退到旧枚举名。
 */
@ApiStability.Stable
public final class PotionEffectTypeResolver {

    private PotionEffectTypeResolver() {
    }

    /** 旧版枚举名 → 新版枚举名 */
    private static final Map<String, String> LEGACY_TO_MODERN = Map.ofEntries(
        Map.entry("SLOW", "SLOWNESS"),
        Map.entry("FAST_DIGGING", "HASTE"),
        Map.entry("SLOW_DIGGING", "MINING_FATIGUE"),
        Map.entry("INCREASE_DAMAGE", "STRENGTH"),
        Map.entry("JUMP", "JUMP_BOOST"),
        Map.entry("CONFUSION", "NAUSEA"),
        Map.entry("HEAL", "INSTANT_HEALTH"),
        Map.entry("HARM", "INSTANT_DAMAGE"),
        Map.entry("DAMAGE_RESISTANCE", "RESISTANCE"),
        Map.entry("UNLUCK", "BAD_LUCK")
    );

    /** 新版枚举名 → 旧版枚举名（在 1.20.1 等旧端上回退查找） */
    private static final Map<String, String> MODERN_TO_LEGACY = Map.ofEntries(
        Map.entry("SLOWNESS", "SLOW"),
        Map.entry("HASTE", "FAST_DIGGING"),
        Map.entry("MINING_FATIGUE", "SLOW_DIGGING"),
        Map.entry("STRENGTH", "INCREASE_DAMAGE"),
        Map.entry("JUMP_BOOST", "JUMP"),
        Map.entry("NAUSEA", "CONFUSION"),
        Map.entry("INSTANT_HEALTH", "HEAL"),
        Map.entry("INSTANT_DAMAGE", "HARM"),
        Map.entry("RESISTANCE", "DAMAGE_RESISTANCE"),
        Map.entry("BAD_LUCK", "UNLUCK")
    );

    /** 中文别名 → 新版枚举名 */
    private static final Map<String, String> CHINESE_ALIASES;

    static {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("速度", "SPEED");
        aliases.put("缓慢", "SLOWNESS");
        aliases.put("减速", "SLOWNESS");
        aliases.put("急迫", "HASTE");
        aliases.put("急速挖掘", "HASTE");
        aliases.put("挖掘疲劳", "MINING_FATIGUE");
        aliases.put("力量", "STRENGTH");
        aliases.put("虚弱", "WEAKNESS");
        aliases.put("抗性提升", "RESISTANCE");
        aliases.put("抗性", "RESISTANCE");
        aliases.put("生命恢复", "REGENERATION");
        aliases.put("回血", "REGENERATION");
        aliases.put("恢复", "REGENERATION");
        aliases.put("防火", "FIRE_RESISTANCE");
        aliases.put("抗火", "FIRE_RESISTANCE");
        aliases.put("水下呼吸", "WATER_BREATHING");
        aliases.put("跳跃提升", "JUMP_BOOST");
        aliases.put("跳跃", "JUMP_BOOST");
        aliases.put("反胃", "NAUSEA");
        aliases.put("恶心", "NAUSEA");
        aliases.put("失明", "BLINDNESS");
        aliases.put("致盲", "BLINDNESS");
        aliases.put("隐身", "INVISIBILITY");
        aliases.put("夜视", "NIGHT_VISION");
        aliases.put("饥饿", "HUNGER");
        aliases.put("中毒", "POISON");
        aliases.put("凋零", "WITHER");
        aliases.put("伤害吸收", "ABSORPTION");
        aliases.put("饱和", "SATURATION");
        aliases.put("发光", "GLOWING");
        aliases.put("飘浮", "LEVITATION");
        aliases.put("漂浮", "LEVITATION");
        aliases.put("缓降", "SLOW_FALLING");
        aliases.put("幸运", "LUCK");
        aliases.put("霉运", "BAD_LUCK");
        aliases.put("不幸", "BAD_LUCK");
        aliases.put("潮涌能量", "CONDUIT_POWER");
        aliases.put("潮涌", "CONDUIT_POWER");
        aliases.put("海豚的恩惠", "DOLPHINS_GRACE");
        aliases.put("海豚恩惠", "DOLPHINS_GRACE");
        aliases.put("村庄英雄", "HERO_OF_THE_VILLAGE");
        aliases.put("黑暗", "DARKNESS");
        aliases.put("不祥之兆", "BAD_OMEN");
        aliases.put("袭击之兆", "RAID_OMEN");
        aliases.put("试炼之兆", "TRIAL_OMEN");
        aliases.put("蓄风", "WIND_CHARGED");
        aliases.put("盘丝", "WEAVING");
        aliases.put("渗浆", "OOZING");
        aliases.put("寄生", "INFESTED");
        aliases.put("瞬间治疗", "INSTANT_HEALTH");
        aliases.put("瞬间伤害", "INSTANT_DAMAGE");
        CHINESE_ALIASES = Map.copyOf(aliases);
    }

    /**
     * 解析配置中的效果名。
     *
     * @param rawName 原始名称（英文枚举名或中文别名）
     * @return 对应的效果类型，无法识别时返回 {@code null}
     */
    public static @Nullable PotionEffectType resolve(@Nullable String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // 中文别名先归一到新版枚举名，再走双向兼容查找
        String canonical = CHINESE_ALIASES.get(trimmed);
        String upper = (canonical != null ? canonical : trimmed).toUpperCase(Locale.ROOT);

        PotionEffectType type = getByName(upper);
        if (type != null) {
            return type;
        }
        String modern = LEGACY_TO_MODERN.get(upper);
        if (modern != null) {
            type = getByName(modern);
            if (type != null) {
                return type;
            }
        }
        String legacy = MODERN_TO_LEGACY.get(upper);
        if (legacy != null) {
            return getByName(legacy);
        }
        return null;
    }

    private static @Nullable PotionEffectType getByName(String name) {
        try {
            return PotionEffectType.getByName(name);
        } catch (Throwable ignored) {
            // 个别混合端/新版本可能在此处抛异常而非返回 null
            return null;
        }
    }
}
