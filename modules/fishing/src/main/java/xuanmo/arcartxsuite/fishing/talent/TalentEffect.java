package xuanmo.arcartxsuite.fishing.talent;

import org.jetbrains.annotations.NotNull;

/**
 * 天赋效果定义。
 *
 * @param effectType 效果类型（见 talents.yml 注释）
 * @param effectValue 效果数值
 */
public record TalentEffect(
    @NotNull String effectType,
    double effectValue
) {

    /**
     * 效果类型枚举，用于类型安全匹配。
     */
    public enum Type {
        BAR_HEIGHT("bar_height"),
        BAR_DAMPING("bar_damping"),
        BAR_BOUNCE("bar_bounce"),
        BAR_FORCE("bar_force"),
        FISH_SPEED("fish_speed"),
        FISH_DWELL("fish_dwell"),
        FISH_DART("fish_dart"),
        FISH_NOISE("fish_noise"),
        FISH_EDGE("fish_edge"),
        FLEE_CHANCE("flee_chance"),
        FLEE_SPEED("flee_speed"),
        FLEE_COOLDOWN("flee_cooldown"),
        FLEE_DURATION("flee_duration"),
        PROGRESS_RATE("progress_rate"),
        PROGRESS_DRAIN("progress_drain"),
        PERFECT_WINDOW("perfect_window"),
        START_PROGRESS("start_progress"),
        XP_MULTIPLIER("xp_multiplier"),
        PRICE_MULTIPLIER("price_multiplier"),
        LEGENDARY_CHANCE("legendary_chance"),
        TREASURE_CHANCE("treasure_chance"),
        RARE_CHANCE("rare_chance"),
        XP_IN_BAR("xp_in_bar"),
        PERFECT_XP("perfect_xp"),
        CATCH_DURATION("catch_duration"),
        BAR_GRAVITY("bar_gravity"),
        BAR_CLICK_FORCE("bar_click_force"),
        FATIGUE_REDUCTION("fatigue_reduction"),
        FATIGUE_EFFICIENCY("fatigue_efficiency"),
        REST_BOOST("rest_boost");

        private final String key;

        Type(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Type fromKey(String key) {
            for (Type t : values()) {
                if (t.key.equals(key)) return t;
            }
            return null;
        }
    }
}
