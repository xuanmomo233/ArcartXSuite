package xuanmo.arcartxsuite.mail.service;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailAttachmentType;
import xuanmo.arcartxsuite.mail.model.MailPresetDefinition;
import xuanmo.arcartxsuite.mail.model.MailPresetCdkDefinition;
import xuanmo.arcartxsuite.mail.util.MailItemSerializer;

public final class MailPresetLoader {

    private MailPresetLoader() {
    }

    public static Map<String, MailPresetDefinition> loadPresets(File directory, Logger logger, int maxAttachments) throws IOException {
        return loadPresets(directory, logger, maxAttachments, null, null);
    }

    /** 加载预设；传入桥接时物品附件的默认描述使用中文名，传入物品来源注册表时支持外部物品库。 */
    public static Map<String, MailPresetDefinition> loadPresets(File directory, Logger logger, int maxAttachments,
            @Nullable ItemSourceRegistry itemSourceRegistry,
            @Nullable xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge) throws IOException {
        Map<String, MailPresetDefinition> result = new LinkedHashMap<>();
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return Map.of();
        }

        for (File file : files) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection presetSection = configuration.getConfigurationSection("preset");
            if (presetSection == null) {
                continue;
            }
            String id = string(presetSection.getString("id", file.getName().replace(".yml", ""))).toLowerCase(Locale.ROOT);
            boolean enabled = presetSection.getBoolean("enabled", true);
            String displayName = string(presetSection.getString("display-name", id));
            String subject = string(presetSection.getString("subject", displayName));
            String body = string(presetSection.getString("body", ""));
            Duration expiresAfter = Duration.ofDays(Math.max(1, presetSection.getLong("expires-after-days", 15L)));
            List<MailAttachment> attachments = loadAttachments(presetSection, logger, maxAttachments, id, itemSourceRegistry, vanillaItemNameBridge);
            List<String> claimCommands = presetSection.getStringList("claim-commands");
            List<ScriptCondition> claimConditions = new ArrayList<>(
                ScriptConditionsLoader.loadClaimConditions(presetSection)
            );
            List<MailPresetCdkDefinition> cdks = loadCdks(presetSection, logger, id);
            result.put(id, new MailPresetDefinition(id, enabled, displayName, subject, body, expiresAfter, attachments, claimCommands, claimConditions, cdks));
        }
        return Map.copyOf(result);
    }

    private static List<MailPresetCdkDefinition> loadCdks(ConfigurationSection presetSection, Logger logger, String presetId) {
        List<Map<?, ?>> rawCdks = new ArrayList<>(presetSection.getMapList("cdks"));
        ConfigurationSection singleCdk = presetSection.getConfigurationSection("cdk");
        if (singleCdk != null) {
            rawCdks.add(singleCdk.getValues(false));
        }
        if (rawCdks.isEmpty()) {
            return List.of();
        }

        ArrayList<MailPresetCdkDefinition> cdks = new ArrayList<>();
        for (Map<?, ?> rawCdk : rawCdks) {
            String code = MailService.normalizeCdkCode(mapString(rawCdk, "code", ""));
            if (code.isBlank() || "AUTO".equals(code)) {
                logger.warning("邮件预设 '" + presetId + "' 包含空 CDK 或 auto CDK，配置预设必须使用固定 code，已跳过。");
                continue;
            }
            int maxClaims = parseInt(mapString(rawCdk, "max-claims", mapString(rawCdk, "maxClaims", "1")), 1);
            if (maxClaims <= 0) {
                logger.warning("邮件预设 '" + presetId + "' 的 CDK '" + code + "' max-claims 必须大于 0，已跳过。");
                continue;
            }
            Instant expiresAt = parseInstant(mapString(rawCdk, "expires-at", ""));
            Duration expiresAfter = parseDuration(mapString(rawCdk, "expires-after", mapString(rawCdk, "ttl", "")));
            boolean enabled = parseBoolean(rawCdk.get("enabled"), true);
            cdks.add(new MailPresetCdkDefinition(code, enabled, maxClaims, expiresAt, expiresAfter));
        }
        return List.copyOf(cdks);
    }

    private static List<MailAttachment> loadAttachments(ConfigurationSection presetSection, Logger logger, int maxAttachments, String presetId,
            @Nullable ItemSourceRegistry itemSourceRegistry,
            @Nullable xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge) {
        java.util.ArrayList<MailAttachment> attachments = new java.util.ArrayList<>();

        for (Map<?, ?> rawCurrency : presetSection.getMapList("currency-attachments")) {
            if (attachments.size() >= maxAttachments) {
                break;
            }
            String currencyId = mapString(rawCurrency, "currency", "money").trim().toLowerCase(Locale.ROOT);
            double amount = parseAmount(mapString(rawCurrency, "amount", "0"));
            if (amount <= 0.0D) {
                continue;
            }
            String description = mapString(rawCurrency, "description", currencyId + " " + trimDecimal(amount));
            attachments.add(new MailAttachment(0L, attachments.size(), MailAttachmentType.CURRENCY, "", currencyId, amount, description));
        }

        List<Map<?, ?>> rawItems = presetSection.getMapList("item-attachments");
        for (Map<?, ?> rawItem : rawItems) {
            if (attachments.size() >= maxAttachments) {
                break;
            }
            String source = mapString(rawItem, "source", "minecraft").trim().toLowerCase(Locale.ROOT);
            String itemId = mapString(rawItem, "id", "");
            if (itemId.isBlank()) {
                logger.warning("邮件预设 '" + presetId + "' 的物品附件缺少 id 字段，已跳过。");
                continue;
            }
            int amount = Math.max(1, parseInt(mapString(rawItem, "amount", "1"), 1));
            ItemStack itemStack = generateItemStack(source, itemId, amount, rawItem, itemSourceRegistry, logger, presetId);
            if (itemStack == null) {
                continue;
            }
            try {
                attachments.add(
                    new MailAttachment(
                        0L,
                        attachments.size(),
                        MailAttachmentType.ITEM,
                        MailItemSerializer.serialize(itemStack),
                        "",
                        0.0D,
                        resolveDescription(itemStack, vanillaItemNameBridge)
                    )
                );
            } catch (IOException exception) {
                logger.warning("邮件预设 '" + presetId + "' 的物品附件序列化失败: " + exception.getMessage());
            }
        }
        return List.copyOf(attachments);
    }

    /**
     * 根据 source + id 生成物品堆；支持 minecraft / mythic / neige / overture / mmoitems 来源。
     * 物品库桥接不可用时返回 null 并打印警告。
     */
    @Nullable
    private static ItemStack generateItemStack(String source, String itemId, int amount, Map<?, ?> rawItem,
            @Nullable ItemSourceRegistry itemSourceRegistry, Logger logger, String presetId) {
        try {
            ItemStack item = switch (source) {
                case "minecraft" -> {
                    Material material = Material.matchMaterial(itemId, true);
                    if (material == null || material.isAir()) {
                        logger.warning("邮件预设 '" + presetId + "' 包含非法 minecraft 物品 id: " + itemId + "，已跳过。");
                        yield null;
                    }
                    ItemStack base = new ItemStack(material, amount);
                    ItemMeta itemMeta = base.getItemMeta();
                    if (itemMeta != null) {
                        Object rawName = rawItem.get("name");
                        if (rawName != null) {
                            itemMeta.setDisplayName(String.valueOf(rawName));
                        }
                        Object rawLore = rawItem.get("lore");
                        if (rawLore instanceof List<?> loreList && !loreList.isEmpty()) {
                            itemMeta.setLore(loreList.stream().map(String::valueOf).toList());
                        }
                        base.setItemMeta(itemMeta);
                    }
                    yield applyItemNbt(base, mapString(rawItem, "nbt", ""));
                }
                // 外部物品源统一通过 ItemSourceRegistry.generateItem 生成（player 传 null，预设加载时无玩家上下文）
                case "mythic", "mythicmobs", "neige", "neigeitems", "overture", "mmoitems", "mmo" -> {
                    if (itemSourceRegistry == null) {
                        logger.warning("邮件预设 '" + presetId + "' 的物品附件 source=" + source + " 但 ItemSourceRegistry 不可用，已跳过: " + itemId);
                        yield null;
                    }
                    yield itemSourceRegistry.generateItem(source, itemId, amount, null, null);
                }
                default -> {
                    logger.warning("邮件预设 '" + presetId + "' 的物品附件包含未知 source: " + source + "，已跳过。");
                    yield null;
                }
            };
            return item;
        } catch (Exception exception) {
            logger.warning("邮件预设 '" + presetId + "' 的物品附件生成异常: " + source + ":" + itemId + " | " + exception.getMessage());
            return null;
        }
    }

    /** 应用原版物品的自定义 NBT（SNBT 字符串）。 */
    @Nullable
    private static ItemStack applyItemNbt(@Nullable ItemStack item, String nbt) {
        if (item == null || nbt == null || nbt.isBlank()) return item;
        try {
            return Bukkit.getUnsafe().modifyItemStack(item.clone(), nbt);
        } catch (Exception ignored) {
            return item;
        }
    }

    /**
     * 将预设保存到 YAML 文件。文件名为 {@code <id>.yml}。
     */
    public static void savePreset(File directory, MailPresetDefinition preset) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建预设目录: " + directory.getAbsolutePath());
        }
        File file = new File(directory, preset.id() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("preset");
        section.set("id", preset.id());
        section.set("enabled", preset.enabled());
        section.set("display-name", preset.displayName());
        section.set("subject", preset.subject());
        section.set("body", preset.body());
        if (preset.expiresAfter() != null) {
            section.set("expires-after-days", preset.expiresAfter().toDays());
        }

        // currency attachments
        List<Map<String, Object>> currencyList = new ArrayList<>();
        for (MailAttachment att : preset.attachments()) {
            if (att.isCurrency()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("currency", att.currencyId());
                map.put("amount", att.amount());
                map.put("description", att.description());
                currencyList.add(map);
            }
        }
        if (!currencyList.isEmpty()) {
            section.set("currency-attachments", currencyList);
        }

        // item attachments（保存为 source+id 新格式；管理员 UI 创建的物品统一保存为 minecraft 源）
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (MailAttachment att : preset.attachments()) {
            if (att.isItem() && att.itemData() != null && !att.itemData().isBlank()) {
                try {
                    ItemStack item = MailItemSerializer.deserialize(att.itemData());
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("source", "minecraft");
                    map.put("id", item.getType().name());
                    map.put("amount", item.getAmount());
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        map.put("name", meta.getDisplayName());
                    }
                    if (meta != null && meta.getLore() != null && !meta.getLore().isEmpty()) {
                        map.put("lore", meta.getLore());
                    }
                    itemList.add(map);
                } catch (Exception ignored) {
                    // 无法反序列化的物品跳过
                }
            }
        }
        if (!itemList.isEmpty()) {
            section.set("item-attachments", itemList);
        }

        // claim commands
        if (preset.claimCommands() != null && !preset.claimCommands().isEmpty()) {
            section.set("claim-commands", preset.claimCommands());
        }

        // claim conditions
        if (preset.claimConditions() != null && !preset.claimConditions().isEmpty()) {
            List<String> condList = new ArrayList<>();
            for (ScriptCondition cond : preset.claimConditions()) {
                condList.add(cond.serialize());
            }
            section.set("claim-conditions", condList);
        }

        // cdks
        if (preset.cdks() != null && !preset.cdks().isEmpty()) {
            List<Map<String, Object>> cdkList = new ArrayList<>();
            for (MailPresetCdkDefinition cdk : preset.cdks()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("code", cdk.code());
                map.put("enabled", cdk.enabled());
                map.put("max-claims", cdk.maxClaims());
                if (cdk.expiresAt() != null) map.put("expires-at", cdk.expiresAt().toString());
                if (cdk.expiresAfter() != null) map.put("expires-after", formatDuration(cdk.expiresAfter()));
                cdkList.add(map);
            }
            section.set("cdks", cdkList);
        }

        yaml.save(file);
    }

    /**
     * 删除预设 YAML 文件。
     *
     * @return true=文件存在且已删除
     */
    public static boolean deletePresetFile(File directory, String presetId) {
        File file = new File(directory, presetId + ".yml");
        return file.exists() && file.delete();
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds % 86400 == 0) return (seconds / 86400) + "d";
        if (seconds % 3600 == 0) return (seconds / 3600) + "h";
        if (seconds % 60 == 0) return (seconds / 60) + "m";
        return seconds + "s";
    }

    private static String resolveDescription(ItemStack itemStack, xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge bridge) {
        String name;
        if (bridge != null) {
            name = bridge.resolveDisplayName(itemStack);
        } else {
            ItemMeta itemMeta = itemStack.getItemMeta();
            name = itemMeta != null && itemMeta.hasDisplayName() ? itemMeta.getDisplayName() : itemStack.getType().name();
        }
        return name + " x" + itemStack.getAmount();
    }

    private static String trimDecimal(double value) {
        return Math.floor(value) == value ? Long.toString((long) value) : Double.toString(value);
    }

    private static String string(String value) {
        return value == null ? "" : value.trim();
    }

    private static String mapString(Map<?, ?> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static double parseAmount(String rawValue) {
        try {
            return Math.max(0.0D, Double.parseDouble(rawValue));
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    private static int parseInt(String rawValue, int defaultValue) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static Instant parseInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(rawValue.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static Duration parseDuration(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        long multiplier = switch (normalized.charAt(normalized.length() - 1)) {
            case 's' -> 1L;
            case 'm' -> 60L;
            case 'h' -> 3600L;
            case 'd' -> 86400L;
            case 'w' -> 604800L;
            default -> -1L;
        };
        if (multiplier < 0L) {
            return null;
        }
        int amount = parseInt(normalized.substring(0, normalized.length() - 1), -1);
        return amount <= 0 ? null : Duration.ofSeconds(amount * multiplier);
    }

    private static boolean parseBoolean(Object rawValue, boolean defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        if (rawValue instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = String.valueOf(rawValue).trim();
        if (normalized.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(normalized);
    }
}
