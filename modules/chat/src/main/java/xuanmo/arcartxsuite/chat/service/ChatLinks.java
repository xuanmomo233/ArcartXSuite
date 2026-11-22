package xuanmo.arcartxsuite.chat.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AbyssQueryable;
import xuanmo.arcartxsuite.api.capability.AxsMarketService;
import xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec;
import xuanmo.arcartxsuite.api.capability.MapNavigable;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.capability.RegionQueryable;
import xuanmo.arcartxsuite.chat.config.ChatChannelDefinition;
import xuanmo.arcartxsuite.chat.config.ChatChannelMode;
import xuanmo.arcartxsuite.chat.model.ChatOperationResult;

/** Local clickable links with the same audience and session checks as the chat message that carried them. */
public final class ChatLinks {
    private static final long LIFETIME_MILLIS = 120_000L;
    private static final int MAX_LINKS = 1024;
    private final Map<String, Link> links = new LinkedHashMap<>();
    private final ChatService chat;
    private final PacketBridgeAPI packets;
    private final Supplier<MapNavigable> maps;
    private final Supplier<QuestGpsNavigable> quests;
    private final Supplier<AxsMarketService> market;
    private final Supplier<AbyssQueryable> abyss;
    private final Supplier<RegionQueryable> regions;
    private final Map<UUID, Long> activeLocations = new LinkedHashMap<>();

    public ChatLinks(ChatService chat, PacketBridgeAPI packets, Supplier<MapNavigable> maps,
                     Supplier<QuestGpsNavigable> quests, Supplier<AxsMarketService> market,
                     Supplier<AbyssQueryable> abyss, Supplier<RegionQueryable> regions) {
        this.chat = chat;
        this.packets = packets;
        this.maps = maps;
        this.quests = quests;
        this.market = market;
        this.abyss = abyss;
        this.regions = regions;
    }

    public void location(Player sender, boolean meeting, Consumer<ChatOperationResult> result) {
        if (maps.get() == null) {
            result.accept(chat.linkResult("links.unavailable"));
            return;
        }
        AbyssQueryable dungeon = abyss.get();
        boolean inDungeon = dungeon != null && dungeon.isInDungeon(sender.getUniqueId());
        boolean partyOnly = meeting || inDungeon;
        ChatChannelDefinition channel = partyOnly ? chat.partyChannel() : null;
        if (partyOnly && channel == null) {
            result.accept(chat.linkResult("result.no-channel"));
            return;
        }
        Location location = sender.getLocation();
        if (location.getWorld() == null) {
            result.accept(chat.linkResult("links.unavailable"));
            return;
        }
        UUID worldId = location.getWorld().getUID();
        var protectedRegions = protectedRegions(location);
        if (protectedRegions.stream().anyMatch(region -> !region.hasAccess(sender.getUniqueId()))) {
            result.accept(chat.linkResult("links.unavailable"));
            return;
        }
        String title = meeting ? "集合点" : "坐标";
        String label = title + " " + location.getWorld().getName() + " "
            + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
        chat.sendLink(sender, channel, label, (selected, viewers) -> {
            var currentRegions = protectedRegions(location);
            if (currentRegions.stream().anyMatch(region -> !region.hasAccess(sender.getUniqueId()))) {
                return chat.linkResult("links.unavailable");
            }
            var allowed = viewers.stream().filter(viewer ->
                currentRegions.stream().allMatch(region -> region.hasAccess(viewer.getUniqueId()))).toList();
            return publish(sender, selected, label, "导航", allowed, viewer -> {
                MapNavigable map = maps.get();
                if (map == null || (inDungeon && !viewer.getWorld().getUID().equals(worldId))) return false;
                if (protectedRegions(location).stream().anyMatch(region ->
                        !region.hasAccess(sender.getUniqueId()) || !region.hasAccess(viewer.getUniqueId()))) return false;
                String id = "chat:shared-location";
                map.upsertExternalTarget(viewer, new MapExternalTargetSpec(id, "chat", location.getWorld().getName(),
                    title, sender.getName(), location.getX(), location.getY(), location.getZ(), null, false, 0, true), true);
                activeLocations.put(viewer.getUniqueId(), System.currentTimeMillis() + LIFETIME_MILLIS);
                return map.navigateExternalTarget(viewer, id);
            });
        }, result);
    }

    private List<RegionQueryable.RegionInfo> protectedRegions(Location location) {
        RegionQueryable provider = regions.get();
        if (provider == null || location.getWorld() == null) return List.of();
        return provider.regionsAt(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ())
            .stream().filter(region -> !region.owners().isEmpty() || !region.members().isEmpty()).toList();
    }

    public void quest(Player sender, String questId, Consumer<ChatOperationResult> result) {
        QuestGpsNavigable navigation = quests.get();
        if (navigation == null || !validKey(questId)
                || navigation.questState(sender, questId) != QuestGpsNavigable.QuestState.ACTIVE) {
            result.accept(chat.linkResult("links.unavailable"));
            return;
        }
        String label = "任务 " + questId;
        chat.sendLink(sender, null, label, (channel, viewers) -> publish(sender, channel, label, "追踪任务", viewers, viewer -> {
            QuestGpsNavigable gps = quests.get();
            if (gps == null || gps.questState(viewer, questId) != QuestGpsNavigable.QuestState.ACTIVE) return false;
            gps.trackQuest(viewer, questId);
            return true;
        }), result);
    }

    public void market(Player sender, String type, String key, Consumer<ChatOperationResult> result) {
        AxsMarketService provider = market.get();
        if (provider == null || !validKey(key)) {
            result.accept(chat.linkResult("links.unavailable"));
            return;
        }
        UUID owner = sender.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(owner);
        long channelRevision = chat.channelRevision(owner);
        provider.describeChatLink(sender, type, key).whenComplete((description, failure) -> chat.onPrimary(() -> {
            if (chat.isStopping() || !sameSession(sender, session)) return;
            if (failure != null || description == null) {
                result.accept(chat.linkResult("links.unavailable"));
                return;
            }
            if (chat.channelRevision(owner) != channelRevision) {
                result.accept(chat.linkResult("result.channel-changed-while-sending"));
                return;
            }
            chat.sendLink(sender, null, description, (channel, viewers) ->
                publishAsync(sender, channel, description, "打开市场", viewers, (viewer, opened) -> {
                    AxsMarketService live = market.get();
                    if (live == null) {
                        opened.accept(false);
                        return;
                    }
                    AuthSnapshot viewing = AuthAccess.snapshot(viewer.getUniqueId());
                    live.openChatLink(viewer, type, key).whenComplete((current, error) -> chat.onPrimary(() -> {
                        if (chat.isStopping() || !sameSession(viewer, viewing)) return;
                        if (error != null || current == null) {
                            opened.accept(false);
                            return;
                        }
                        if (!current.equals(description)) viewer.sendMessage("市场信息已更新 · " + ChatColor.stripColor(current));
                        opened.accept(true);
                    }));
                }), result);
        }));
    }

    public void ready(Player sender, Consumer<ChatOperationResult> result) {
        var party = chat.partyOf(sender);
        if (party == null || !party.leader().equals(sender.getUniqueId())) {
            result.accept(chat.linkResult("links.leader-required"));
            return;
        }
        ChatChannelDefinition channel = chat.partyChannel();
        if (channel == null) {
            result.accept(chat.linkResult("result.no-channel"));
            return;
        }
        String label = "小队准备确认";
        chat.sendLink(sender, channel, label, (selected, viewers) -> {
            var current = chat.partyOf(sender);
            if (current == null || !current.leader().equals(sender.getUniqueId())) return chat.linkResult("links.leader-required");
            return publish(sender, selected, label, "我已准备", viewers, viewer ->
                viewer.performCommand("abyss party ready on"));
        }, result);
    }

    private ChatOperationResult publish(Player sender, ChatChannelDefinition channel, String label, String action,
                                        List<Player> viewers, Predicate<Player> handler) {
        return publishAsync(sender, channel, label, action, viewers, (viewer, opened) -> opened.accept(handler.test(viewer)));
    }

    private ChatOperationResult publishAsync(Player sender, ChatChannelDefinition channel, String label, String action,
                                             List<Player> viewers, BiConsumer<Player, Consumer<Boolean>> handler) {
        if (viewers.isEmpty()) return chat.linkResult("links.no-audience");
        String token = UUID.randomUUID().toString().replace("-", "");
        Map<UUID, AuthSnapshot> recipients = new LinkedHashMap<>();
        for (Player player : viewers) {
            recipients.put(player.getUniqueId(), AuthAccess.snapshot(player.getUniqueId()));
        }
        var party = chat.partyOf(sender);
        UUID partyId = channel.mode() == ChatChannelMode.PARTY && party != null ? party.id() : null;
        prune();
        if (links.size() >= MAX_LINKS) links.remove(links.keySet().iterator().next());
        links.put(token, new Link(sender.getUniqueId(), AuthAccess.snapshot(sender.getUniqueId()), channel.id(),
            channel.audienceKey(), partyId, recipients, System.currentTimeMillis() + LIFETIME_MILLIS, handler));
        String title = "[" + channel.displayName() + "] " + sender.getName() + " · " + ChatColor.stripColor(label);
        for (Player recipient : viewers) {
            if (!packets.sendChatCard(recipient, "axs_chat_link", Map.of("title", title, "action", action, "token", token))) {
                TextComponent text = new TextComponent(title + " [" + action + "]");
                text.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chat open " + token));
                recipient.spigot().sendMessage(text);
            }
        }
        chat.recordLinkSend(sender, label);
        return ChatOperationResult.success(chat.linkMessage("links.sent"));
    }

    public void open(Player viewer, String token, Consumer<ChatOperationResult> result) {
        prune();
        Link link = links.get(token);
        if (link == null || !sameSession(viewer, link.recipients().get(viewer.getUniqueId()))) {
            result.accept(chat.linkResult("links.expired"));
            return;
        }
        Player sender = Bukkit.getPlayer(link.sender());
        ChatChannelDefinition channel = chat.configuration().channel(link.channelId());
        if (channel == null || !channel.audienceKey().equals(link.audienceKey())
                || !sameSession(sender, link.senderSession())) {
            result.accept(chat.linkResult("links.expired"));
            return;
        }
        chat.checkLinkAudience(channel, sender, viewer, allowed -> {
            if (!allowed || links.get(token) != link || link.expiresAt() <= System.currentTimeMillis()
                    || !sameSession(sender, link.senderSession())) {
                result.accept(chat.linkResult("links.expired"));
                return;
            }
            if (link.partyId() != null) {
                var party = chat.partyOf(viewer);
                if (party == null || !link.partyId().equals(party.id())) {
                    result.accept(chat.linkResult("links.expired"));
                    return;
                }
            }
            link.handler().accept(viewer, opened -> result.accept(opened
                ? ChatOperationResult.success("") : chat.linkResult("links.unavailable")));
        });
    }

    void prune() {
        if (links.isEmpty() && activeLocations.isEmpty()) return;
        long now = System.currentTimeMillis();
        var iterator = links.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAt() <= now) iterator.remove();
        }
        if (activeLocations.isEmpty()) return;
        for (UUID id : java.util.List.copyOf(activeLocations.keySet())) {
            Long expiresAt = activeLocations.get(id);
            if (expiresAt != null && expiresAt <= now) clearLocation(id);
        }
    }

    void clearLocation(UUID id) {
        if (activeLocations.remove(id) == null) return;
        MapNavigable map = maps.get();
        Player player = Bukkit.getPlayer(id);
        if (map != null && player != null) map.clearExternalTargets(player, "chat", true);
    }

    void close() {
        for (UUID id : java.util.List.copyOf(activeLocations.keySet())) clearLocation(id);
        links.clear();
    }

    private static boolean sameSession(Player player, AuthSnapshot previous) {
        if (player == null || !player.isOnline() || previous == null) return false;
        AuthSnapshot current = AuthAccess.snapshot(player.getUniqueId());
        return current.admitted() && current.generation() == previous.generation()
            && java.util.Objects.equals(current.connectionId(), previous.connectionId());
    }

    private static boolean validKey(String key) {
        return key.matches("[a-zA-Z0-9_.:-]{1,80}");
    }

    private record Link(UUID sender, AuthSnapshot senderSession, String channelId, String audienceKey, UUID partyId,
                        Map<UUID, AuthSnapshot> recipients, long expiresAt, BiConsumer<Player, Consumer<Boolean>> handler) {}
}
