package xuanmo.arcartxsuite.bridge;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.item.VanillaItemNameTable;

/**
 * 原版物品中文名称解析桥接默认实现。
 * <p>
 * 解析优先级：自定义显示名 → 附魔书/药水子类型 → Material 静态表。
 * 附魔与药水子类型译名参考 Minecraft Wiki 官方中文译名。
 */
public final class DefaultVanillaItemNameBridge implements VanillaItemNameBridge {

    // 附魔 namespaced key（小写，如 "minecraft:sharpness"）→ 中文名
    private static final Map<String, String> ENCHANTMENTS = Map.ofEntries(
        Map.entry("minecraft:protection", "保护"),
        Map.entry("minecraft:fire_protection", "火焰保护"),
        Map.entry("minecraft:feather_falling", "摔落保护"),
        Map.entry("minecraft:blast_protection", "爆炸保护"),
        Map.entry("minecraft:projectile_protection", "弹射物保护"),
        Map.entry("minecraft:respiration", "水下呼吸"),
        Map.entry("minecraft:aqua_affinity", "水下速掘"),
        Map.entry("minecraft:thorns", "荆棘"),
        Map.entry("minecraft:depth_strider", "深海探索者"),
        Map.entry("minecraft:frost_walker", "冰霜行者"),
        Map.entry("minecraft:binding_curse", "绑定诅咒"),
        Map.entry("minecraft:vanishing_curse", "消失诅咒"),
        Map.entry("minecraft:sharpness", "锋利"),
        Map.entry("minecraft:smite", "亡灵杀手"),
        Map.entry("minecraft:bane_of_arthropods", "节肢杀手"),
        Map.entry("minecraft:knockback", "击退"),
        Map.entry("minecraft:fire_aspect", "火焰附加"),
        Map.entry("minecraft:looting", "抢夺"),
        Map.entry("minecraft:sweeping", "横扫之刃"),
        Map.entry("minecraft:efficiency", "效率"),
        Map.entry("minecraft:silk_touch", "精准采集"),
        Map.entry("minecraft:unbreaking", "耐久"),
        Map.entry("minecraft:fortune", "时运"),
        Map.entry("minecraft:power", "力量"),
        Map.entry("minecraft:punch", "冲击"),
        Map.entry("minecraft:flame", "火矢"),
        Map.entry("minecraft:infinity", "无限"),
        Map.entry("minecraft:luck_of_the_sea", "海之眷顾"),
        Map.entry("minecraft:lure", "饵钓"),
        Map.entry("minecraft:loyalty", "忠诚"),
        Map.entry("minecraft:impaling", "穿刺"),
        Map.entry("minecraft:riptide", "激流"),
        Map.entry("minecraft:channeling", "引雷"),
        Map.entry("minecraft:multishot", "多重射击"),
        Map.entry("minecraft:quick_charge", "快速装填"),
        Map.entry("minecraft:piercing", "穿透"),
        Map.entry("minecraft:mending", "经验修补"),
        Map.entry("minecraft:soul_speed", "灵魂疾行"),
        Map.entry("minecraft:swift_sneak", "迅捷潜行")
    );

    // 药水 PotionType.name() → 效果中文名（不含载体后缀）
    private static final Map<PotionType, String> POTION_EFFECTS = Map.ofEntries(
        Map.entry(PotionType.NIGHT_VISION, "夜视"),
        Map.entry(PotionType.INVISIBILITY, "隐身"),
        Map.entry(PotionType.JUMP, "跳跃"),
        Map.entry(PotionType.FIRE_RESISTANCE, "抗火"),
        Map.entry(PotionType.SPEED, "迅捷"),
        Map.entry(PotionType.SLOWNESS, "迟缓"),
        Map.entry(PotionType.WATER_BREATHING, "水肺"),
        Map.entry(PotionType.INSTANT_HEAL, "治疗"),
        Map.entry(PotionType.INSTANT_DAMAGE, "伤害"),
        Map.entry(PotionType.POISON, "剧毒"),
        Map.entry(PotionType.REGEN, "再生"),
        Map.entry(PotionType.WEAKNESS, "虚弱"),
        Map.entry(PotionType.STRENGTH, "力量"),
        Map.entry(PotionType.SLOW_FALLING, "缓降"),
        Map.entry(PotionType.LUCK, "幸运"),
        Map.entry(PotionType.TURTLE_MASTER, "神龟")
    );

    // 基础药水（无效果）PotionType → 基础名（不含"药水"后缀）
    private static final Map<PotionType, String> BASE_POTIONS = Map.of(
        PotionType.WATER, "水瓶",
        PotionType.MUNDANE, "平凡的药水",
        PotionType.THICK, "浓稠的药水",
        PotionType.AWKWARD, "粗制的药水"
    );

    private volatile boolean available = true;

    @Override
    public boolean isAvailable() {
        return available;
    }

    /** 标记桥接可用性（宿主关闭时调用） */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public @NotNull String translate(@NotNull Material material) {
        return VanillaItemNameTable.translate(material);
    }

    @Override
    public @NotNull String resolveDisplayName(@NotNull ItemStack itemStack) {
        if (itemStack == null) {
            return "空气";
        }
        ItemMeta meta = itemStack.getItemMeta();
        // 优先返回自定义显示名
        if (meta != null && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        Material material = itemStack.getType();
        if (material == null || material == Material.AIR) {
            return "空气";
        }
        // 附魔书：按首个附魔类型拼名
        if (material == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta bookMeta) {
            String enchantName = resolveEnchantmentBookName(bookMeta);
            if (enchantName != null) {
                return enchantName;
            }
        }
        // 药水/药箭：按 PotionType 拼名
        String potionName = resolvePotionName(material, meta);
        if (potionName != null) {
            return potionName;
        }
        // 回退到 Material 静态表
        return VanillaItemNameTable.translate(material);
    }

    /** 解析附魔书名称：首个附魔译名 + "附魔书"，多个附魔回退到"附魔书" */
    private static String resolveEnchantmentBookName(EnchantmentStorageMeta bookMeta) {
        if (bookMeta.hasStoredEnchants()) {
            var entries = bookMeta.getStoredEnchants();
            if (entries != null && !entries.isEmpty()) {
                if (entries.size() == 1) {
                    var entry = entries.entrySet().iterator().next();
                    String zh = ENCHANTMENTS.get(entry.getKey().getKey().toString());
                    if (zh != null) {
                        return zh + "附魔书";
                    }
                }
                // 多个附魔或未命中译名表，回退到 Material 译名（"附魔书"）
                return null;
            }
        }
        return null;
    }

    /** 解析药水/药箭名称：按 PotionType + 载体类型拼名 */
    private static String resolvePotionName(Material material, ItemMeta meta) {
        if (meta == null || !(meta instanceof PotionMeta potionMeta)) {
            return null;
        }
        PotionType potionType;
        try {
            potionType = potionMeta.getBasePotionData().getType();
        } catch (NoSuchMethodError | NullPointerException ignored) {
            // 跨版本兼容或无药水数据时回退
            return null;
        }
        if (potionType == null) {
            return null;
        }
        // 基础药水（无效果）
        String baseName = BASE_POTIONS.get(potionType);
        if (baseName != null) {
            return withBasePrefix(material, baseName);
        }
        // 有效果的药水
        String effect = POTION_EFFECTS.get(potionType);
        if (effect == null) {
            return null;
        }
        return switch (material) {
            case POTION -> effect + "药水";
            case SPLASH_POTION -> "喷溅" + effect + "药水";
            case LINGERING_POTION -> "滞留" + effect + "药水";
            case TIPPED_ARROW -> effect + "药箭";
            default -> null;
        };
    }

    /** 基础药水按载体加前缀：水瓶/喷溅型水瓶/滞留型水瓶 等 */
    private static String withBasePrefix(Material material, String baseName) {
        return switch (material) {
            case SPLASH_POTION -> "喷溅型" + baseName;
            case LINGERING_POTION -> "滞留型" + baseName;
            // TIPPED_ARROW 的基础药水回退到 Material 译名（药箭）
            default -> baseName;
        };
    }
}
