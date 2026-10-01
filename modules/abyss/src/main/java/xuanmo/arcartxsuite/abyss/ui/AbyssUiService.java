package xuanmo.arcartxsuite.abyss.ui;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.party.Party;
import xuanmo.arcartxsuite.abyss.party.PartyManager;
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
    private String lobbyUi() { return axsUiId(service.config().ui().lobbyId()); }
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
            // 描述按行下发（ARIA texts 多行需逐行表达式，\n 不会渲染换行）
            List<String> dl = t.description();
            row.put("desc0", dl.size() > 0 ? dl.get(0) : "");
            row.put("desc1", dl.size() > 1 ? dl.get(1) : "");
            row.put("desc2", dl.size() > 2 ? dl.get(2) : "");
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
        List<String> dl = template.description();
        payload.put("desc0", dl.size() > 0 ? dl.get(0) : "");
        payload.put("desc1", dl.size() > 1 ? dl.get(1) : "");
        payload.put("desc2", dl.size() > 2 ? dl.get(2) : "");
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
        // 不在队伍中 → 打开组队大厅（房间是无队玩家的入口）
        if (service.party().partyOf(player) == null) {
            openLobby(player);
            return;
        }
        Map<String, Object> payload = partyRoomPayload(player, true);
        packetBridge.sendPacket(player, partyUi(), "init", payload);
        packetBridge.openUiUnsafe(player, partyUi());
    }

    @Override
    public void openLobby(Player player) {
        if (!available()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("title", "组队大厅");
        payload.put("hasParty", service.party().partyOf(player) != null);
        payload.put("allowInvite", service.party().inviteAllowed(player.getUniqueId()));
        putLobbyData(payload, player, "", "");
        packetBridge.sendPacket(player, lobbyUi(), "init", payload);
        packetBridge.openUiUnsafe(player, lobbyUi());
    }

    /** 组装大厅列表数据（供 init / lobby_list 推送复用）。 */
    private void putLobbyData(Map<String, Object> payload, Player player,
                              String keyword, String dungeonFilter) {
        // 公开队伍行
        Map<String, Object> partyRows = new LinkedHashMap<>();
        int i = 0;
        for (var e : service.party().listPublic(dungeonFilter, keyword)) {
            AbyssTemplate t = e.dungeonId().isEmpty()
                ? null : service.instances().getTemplate(e.dungeonId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("partyId", e.partyId().toString());
            row.put("name", e.name());
            row.put("leader", e.leaderName());
            row.put("size", e.size());
            row.put("max", e.max());
            row.put("dungeonId", e.dungeonId());
            row.put("dungeonName", t != null ? t.name()
                : (e.dungeonId().isEmpty() ? "未定" : e.dungeonId()));
            row.put("joinMode", e.joinMode().name());
            row.put("full", e.full());
            partyRows.put(Integer.toString(i++), row);
        }
        payload.put("partyRows", partyRows);
        payload.put("partyCount", i);

        // 副本筛选行
        Map<String, Object> dungeonRows = new LinkedHashMap<>();
        int di = 0;
        for (AbyssTemplate t : service.instances().getAllTemplates()) {
            dungeonRows.put(Integer.toString(di++),
                Map.of("id", t.id(), "name", t.name()));
        }
        payload.put("dungeonRows", dungeonRows);
        payload.put("dungeonCount", di);

        // 我收到的邀请
        Map<String, Object> inviteRows = new LinkedHashMap<>();
        int ii = 0;
        for (var e : service.party().inviteEntries(player.getUniqueId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("partyId", e.partyId().toString());
            row.put("partyName", e.partyName());
            row.put("from", e.from());
            inviteRows.put(Integer.toString(ii++), row);
        }
        payload.put("inviteRows", inviteRows);
        payload.put("inviteCount", ii);
        payload.put("keyword", keyword);
        payload.put("dungeonFilter", dungeonFilter);
    }

    /**
     * 组队大厅搜索/筛选结果推送（lobby_list handler）。
     */
    private void pushLobbyList(Player player, String keyword, String dungeonFilter) {
        if (!available()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        putLobbyData(payload, player, keyword, dungeonFilter);
        packetBridge.sendPacket(player, lobbyUi(), "lobby_list", payload);
    }

    /**
     * 组装副本房间 payload（init 用全量，update 用同样的键）。
     */
    private Map<String, Object> partyRoomPayload(Player viewer, boolean includeToken) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        if (includeToken) payload.put("action_token", token(viewer));

        Party party = service.party().partyOf(viewer);
        payload.put("hasParty", party != null);
        payload.put("myUuid", viewer.getUniqueId().toString());
        payload.put("maxMembers", service.party().maxMembers());
        if (party == null) return payload;

        payload.put("isLeader", party.isLeader(viewer.getUniqueId()));
        payload.put("partyName", party.name());
        payload.put("listed", party.listed());
        payload.put("joinMode", party.joinMode().name());
        payload.put("allReady", party.allReady());
        payload.put("myReady", party.isReady(viewer.getUniqueId()));

        // 成员行（含模型 UUID、准备状态、队长标记）
        Map<String, Object> members = new LinkedHashMap<>();
        int i = 0;
        for (UUID uid : party.members()) {
            Map<String, Object> m = new LinkedHashMap<>();
            org.bukkit.entity.Player mp = org.bukkit.Bukkit.getPlayer(uid);
            m.put("uuid", uid.toString());
            m.put("name", mp != null ? mp.getName() : uid.toString().substring(0, 8));
            m.put("online", mp != null);
            m.put("leader", party.isLeader(uid));
            m.put("ready", party.isReady(uid));
            members.put(Integer.toString(i++), m);
        }
        payload.put("memberRows", members);
        payload.put("memberCount", i);

        // 目标副本信息
        String dungeonId = party.dungeonId();
        AbyssTemplate t = dungeonId != null
            ? service.instances().getTemplate(dungeonId) : null;
        payload.put("hasDungeon", t != null);
        if (t != null) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", t.id());
            d.put("name", t.name());
            d.put("difficulty", t.difficulty());
            org.bukkit.Material iconMat = org.bukkit.Material.matchMaterial(t.icon());
            d.put("iconJson", iconMat != null && itemBridge != null
                ? itemBridge.itemToJson(new org.bukkit.inventory.ItemStack(iconMat)).orElse("")
                : "");
            List<String> dl = t.description();
            d.put("desc0", dl.isEmpty() ? "" : dl.get(0));
            var req = t.requirements();
            d.put("minPlayers", req.minPlayers());
            d.put("maxPlayers", req.maxPlayers());
            d.put("timeLimit", t.timeLimitSeconds());
            d.put("staminaCost", req.staminaCost());
            payload.put("dungeon", d);
        }

        // 消息流（聊天 + 系统事件）
        Map<String, Object> feedRows = new LinkedHashMap<>();
        int fi = 0;
        for (var e : party.feedSnapshot()) {
            feedRows.put(Integer.toString(fi++), Map.of(
                "sender", e.sender() != null ? e.sender() : "",
                "text", e.text()));
        }
        payload.put("feedRows", feedRows);
        payload.put("feedCount", fi);

        // 待审批申请（仅队长可见）
        Map<String, Object> applicantRows = new LinkedHashMap<>();
        int ai = 0;
        if (party.isLeader(viewer.getUniqueId())) {
            for (UUID u : party.applications()) {
                org.bukkit.entity.Player ap = org.bukkit.Bukkit.getPlayer(u);
                applicantRows.put(Integer.toString(ai++), Map.of(
                    "uuid", u.toString(),
                    "name", ap != null ? ap.getName() : u.toString().substring(0, 8)));
            }
        }
        payload.put("applicantRows", applicantRows);
        payload.put("applicantCount", ai);

        // 副本选择列表（队长"更换副本"副面板用）
        Map<String, Object> allDungeons = new LinkedHashMap<>();
        int di = 0;
        for (AbyssTemplate dt : service.instances().getAllTemplates()) {
            allDungeons.put(Integer.toString(di++),
                Map.of("id", dt.id(), "name", dt.name(),
                    "difficulty", dt.difficulty()));
        }
        payload.put("allDungeonRows", allDungeons);
        payload.put("allDungeonCount", di);
        return payload;
    }

    @Override
    public void pushPartyUpdate(Party party) {
        if (!available() || party == null) return;
        for (Player member : party.onlineMembers()) {
            Map<String, Object> payload = partyRoomPayload(member, false);
            packetBridge.sendPacket(member, partyUi(), "update", payload);
        }
    }

    /** 向房间 UI 推送在线玩家列表（邀请副面板）。 */
    private void pushOnlinePlayers(Player player) {
        if (!available()) return;
        Party party = service.party().partyOf(player);
        Map<String, Object> rows = new LinkedHashMap<>();
        int i = 0;
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(player.getUniqueId())) continue;
            if (service.party().inParty(p.getUniqueId())) continue;
            if (!service.party().inviteAllowed(p.getUniqueId())) continue;
            rows.put(Integer.toString(i++), Map.of(
                "uuid", p.getUniqueId().toString(), "name", p.getName()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("onlineRows", rows);
        payload.put("onlineCount", i);
        packetBridge.sendPacket(player, partyUi(), "online_list", payload);
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

    /** HUD 已确认打开的玩家（仅这些玩家会收到周期推送，避免给未渲染客户端刷屏包）。 */
    private final Set<UUID> hudOpen = ConcurrentHashMap.newKeySet();

    /** 进入副本：开 HUD + 首帧数据。 */
    public void openHud(Player player, AbyssInstance instance) {
        if (!available()) return;
        if (packetBridge.openUiUnsafe(player, hudUi())) {
            hudOpen.add(player.getUniqueId());
            pushHudUpdate(player, instance);
        }
    }

    /** 离开副本：关 HUD。 */
    public void closeHud(Player player) {
        hudOpen.remove(player.getUniqueId());
        if (available()) packetBridge.closeUiUnsafe(player, hudUi());
    }

    /** HUD 周期数据推送（service proximityTick 调用）。仅在 HUD 已打开时推送。 */
    public void pushHudUpdate(Player player, AbyssInstance instance) {
        if (!available() || !hudOpen.contains(player.getUniqueId())) return;
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
                        handleInviteResult(player,
                            service.party().invite(player, target), target);
                    } else {
                        player.sendMessage(service.msg("error.player-not-found",
                            "player", data.size() > 1 ? data.get(1) : ""));
                    }
                }
                return true;
            }
            case "party_invite_uuid" -> {
                if (checkToken(player, data, 2)) {
                    UUID tu = parseUuid(data.size() > 1 ? data.get(1) : null);
                    Player target = tu != null ? org.bukkit.Bukkit.getPlayer(tu) : null;
                    if (target != null) {
                        handleInviteResult(player,
                            service.party().invite(player, target), target);
                    } else {
                        player.sendMessage(service.msg("error.player-not-found",
                            "player", ""));
                    }
                }
                return true;
            }
            case "party_online" -> {
                // 邀请副面板请求在线玩家列表
                if (checkToken(player, data, 1)) pushOnlinePlayers(player);
                return true;
            }
            case "party_accept" -> {
                if (checkToken(player, data, 1)) {
                    var r = service.party().accept(player);
                    if (r instanceof PartyManager.JoinResult.Ok ok) {
                        ok.party().appendFeed(null, player.getName() + " 加入了队伍");
                        pushPartyUpdate(ok.party());
                        openParty(player);
                    }
                }
                return true;
            }
            case "party_leave" -> {
                if (checkToken(player, data, 1)) {
                    Party party = service.party().partyOf(player);
                    if (party != null) {
                        party.appendFeed(null, player.getName() + " 离开了队伍");
                        service.party().leave(player);
                        pushPartyUpdate(party);
                    }
                }
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
            case "party_ready" -> {
                if (checkToken(player, data, 1)) {
                    Party party = service.party().partyOf(player);
                    if (party != null) {
                        service.party().toggleReady(player);
                        pushPartyUpdate(party);
                    }
                }
                return true;
            }
            case "party_start" -> {
                // 队长开始游戏：要求全员已准备
                if (checkToken(player, data, 1)) {
                    Party party = service.party().partyOf(player);
                    if (party == null || !party.isLeader(player.getUniqueId())) {
                        return true;
                    }
                    if (party.size() > 1 && !party.allReady()) {
                        player.sendMessage(service.msg("party.not-all-ready"));
                        return true;
                    }
                    String dungeonId = party.dungeonId();
                    if (dungeonId == null) {
                        player.sendMessage(service.msg("party.no-dungeon"));
                        return true;
                    }
                    AbyssService.EnterResult r = service.enterDungeon(
                        party.onlineMembers(), dungeonId);
                    if (!r.ok() && r.reason() != null) {
                        player.sendMessage(r.reason());
                    }
                }
                return true;
            }
            case "party_set_dungeon" -> {
                if (checkToken(player, data, 2)) {
                    Party party = service.party().partyOf(player);
                    if (party != null && party.isLeader(player.getUniqueId())) {
                        String id = data.get(1);
                        AbyssTemplate t = service.instances().getTemplate(id);
                        party.dungeonId(t != null ? t.id() : null);
                        party.appendFeed(null, "目标副本已设置为 "
                            + (t != null ? t.name() : id));
                        pushPartyUpdate(party);
                    }
                }
                return true;
            }
            case "party_set_listed" -> {
                if (checkToken(player, data, 2)) {
                    Party party = service.party().partyOf(player);
                    if (party != null && party.isLeader(player.getUniqueId())) {
                        party.listed("1".equals(data.get(1)) || "true".equals(data.get(1)));
                        pushPartyUpdate(party);
                    }
                }
                return true;
            }
            case "party_set_joinmode" -> {
                if (checkToken(player, data, 2)) {
                    Party party = service.party().partyOf(player);
                    if (party != null && party.isLeader(player.getUniqueId())) {
                        party.joinMode("OPEN".equalsIgnoreCase(data.get(1))
                            ? Party.JoinMode.OPEN : Party.JoinMode.APPLY);
                        pushPartyUpdate(party);
                    }
                }
                return true;
            }
            case "party_apply_ok" -> {
                if (checkToken(player, data, 2)) {
                    UUID applicant = parseUuid(data.size() > 1 ? data.get(1) : null);
                    if (applicant != null) {
                        Party party = service.party().partyOf(player);
                        Player ap = org.bukkit.Bukkit.getPlayer(applicant);
                        var r = service.party().approveApplication(player, applicant);
                        if (r instanceof PartyManager.JoinResult.Ok ok && party != null) {
                            ok.party().appendFeed(null,
                                (ap != null ? ap.getName() : "?") + " 加入了队伍");
                            pushPartyUpdate(ok.party());
                        }
                    }
                }
                return true;
            }
            case "party_apply_no" -> {
                if (checkToken(player, data, 2)) {
                    UUID applicant = parseUuid(data.size() > 1 ? data.get(1) : null);
                    if (applicant != null) {
                        service.party().denyApplication(player, applicant);
                        Party party = service.party().partyOf(player);
                        if (party != null) pushPartyUpdate(party);
                    }
                }
                return true;
            }
            case "lobby_refresh" -> {
                if (checkToken(player, data, 1)) {
                    pushLobbyList(player, "", "");
                }
                return true;
            }
            case "lobby_search" -> {
                // lobby_search <keyword> <dungeonFilter> <token>
                if (checkToken(player, data, 3)) {
                    pushLobbyList(player, data.get(1), data.get(2));
                }
                return true;
            }
            case "lobby_join" -> {
                // lobby_join <partyId> <token>
                if (checkToken(player, data, 2)) {
                    UUID partyId = parseUuid(data.get(1));
                    if (partyId != null) {
                        var r = service.party().joinViaLobby(player, partyId);
                        handleJoinLobbyResult(player, r);
                    }
                }
                return true;
            }
            case "lobby_create" -> {
                if (checkToken(player, data, 1)) {
                    if (service.party().partyOf(player) != null) {
                        openParty(player);
                    } else {
                        service.party().create(player);
                        openParty(player);
                    }
                }
                return true;
            }
            case "lobby_invite_accept" -> {
                if (checkToken(player, data, 2)) {
                    UUID partyId = parseUuid(data.get(1));
                    if (partyId != null) {
                        var r = service.party().accept(player, partyId);
                        if (r instanceof PartyManager.JoinResult.Ok ok) {
                            ok.party().appendFeed(null, player.getName() + " 加入了队伍");
                            pushPartyUpdate(ok.party());
                            openParty(player);
                        } else if (r instanceof PartyManager.JoinResult.Full) {
                            player.sendMessage(service.msg("error.party-full"));
                        } else {
                            player.sendMessage(service.msg("error.invite-expired"));
                            pushLobbyList(player, "", "");
                        }
                    }
                }
                return true;
            }
            case "lobby_invite_decline" -> {
                if (checkToken(player, data, 2)) {
                    UUID partyId = parseUuid(data.get(1));
                    if (partyId != null
                        && service.party().declineInvite(player, partyId)) {
                        pushLobbyList(player, "", "");
                    }
                }
                return true;
            }
            case "lobby_toggle_invites" -> {
                // lobby_toggle_invites <0|1> <token>
                if (checkToken(player, data, 2)) {
                    boolean allow = "1".equals(data.get(1)) || "true".equals(data.get(1));
                    service.party().setInviteAllowed(player.getUniqueId(), allow);
                    player.sendMessage(service.msg(allow
                        ? "party.invite-allowed-on" : "party.invite-allowed-off"));
                }
                return true;
            }
            case "open_lobby" -> {
                if (checkToken(player, data, 1)) openLobby(player);
                return true;
            }
            case "party_kick" -> {
                if (checkToken(player, data, 2)) {
                    Party party = service.party().partyOf(player);
                    UUID targetUuid = parseUuid(data.size() > 1 ? data.get(1) : null);
                    if (targetUuid != null) {
                        service.party().kick(player, targetUuid);
                    } else {
                        Player target = data.size() > 1
                            ? org.bukkit.Bukkit.getPlayerExact(data.get(1)) : null;
                        if (target != null) service.party().kick(player, target);
                    }
                    if (party != null) pushPartyUpdate(party);
                }
                return true;
            }
            case "party_transfer" -> {
                if (checkToken(player, data, 2)) {
                    Party party = service.party().partyOf(player);
                    UUID targetUuid = parseUuid(data.size() > 1 ? data.get(1) : null);
                    if (targetUuid != null) {
                        service.party().transferLeader(player, targetUuid);
                    } else {
                        Player target = data.size() > 1
                            ? org.bukkit.Bukkit.getPlayerExact(data.get(1)) : null;
                        if (target != null) service.party().transferLeader(player, target);
                    }
                    if (party != null) pushPartyUpdate(party);
                }
                return true;
            }
            case "party_chat" -> {
                if (checkToken(player, data, 2)) {
                    service.party().sendChat(player, data.size() > 1 ? data.get(1) : "");
                    Party party = service.party().partyOf(player);
                    if (party != null) pushPartyUpdate(party);
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
    /** 邀请结果 → 玩家提示。 */
    private void handleInviteResult(Player inviter, PartyManager.InviteResult r, Player target) {
        if (r instanceof PartyManager.InviteResult.Ok ok) {
            inviter.sendMessage(service.msg("party.invited",
                "player", target.getName(),
                "timeout", String.valueOf(service.party().inviteTimeout())));
            target.sendMessage(service.msg("party.invite-received",
                "leader", inviter.getName()));
            pushPartyUpdate(ok.party());
        } else if (r instanceof PartyManager.InviteResult.Cooldown cd) {
            inviter.sendMessage(service.msg("party.invite-cooldown",
                "seconds", String.valueOf(cd.remainingSeconds())));
        } else if (r instanceof PartyManager.InviteResult.OptOut) {
            inviter.sendMessage(service.msg("party.invite-opted-out",
                "player", target.getName()));
        } else if (r instanceof PartyManager.InviteResult.SelfInvite) {
            inviter.sendMessage(service.msg("error.self-invite"));
        } else if (r instanceof PartyManager.InviteResult.TargetInParty) {
            inviter.sendMessage(service.msg("error.already-in-party"));
        }
    }

    /** 大厅加入结果 → 玩家提示/界面流转。 */
    private void handleJoinLobbyResult(Player player, PartyManager.JoinLobbyResult r) {
        if (r instanceof PartyManager.JoinLobbyResult.Ok ok) {
            ok.party().appendFeed(null, player.getName() + " 加入了队伍");
            pushPartyUpdate(ok.party());
            openParty(player);
        } else if (r instanceof PartyManager.JoinLobbyResult.Applied) {
            player.sendMessage(service.msg("party.apply-sent"));
            pushLobbyList(player, "", "");
        } else if (r instanceof PartyManager.JoinLobbyResult.Full) {
            player.sendMessage(service.msg("error.party-full"));
        } else if (r instanceof PartyManager.JoinLobbyResult.AlreadyMember) {
            openParty(player);
        } else if (r instanceof PartyManager.JoinLobbyResult.Denied d) {
            player.sendMessage(service.msg("error.join-denied", "reason", d.reason()));
        } else {
            player.sendMessage(service.msg("error.party-not-found"));
            pushLobbyList(player, "", "");
        }
    }

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

    /** 玩家完全离开服务时清理会话态（token/HUD 标记/待领宝箱已先发放）。 */
    public void onPlayerGone(Player player) {
        UUID uid = player.getUniqueId();
        tokens.remove(uid);
        hudOpen.remove(uid);
        flushPendingChest(player);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static @Nullable UUID parseUuid(@Nullable String s) {
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
