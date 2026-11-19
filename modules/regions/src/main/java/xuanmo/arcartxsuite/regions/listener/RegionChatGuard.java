package xuanmo.arcartxsuite.regions.listener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.capability.RegionChatAccess;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.service.RegionManager;

/** 聊天事件可能异步触发，区域、玩家位置和 Bukkit 权限查询留在主线程。 */
public final class RegionChatGuard implements Listener, RegionChatAccess {

    private final Plugin plugin;
    private final RegionManager manager;
    private final RegionsConfiguration config;
    private final java.util.concurrent.atomic.AtomicBoolean loggedFailure = new java.util.concurrent.atomic.AtomicBoolean();

    public RegionChatGuard(Plugin plugin, RegionManager manager, RegionsConfiguration config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        try {
            Class<? extends Event> type = Class.forName("io.papermc.paper.event.player.AsyncChatEvent").asSubclass(Event.class);
            var viewers = type.getMethod("viewers");
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGH, (listener, raw) -> {
                if (!type.isInstance(raw) || !manager.hasChatRules()) return;
                Cancellable event = (Cancellable) raw;
                Player sender = ((PlayerEvent) raw).getPlayer();
                try {
                    filter(event, sender, (Set<?>) viewers.invoke(raw));
                } catch (ReflectiveOperationException e) {
                    event.setCancelled(true);
                    logFailure(e);
                }
            }, plugin, true);
        } catch (ClassNotFoundException ignored) {
            // Spigot 使用 AsyncPlayerChatEvent。
        } catch (ReflectiveOperationException | RuntimeException e) {
            logFailure(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        // Paper 的 legacy 事件还可能被其他插件触发，原生事件也独立检查。
        if (manager.hasChatRules()) filter(event, event.getPlayer(), event.getRecipients());
    }

    private void filter(Cancellable event, Player sender, Set<?> recipients) {
        ChatDecision result;
        Future<ChatDecision> future = null;
        try {
            if (Bukkit.isPrimaryThread()) {
                result = check(sender, recipients);
            } else {
                // The event thread can resume at the timeout. Do not traverse its mutable viewer set then.
                var viewers = manager.hasReceiveChatRules() ? new ArrayList<>(recipients) : null;
                future = Bukkit.getScheduler().callSyncMethod(plugin, () -> check(sender, viewers));
                result = future.get(1, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            if (future != null) future.cancel(false);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            event.setCancelled(true);
            logFailure(e);
            return;
        }
        if (!result.canSend()) {
            event.setCancelled(true);
        } else if (!result.blockedRecipients().isEmpty()) {
            try {
                recipients.removeIf(viewer -> viewer instanceof Player player
                    && result.blockedRecipients().contains(player.getUniqueId()));
            } catch (UnsupportedOperationException e) {
                event.setCancelled(true);
                logFailure(e);
            }
        }
    }

    @Override
    public boolean canSendPublic(Player sender) {
        return !manager.hasSendChatRules()
            || manager.queryFlagForPlayer(sender.getLocation(), RegionFlag.SEND_CHAT, sender) != RegionFlag.State.DENY;
    }

    @Override
    public boolean canReceivePublic(Player recipient) {
        return !manager.hasReceiveChatRules()
            || manager.queryFlagForPlayer(recipient.getLocation(), RegionFlag.RECEIVE_CHAT, recipient) != RegionFlag.State.DENY;
    }

    private ChatDecision check(Player sender, Iterable<?> viewers) {
        if (!canSendPublic(sender)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.messages().prefix() + config.messages().noPermissionRegion()));
            return new ChatDecision(false, Set.of());
        }
        if (!manager.hasReceiveChatRules()) return new ChatDecision(true, Set.of());
        Set<UUID> blocked = new HashSet<>();
        // Receive rules may have been enabled since a send-only event entered the scheduler.
        if (viewers == null) viewers = Bukkit.getOnlinePlayers();
        for (Object viewer : viewers) {
            if (viewer instanceof Player recipient && !canReceivePublic(recipient)) {
                blocked.add(recipient.getUniqueId());
            }
        }
        return new ChatDecision(true, blocked);
    }

    private void logFailure(Exception error) {
        if (loggedFailure.compareAndSet(false, true)) {
            plugin.getLogger().log(Level.WARNING, "Regions 聊天规则检查失败", error);
        }
    }

    private record ChatDecision(boolean canSend, Set<UUID> blockedRecipients) {}
}
