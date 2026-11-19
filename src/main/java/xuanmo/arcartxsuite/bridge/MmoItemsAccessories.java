package xuanmo.arcartxsuite.bridge;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.api.player.inventory.EquippedItem;
import net.Indyuce.mmoitems.comp.inventory.PlayerInventory;
import net.Indyuce.mmoitems.comp.inventory.PlayerInventoryHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.PropPlayerHandle;

/** Supplies accessories to MMOItems so its normal placement, requirements and bonuses apply. */
final class MmoItemsAccessories implements PlayerInventory {
    private final JavaPlugin plugin;
    private final ArcartXPropBridge bridge;
    private final Set<String> slots;
    private final PlayerInventoryHandler inventories;
    private final Set<UUID> pending = new HashSet<>();
    private Runnable unobserve;
    private BukkitTask updateTask;
    private boolean closed;

    private MmoItemsAccessories(JavaPlugin plugin, ArcartXPropBridge bridge, Set<String> slots) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.slots = Set.copyOf(slots);
        this.inventories = MMOItems.plugin.getInventory();
    }

    static Runnable register(JavaPlugin plugin, ArcartXPropBridge bridge, Set<String> slots) {
        var source = new MmoItemsAccessories(plugin, bridge, slots);
        source.unobserve = bridge.observeSlots((player, slot) -> {
            if (source.slots.contains(slot)) source.queue(player.getUniqueId());
        });
        if (source.unobserve == null) return null;
        try {
            source.inventories.register(source);
            Bukkit.getOnlinePlayers().forEach(player -> source.queue(player.getUniqueId()));
            return source::close;
        } catch (RuntimeException | LinkageError failure) {
            source.close();
            throw failure;
        }
    }

    @Override
    public List<EquippedItem> getInventory(Player player) {
        if (closed) return List.of();
        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) return List.of();
        var equipped = new ArrayList<EquippedItem>();
        for (String slot : slots) {
            ItemStack stack = handle.getSlotItemStack(slot);
            if (stack == null || stack.getType().isAir() || stack.getAmount() < 1) continue;
            ItemStack snapshot = stack.clone();
            NBTItem nbt = NBTItem.get(snapshot.clone());
            String type = nbt.getType();
            if (type == null || type.isBlank()) continue;
            equipped.add(new EquippedItem(nbt, EquipmentSlot.ACCESSORY) {
                @Override
                public void setItem(ItemStack item) {
                    // A reforge or bound-item update must not overwrite a newer item in the slot.
                    if (!closed && !Objects.equals(snapshot, item)
                            && Objects.equals(snapshot, handle.getSlotItemStack(slot))) {
                        handle.trySetSlotItemStack(slot, item);
                    }
                }
            });
        }
        return equipped;
    }

    private void queue(UUID playerId) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> queue(playerId));
            return;
        }
        if (closed) return;
        pending.add(playerId);
        if (updateTask == null) {
            updateTask = Bukkit.getScheduler().runTask(plugin, () -> {
                updateTask = null;
                var batch = List.copyOf(pending);
                pending.clear();
                for (UUID id : batch) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) refresh(player);
                }
            });
        }
    }

    private void close() {
        if (closed) return;
        closed = true;
        unobserve.run();
        inventories.unregisterIf(provider -> provider == this);
        if (updateTask != null) updateTask.cancel();
        pending.clear();
        Bukkit.getOnlinePlayers().forEach(MmoItemsAccessories::refresh);
    }

    private static void refresh(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")
            || !Bukkit.getPluginManager().isPluginEnabled("MythicLib")) return;
        try {
            PlayerData.get(player).updateInventory();
        } catch (RuntimeException | LinkageError failure) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("刷新 MMOItems 饰品属性失败: " + failure);
        }
    }
}
