package xuanmo.arcartxsuite.api.potion;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link PotionEffectTypeResolver} 单元测试。
 * <p>
 * 测试类路径使用 1.20.1 API（旧枚举名时代），因此新枚举名走
 * MODERN_TO_LEGACY 回退路径——恰好覆盖了双向兼容中较难验证的一侧。
 */
class PotionEffectTypeResolverTest {

    /** 1.21+ 才引入的效果名：在 1.20.1 测试类路径上允许解析失败 */
    private static final Set<String> POST_1_20_NAMES = Set.of(
        "RAID_OMEN", "TRIAL_OMEN", "WIND_CHARGED", "WEAVING", "OOZING", "INFESTED"
    );

    /**
     * 模拟服务端启动时对效果类型的注册：
     * {@code byName} 只在服务器实现调用 {@code registerPotionEffectType} 时填充，
     * 纯类路径下为空表，需要手动把静态常量按「小写名称 → 实例」灌入。
     */
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void bootstrapPotionTypes() throws Exception {
        Field byNameField = PotionEffectType.class.getDeclaredField("byName");
        byNameField.setAccessible(true);
        Map<String, PotionEffectType> byName = (Map<String, PotionEffectType>) byNameField.get(null);
        for (Field field : PotionEffectType.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && PotionEffectType.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                PotionEffectType type = (PotionEffectType) field.get(null);
                if (type != null) {
                    byName.put(field.getName().toLowerCase(Locale.ENGLISH), type);
                }
            }
        }
    }

    @Test
    void resolvesNullAndBlankToNull() {
        assertNull(PotionEffectTypeResolver.resolve(null));
        assertNull(PotionEffectTypeResolver.resolve(""));
        assertNull(PotionEffectTypeResolver.resolve("   "));
    }

    @Test
    void resolvesUnknownToNull() {
        assertNull(PotionEffectTypeResolver.resolve("不存在的"));
        assertNull(PotionEffectTypeResolver.resolve("definitely_not_a_potion"));
    }

    @Test
    void resolvesModernNameViaLegacyFallback() {
        // 1.20.1 API 不存在 HASTE，需要回退到 FAST_DIGGING
        assertSame(PotionEffectType.FAST_DIGGING, PotionEffectTypeResolver.resolve("haste"));
        assertSame(PotionEffectType.FAST_DIGGING, PotionEffectTypeResolver.resolve("HASTE"));
        assertSame(PotionEffectType.DAMAGE_RESISTANCE, PotionEffectTypeResolver.resolve("resistance"));
        assertSame(PotionEffectType.HEAL, PotionEffectTypeResolver.resolve("instant_health"));
    }

    @Test
    void resolvesLegacyNameDirectly() {
        assertSame(PotionEffectType.INCREASE_DAMAGE, PotionEffectTypeResolver.resolve("increase_damage"));
        assertSame(PotionEffectType.INCREASE_DAMAGE, PotionEffectTypeResolver.resolve("INCREASE_DAMAGE"));
        // 对应的新版名也应解析到同一类型
        assertSame(PotionEffectType.INCREASE_DAMAGE, PotionEffectTypeResolver.resolve("strength"));
        assertSame(PotionEffectType.FAST_DIGGING, PotionEffectTypeResolver.resolve("fast_digging"));
    }

    @Test
    void resolvesChineseAliases() {
        assertSame(PotionEffectType.SPEED, PotionEffectTypeResolver.resolve("速度"));
        assertSame(PotionEffectType.FAST_DIGGING, PotionEffectTypeResolver.resolve("急迫"));
        assertSame(PotionEffectType.DAMAGE_RESISTANCE, PotionEffectTypeResolver.resolve("抗性提升"));
        assertSame(PotionEffectType.DAMAGE_RESISTANCE, PotionEffectTypeResolver.resolve("抗性"));
        assertSame(PotionEffectType.INCREASE_DAMAGE, PotionEffectTypeResolver.resolve("力量"));
        assertSame(PotionEffectType.WATER_BREATHING, PotionEffectTypeResolver.resolve("水下呼吸"));
        assertSame(PotionEffectType.REGENERATION, PotionEffectTypeResolver.resolve("生命恢复"));
        assertSame(PotionEffectType.FIRE_RESISTANCE, PotionEffectTypeResolver.resolve("防火"));
    }

    @Test
    void legacyToModernMapTargetsAreRealNames() throws Exception {
        // 回归防护：映射目标必须是真实存在的效果名（防止 HEAL→INSTANT_HEAL 这类笔误）
        Map<?, ?> legacyToModern = staticMapField("LEGACY_TO_MODERN");
        Map<?, ?> modernToLegacy = staticMapField("MODERN_TO_LEGACY");
        for (Map.Entry<?, ?> entry : legacyToModern.entrySet()) {
            assertRealName((String) entry.getKey());
            assertRealName((String) entry.getValue());
        }
        for (Map.Entry<?, ?> entry : modernToLegacy.entrySet()) {
            assertRealName((String) entry.getKey());
            assertRealName((String) entry.getValue());
        }
    }

    @Test
    void chineseAliasTargetsAreRealNames() throws Exception {
        Map<?, ?> aliases = staticMapField("CHINESE_ALIASES");
        for (Map.Entry<?, ?> entry : aliases.entrySet()) {
            assertRealName((String) entry.getValue());
        }
    }

    private static void assertRealName(String name) {
        if (POST_1_20_NAMES.contains(name)) {
            return; // 1.21+ 效果在 1.20.1 测试类路径上不存在
        }
        // 通过 resolve() 验证：新旧任一时代的名字被接受即为真实名，
        // 直接 getByName 会在 1.20.1 类路径上误杀所有新枚举名
        assertNotNull(PotionEffectTypeResolver.resolve(name),
            "PotionEffectTypeResolver 引用了不存在的效果名: " + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> staticMapField(String fieldName) throws Exception {
        Field field = PotionEffectTypeResolver.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<String, String>) field.get(null);
    }
}
