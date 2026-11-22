package xuanmo.arcartxsuite.prop.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Player;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI.PotionDrink;
import xuanmo.arcartxsuite.api.bridge.PropPlayerHandle;
import xuanmo.arcartxsuite.api.message.MessageProvider;

/** 原版药水与 MMOItems 专用按键入口。额外槽位始终持有原物品。 */
final class PropQuickSlotUse {
    private static final long VANILLA_DRINK_TICKS = 32L;

    private final JavaPlugin plugin;
    private final PropBridgeAPI bridge;
    private final MessageProvider messages;
    private final Predicate<Player> canUse;
    private final Logger logger;
    private final Map<UUID, BukkitTask> drinks = new HashMap<>();

    PropQuickSlotUse(JavaPlugin plugin, PropBridgeAPI bridge, MessageProvider messages,
                     Predicate<Player> canUse, Logger logger) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.messages = messages;
        this.canUse = canUse;
        this.logger = logger;
    }

    void cancel(Player player) {
        BukkitTask task = drinks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    void shutdown() {
        drinks.values().forEach(BukkitTask::cancel);
        drinks.clear();
    }

    void potion(Player player, PropPlayerHandle handle, String slotId, ItemStack stack) {
        if (!hasItem(stack) || drinks.containsKey(player.getUniqueId()) || player.hasCooldown(stack.getType())) return;
        var slot = new SlotDose(player, handle, slotId, stack);
        switch (stack.getType()) {
            case POTION -> beginDrink(slot);
            case SPLASH_POTION, LINGERING_POTION -> throwPotion(slot);
            default -> player.sendMessage(messages.get("use.potion-required"));
        }
    }

    void mmoItem(Player player, PropPlayerHandle handle, String slotId, ItemStack stack) {
        if (!hasItem(stack) || drinks.containsKey(player.getUniqueId())) return;
        var slot = new SlotDose(player, handle, slotId, stack);
        try {
            switch (bridge.useMmoItem(player, slot.one(), slot::take, slot::refund)) {
                case UNAVAILABLE -> player.sendMessage(messages.get("use.mmoitems-unavailable"));
                case UNSUPPORTED -> player.sendMessage(messages.get("use.mmoitems-unsupported"));
                case REJECTED -> player.sendMessage(messages.get("use.mmoitems-rejected"));
                case SLOT_UPDATE_FAILED -> slot.writeFailed();
                case USED -> { }
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "MMOItems 快捷使用失败", exception);
            player.sendMessage(messages.get("use.quick-use-failed"));
        }
    }

    private void beginDrink(SlotDose slot) {
        Player player = slot.player;
        ItemStack one = slot.one();
        PotionDrink prepared = bridge.preparePotionDrink(player, one).orElse(null);
        if (prepared == null) {
            player.sendMessage(messages.get("use.potion-unavailable"));
            return;
        }
        UUID playerId = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (slot.usable() && slot.matchesOriginal() && !player.hasCooldown(Material.POTION)) {
                    finishDrink(slot, one, prepared);
                }
            } finally {
                drinks.remove(playerId);
            }
        }, VANILLA_DRINK_TICKS);
        drinks.put(playerId, task);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.5F, 1.0F);
    }

    private void finishDrink(SlotDose slot, ItemStack original, PotionDrink prepared) {
        Player player = slot.player;
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (!creative && !slot.take()) {
            slot.writeFailed();
            return;
        }
        if (!slot.usable()) {
            slot.refund();
            return;
        }
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(player, original.clone(), EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || !slot.usable()) {
            slot.refund();
            return;
        }
        ItemStack replacement = event.getItem();
        if (replacement.getType() != Material.POTION || replacement.getAmount() <= 0) {
            slot.refund();
            return;
        }
        PotionDrink drink = prepared;
        if (!original.equals(replacement)) {
            replacement = replacement.clone();
            replacement.setAmount(1);
            drink = bridge.preparePotionDrink(player, replacement).orElse(null);
            if (drink == null) {
                slot.refund();
                player.sendMessage(messages.get("use.potion-unavailable"));
                return;
            }
        }
        try {
            ItemStack remaining = drink.finish();
            if (!creative && hasItem(remaining)) give(player, remaining);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "快捷槽药水饮用结束失败", exception);
            player.sendMessage(messages.get("use.quick-use-failed"));
        }
    }

    private void throwPotion(SlotDose slot) {
        Player player = slot.player;
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        ItemStack one = slot.one();
        if (!creative && !slot.take()) {
            slot.writeFailed();
            return;
        }
        if (!slot.usable()) {
            slot.refund();
            return;
        }

        var from = player.getEyeLocation().subtract(0, 0.1, 0);
        var direction = from.clone();
        direction.setPitch(direction.getPitch() - 20);
        boolean lingering = one.getType() == Material.LINGERING_POTION;
        Class<? extends ThrownPotion> projectileType = lingering ? LingeringPotion.class : SplashPotion.class;
        ThrownPotion potion;
        try {
            potion = player.getWorld().spawn(from, projectileType, entity -> {
                entity.setShooter(player);
                entity.setItem(one);
                entity.setVelocity(direction.getDirection().multiply(0.5));
            });
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "快捷槽药水投掷失败", exception);
            slot.refund();
            player.sendMessage(messages.get("use.quick-use-failed"));
            return;
        }
        if (!potion.isValid()) {
            slot.refund();
            return;
        }
        Sound sound = lingering ? Sound.ENTITY_LINGERING_POTION_THROW : Sound.ENTITY_SPLASH_POTION_THROW;
        player.getWorld().playSound(from, sound, 0.5F, 1.0F);
    }

    private static boolean hasItem(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0;
    }

    private static void give(Player player, ItemStack item) {
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private final class SlotDose {
        private final Player player;
        private final PropPlayerHandle handle;
        private final String slotId;
        private final World world;
        private final ItemStack original;
        private final AuthSnapshot admission;
        private ItemStack debited;

        private SlotDose(Player player, PropPlayerHandle handle, String slotId, ItemStack original) {
            this.player = player;
            this.handle = handle;
            this.slotId = slotId;
            this.world = player.getWorld();
            this.original = original.clone();
            this.admission = AuthAccess.snapshot(player.getUniqueId());
        }

        private ItemStack one() {
            ItemStack copy = original.clone();
            copy.setAmount(1);
            return copy;
        }

        private boolean matchesOriginal() {
            return original.equals(handle.getSlotItemStack(slotId));
        }

        private boolean usable() {
            if (!admission.admitted() || !canUse.test(player) || !world.equals(player.getWorld())) return false;
            AuthSnapshot current = AuthAccess.snapshot(admission.playerId());
            return current.admitted() && current.generation() == admission.generation()
                && Objects.equals(current.connectionId(), admission.connectionId());
        }

        private boolean take() {
            if (debited != null || !usable() || !matchesOriginal()) return false;
            ItemStack update = new ItemStack(Material.AIR);
            if (original.getAmount() > 1) {
                update = original.clone();
                update.setAmount(original.getAmount() - 1);
            }
            if (!handle.trySetSlotItemStack(slotId, update)) return false;
            debited = update;
            return true;
        }

        private void refund() {
            if (debited == null) return;
            ItemStack expected = debited;
            debited = null;
            ItemStack current = handle.getSlotItemStack(slotId);
            boolean unchanged = hasItem(expected) ? expected.equals(current) : !hasItem(current);
            if (unchanged && handle.trySetSlotItemStack(slotId, original.clone())) return;
            // 槽位若被外部事件更新，将扣除的单件归还背包。
            if (!matchesOriginal()) give(player, one());
        }

        private void writeFailed() {
            player.sendMessage(messages.get("use.slot-update-failed"));
            handle.syncSlotToClient(slotId);
        }
    }
}
