package xuanmo.arcartxsuite.exploration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.auth.AuthRestrictionEvent;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.message.MessageProvider;

/** Server-owned loot for each private NPC. All access is on the Bukkit main thread. */
final class ExplorationChests implements Listener {
    private final ChestModels models;
    private final PacketBridgeAPI bridge;
    private final String ui;
    private final ItemBridgeAPI icons;
    private final VanillaItemNameBridge names;
    private final ItemRewardDispatcher rewards;
    private final MessageProvider messages;
    private final Predicate<Player> admitted;
    private final Map<UUID, Chest> chests = new HashMap<>();

    private static final class Chest {
        final Player owner;
        final String title;
        final Location origin;
        final List<ItemStack> items;
        final ItemRewardDispatcher.DispatchOptions options;
        String token;

        Chest(Player owner, String title, List<ItemStack> items, ItemRewardDispatcher.DispatchOptions options) {
            this.owner = owner;
            this.origin = owner.getLocation();
            this.title = title;
            this.items = List.copyOf(items);
            this.options = options;
        }
    }

    ExplorationChests(ChestModels models, PacketBridgeAPI bridge, String ui, ItemBridgeAPI icons,
                      VanillaItemNameBridge names, ItemRewardDispatcher rewards, MessageProvider messages,
                      Predicate<Player> admitted) {
        this.models = models;
        this.bridge = bridge;
        this.ui = ui;
        this.icons = icons;
        this.names = names;
        this.rewards = rewards;
        this.messages = messages;
        this.admitted = admitted;
    }

    boolean spawn(Player player, String title, String model, double scale, List<ItemStack> items,
                  ItemRewardDispatcher.DispatchOptions options) {
        if (pending(player.getUniqueId())) return false;
        Chest chest = new Chest(player, title, items, options);
        if (!models.spawn(player, model, scale, clicker -> open(clicker, chest))) return false;
        chests.put(player.getUniqueId(), chest);
        return true;
    }

    boolean pending(UUID player) {
        return chests.containsKey(player);
    }

    private boolean canUse(Player player, Chest chest) {
        return chest != null && chest.owner == player && chests.get(player.getUniqueId()) == chest
            && player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
            && admitted.test(player) && models.inReach(player);
    }

    private void open(Player player, Chest chest) {
        if (!canUse(player, chest)) return;
        if (chest.token != null && bridge.isUiOpen(player, ui)) return;
        String token = UUID.randomUUID().toString();
        chest.token = token;
        if (!bridge.openUiWithCallback(player, ui, () -> syncOpened(player, chest, token))) {
            chest.token = null;
        }
    }

    private void syncOpened(Player player, Chest chest, String token) {
        if (!token.equals(chest.token) || !canUse(player, chest) || !bridge.isUiOpen(player, ui)) return;
        var rows = new LinkedHashMap<String, Object>();
        for (ItemStack stack : chest.items) {
            String icon = icons == null ? "" : icons.itemToPreviewJson(stack).orElse("");
            rows.put(String.valueOf(rows.size()), Map.of("name", names.resolveDisplayName(stack),
                "amount", stack.getAmount(), "icon", icon));
        }
        if (!bridge.sendPacket(player, ui, "sync", Map.of("title", chest.title, "count", rows.size(), "rows", rows, "token", token))) {
            chest.token = null;
            bridge.closeUi(player, ui);
        }
    }

    void claim(Player player, List<String> data) {
        Chest chest = chests.get(player.getUniqueId());
        if (data.size() != 2 || !"claim".equals(data.get(0)) || chest == null || chest.token == null
            || !chest.token.equals(data.get(1)) || !canUse(player, chest) || !bridge.isUiOpen(player, ui)) return;
        // Retire the claim before dispatch: reward delivery may trigger synchronous events.
        remove(player);
        boolean delivered = rewards.dispatchItems(player, chest.items, chest.options).success();
        player.sendMessage(messages.get(delivered ? "chest-claimed" : "delivery-failed"));
    }

    void remove(Player player) {
        Chest chest = chests.get(player.getUniqueId());
        if (chest == null || chest.owner != player) return;
        chests.remove(player.getUniqueId());
        boolean viewing = chest.token != null;
        chest.token = null;
        try {
            models.remove(player);
        } finally {
            if (viewing && bridge.isUiOpen(player, ui)) bridge.closeUi(player, ui);
        }
    }

    private void checkDistance(Player player, Location to) {
        Chest chest = chests.get(player.getUniqueId());
        if (chest != null && to != null && (!to.getWorld().equals(chest.origin.getWorld())
            || to.distanceSquared(chest.origin) > 32 * 32)) remove(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        checkDistance(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        checkDistance(event.getPlayer(), event.getTo());
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { remove(event.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { remove(event.getEntity()); }

    @EventHandler public void onRestriction(AuthRestrictionEvent event) {
        Chest chest = chests.get(event.previous().playerId());
        if (chest != null) remove(chest.owner);
    }

    void shutdown() {
        try {
            for (Chest chest : List.copyOf(chests.values())) remove(chest.owner);
        } finally {
            models.shutdown();
        }
    }
}
