package xuanmo.arcartxsuite.lottery.engine;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
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

    public boolean distribute(@NotNull Player player, @NotNull PoolItem item) {
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
                return false;
            }
        }
    }

    private boolean giveDirectItem(Player player, PoolItem item) {
        ItemStack stack = generateItem(item);
        if (stack == null) {
            logger.warning("[Lottery] 无法生成物品: " + item.pluginId() + " (type=" + item.pluginType() + ")");
            return false;
        }

        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            // 背包已满，回退到邮件
            MailDispatchable mail = mailSupplier != null ? mailSupplier.get() : null;
            if (mail != null) {
                for (ItemStack remaining : leftover.values()) {
                    if (item.mailPreset() != null && !item.mailPreset().isBlank()) {
                        mail.dispatchPreset(item.mailPreset(), player.getName(), "Lottery");
                    } else {
                        logger.info("[Lottery] 玩家 " + player.getName() + " 背包已满，物品掉落在地: " + item.name());
                        player.getWorld().dropItemNaturally(player.getLocation(), remaining);
                    }
                }
                return true;
            }
            // 无法发邮件，物品掉地上
            for (ItemStack remaining : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        }
        return true;
    }

    private boolean executeCommands(Player player, PoolItem item) {
        List<String> commands = item.commands();
        if (commands == null || commands.isEmpty()) return false;
        for (String cmd : commands) {
            if (cmd == null || cmd.isBlank()) continue;
            String processed = cmd
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
        }
        return true;
    }

    private boolean sendMail(Player player, PoolItem item) {
        MailDispatchable mail = mailSupplier != null ? mailSupplier.get() : null;
        if (mail == null) {
            logger.warning("[Lottery] MailDispatchable 不可用，无法发送邮件奖励");
            return false;
        }
        String itemJson = toItemJson(generateItem(item));
        if (item.mailPreset() != null && !item.mailPreset().isBlank()) {
            mail.dispatchPreset(item.mailPreset(), player.getName(), "Lottery");
            return true;
        }
        logger.warning("[Lottery] 奖品 " + item.id() + " 配置了 MAIL 但未设置 mail-preset");
        return false;
    }

    @Nullable
    private ItemStack generateItem(PoolItem item) {
        if (item.pluginId() == null || item.pluginId().isBlank()) return null;
        return switch (item.pluginType()) {
            case MYTHIC -> itemSourceRegistry.generateMythicItem(item.pluginId(), 1);
            case NEIGE -> itemSourceRegistry.generateNeigeItem(item.pluginId(), 1);
            case OVERTURE -> itemSourceRegistry.generateOvertureItem(item.pluginId(), null, 1);
            case MMO -> itemSourceRegistry.generateMmoItem(item.pluginId(), item.pluginId(), 1); // 简化处理
            case PLAIN -> null; // 需要手动处理原版物品
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
}
