package xuanmo.arcartxsuite.entitytracker.reward;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;

/** Inventory placement stays on Main. Only immutable overflow data crosses to a storage worker. */
public final class BossRewardItems {
    private static final String SOURCE = "entitytracker";
    private final ItemSourceRegistry items;
    private final PendingRewardService pending;

    public BossRewardItems(ItemSourceRegistry items, PendingRewardService pending) {
        this.items = items;
        this.pending = pending;
    }

    public record Prepared(RewardActionResult result, PendingReward pendingReward) {
        public boolean requiresStorage() { return pendingReward != null; }
    }

    public Prepared prepareOnline(Player player, BossDamageRewardAction action, String detail) {
        if (action.id().isBlank()) return failed("缺少 id");
        ItemStack item = items.generateItem(action.source(), action.id(), action.amount(), action.nbt(), player);
        if (InventoryItems.empty(item)) return failed("物品不存在: " + action.id());
        if (placeWhole(player.getInventory(), item)) {
            return new Prepared(RewardActionResult.ok("item", item.getAmount() + "x " + action.id()), null);
        }
        if (pending == null) return failed("待发放奖励服务未就绪");
        String data = Base64.getEncoder().encodeToString(ItemSerializer.serialize(item));
        return new Prepared(null, PendingReward.itemSerialized(player.getUniqueId(), SOURCE, data, detail));
    }

    public Prepared prepareOffline(UUID recipient, BossDamageRewardAction action, String detail) {
        if (action.id().isBlank()) return failed("缺少 id");
        if (pending == null) return failed("待发放奖励服务未就绪");
        return new Prepared(null, PendingReward.item(recipient, SOURCE, action.source(), action.id(), action.amount(),
            action.nbt(), detail));
    }

    public RewardActionResult persist(String requestKey, PendingReward reward) {
        return pending.savePendingBatchConfirmed(requestKey, List.of(reward))
            ? RewardActionResult.ok("item", "已入库，等待补发")
            : RewardActionResult.fail("item", "待发放奖励未确认");
    }

    public void flushOnline(UUID recipient) {
        Player player = Bukkit.getPlayer(recipient);
        if (player != null && player.isOnline()) pending.flushPlayer(player);
    }

    private static Prepared failed(String message) {
        return new Prepared(RewardActionResult.fail("item", message), null);
    }

    private static boolean placeWhole(PlayerInventory inventory, ItemStack item) {
        ItemStack[] current = inventory.getStorageContents();
        ItemStack[] updated = current.clone();
        int limit = Math.min(inventory.getMaxStackSize(), item.getMaxStackSize());
        int remaining = item.getAmount();
        for (int slot = 0; slot < current.length && remaining > 0; slot++) {
            ItemStack existing = current[slot];
            if (InventoryItems.empty(existing)) continue;
            int add = Math.min(remaining, Math.max(0, limit - existing.getAmount()));
            if (add == 0 || !existing.isSimilar(item)) continue;
            var merged = existing.clone();
            merged.setAmount(existing.getAmount() + add);
            updated[slot] = merged;
            remaining -= add;
        }
        for (int slot = 0; slot < current.length && remaining > 0; slot++) {
            if (!InventoryItems.empty(current[slot])) continue;
            int add = Math.min(remaining, limit);
            if (add <= 0) break;
            var split = item.clone();
            split.setAmount(add);
            updated[slot] = split;
            remaining -= add;
        }
        if (remaining > 0) return false;
        int slot = 0;
        try {
            for (; slot < current.length; slot++) {
                if (updated[slot] != current[slot]) inventory.setItem(slot, updated[slot]);
            }
        } catch (RuntimeException failure) {
            for (int restore = 0; restore <= slot; restore++) {
                if (updated[restore] == current[restore]) continue;
                try { inventory.setItem(restore, current[restore]); }
                catch (RuntimeException rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
            }
            throw failure;
        }
        return true;
    }
}
