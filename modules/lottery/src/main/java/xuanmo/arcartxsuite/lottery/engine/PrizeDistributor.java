package xuanmo.arcartxsuite.lottery.engine;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

public class PrizeDistributor {

    private final ItemSourceRegistry itemSourceRegistry;
    private final Supplier<MailDispatchable> mailSupplier;
    private final Logger logger;

    public PrizeDistributor(ItemSourceRegistry itemSourceRegistry,
                            Supplier<MailDispatchable> mailSupplier,
                            Logger logger) {
        this.itemSourceRegistry = itemSourceRegistry;
        this.mailSupplier = mailSupplier;
        this.logger = logger;
    }

    @NotNull
    public DistributionResult distribute(@NotNull Player player, @NotNull PoolItem item) {
        switch (item.delivery()) {
            case DIRECT -> {
                return giveDirectItem(player, item);
            }
            case COMMAND -> {
                return executeCommands(player, item);
            }
            case MAIL -> {
                return sendMail(player, item);
            }
            default -> {
                return DistributionResult.failure(null, "未知奖励投递类型");
            }
        }
    }

    private DistributionResult giveDirectItem(Player player, PoolItem item) {
        ItemStack stack = generateItem(item);
        if (stack == null) {
            logger.warning("[Lottery] 无法生成物品: " + item.pluginId() + " (type=" + item.pluginType() + ")");
            return DistributionResult.failure(null, "物品生成失败");
        }

        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            MailDispatchable mail = mailSupplier != null ? mailSupplier.get() : null;
            if (mail != null && item.mailPreset() != null && !item.mailPreset().isBlank()
                    && mail.dispatchPreset(item.mailPreset(), player.getName(), "Lottery")) {
                return DistributionResult.mailSent(stack);
            }
            return DistributionResult.failure(stack, "背包空间不足且邮件发送失败");
        }
        return DistributionResult.success(stack);
    }

    private DistributionResult executeCommands(Player player, PoolItem item) {
        List<String> commands = item.commands();
        if (commands == null || commands.isEmpty()) return DistributionResult.failure(null, "奖励命令为空");
        for (String cmd : commands) {
            if (cmd == null || cmd.isBlank()) continue;
            String processed = cmd
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
        }
        return DistributionResult.success(null);
    }

    private DistributionResult sendMail(Player player, PoolItem item) {
        MailDispatchable mail = mailSupplier != null ? mailSupplier.get() : null;
        if (mail == null) {
            logger.warning("[Lottery] MailDispatchable 不可用，无法发送邮件奖励");
            return DistributionResult.failure(generateItem(item), "邮件能力不可用");
        }
        if (item.mailPreset() != null && !item.mailPreset().isBlank()) {
            ItemStack stack = generateItem(item);
            if (mail.dispatchPreset(item.mailPreset(), player.getName(), "Lottery")) {
                return DistributionResult.mailSent(stack);
            }
            return DistributionResult.failure(stack, "邮件发送失败");
        }
        logger.warning("[Lottery] 奖品 " + item.id() + " 配置了 MAIL 但未设置 mail-preset");
        return DistributionResult.failure(generateItem(item), "缺少 mail-preset");
    }

    @Nullable
    public ItemStack generateItem(PoolItem item) {
        if (item.pluginType() == PoolItem.PluginItemType.PLAIN && item.itemJson() != null
                && !item.itemJson().isBlank()) {
            try {
                return ItemSerializer.deserialize(java.util.Base64.getDecoder().decode(item.itemJson()));
            } catch (Exception e) {
                logger.warning("[Lottery] 预序列化物品解析失败: " + e.getMessage());
            }
        }
        if (item.pluginId() == null || item.pluginId().isBlank()) return null;
        return switch (item.pluginType()) {
            case MYTHIC -> itemSourceRegistry.generateMythicItem(item.pluginId(), item.amount());
            case NEIGE -> itemSourceRegistry.generateNeigeItem(item.pluginId(), item.amount());
            case OVERTURE -> itemSourceRegistry.generateOvertureItem(item.pluginId(), null, item.amount());
            case MMO -> itemSourceRegistry.generateMmoItem(item.pluginId(), item.pluginId(), item.amount());
            case PLAIN -> {
                String materialName = item.pluginId().startsWith("minecraft:")
                    ? item.pluginId().substring("minecraft:".length()) : item.pluginId();
                Material material = Material.matchMaterial(materialName, true);
                yield material == null ? null : new ItemStack(material, item.amount());
            }
        };
    }

    @NotNull
    public String toItemJson(@Nullable ItemStack stack) {
        if (stack == null) return "";
        try {
            byte[] bytes = ItemSerializer.serialize(stack);
            return java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            logger.warning("[Lottery] 序列化物品失败: " + e.getMessage());
            return "";
        }
    }

    public record DistributionResult(boolean success, @Nullable ItemStack item,
                                     boolean mailSent, @NotNull String failureReason) {
        public static DistributionResult success(@Nullable ItemStack item) {
            return new DistributionResult(true, item, false, "");
        }

        public static DistributionResult mailSent(@Nullable ItemStack item) {
            return new DistributionResult(true, item, true, "");
        }

        public static DistributionResult failure(@Nullable ItemStack item, @NotNull String reason) {
            return new DistributionResult(false, item, false, reason);
        }
    }
}
