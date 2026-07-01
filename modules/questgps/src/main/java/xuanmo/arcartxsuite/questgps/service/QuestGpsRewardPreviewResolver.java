package xuanmo.arcartxsuite.questgps.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import xuanmo.arcartxsuite.api.capability.TitleConfigQueryable;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

public final class QuestGpsRewardPreviewResolver {

    private final JavaPlugin plugin;
    private final Supplier<TitleConfigQueryable> titleConfigurationProvider;
    private final Logger logger;
    private final Function<ItemStack, Optional<String>> itemJsonSerializer;
    private final ItemSourceRegistry itemSourceRegistry;

    public QuestGpsRewardPreviewResolver(
        JavaPlugin plugin,
        Supplier<TitleConfigQueryable> titleConfigurationProvider,
        Function<ItemStack, Optional<String>> itemJsonSerializer,
        ItemSourceRegistry itemSourceRegistry
    ) {
        this.plugin = plugin;
        this.titleConfigurationProvider = titleConfigurationProvider == null ? () -> null : titleConfigurationProvider;
        this.logger = plugin.getLogger();
        this.itemJsonSerializer = itemJsonSerializer == null ? item -> Optional.empty() : itemJsonSerializer;
        this.itemSourceRegistry = itemSourceRegistry;
    }

    public List<ResolvedRewardPreview> resolve(
        QuestGpsModuleConfiguration configuration,
        QuestGpsCategory category,
        String questId
    ) {
        QuestGpsModuleConfiguration.QuestDefinition quest = configuration.quest(questId);
        if (quest == null || quest.rewards().isEmpty()) {
            return List.of();
        }
        List<ResolvedRewardPreview> resolved = new ArrayList<>(quest.rewards().size());
        for (int index = 0; index < quest.rewards().size(); index++) {
            resolved.add(resolveSingle(quest.id(), index, quest.rewards().get(index)));
        }
        return List.copyOf(resolved);
    }

    private ResolvedRewardPreview resolveSingle(
        String questId,
        int index,
        QuestGpsModuleConfiguration.RewardPreviewDefinition definition
    ) {
        String rewardId = (questId == null ? "quest" : questId) + "#" + index;
        String type = definition.type().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "neigeitems" -> resolveItem(
                rewardId,
                type,
                itemSourceRegistry.generateNeigeItem(definition.neigeItemId(), definition.amount()),
                definition,
                definition.neigeItemId()
            );
            case "mythicmobs", "mythicitems" -> resolveItem(
                rewardId,
                type,
                itemSourceRegistry.generateMythicItem(definition.mythicItemId(), definition.amount()),
                definition,
                definition.mythicItemId()
            );
            case "overture" -> resolveItem(
                rewardId,
                type,
                itemSourceRegistry.generateOvertureItem(definition.overtureItemId(), null, definition.amount()),
                definition,
                definition.overtureItemId()
            );
            case "mmoitems" -> resolveItem(
                rewardId,
                type,
                itemSourceRegistry.generateMmoItem(definition.mmoItemType(), definition.mmoItemId(), definition.amount()),
                definition,
                definition.mmoItemType() + ":" + definition.mmoItemId()
            );
            case "material", "itemstack" -> resolveItem(
                rewardId,
                type,
                buildMaterialItem(definition.material(), definition.amount(), definition.displayName(), definition.lore()),
                definition,
                definition.material()
            );
            case "title" -> resolveTitle(rewardId, definition);
            default -> new ResolvedRewardPreview(
                rewardId,
                "text",
                blankTo(definition.displayName(), blankTo(definition.text(), "任务奖励")),
                blankTo(definition.text(), "奖励信息未配置"),
                "",
                "",
                "",
                Math.max(1, definition.amount())
            );
        };
    }

    private ResolvedRewardPreview resolveItem(
        String rewardId,
        String type,
        ItemStack itemStack,
        QuestGpsModuleConfiguration.RewardPreviewDefinition definition,
        String configuredId
    ) {
        ItemStack resolved = itemStack;
        if (resolved == null) {
            resolved = buildMaterialItem(definition.fallbackMaterial(), definition.amount(), definition.displayName(), definition.lore());
        }
        if (resolved != null && !resolved.getType().isAir()) {
            return fromItemStack(rewardId, type, resolved, definition.displayName(), definition.text());
        }
        logger.fine("QuestGPS: 奖励预览未能解析: " + type + " -> " + configuredId);
        return new ResolvedRewardPreview(
            rewardId,
            type,
            blankTo(definition.displayName(), blankTo(definition.text(), configuredId)),
            blankTo(definition.text(), "奖励物品未找到"),
            "",
            definition.fallbackMaterial(),
            configuredId,
            Math.max(1, definition.amount())
        );
    }

    private ResolvedRewardPreview resolveTitle(
        String rewardId,
        QuestGpsModuleConfiguration.RewardPreviewDefinition definition
    ) {
        TitleConfigQueryable queryable = titleConfigurationProvider.get();
        TitleConfigQueryable.TitleInfo titleInfo = queryable == null ? null : queryable.queryTitle(definition.titleId());
        String titleName = titleInfo == null ? definition.titleId() : titleInfo.displayName();
        String qualityName = titleInfo == null ? "" : titleInfo.qualityName();
        String titleDesc = titleInfo == null ? "" : titleInfo.description();
        String duration = "permanent".equalsIgnoreCase(definition.duration()) ? "永久" : "限时 " + definition.duration();
        String description = blankTo(
            definition.text(),
            (qualityName.isBlank() ? "" : qualityName + " · ") + duration + (titleDesc.isBlank() ? "" : " · " + titleDesc)
        );
        return new ResolvedRewardPreview(
            rewardId,
            "title",
            blankTo(definition.displayName(), "称号: " + titleName),
            description,
            "",
            "",
            titleName,
            Math.max(1, definition.amount())
        );
    }

    private ResolvedRewardPreview fromItemStack(
        String rewardId,
        String type,
        ItemStack itemStack,
        String configuredTitle,
        String configuredDescription
    ) {
        String title = blankTo(configuredTitle, resolveItemName(itemStack));
        String description = blankTo(configuredDescription, resolveItemSummary(itemStack));
        String itemJson = itemJsonSerializer.apply(itemStack).orElse("");
        return new ResolvedRewardPreview(
            rewardId,
            type,
            title,
            description,
            itemJson,
            itemStack.getType().name(),
            resolveItemName(itemStack),
            Math.max(1, itemStack.getAmount())
        );
    }

    private ItemStack buildMaterialItem(
        String materialId,
        int amount,
        String displayName,
        List<String> lore
    ) {
        Material material = materialId == null || materialId.isBlank() ? null : Material.matchMaterial(materialId.trim(), true);
        if (material == null || material.isAir()) {
            return null;
        }
        ItemStack itemStack = new ItemStack(material, Math.max(1, amount));
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            if (displayName != null && !displayName.isBlank()) {
                itemMeta.setDisplayName(displayName);
            }
            if (lore != null && !lore.isEmpty()) {
                itemMeta.setLore(lore);
            }
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }

    private static String resolveItemName(ItemStack itemStack) {
        if (itemStack == null) {
            return "";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            return itemMeta.getDisplayName();
        }
        return itemStack.getType().name();
    }

    private static String resolveItemSummary(ItemStack itemStack) {
        if (itemStack == null) {
            return "";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.hasLore() && itemMeta.getLore() != null && !itemMeta.getLore().isEmpty()) {
            return itemMeta.getLore().get(0);
        }
        return resolveItemName(itemStack) + " x" + itemStack.getAmount();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record ResolvedRewardPreview(
        String rewardId,
        String type,
        String title,
        String description,
        String itemJson,
        String materialId,
        String itemName,
        int amount
    ) {
    }
}

