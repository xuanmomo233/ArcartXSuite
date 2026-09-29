package xuanmo.arcartxsuite.abyss.ui;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.party.Party;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.abyss.template.RewardConfig;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;

/**
 * Abyss ArcartX UI 服务：5 个界面（浏览/队伍/宝箱/HUD/排行统计）+ 客户端包路由。
 * <p>
 * 通道约定：客户端 {@code Packet.send('abyss', '<action>', ...)} →
 * {@link AbyssService#handleClientPacket} → 本类 {@link #handlePacket}。
 * 服务端→客户端：{@code packetBridge.sendPacket(player, "AXS:<ui>", "<handler>", payload)}。
 * 所有回包带 action_token 校验（防伪造）。
 */
public final class AbyssUiService implements AbyssService.UiServiceFacade,
    AbyssService.ChestUiHandler {

    public static final String CHANNEL = "abyss";

    private final AbyssService service;
    private final @Nullable PacketBridgeAPI packetBridge;
    private final @Nullable ItemBridgeAPI itemBridge;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();

    /** 玩家 → 操作令牌（每次开界面轮换）。 */
    private final Map<UUID, String> tokens = new ConcurrentHashMap<>();
    /** 玩家 → 待领取宝箱物品（UI 打开期间 roll 好的）。 */
    private final Map<UUID, List<ItemStack>> pendingChests = new ConcurrentHashMap<>();

    public AbyssUiService(AbyssService service, @Nullable PacketBridgeAPI packetBridge,
                          @Nullable ItemBridgeAPI itemBridge, Logger logger) {
        this.service = service;
        this.packetBridge = packetBridge;
        this.itemBridge = itemBridge;
        this.logger = logger;
    }

    public boolean available() {
        return packetBridge != null && packetBridge.isAvailable();
    }

    // ── 配置化 UI id（AXS:<id>，已带前缀则不重复添加）──
    private static String axsUiId(String id) {
        return id != null && id.startsWith("AXS:") ? id : "AXS:" + id;
    }
    private String browserUi() { return axsUiId(service.config().ui().browserId()); }
    private String confirmUi() { return axsUiId(service.config().ui().confirmId()); }
    private String partyUi() { return axsUiId(service.config().ui().partyId()); }
    private String chestUi() { return axsUiId(service.config().ui().chestId()); }
    private String statsUi() { return axsUiId(service.config().ui().statsId()); }
    private String leaderboardUi() { return axsUiId(service.config().ui().leaderboardId()); }
    private String hudUi() { return axsUiId(service.config().ui().hudId()); }

    private String token(Player p) {
        String t = Long.toHexString(random.nextLong());
        tokens.put(p.getUniqueId(), t);
        return t;
    }

    private boolean checkToken(Player p, List<String> data, int index) {
        String expected = tokens.get(p.getUniqueId());
        return expected != null && data.size() > index && expected.equals(data.get(index));
    }

    // ═══ UiServiceFacade ═══

    @Override
    public void openBrowser(Player player) {
        if (!available()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("title", "深渊回廊");

        // 地牢列表（key 用整数字符串，UI 端 entryKey 整数匹配）
        Map<String, Object> rows = new LinkedHashMap<>();
        int i = 0;
        Party party = service.party().partyOf(player);
        int partySize = party != null ? party.size() : 1;
        for (AbyssTemplate t : service.instances().getAllTemplates()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.id());
            row.put("name", t.name());
            row.put("difficulty", t.difficulty());
            row.put("icon", t.icon());
            // 图标 itemJson（Slot.setItemIcon 渲染）
            org.bukkit.Material iconMat = org.bukkit.Material.matchMaterial(t.icon());
            row.put("iconJson", iconMat != null && itemBridge != null
                ? itemBridge.itemToJson(new org.bukkit.inventory.ItemStack(iconMat)).orElse("")
                : "");
            row.put("desc", String.join("\n", t.description()));
            var req = t.requirements();
            row.put("minPlayers", req.minPlayers());
            row.put("maxPlayers", req.maxPlayers());
            row.put("partyRequired", req.partyRequired());
            row.put("staminaCost", req.staminaCost());
            row.put("moneyCost", req.moneyCost());
            row.put("cooldown", req.cooldownSeconds());
            row.put("cooldownLeft", service.cooldowns().remaining(
                player.getUniqueId(), t.id()));
            row.put("stamina", service.stamina().getStamina(player.getUniqueId()));
            row.put("partySize", partySize);
            row.put("running", service.instances().getInstances(t.id()).stream()
                .filter(inst -> !inst.isFinished()).count());
            rows.put(Integer.toString(i++), row);
        }
        payload.put("dungeonRows", rows);
        payload.put("dungeonCount", i);
        payload.put("hasParty", party != null);
        payload.put("partyText", party != null
            ? party.size() + " 人小队" : "未组队");

        packetBridge.sendPacket(player, browserUi(), "init", payload);
        packetBridge.openUiUnsafe(player, browserUi());
    }

    @Override
    public void openEntryConfirm(Player player, AbyssTemplate template) {
        if (!available()) return;
        var req = template.requirements();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("dungeonId", template.id());
        payload.put("dungeonName", template.name());
        payload.put("difficulty", template.difficulty());
        payload.put("desc", String.join("\n", template.description()));
        payload.put("minPlayers", req.minPlayers());
        payload.put("maxPlayers", req.maxPlayers());
        payload.put("staminaCost", req.staminaCost());
        payload.put("moneyCost", req.moneyCost());
        payload.put("cooldown", req.cooldownSeconds());
        payload.put("timeLimit", template.timeLimitSeconds());
        packetBridge.sendPacket(player, confirmUi(), "init", payload);
        packetBridge.openUiUnsafe(player, confirmUi());
    }

    @Override
    public void openParty(Player player) {
        if (!available()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));

        Party party = service.party().partyOf(player);
        Map<String, Object> members = new LinkedHashMap<>();
        Map<String, Object> invites = new LinkedHashMap<>();
        if (party != null) {
            payload.put("hasParty", true);
            payload.put("isLeader", party.isLeader(player.getUniqueId()));
            payload.put("partyName", party.name());
            payload.put("lootMode", party.lootMode().name());
            payload.put("expShare", service.party().expShareMode().name());
            int i = 0;
            for (UUID uid : party.members()) {
                Map<String, Object> m = new LinkedHashMap<>();
                org.bukkit.entity.Player mp = org.bukkit.Bukkit.getPlayer(uid);
                m.put("name", mp != null ? mp.getName() : uid.toString().substring(0, 8));
                m.put("online", mp != null);
                m.put("leader", party.isLeader(uid));
                members.put(Integer.toString(i++), m);
            }
            payload.put("memberCount", party.size());
        } else {
            payload.put("hasParty", false);
            payload.put("isLeader", false);
            payload.put("memberCount", 0);
        }
        payload.put("memberRows", members);
        // 收到的邀请
        int j = 0;
        for (String from : service.party().inviteSources(player.getUniqueId())) {
            invites.put(Integer.toString(j++), Map.of("from", from));
        }
        payload.put("inviteRows", invites);
        payload.put("maxMembers", service.party().maxMembers());

        packetBridge.sendPacket(player, partyUi(), "init", payload);
        packetBridge.openUiUnsafe(player, partyUi());
    }

    @Override
    public void openStats(Player player, AbyssInstance instance) {
        if (!available()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("dungeonName", instance != null ? instance.getTemplate().name() : "");
        payload.put("mode", "stats");

        Map<String, Object> rows = new LinkedHashMap<>();
        int i = 0;
        if (instance != null) {
            for (Map.Entry<UUID, Integer> e : instance.getKillCounts().entrySet()) {
                Player p = org.bukkit.Bukkit.getPlayer(e.getKey());
                Map<String, Double> sum = service.stats().summary(instance, e.getKey());
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("name", p != null ? p.getName() : "?");
                r.put("kills", e.getValue());
                r.put("damage", sum.get("damage"));
                r.put("taken", sum.get("damage_taken"));
                r.put("heal", sum.get("healing"));
                rows.put(Integer.toString(i++), r);
            }
        }
        payload.put("statRows", rows);
        packetBridge.sendPacket(player, statsUi(), "init", payload);
        packetBridge.openUiUnsafe(player, statsUi());
    }

    @Override
    public void openLeaderboard(Player player, String dungeonId) {
        if (!available()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("mode", "leaderboard");

        AbyssTemplate t = service.instances().getTemplate(dungeonId);
        payload.put("dungeonName", t != null ? t.name() : dungeonId);

        Map<String, Object> rows = new LinkedHashMap<>();
        var top = service.leaderboard().top(dungeonId);
        int i = 0;
        for (var e : top) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("rank", e.rank());
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(e.playerUuid());
            org.bukkit.OfflinePlayer op = p != null ? p
                : org.bukkit.Bukkit.getOfflinePlayer(e.playerUuid());
            r.put("name", op.getName() != null ? op.getName() : "?");
            r.put("time", AbyssService.formatTime(e.bestTimeSeconds()));
            r.put("completions", e.completions());
            rows.put(Integer.toString(i++), r);
        }
        payload.put("boardRows", rows);
        packetBridge.sendPacket(player, leaderboardUi(), "init", payload);
        packetBridge.openUiUnsafe(player, leaderboardUi());
    }

    // ═══ ChestUiHandler ═══

    @Override
    public void open(Player player, RewardConfig.ChestReward chest, AbyssInstance instance) {
        if (!available()) {
            // UI 不可用时回退直接发放
            List<ItemStack> items = service.rewards().rollChestOptions(chest,
                Math.min(9, chest.rows() * 9 / 2));
            if (!items.isEmpty()) {
                service.dispatchItems(player, items, "chest:" + chest.name());
            }
            return;
        }
        List<ItemStack> items = service.rewards().rollChestOptions(chest,
            Math.min(27, chest.rows() * 9));
        pendingChests.put(player.getUniqueId(), items);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("chestTitle", chest.title());
        payload.put("itemCount", items.size());
        for (int i = 0; i < items.size(); i++) {
            payload.put("itemJson" + i, itemBridge != null
                ? itemBridge.itemToJson(items.get(i)).orElse("") : "");
        }
        packetBridge.sendPacket(player, chestUi(), "init", payload);
        packetBridge.openUiUnsafe(player, chestUi());
    }

    // ═══ HUD ═══

    /** 进入副本：开 HUD + 首帧数据。 */
    public void openHud(Player player, AbyssInstance instance) {
        if (!available()) return;
        pushHudUpdate(player, instance);
        packetBridge.openUiUnsafe(player, hudUi());
    }

    /** 离开副本：关 HUD。 */
    public void closeHud(Player player) {
        if (available()) packetBridge.closeUiUnsafe(player, hudUi());
    }

    /** HUD 周期数据推送（service proximityTick 调用）。 */
    public void pushHudUpdate(Player player, AbyssInstance instance) {
        if (!available()) return;
        var mgr = service.mobGroups();
        int[] gp = mgr.groupProgress(instance);
        long limit = instance.getTemplate().timeLimitSeconds();
        long remain = limit > 0 ? Math.max(0, limit - instance.getRunningTime()) : 0;
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("dungeonName", instance.getTemplate().name());
        p.put("status", instance.getStatus().name());
        p.put("groupTotal", gp[0]);
        p.put("groupDone", gp[1]);
        p.put("activeGroup", mgr.activeGroupName(instance) != null
            ? mgr.activeGroupName(instance) : "");
        p.put("aliveMobs", mgr.totalAlive(instance));
        p.put("alivePlayers", instance.getAlivePlayerCount());
        p.put("totalPlayers", instance.getOnlinePlayerCount());
        p.put("timeText", limit > 0 ? AbyssService.formatTime(remain) : "∞");
        p.put("timeRatio", limit > 0
            ? Math.max(0.0, Math.min(1.0, (double) remain / limit)) : 1.0);
        // 任务行
        Map<String, Object> quests = new LinkedHashMap<>();
        int qi = 0;
        for (var e : service.quests().statusMap(instance).entrySet()) {
            var q = instance.getTemplate().quests().quests().get(e.getKey());
            if (q == null) continue;
            quests.put(Integer.toString(qi++), Map.of(
                "name", q.name(), "status", e.getValue().name()));
        }
        p.put("questRows", quests);
        packetBridge.sendPacket(player, hudUi(), "update", p);
    }

    // ═══ 客户端包路由 ═══

    @Override
    public boolean handlePacket(Player player, String packetId, List<String> data) {
        if (!CHANNEL.equalsIgnoreCase(packetId) || data == null || data.isEmpty()) {
            return false;
        }
        String action = data.get(0).toLowerCase();
        switch (action) {
            case "enter" -> {
                if (!checkToken(player, data, 2)) return true;
                String dungeonId = data.size() > 1 ? data.get(1) : null;
                if (dungeonId == null) return true;
                // 组队时整队进本；单人直接进
                Party party = service.party().partyOf(player);
                List<Player> entrants = party != null && party.size() > 1
                    ? party.onlineMembers() : List.of(player);
                AbyssService.EnterResult r = service.enterDungeon(entrants, dungeonId);
                if (!r.ok() && r.reason() != null) {
                    player.sendMessage(r.reason());
                }
                return true;
            }
            case "refresh_browser" -> {
                if (checkToken(player, data, 1)) openBrowser(player);
                return true;
            }
            case "open_party_ui" -> {
                if (checkToken(player, data, 1)) openParty(player);
                return true;
            }
            case "party_invite" -> {
                if (checkToken(player, data, 2)) {
                    Player target = data.size() > 1
                        ? org.bukkit.Bukkit.getPlayerExact(data.get(1)) : null;
                    if (target != null) {
                        service.party().invite(player, target);
                    } else {
                        player.sendMessage("§c玩家不在线");
                    }
                }
                return true;
            }
            case "party_accept" -> {
                if (checkToken(player, data, 1)) service.party().accept(player);
                return true;
            }
            case "party_leave" -> {
                if (checkToken(player, data, 1)) service.party().leave(player);
                return true;
            }
            case "party_disband" -> {
                if (checkToken(player, data, 1)) {
                    Party party = service.party().partyOf(player);
                    if (party != null && party.isLeader(player.getUniqueId())) {
                        service.party().disband(party);
                    }
                }
                return true;
            }
            case "party_kick" -> {
                if (checkToken(player, data, 2)) {
                    Player target = data.size() > 1
                        ? org.bukkit.Bukkit.getPlayerExact(data.get(1)) : null;
                    if (target != null) service.party().kick(player, target);
                }
                return true;
            }
            case "party_transfer" -> {
                if (checkToken(player, data, 2)) {
                    Player target = data.size() > 1
                        ? org.bukkit.Bukkit.getPlayerExact(data.get(1)) : null;
                    if (target != null) service.party().transferLeader(player, target);
                }
                return true;
            }
            case "party_chat" -> {
                if (checkToken(player, data, 2)) {
                    service.party().sendChat(player, data.size() > 1 ? data.get(1) : "");
                }
                return true;
            }
            case "chest_claim" -> {
                if (checkToken(player, data, 1)) claimChest(player, -1);
                return true;
            }
            case "chest_claim_one" -> {
                if (checkToken(player, data, 2)) {
                    claimChest(player, parseInt(data.get(1), -1));
                }
                return true;
            }
            case "open_stats" -> {
                if (checkToken(player, data, 1)) {
                    openStats(player, service.instances().getInstance(player));
                }
                return true;
            }
            default -> {
                logger.fine("[abyss] 未识别的客户端包动作: " + action);
                return false;
            }
        }
    }

    /** 宝箱领取：index<0 = 全部。 */
    private void claimChest(Player player, int index) {
        List<ItemStack> items = pendingChests.get(player.getUniqueId());
        if (items == null) return;
        if (index < 0) {
            pendingChests.remove(player.getUniqueId());
            if (!items.isEmpty()) {
                service.dispatchItems(player, items, "chest");
            }
            if (available()) packetBridge.closeUiUnsafe(player, chestUi());
            return;
        }
        if (index >= 0 && index < items.size()) {
            ItemStack it = items.remove(index);
            if (it != null) {
                service.dispatchItems(player, List.of(it), "chest");
            }
            if (items.isEmpty() && available()) {
                pendingChests.remove(player.getUniqueId());
                packetBridge.closeUiUnsafe(player, chestUi());
            }
        }
    }

    /** 玩家掉线/关 UI 未领取：全部发放（不丢奖励）。 */
    public void flushPendingChest(Player player) {
        List<ItemStack> items = pendingChests.remove(player.getUniqueId());
        if (items != null && !items.isEmpty()) {
            service.dispatchItems(player, items, "chest:auto");
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
