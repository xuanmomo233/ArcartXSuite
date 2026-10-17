package xuanmo.arcartxsuite.questgps.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.capability.TitleConfigQueryable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

/**
 * 任务奖励预览解析器，将任务奖励配置解析为可展示的物品 JSON 与称号信息。
 * <p>
 * 通过 {@link IconResolver} 统一生成物品（原版材质 + 外部物品库），
 * 支持称号奖励的预览渲染。
 */
public final class QuestGpsRewardPreviewResolver {

    private final JavaPlugin plugin;
    private final Supplier<TitleConfigQueryable> titleConfigurationProvider;
    private final Logger logger;
    private final Function<ItemStack, Optional<String>> itemJsonSerializer;
    private final ItemSourceRegistry itemSourceRegistry;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final IconResolver iconResolver;

    public QuestGpsRewardPreviewResolver(
        JavaPlugin plugin,
        Logger logger,
        Supplier<TitleConfigQueryable> titleConfigurationProvider,
        Function<ItemStack, Optional<String>> itemJsonSerializer,
        ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this.plugin = plugin;
        this.titleConfigurationProvider = titleConfigurationProvider == null ? () -> null : titleConfigurationProvider;
        this.logger = logger;
        this.itemJsonSerializer = itemJsonSerializer == null ? item -> Optional.empty() : itemJsonSerializer;
        this.itemSourceRegistry = itemSourceRegistry;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.iconResolver = new IconResolver(null, itemSourceRegistry);
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
                generateIcon("neige", definition.neigeItemId(), definition),
                definition,
                definition.neigeItemId()
            );
            case "mythicmobs", "mythicitems" -> resolveItem(
                rewardId,
                type,
                generateIcon("mythic", definition.mythicItemId(), definition),
                definition,
                definition.mythicItemId()
            );
            case "overture" -> resolveItem(
                rewardId,
                type,
                generateIcon("overture", definition.overtureItemId(), definition),
                definition,
                definition.overtureItemId()
            );
            case "mmoitems" -> resolveItem(
                rewardId,
                type,
                generateIcon("mmoitems", definition.mmoItemType() + ";" + definition.mmoItemId(), definition),
                definition,
                definition.mmoItemType() + ":" + definition.mmoItemId()
            );
            case "material", "itemstack" -> resolveItem(
                rewardId,
                type,
                generateMaterial(definition.material(), definition),
                definition,
                definition.material()
            );
            case "title" -> resolveTitle(rewardId, definition);
            default -> new ResolvedRewardPreview(
                rewardId,
                "text",
                blankTo(colored(definition.displayName()), blankTo(colored(definition.text()), "任务奖励")),
                blankTo(colored(definition.text()), "奖励信息未配置"),
                "",
                "",
                "",
                Math.max(1, definition.amount())
            );
        };
    }

    /**
     * 通过 {@link IconResolver} 生成外部物品库物品。
     */
    private ItemStack generateIcon(String source, String sourceId,
                                   QuestGpsModuleConfiguration.RewardPreviewDefinition definition) {
        if (sourceId == null || sourceId.isBlank()) return null;
        IconDefinition icon = new IconDefinition(
            "", Math.max(1, definition.amount()), "", List.of(), 0,
            source, sourceId, "", "", "",
            "", "", java.util.Map.of(), "", false, "", ""
        );
        return iconResolver.resolveItemStack(null, icon);
    }

    /**
     * 通过 {@link IconResolver} 生成原版材质物品（含 displayName/lore）。
     */
    private ItemStack generateMaterial(String materialId,
                                       QuestGpsModuleConfiguration.RewardPreviewDefinition definition) {
        if (materialId == null || materialId.isBlank()) return null;
        IconDefinition icon = new IconDefinition(
            materialId, Math.max(1, definition.amount()),
            blankTo(colored(definition.displayName()), ""),
            definition.lore() != null ? definition.lore().stream().map(QuestGpsRewardPreviewResolver::colored).toList() : List.of(),
            0, "", "", "", "", "",
            "", "", java.util.Map.of(), "", false, "", ""
        );
        return iconResolver.resolveItemStack(null, icon);
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
            resolved = generateMaterial(definition.fallbackMaterial(), definition);
        }
        if (resolved != null && !resolved.getType().isAir()) {
            return fromItemStack(rewardId, type, resolved, colored(definition.displayName()), colored(definition.text()));
        }
        logger.fine("QuestGPS: 奖励预览未能解析: " + type + " -> " + configuredId);
        return new ResolvedRewardPreview(
            rewardId,
            type,
            blankTo(colored(definition.displayName()), blankTo(colored(definition.text()), configuredId)),
            blankTo(colored(definition.text()), "奖励物品未找到"),
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
            colored(definition.text()),
            (qualityName.isBlank() ? "" : qualityName + " · ") + duration + (titleDesc.isBlank() ? "" : " · " + titleDesc)
        );
        return new ResolvedRewardPreview(
            rewardId,
            "title",
            blankTo(colored(definition.displayName()), "称号: " + titleName),
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
        String itemJson = itemJsonSerializer.apply(
            xuanmo.arcartxsuite.api.util.ItemPreview.copyForIcon(itemStack)).orElse("");
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

    private String resolveItemName(ItemStack itemStack) {
        if (itemStack == null) {
            return "";
        }
        if (vanillaItemNameBridge != null) {
            return vanillaItemNameBridge.resolveDisplayName(itemStack);
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            return itemMeta.getDisplayName();
        }
        return itemStack.getType().name();
    }

    private String resolveItemSummary(ItemStack itemStack) {
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

    private static String colored(String value) {
        return value == null || value.isEmpty() ? "" : MessageProvider.translateColors(value);
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



