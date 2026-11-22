package xuanmo.arcartxsuite.prop.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import xuanmo.arcartxsuite.api.command.CommandSanitizer;
import xuanmo.arcartxsuite.api.modattr.ModAttributeModifier.Operation;
import xuanmo.arcartxsuite.api.mythiclib.MythicLibStatKeyNormalizer;
import xuanmo.arcartxsuite.api.util.AttributeResolver;

/** 道具加载时生成的效果定义。使用时直接执行已验证的计划。 */
public record PropEffects(List<Consumer<Player>> instant, List<AttributeGroup> attributes, int maxDurationSeconds) {
    private static final Set<String> EFFECT_KEYS = Set.of(
        "commands", "messages", "food", "health", "experience", "potions", "attributes");
    private static final Set<String> HEALTH_KEYS = Set.of("points", "max-health-percent");
    private static final Set<String> POTION_KEYS = Set.of("id", "level", "duration", "ambient", "particles", "icon");
    private static final Set<String> MODIFIER_KEYS = Set.of("id", "amount", "operation");

    public PropEffects {
        instant = List.copyOf(instant);
        attributes = List.copyOf(attributes);
    }

    public enum Provider {
        ATTRIBUTE_PLUS("attributeplus", "AttributePlus"), CRANE_ATTRIBUTE("craneattribute", "CraneAttribute"),
        MYTHIC_LIB("mythiclib", "MythicLib"), SYMPHONY("symphony", "Symphony"), MOD("mod", "Mod Attributes");

        private final String key;
        private final String displayName;

        Provider(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String key() { return key; }
        public String displayName() { return displayName; }
        boolean usesLines() { return this == ATTRIBUTE_PLUS || this == CRANE_ATTRIBUTE; }
    }

    public record AttributeGroup(Provider provider, String id, int durationSeconds,
                                 List<String> lines, List<Modifier> modifiers) {
        public AttributeGroup {
            lines = List.copyOf(lines);
            modifiers = List.copyOf(modifiers);
        }
    }

    public record Modifier(String id, PropAmount amount, Operation operation) {}

    static PropEffects load(ConfigurationSection definition) {
        ConfigurationSection section = section(definition, "effects");
        checkKeys(section.getKeys(false), EFFECT_KEYS, "effects");
        List<Consumer<Player>> instant = new ArrayList<>();
        if (section.contains("health")) {
            instant.add(health(section(section, "health")));
        }
        if (section.contains("food")) {
            int amount = integer(section.get("food"), "effects.food", Integer.MIN_VALUE);
            instant.add(player -> player.setFoodLevel((int) Math.max(0, Math.min(20, (long) player.getFoodLevel() + amount))));
        }
        if (section.contains("experience")) {
            int amount = integer(section.get("experience"), "effects.experience", 0);
            instant.add(player -> player.giveExp(amount));
        }
        int maxDuration = 0;
        if (section.contains("potions")) {
            int index = 0;
            for (Object value : list(section.get("potions"), "effects.potions")) {
                PotionEffect effect = potion(fields(value, POTION_KEYS, "effects.potions[" + index++ + "]"));
                instant.add(player -> player.addPotionEffect(effect));
                maxDuration = Math.max(maxDuration, effect.getDuration() / 20);
            }
        }
        if (section.contains("commands")) {
            for (String command : lines(section.get("commands"), "effects.commands")) {
                instant.add(player -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), render(command, player)));
            }
        }
        if (section.contains("messages")) {
            for (String message : lines(section.get("messages"), "effects.messages")) {
                instant.add(player -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', render(message, player))));
            }
        }
        List<AttributeGroup> attributes = section.contains("attributes")
            ? attributes(section(section, "attributes")) : List.of();
        for (AttributeGroup group : attributes) maxDuration = Math.max(maxDuration, group.durationSeconds());
        return new PropEffects(instant, attributes, maxDuration);
    }

    private static Consumer<Player> health(ConfigurationSection section) {
        checkKeys(section.getKeys(false), HEALTH_KEYS, section.getCurrentPath());
        double points = section.contains("points") ? number(section.get("points"), "health.points") : 0.0;
        double fraction = section.contains("max-health-percent")
            ? number(section.get("max-health-percent"), "health.max-health-percent") / 100.0 : 0.0;
        return player -> {
            double maximum = AttributeResolver.getMaxHealth(player);
            player.setHealth(Math.min(maximum, Math.max(0.0, player.getHealth() + points + maximum * fraction)));
        };
    }

    private static PotionEffect potion(Map<?, ?> fields) {
        String id = string(fields.get("id"), "potions.id");
        PotionEffectType type = PotionEffectType.getByKey(namespacedId(id, "potions.id"));
        if (type == null) throw new IllegalArgumentException("未知药水效果 " + id);
        int level = fields.containsKey("level") ? integer(fields.get("level"), "potions.level", 1) : 1;
        if (level > 256) throw new IllegalArgumentException("potions.level 必须在 1 到 256 之间");
        int seconds = integer(fields.get("duration"), "potions.duration", 1);
        if (seconds > Integer.MAX_VALUE / 20) throw new IllegalArgumentException("potions.duration 超出 Bukkit 可表示的秒数");
        int duration = seconds * 20;
        return new PotionEffect(type, duration, level - 1,
            bool(fields, "ambient", false), bool(fields, "particles", true), bool(fields, "icon", true));
    }

    private static List<AttributeGroup> attributes(ConfigurationSection root) {
        Set<String> supported = new HashSet<>();
        List<AttributeGroup> result = new ArrayList<>();
        for (Provider provider : Provider.values()) {
            String key = provider.key();
            supported.add(key);
            if (!root.contains(key)) continue;
            ConfigurationSection providerSection = section(root, key);
            for (String id : providerSection.getKeys(false)) {
                ConfigurationSection group = section(providerSection, id);
                String path = group.getCurrentPath();
                String entriesKey = provider.usesLines() ? "lines" : "modifiers";
                checkKeys(group.getKeys(false), Set.of("duration", entriesKey), path);
                int duration = integer(group.get("duration"), path + ".duration", 1);
                List<?> entries = list(group.get(entriesKey), path + "." + entriesKey);
                if (entries.isEmpty()) throw new IllegalArgumentException(path + "." + entriesKey + " 不能为空");
                result.add(provider.usesLines()
                    ? new AttributeGroup(provider, id, duration, lines(entries, path + ".lines"), List.of())
                    : new AttributeGroup(provider, id, duration, List.of(), modifiers(provider, entries, path)));
            }
        }
        checkKeys(root.getKeys(false), supported, root.getCurrentPath());
        return result;
    }

    private static List<Modifier> modifiers(Provider provider, List<?> entries, String path) {
        List<Modifier> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            String field = path + ".modifiers[" + i + "]";
            Map<?, ?> values = fields(entries.get(i), MODIFIER_KEYS, field);
            String id = string(values.get("id"), field + ".id");
            id = provider == Provider.MYTHIC_LIB ? MythicLibStatKeyNormalizer.normalize(id) : namespacedId(id, field + ".id").toString();
            if (provider == Provider.SYMPHONY && id.length() > 128) {
                throw new IllegalArgumentException(field + ".id 超出 Symphony 的 128 字符限制");
            }
            if (id.isBlank() || !ids.add(id)) throw new IllegalArgumentException(field + " 属性 ID 无效或重复 " + id);
            PropAmount amount = PropAmount.load(values.get("amount"), field + ".amount");
            Operation operation = values.containsKey("operation")
                ? operation(string(values.get("operation"), field + ".operation")) : Operation.ADDITION;
            if (provider == Provider.MYTHIC_LIB && operation != Operation.ADDITION) {
                throw new IllegalArgumentException(field + " 所选属性提供方不支持此运算");
            }
            result.add(new Modifier(id, amount, operation));
        }
        return result;
    }

    static NamespacedKey namespacedId(String raw, String field) {
        NamespacedKey key = raw.contains(":") ? NamespacedKey.fromString(raw) : null;
        if (key == null) throw new IllegalArgumentException(field + " 必须为命名空间 ID");
        return key;
    }

    private static Operation operation(String value) {
        return switch (value) {
            case "add" -> Operation.ADDITION;
            case "multiply-base" -> Operation.MULTIPLY_BASE;
            case "multiply-total" -> Operation.MULTIPLY_TOTAL;
            default -> throw new IllegalArgumentException("未知属性运算 " + value);
        };
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection child = parent.getConfigurationSection(key);
        if (child == null) throw new IllegalArgumentException(parent.getCurrentPath() + " " + key + " 必须为配置节");
        return child;
    }

    private static void checkKeys(Set<?> keys, Set<String> supported, String path) {
        for (Object key : keys) {
            if (!supported.contains(key)) throw new IllegalArgumentException(path + " 含未知字段 " + key);
        }
    }

    private static Map<?, ?> fields(Object value, Set<String> supported, String path) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + " 必须为对象");
        checkKeys(map.keySet(), supported, path);
        return map;
    }

    private static List<?> list(Object value, String path) {
        if (value instanceof List<?> result) return result;
        throw new IllegalArgumentException(path + " 必须为列表");
    }

    private static List<String> lines(Object value, String path) {
        return list(value, path).stream().map(entry -> string(entry, path)).toList();
    }

    private static String string(Object value, String path) {
        if (value instanceof String text && !text.isBlank()) return text.trim();
        throw new IllegalArgumentException(path + " 必须为非空字符串");
    }

    private static double number(Object value, String path) {
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) return number.doubleValue();
        throw new IllegalArgumentException(path + " 必须为有限数值");
    }

    private static int integer(Object value, String path, int minimum) {
        double number = number(value, path);
        if (number < minimum || number > Integer.MAX_VALUE || number != Math.rint(number)) {
            throw new IllegalArgumentException(path + " 必须为 " + minimum + " 到 " + Integer.MAX_VALUE + " 之间的整数");
        }
        return (int) number;
    }

    private static boolean bool(Map<?, ?> fields, String key, boolean fallback) {
        if (!fields.containsKey(key)) return fallback;
        if (fields.get(key) instanceof Boolean value) return value;
        throw new IllegalArgumentException("potions." + key + " 必须为布尔值");
    }

    private static String render(String value, Player player) {
        return value.contains("{player}")
            ? value.replace("{player}", CommandSanitizer.validatePlayerName(player.getName())) : value;
    }
}
