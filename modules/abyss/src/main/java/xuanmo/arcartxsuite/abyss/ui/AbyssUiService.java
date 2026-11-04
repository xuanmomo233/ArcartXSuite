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
import org.bukkit.ChatColor;
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
import xuanmo.arcartxsuite.api.message.MessageProvider;

/**
 * Abyss ArcartX UI 服务：5 个界面（浏览/队伍/宝箱/HUD/排行统计）+ 客户端包路由。
 * <p>
 * 通道约定：客户端 {@code Packet.send('abyss', '<action>', ...)} →
 * {@link AbyssService#handleClientPacket} → 本类 {@link #handlePacket}。
 * 服务端→客户端：{@code views.send(player, "AXS:<ui>", "<handler>", payload)}。
 * 所有回包带 action_token 校验（防伪造）。
 */
public final class AbyssUiService implements AbyssService.UiServiceFacade,
    AbyssService.ChestUiHandler,
    xuanmo.arcartxsuite.abyss.design.DesignManager.Ui {

    public static final String CHANNEL = "abyss";

    private final AbyssService service;
    private final @Nullable PacketBridgeAPI packetBridge;
    private final @Nullable ItemBridgeAPI itemBridge;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();
    private final AbyssUiLifecycle views;

    /** 玩家 → 操作令牌（每次开界面轮换）。 */
    private final Map<UUID, String> tokens = new ConcurrentHashMap<>();
    private final Map<UUID,String> foregroundUis=new ConcurrentHashMap<>();
    private final Map<UUID,Map<String,Object>> partyStates=new ConcurrentHashMap<>();
    private record StatsSnapshot(UUID instance,Map<String,Object> payload,long expires) { }
    private final Map<UUID,StatsSnapshot> recentStats=new ConcurrentHashMap<>();
    private final Map<UUID,UUID> statsViews=new ConcurrentHashMap<>();
    private final Map<UUID,UUID> boardRequests=new ConcurrentHashMap<>();
    private final Map<UUID,java.util.concurrent.CompletableFuture<?>> boardQueries=new ConcurrentHashMap<>();
    /** 玩家 → 待领取宝箱（roll 结果 + 所属实例/箱名；ESC 重开返回同批防刷奖）。 */
    private record PendingChest(AbyssInstance instance, String chestName,
                                String title, List<ItemStack> items, Map<ItemStack, String> itemKeys) {
        PendingChest(AbyssInstance instance, String chestName, String title, List<ItemStack> items) {
            this(instance, chestName, title, items, new java.util.IdentityHashMap<>());
            for (ItemStack item : items) itemKeys.put(item, UUID.randomUUID().toString());
        }
        String key(ItemStack item) { return "chest:" + instance.getInstanceId() + ":" + chestName + ":" + itemKeys.get(item); }
    }
    private final Map<UUID, PendingChest> pendingChests = new ConcurrentHashMap<>();
    private record UiOffer(UUID instance, String target) { }
    private final Map<UUID, UiOffer> shopOffers = new ConcurrentHashMap<>();
    private final Map<UUID, UiOffer> inputOffers = new ConcurrentHashMap<>();
    private final Map<UUID, UiOffer> buffOffers = new ConcurrentHashMap<>();
    /** 空槽位 JSON（9 槽常显，未 roll 到的槽位渲染空）。 */
    private static final String AIR_ITEM_JSON = "{\"id\":\"minecraft:air\",\"Count\":1b}";

    public AbyssUiService(AbyssService service, @Nullable PacketBridgeAPI packetBridge,
                          @Nullable ItemBridgeAPI itemBridge, Logger logger) {
        this.service = service;
        this.packetBridge = packetBridge;
        this.itemBridge = itemBridge;
        this.logger = logger;
        this.views = new AbyssUiLifecycle(packetBridge, service::scheduleDelayedRaw);
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
    private String inputUi() { return axsUiId(service.config().ui().inputId()); }
    private String buffUi() { return axsUiId(service.config().ui().buffId()); }
    private String designUi() { return axsUiId(service.config().ui().designId()); }
    private String designPickUi() { return axsUiId(service.config().ui().designPickId()); }
    private String designEditUi() { return axsUiId(service.config().ui().designEditId()); }
    private String shopUi() { return axsUiId(service.config().ui().shopId()); }

    private String token(Player p) {
        return tokens.computeIfAbsent(p.getUniqueId(), id -> Long.toHexString(random.nextLong()));
    }

    private boolean checkToken(Player p, List<String> data, int index) {
        String expected = tokens.get(p.getUniqueId());
        String ui=foregroundUis.get(p.getUniqueId());
        if(expected==null || data.size()<=index || !expected.equals(data.get(index)) || ui==null || !views.isOpen(p,ui))return false;
        String action=data.get(0);
        if(action.startsWith("shop_")||action.startsWith("input_")||action.startsWith("buff_")) {
            AbyssInstance instance=service.instances().getInstance(p);
            if(instance==null || !instance.admitted() || instance.isFinished() || instance.isPlayerCurrentlyDead(p))return false;
        }
        if(action.startsWith("design_"))return Set.of(designUi(),designPickUi(),designEditUi()).contains(ui);
        if(action.startsWith("party_"))return ui.equals(partyUi()) || ui.equals(lobbyUi());
        if(action.startsWith("lobby_"))return ui.equals(lobbyUi());
        if(action.startsWith("shop_"))return ui.equals(shopUi());
        if(action.startsWith("input_"))return ui.equals(inputUi());
        if(action.startsWith("buff_"))return ui.equals(buffUi());
        if(action.startsWith("chest_"))return ui.equals(chestUi());
        if(action.startsWith("settlement_"))return ui.equals(statsUi());
        if(action.equals("reopen_chest"))return ui.equals(statsUi());
        return true;
    }

    private void openView(Player player, String uiId, Map<String, Object> payload) {
        boardRequests.remove(player.getUniqueId());
        var previousQuery=boardQueries.remove(player.getUniqueId());if(previousQuery!=null)previousQuery.cancel(false);
        if(uiId.equals(statsUi())) {
            Object id=payload.get("instance_id");
            if(id!=null)statsViews.put(player.getUniqueId(),UUID.fromString(String.valueOf(id)));else statsViews.remove(player.getUniqueId());
        }
        if(uiId.equals(partyUi())) {
            Map<String,Object> state=new LinkedHashMap<>(payload);state.remove("action_token");partyStates.put(player.getUniqueId(),state);
        }
        String fresh=UUID.randomUUID().toString();
        tokens.put(player.getUniqueId(),fresh);foregroundUis.put(player.getUniqueId(),uiId);
        payload.put("action_token",fresh);
        if (!views.open(player, uiId, "init", payload, false))
            player.sendMessage("§c界面打开失败，请检查 ArcartX 客户端和UI注册状态。");
    }

    private String uiForKey(String key) {
        return switch (key) {
            case "browser" -> browserUi(); case "confirm" -> confirmUi();
            case "party" -> partyUi(); case "lobby" -> lobbyUi(); case "chest" -> chestUi();
            case "stats" -> statsUi(); case "leaderboard" -> leaderboardUi(); case "input" -> inputUi(); case "hud" -> hudUi();
            case "buff" -> buffUi(); case "shop" -> shopUi(); case "design" -> designUi();
            case "design_pick" -> designPickUi(); case "design_edit" -> designEditUi();
            default -> null;
        };
    }

    public void closeInstanceViews(Player player) {
        shopOffers.remove(player.getUniqueId()); inputOffers.remove(player.getUniqueId()); buffOffers.remove(player.getUniqueId());
        for (String key : List.of("chest", "input", "buff", "shop", "design", "design_pick", "design_edit"))
            views.close(player, uiForKey(key));
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
            // 图标 itemJson（Slot.setItemIcon 渲染；桥缺席时 itemJsonOf 兜底基础图标）
            org.bukkit.Material iconMat = org.bukkit.Material.matchMaterial(t.icon());
            row.put("iconJson", iconMat != null
                ? itemJsonOf(new org.bukkit.inventory.ItemStack(iconMat)) : "");
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
                .filter(inst -> !inst.isFinished() && !inst.designMode()).count());
            rows.put(Integer.toString(i++), row);
        }
        payload.put("dungeonRows", rows);
        payload.put("dungeonCount", i);
        payload.put("hasParty", party != null);
        payload.put("partyText", party != null
            ? party.size() + " 人小队" : "未组队");

        if (views.isOpen(player, browserUi())) views.send(player, browserUi(), "init", payload);
        else openView(player, browserUi(), payload);
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
        openView(player, confirmUi(), payload);
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
        openView(player, partyUi(), payload);
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
        openView(player, lobbyUi(), payload);
    }

    /**
     * 机关输入面板：puzzles.yml input 型。
     * 答案永不下发；init 只带元数据（标题/提示/长度/正则/掩码/剩余次数）。
     * 客户端提交 → {@code input_submit <puzzleId> <answer> <token>} 回包。
     */
    @Override
    public void openPuzzleInput(Player player, AbyssInstance instance,
                                xuanmo.arcartxsuite.abyss.template.PuzzleConfig.Puzzle puzzle,
                                int attemptsLeft) {
        if (!available()) return;
        inputOffers.put(player.getUniqueId(), new UiOffer(instance.getInstanceId(), puzzle.id()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("puzzleId", puzzle.id());
        payload.put("title", puzzle.title() != null ? puzzle.title() : puzzle.name());
        payload.put("hint", puzzle.hint() != null ? puzzle.hint() : "");
        payload.put("length", puzzle.length() > 0 ? puzzle.length() : 4096);
        payload.put("pattern", puzzle.inputPattern() != null ? puzzle.inputPattern() : "");
        payload.put("masked", puzzle.masked() ? "1" : "0");
        payload.put("attemptsText", attemptsLeft < 0 ? ""
            : service.msg("puzzle.attempts-left", "left", String.valueOf(attemptsLeft)));
        openView(player, inputUi(), payload);
    }

    /**
     * 增益选择面板：buffs.yml panel 池。
     * init 下发全部候选卡片（可滚动）；多选时重开剩余候选，客户端选择 →
     * {@code buff_pick <poolId> <optionId> <token>} 回包。
     */
    @Override
    public void openBuffOffer(Player player, AbyssInstance instance,
                              xuanmo.arcartxsuite.abyss.template.BuffConfig.Pool pool,
                              List<xuanmo.arcartxsuite.abyss.template.BuffConfig.Option> candidates,
                              int timeoutSeconds) {
        if (!available() || candidates.isEmpty()) return;
        buffOffers.put(player.getUniqueId(), new UiOffer(instance.getInstanceId(), pool.id()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("poolId", pool.id());
        payload.put("poolName", pool.name());
        payload.put("timeout", Math.max(0, timeoutSeconds));
        payload.put("picksLeft", Math.min(Math.max(1, pool.pick()), candidates.size()));
        payload.put("timeoutText", "none".equals(pool.timeoutAuto()) ? "到时放弃未选增益" : "到时自动补选");
        Map<String, Object> rows = new LinkedHashMap<>();
        int i = 0;
        for (var opt : candidates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", opt.id());
            row.put("name", opt.name());
            row.put("icon", opt.icon());
            // 物品图标 JSON（材质名 → itemJson；桥缺席时 itemJsonOf 兜底基础图标）
            String iconJson = "";
            if (!opt.icon().isBlank()) {
                try {
                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(opt.icon());
                    if (mat != null) {
                        iconJson = itemJsonOf(new org.bukkit.inventory.ItemStack(mat));
                    }
                } catch (Throwable ignored) { }
            }
            row.put("iconJson", iconJson);
            row.put("rarity", opt.rarity());
            // 稀有度描边色服务端算好（UI 侧规避嵌套三元表达式坑）
            row.put("rarityColor", switch (opt.rarity()) {
                case "rare" -> "80,150,235,255";
                case "epic" -> "170,100,235,255";
                case "legendary" -> "235,180,60,255";
                default -> "140,145,155,255";
            });
            row.put("desc0", opt.desc().size() > 0 ? opt.desc().get(0) : "");
            row.put("desc1", opt.desc().size() > 1 ? opt.desc().get(1) : "");
            row.put("desc2", opt.desc().size() > 2 ? opt.desc().get(2) : "");
            rows.put(Integer.toString(i++), row);
        }
        payload.put("count", i);
        payload.put("cards", rows);
        openView(player, buffUi(), payload);
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
        payload.put("allowInvite", service.party().inviteAllowed(player.getUniqueId()));
        putLobbyData(payload, player, keyword, dungeonFilter);
        views.send(player, lobbyUi(), "lobby_list", payload);
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
            d.put("iconJson", iconMat != null
                ? itemJsonOf(new org.bukkit.inventory.ItemStack(iconMat)) : "");
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
            String sender = e.sender() != null ? e.sender() : "";
            List<String> displayLines = AbyssTextLayout.wrap(sender.isEmpty()
                ? "&7/ " + e.text() : "&6" + sender + " &7: &f" + e.text(), 40);
            for (String line : displayLines) {
                feedRows.put(Integer.toString(fi++), Map.of(
                    "sender", sender, "text", e.text(), "display", line));
            }
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
            Map<String,Object> previous=partyStates.put(member.getUniqueId(),new LinkedHashMap<>(payload));
            if(!payload.equals(previous) && partyUi().equals(foregroundUis.get(member.getUniqueId())) && views.isOpen(member,partyUi()))
                tokens.put(member.getUniqueId(),UUID.randomUUID().toString());
            payload.put("action_token",token(member));
            views.send(member, partyUi(), "update", payload);
        }
    }

    public void refreshPartyState(Party party) {
        for(UUID uuid:party.observers()) {
            Player player=org.bukkit.Bukkit.getPlayer(uuid);
            if(player==null || !player.isOnline())continue;
            boolean member=service.party().partyOf(player)==party;
            if(views.isOpen(player,partyUi()) && !member)openLobby(player);
            else if(views.isOpen(player,lobbyUi())) {
                if(member)openParty(player); else pushLobbyList(player,"","");
            }
        }
        pushPartyUpdate(party);
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
        views.send(player, partyUi(), "online_list", payload);
    }

    @Override
    public void openStats(Player player, AbyssInstance instance) {
        if (!available()) return;
        if(instance!=null && instance.isFinished()) {
            StatsSnapshot cached=recentStats.get(player.getUniqueId());
            if(cached!=null && cached.instance().equals(instance.getInstanceId()) && cached.expires()>System.currentTimeMillis())presentStats(player,cached);
            else captureSettlementStats(player,instance).run();
            return;
        }
        openView(player,statsUi(),statsPayload(player,instance));
    }

    private Map<String,Object> statsPayload(Player player,AbyssInstance instance) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("dungeonName", instance != null ? instance.getTemplate().name() : "");
        payload.put("mode", "stats");
        if(instance!=null)payload.put("instance_id",instance.getInstanceId().toString());

        Map<String, Object> rows = new LinkedHashMap<>();
        int i = 0;
        if (instance != null) {
            Set<UUID> players = new java.util.LinkedHashSet<>(instance.getPlayerUUIDs());
            players.addAll(instance.getKillCounts().keySet());
            for (UUID playerId : players) {
                Player p = org.bukkit.Bukkit.getPlayer(playerId);
                Map<String, Double> sum = service.stats().summary(instance, playerId);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("name", p != null ? p.getName() : "?");
                r.put("kills", instance.getKillCounts().getOrDefault(playerId, 0));
                r.put("damage", sum.get("damage"));
                r.put("taken", sum.get("damage_taken"));
                r.put("heal", sum.get("healing"));
                r.put("killsText", compact(instance.getKillCounts().getOrDefault(playerId, 0)));
                r.put("damageText", compact(sum.getOrDefault("damage", 0.0)));
                r.put("takenText", compact(sum.getOrDefault("damage_taken", 0.0)));
                r.put("healText", compact(sum.getOrDefault("healing", 0.0)));
                rows.put(Integer.toString(i++), r);
            }
        }
        payload.put("statRows", rows);
        payload.put("statCount", rows.size());
        PendingChest pending = pendingChests.get(player.getUniqueId());
        payload.put("hasChest", pending != null && pending.instance() == instance);
        payload.put("settling",instance!=null && instance.isFinished());
        payload.put("canLeave",false);
        payload.put("settlementHint","");
        payload.put("outcomeLabel",instance!=null && instance.getStatus()==xuanmo.arcartxsuite.abyss.instance.AbyssStatus.COMPLETED?"通关成功":"挑战结束");
        String grade=instance!=null?String.valueOf(instance.getVariables().getOrDefault("procgen_grade","—")):"—";
        payload.put("summaryText","本次用时 "+AbyssService.formatTime(instance!=null?instance.getRunningTime():0)
            +" · 评级 "+grade+" · 奖励通过宿主自动到账");
        return payload;
    }

    public Runnable captureSettlementStats(Player player,AbyssInstance instance) {
        if(views.isOpen(player,statsUi()))views.close(player,statsUi());
        StatsSnapshot snapshot=new StatsSnapshot(instance.getInstanceId(),Map.copyOf(statsPayload(player,instance)),System.currentTimeMillis()+600_000L);
        recentStats.put(player.getUniqueId(),snapshot);
        return ()->{
            StatsSnapshot latest=recentStats.get(player.getUniqueId());
            if(latest!=null && latest.instance().equals(snapshot.instance()))presentStats(player,latest);
        };
    }

    private boolean presentStats(Player player,StatsSnapshot snapshot) {
        if(!available() || !player.isOnline())return false;
        AbyssInstance current=service.instances().getInstance(player);
        if(current!=null && !current.getInstanceId().equals(snapshot.instance()))return false;
        recentStats.computeIfPresent(player.getUniqueId(),(id,last)->last.instance().equals(snapshot.instance())
            ?new StatsSnapshot(last.instance(),last.payload(),System.currentTimeMillis()+600_000L):last);
        Map<String,Object> payload=new LinkedHashMap<>(snapshot.payload());
        boolean owned=current!=null && current.isFinished();
        payload.put("canLeave",owned);
        payload.put("settlementHint",owned?service.settlementHint(current):"已退出副本。奖励由宿主自动到账，可使用 /abyss stats 重看本次结算。");
        PendingChest pending=pendingChests.get(player.getUniqueId());
        payload.put("hasChest",pending!=null && pending.instance().getInstanceId().equals(snapshot.instance()));
        openView(player,statsUi(),payload);return true;
    }

    public boolean openRecentStats(Player player) {
        StatsSnapshot snapshot=recentStats.get(player.getUniqueId());
        if(snapshot==null)return false;
        if(snapshot.expires()<System.currentTimeMillis()){recentStats.remove(player.getUniqueId(),snapshot);return false;}
        return presentStats(player,snapshot);
    }

    public void pushSettlementUpdate(Player player,AbyssInstance instance) {
        StatsSnapshot snapshot=recentStats.get(player.getUniqueId());
        if(snapshot==null || !snapshot.instance().equals(instance.getInstanceId()) || !instance.getInstanceId().equals(statsViews.get(player.getUniqueId()))
            || !views.isOpen(player,statsUi()))return;
        PendingChest pending=pendingChests.get(player.getUniqueId());
        views.send(player,statsUi(),"settlement_update",Map.of("settlementHint",service.settlementHint(instance),"canLeave",true,
            "hasChest",pending!=null && pending.instance()==instance));
    }

    @Override
    public void openLeaderboard(Player player,String dungeonId) {
        if(!available() || !service.running())return;
        boolean enabled=service.leaderboard().enabled();
        boolean exists=service.instances().getTemplate(dungeonId)!=null;
        openView(player,leaderboardUi(),leaderboardPayload(player,dungeonId,List.of(),!exists?"副本不存在":enabled?"正在读取排行榜…":"排行榜未启用"));
        if(!enabled || !exists)return;
        UUID request=UUID.randomUUID();boardRequests.put(player.getUniqueId(),request);
        java.util.concurrent.CompletableFuture<List<xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository.LeaderboardEntry>> query;
        try{query=service.leaderboard().topAsync(dungeonId);}
        catch(RuntimeException failure){boardRequests.remove(player.getUniqueId(),request);views.send(player,leaderboardUi(),"init",leaderboardPayload(player,dungeonId,List.of(),"排行榜暂不可用，请稍后重试"));return;}
        boardQueries.put(player.getUniqueId(),query);
        query.whenComplete((rows,error)->service.runOnMain(()->{
            boardQueries.remove(player.getUniqueId(),query);
            if(!service.running() || !player.isOnline() || !request.equals(boardRequests.get(player.getUniqueId()))
                || !views.isOpen(player,leaderboardUi()))return;
            boardRequests.remove(player.getUniqueId(),request);
            if(error!=null)logger.warning("[abyss] 排行榜异步查询失败: "+error.getMessage());
            var payload=leaderboardPayload(player,dungeonId,error==null?rows:List.of(),error==null?"暂无通关排行记录":"排行榜暂不可用，请稍后重试");
            views.send(player,leaderboardUi(),"init",payload);
        }));
    }

    public void stopQueries() {
        boardRequests.clear();boardQueries.values().forEach(f->f.cancel(false));boardQueries.clear();
    }

    private Map<String,Object> leaderboardPayload(Player player,String dungeonId,
            List<xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository.LeaderboardEntry> top,String emptyMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("mode", "leaderboard");

        AbyssTemplate t = service.instances().getTemplate(dungeonId);
        payload.put("dungeonName", t != null ? t.name() : dungeonId);

        Map<String, Object> rows = new LinkedHashMap<>();
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
            r.put("completionsText", compact(e.completions()));
            rows.put(Integer.toString(i++), r);
        }
        payload.put("boardRows", rows);
        payload.put("boardCount", rows.size());
        payload.put("emptyMessage",emptyMessage);
        return payload;
    }

    // ═══ ChestUiHandler ═══

    @Override
    public void open(Player player, RewardConfig.ChestReward chest, AbyssInstance instance) {
        openChest(player,chest,instance,false);
    }
    private void openChest(Player player,RewardConfig.ChestReward chest,AbyssInstance instance,boolean requested) {
        if (!available()) {
            // UI 不可用时回退直接发放
            List<ItemStack> items = service.rewards().rollChestOptions(chest,
                Math.min(9, chest.rows() * 9));
            if (!items.isEmpty() && service.dispatchItemsConfirmed(player, items,
                    "chest:" + instance.getInstanceId() + ":" + player.getUniqueId() + ":" + chest.name())) {
                instance.markTriggeredOnce(
                    AbyssService.chestClaimKey(chest.name(), player.getUniqueId()));
            }
            return;
        }
        UUID uid = player.getUniqueId();
        PendingChest pending = pendingChests.get(uid);
        List<ItemStack> items;
        if (pending != null && pending.instance() == instance
                && pending.chestName().equals(chest.name())) {
            // 重开同一箱：返回原 roll（ESC 未领不重置，防反复开箱刷奖励）
            items = pending.items();
        } else {
            if (pending != null) {
                // 换箱：先把上个未领箱直接发放并标记，不吞奖励
                flushPendingChest(player);
                if (pendingChests.containsKey(uid)) {
                    player.sendMessage("§c上一份宝箱奖励尚未确认保存，请稍后重试。");
                    return;
                }
            }
            items = new ArrayList<>(service.rewards().rollChestOptions(chest,
                Math.min(9, chest.rows() * 9))); // UI 固定 9 槽
            pendingChests.put(uid, new PendingChest(instance, chest.name(),
                chest.title(), items));
        }
        if (items.isEmpty()) {
            // Generation failure must not consume the player's claim.
            pendingChests.remove(uid);
            player.sendMessage("§c宝箱未生成有效奖励，请联系管理员检查物品源。");
            return;
        }
        // Settlement presents one summary first; its reward button opens this retained roll.
        if(instance.isFinished() && service.isSettling(instance) && !requested)return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("chestTitle", chest.title());
        payload.put("itemCount", items.size());
        for (int i = 0; i < 9; i++) {
            payload.put("itemJson" + i,
                i < items.size() ? itemJsonOf(items.get(i)) : AIR_ITEM_JSON);
            payload.put("itemAmount" + i, i < items.size() ? items.get(i).getAmount() : 0);
        }
        openView(player, chestUi(), payload);
    }

    // ═══ 副本商店 ═══

    /**
     * 打开商店 UI（shops.yml 定义）。商品行含 iconJson/name/price/desc；
     * 货币读实例变量 currencyVar（缺省 coins）下发供 UI 余额展示/可购置灰。
     */
    public void openShop(Player player,
                         xuanmo.arcartxsuite.abyss.template.ShopConfig.Shop shop,
                         AbyssInstance instance) {
        if (!available()) return;
        shopOffers.put(player.getUniqueId(), new UiOffer(instance.getInstanceId(), shop.id()));
        int coins = varInt(instance, shop.currencyVar());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("shopId", shop.id());
        payload.put("shopTitle", shop.title());
        payload.put("coins", coins);
        payload.put("coinsText", compact(coins));
        Map<String, Object> rows = new LinkedHashMap<>();
        int i = 0;
        for (var g : shop.goods()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", g.id());
            row.put("name", g.name());
            row.put("price", g.price());
            row.put("priceText", Integer.toString(g.price()));
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(
                g.icon().contains(":") ? g.icon().substring(g.icon().indexOf(':') + 1) : g.icon());
            row.put("iconJson", mat != null
                ? itemJsonOf(new org.bukkit.inventory.ItemStack(mat)) : "");
            List<String> d = g.desc();
            row.put("desc0", d.size() > 0 ? d.get(0) : "");
            row.put("desc1", d.size() > 1 ? d.get(1) : "");
            rows.put(Integer.toString(i++), row);
        }
        payload.put("goodsCount", i);
        payload.put("goods", rows);
        openView(player, shopUi(), payload);
    }

    /**
     * shop_buy 回包：shopId + 商品索引 + token。
     * 校验副本内身份 → 价格 → 扣货币变量 → 跑商品脚本 → 推 update 刷新余额。
     */
    private void buyShop(Player player, String shopId, int index) {
        AbyssInstance inst = service.instances().getInstance(player);
        if (inst == null || inst.isFinished()) return;
        if (!new UiOffer(inst.getInstanceId(), shopId).equals(shopOffers.get(player.getUniqueId()))
            || !views.isOpen(player, shopUi())) return;
        var shop = inst.getTemplate().shops().shop(shopId);
        if (shop == null || index < 0 || index >= shop.goods().size()) return;
        var g = shop.goods().get(index);
        if (g.price() < 0) return;
        int coins = varInt(inst, shop.currencyVar());
        if (coins < g.price()) {
            player.sendMessage(color("&c金币不足（需要 &e" + g.price() + "&c，当前 &e" + coins + "&c）"));
            return;
        }
        inst.getVariables().put(shop.currencyVar(), coins - g.price());
        // 商品脚本（@trigger=购买者）：发放/治疗/buff 全走脚本行
        var execution=service.runScript(inst, g.script(),
            xuanmo.arcartxsuite.abyss.script.ScriptContext.of(inst, player));
        if(execution==null) {
            inst.getVariables().put(shop.currencyVar(),coins);pushShopBalance(player,inst,shop);return;
        }
        execution.completion().whenComplete((ok,error)->service.runOnMain(()->{
            if(!service.running())return;
            boolean success=error==null && Boolean.TRUE.equals(ok);
            if(!success && execution.successfulActions()==0)
                inst.getVariables().put(shop.currencyVar(),(int)Math.min(Integer.MAX_VALUE,(long)varInt(inst,shop.currencyVar())+g.price()));
            if(player.isOnline())player.sendMessage(color(success?"&a购买已受理 &f"+g.name()
                :execution.successfulActions()==0?"&c购买未完成，队伍金币已退回。":"&e商品部分操作已执行，请联系管理员核对。"));
            if(!success)logger.warning("[abyss] 商品脚本未完成: instance="+inst.getInstanceId()+" shop="+shopId+" goods="+g.id()+" actions="+execution.successfulActions());
            pushShopBalance(player,inst,shop);
        }));
    }

    private void pushShopBalance(Player player,AbyssInstance inst,xuanmo.arcartxsuite.abyss.template.ShopConfig.Shop shop) {
        if(!new UiOffer(inst.getInstanceId(),shop.id()).equals(shopOffers.get(player.getUniqueId())) || !views.isOpen(player,shopUi()))return;
        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("coins", varInt(inst, shop.currencyVar()));
        upd.put("coinsText", compact(varInt(inst, shop.currencyVar())));
        views.send(player, shopUi(), "update", upd);
    }

    private static int varInt(AbyssInstance inst, String var) {
        Object o = inst.getVariables().get(var);
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private static String color(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String compact(double value) {
        if (!Double.isFinite(value)) return "0";
        if (Math.abs(value) >= 100_000_000) return String.format(java.util.Locale.ROOT, "%.1f亿", value / 100_000_000);
        if (Math.abs(value) >= 10_000) return String.format(java.util.Locale.ROOT, "%.1f万", value / 10_000);
        return Long.toString(Math.round(value));
    }

    /**
     * ItemStack → itemJson（Slot.setItemIcon 渲染格式）。
     * itemStackBridge 缺席/序列化失败时兜底拼基础 NBT——只丢自定义 meta，
     * 材质图标一定渲染，避免槽位整体隐藏成空面板。
     */
    private String itemJsonOf(ItemStack it) {
        if (it == null) return "";
        if (itemBridge != null) {
            try {
                String j = itemBridge.itemToPreviewJson(it).orElse("");
                if (!j.isEmpty()) return j;
            } catch (Throwable ignored) { }
        }
        return "{\"id\":\"minecraft:" + it.getType().name().toLowerCase(java.util.Locale.ROOT)
            + "\",\"Count\":1b}";
    }

    // ═══ 设计模式 ═══

    /** 主面板：流程节点链 + 当前节点对象列表 + 未保存角标。 */
    public void openDesignMain(Player player,
                               xuanmo.arcartxsuite.abyss.design.DesignSession s) {
        if (!available()) return;
        Map<String, Object> payload = designMainPayload(player, s, true);
        openView(player, designUi(), payload);
    }

    /** 主面板数据刷新（节点切换/对象变动后推 update）。 */
    public void pushDesignMain(Player player,
                               xuanmo.arcartxsuite.abyss.design.DesignSession s) {
        if (!available()) return;
        views.send(player, designUi(), "update",
            designMainPayload(player, s, false));
    }

    private Map<String, Object> designMainPayload(Player player,
            xuanmo.arcartxsuite.abyss.design.DesignSession s, boolean includeToken) {
        var dm = service.designManager();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        if (includeToken) payload.put("action_token", token(player));
        payload.put("dungeonName", s.instance().getTemplate().name());
        payload.put("dungeonId", s.instance().getTemplate().id());
        payload.put("dirty", s.dirtyCount());
        payload.put("worldDirty", s.worldDirty());
        payload.put("saving", s.saving());
        payload.put("worldSaveLabel", s.instance().getTemplate().procgen() != null ? "保存当前构件" : "配置+建筑");
        payload.put("worldSaveHint", s.instance().getTemplate().procgen() != null
            ? "站在要保存的房间内；保存会更新同名共享构件" : "建筑保存为独立世界快照，退出前请等待完成提示");
        payload.put("selNode", s.selectedNode() != null ? s.selectedNode() : "");
        // 节点链
        Map<String, Object> nodes = new LinkedHashMap<>();
        List<xuanmo.arcartxsuite.abyss.design.DesignManager.Node> nodeList =
            dm != null ? dm.buildNodes(s) : List.of();
        int ni = 0;
        for (var n : nodeList) {
            nodes.put(Integer.toString(ni++), Map.of(
                "key", n.key(), "label", n.label(),
                "depth", n.depth(), "count", n.objectCount()));
        }
        payload.put("nodeRows", nodes);
        payload.put("nodeCount", ni);
        // 当前节点对象行
        Map<String, Object> objs = new LinkedHashMap<>();
        String sel = s.selectedNode();
        int oi = 0;
        if (sel != null && dm != null) {
            for (var o : s.nodeObjects(sel)) {
                objs.put(Integer.toString(oi++), Map.of(
                    "key", o.key(), "icon", o.kind().icon(),
                    "kindLabel", o.kind().label(),
                    "kindColor", o.kind().color(),
                    "id", o.id(), "summary", o.summary(),
                    "dirty", o.dirty()));
            }
        }
        payload.put("objRows", objs);
        payload.put("objCount", oi);
        return payload;
    }

    /** 副面板：节点内添加对象——类型卡片挑选。 */
    public void openDesignPick(Player player,
                               xuanmo.arcartxsuite.abyss.design.DesignSession s,
                               String nodeKey) {
        if (!available()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("nodeKey", nodeKey);
        Map<String, Object> kinds = new LinkedHashMap<>();
        int i = 0;
        for (var kind : xuanmo.arcartxsuite.abyss.design.DesignObject.Kind.values()) {
            if (!canCreateDesignObject(s, nodeKey, kind)) continue;
            String mark = kind.pointMarkable() ? "point"
                : (kind.regionMarkable() ? "region" : "none");
            kinds.put(Integer.toString(i++), Map.of(
                "kind", kind.name().toLowerCase(), "icon", kind.icon(),
                "label", kind.label(), "mark", mark,
                "kindColor", kind.color()));
        }
        payload.put("kindRows", kinds);
        payload.put("kindCount", i);
        openView(player, designPickUi(), payload);
    }

    /** 编辑面板：字段表单 + 脚本行组。 */
    public void openDesignEdit(Player player,
                               xuanmo.arcartxsuite.abyss.design.DesignSession s,
                               String objKey) {
        if (!available()) return;
        var dm = service.designManager();
        var obj = s.get(objKey);
        if (dm == null || obj == null) return;
        Map<String, Object> payload = designEditPayload(dm, s, obj, true, player);
        openView(player, designEditUi(), payload);
    }

    /** 编辑面板数据刷新。 */
    public void pushDesignEdit(Player player,
                               xuanmo.arcartxsuite.abyss.design.DesignSession s,
                               String objKey) {
        if (!available()) return;
        var dm = service.designManager();
        var obj = s.get(objKey);
        if (dm == null || obj == null) return;
        views.send(player, designEditUi(), "update",
            designEditPayload(dm, s, obj, false, player));
    }

    private Map<String, Object> designEditPayload(
            xuanmo.arcartxsuite.abyss.design.DesignManager dm,
            xuanmo.arcartxsuite.abyss.design.DesignSession s,
            xuanmo.arcartxsuite.abyss.design.DesignObject obj,
            boolean includeToken, Player player) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        if (includeToken) payload.put("action_token", token(player));
        payload.put("objKey", obj.key());
        payload.put("objId", obj.id());
        payload.put("kindLabel", obj.kind().label());
        payload.put("kindIcon", obj.kind().icon());
        payload.put("summary", obj.summary());
        payload.put("dirty", obj.dirty());
        payload.put("markable", obj.kind().pointMarkable() || obj.kind().regionMarkable());
        // 单值字段行
        Map<String, Object> fields = new LinkedHashMap<>();
        List<String> rows = dm.fieldRows(s, obj);
        for (int i = 0; i < rows.size(); i++) {
            String[] v = rows.get(i).split("\\|", -1);
            if (v.length < 6) continue;
            fields.put(Integer.toString(i), Map.of(
                "name", v[0], "editor", v[1], "label", v[2],
                "value", v[3], "valueZh", v[4], "options", v[5]));
        }
        payload.put("fieldRows", fields);
        payload.put("fieldCount", rows.size());
        // 脚本/列表字段组
        Map<String, Object> groups = new LinkedHashMap<>();
        List<String> groupRows = dm.scriptGroupRows(obj);
        int gi = 0;
        for (String g : groupRows) {
            String[] v = g.split("\\|", -1);
            if (v.length < 2) continue;
            String field = v[0];
            List<String> lines = dm.lines(obj, field);
            int pages = Math.max(1, (lines.size() + 11) / 12);
            int page = Math.min(s.linePage(obj.key(), field), pages - 1);
            s.linePage(obj.key(), field, page);
            int offset = page * 12;
            Map<String, Object> lineMap = new LinkedHashMap<>();
            for (int li = offset; li < Math.min(lines.size(), offset + 12); li++) {
                lineMap.put(Integer.toString(li - offset), lines.get(li));
            }
            groups.put(Integer.toString(gi++), Map.of(
                "field", field, "label", v[1],
                "lines", lineMap, "lineCount", lineMap.size(), "lineTotal", lines.size(),
                "lineOffset", offset, "linePage", page, "linePages", pages,
                "trialEvent", xuanmo.arcartxsuite.abyss.design.DesignObject.schema(obj.kind()).stream()
                    .anyMatch(spec -> spec.name().equals(field)
                        && spec.editor() == xuanmo.arcartxsuite.abyss.design.DesignObject.Editor.SCRIPT)));
        }
        payload.put("groupRows", groups);
        payload.put("groupCount", gi);
        return payload;
    }

    /** 关全部设计面板（退出设计/实例销毁时）。 */
    public void closeDesignUis(Player player) {
        if (!available()) return;
        views.close(player, designUi());
        views.close(player, designPickUi());
        views.close(player, designEditUi());
    }

    // ── DesignManager.Ui 桥接（设计面板打开/刷新回调）──
    @Override
    public void openMain(Player p, xuanmo.arcartxsuite.abyss.design.DesignSession s) {
        openDesignMain(p, s);
    }

    @Override
    public void openEdit(Player p, xuanmo.arcartxsuite.abyss.design.DesignSession s,
                         String objKey) {
        openDesignEdit(p, s, objKey);
    }

    @Override
    public void openPick(Player p, xuanmo.arcartxsuite.abyss.design.DesignSession s,
                       String nodeKey) {
        openDesignPick(p, s, nodeKey);
    }

    @Override
    public void refreshMain(Player p, xuanmo.arcartxsuite.abyss.design.DesignSession s) {
        pushDesignMain(p, s);
    }

    @Override
    public void closeAll(Player p) {
        closeDesignUis(p);
    }

    // ═══ HUD ═══

    /** HUD 已确认打开的玩家（仅这些玩家会收到周期推送，避免给未渲染客户端刷屏包）。 */
    private final Set<UUID> hudOpen = ConcurrentHashMap.newKeySet();

    /** 进入副本：开 HUD + 首帧数据。 */
    public void openHud(Player player, AbyssInstance instance) {
        if (!available()) return;
        if (views.open(player, hudUi(), "update", hudPayload(instance), true))
            hudOpen.add(player.getUniqueId());
    }

    /** 离开副本：关 HUD。 */
    public void closeHud(Player player) {
        hudOpen.remove(player.getUniqueId());
        if (available()) views.close(player, hudUi());
    }

    /** HUD 周期数据推送（service proximityTick 调用）。仅在 HUD 已打开时推送。 */
    public void pushHudUpdate(Player player, AbyssInstance instance) {
        if (!available() || !hudOpen.contains(player.getUniqueId())) return;
        views.send(player, hudUi(), "update", hudPayload(instance));
    }

    private Map<String, Object> hudPayload(AbyssInstance instance) {
        var mgr = service.mobGroups();
        int[] gp = mgr.groupProgress(instance);
        String progressLabel = "波次";
        if (instance.getTemplate().procgen() != null && service.procgen() != null) {
            gp = service.procgen().progress(instance); progressLabel = "房间";
        } else if (instance.getTemplate().mode() == AbyssTemplate.Mode.FLOORS && service.roomManager() != null) {
            gp = service.roomManager().progress(instance); progressLabel = "房间";
        }
        long limit = instance.getTemplate().timeLimitSeconds();
        long remain = limit > 0 ? Math.max(0, limit - instance.getRunningTime()) : 0;
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("dungeonName", instance.getTemplate().name());
        p.put("status", instance.getStatus().name());
        p.put("statusLabel", switch (instance.getStatus()) {
            case PREPARING -> "准备中"; case ACTIVE -> "进行中"; case COMPLETED -> "已通关";
            case FAILED -> "挑战失败"; case CANCELLED -> "已取消";
        });
        p.put("progressLabel", progressLabel);
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
        // 引导目标行（guide_point 动作驱动；无目标=空串隐藏）
        // 任务进度不下发：由 QuestGPS guide HUD / Chemdah scoreboard 呈现
        var gs = service.guide();
        p.put("guideText", gs != null ? gs.guideText(instance) : "");
        if(service.isSettling(instance)) {
            long seconds=service.settlementRemaining(instance);
            p.put("statusLabel",instance.getStatus()==xuanmo.arcartxsuite.abyss.instance.AbyssStatus.COMPLETED?"通关结算":"失败结算");
            p.put("timeText",seconds<0?"主动退出":AbyssService.formatTime(seconds));
            p.put("timeRatio",service.settlementTimeRatio(instance));
            p.put("guideText",seconds<0?"拾取完成后 /abyss leave":seconds==0?"正在离开 · /abyss leave 可重试":"剩余 "+seconds+" 秒拾取 · /abyss leave");
            if(progressLabel.equals("波次")) {
                p.put("groupTotal",instance.getVariables().getOrDefault("settlement_group_total",gp[0]));
                p.put("groupDone",instance.getVariables().getOrDefault("settlement_group_done",gp[1]));
            }
        }
        return p;
    }

    // ═══ 客户端包路由 ═══

    @Override
    public boolean handlePacket(Player player, String packetId, List<String> data) {
        try {
            if (CHANNEL.equalsIgnoreCase(packetId) && data != null && !data.isEmpty()
                && data.get(0).startsWith("design_")) {
                var s = designSession(player);
                if (s == null || !player.hasPermission("arcartxsuite.abyss.admin")) return true;
                if (s.saving()) { player.sendMessage("§e正在保存，请等待完成。"); return true; }
                if (data.stream().anyMatch(v -> v == null || v.length() > 65536)) return true;
            }
            return handlePacketChecked(player, packetId, data);
        } catch (IllegalArgumentException ex) {
            player.sendMessage("§c操作未应用: " + ex.getMessage());
            return true;
        }
    }

    private boolean handlePacketChecked(Player player, String packetId, List<String> data) {
        if (!CHANNEL.equalsIgnoreCase(packetId) || data == null || data.isEmpty()) {
            return false;
        }
        String action = data.get(0).toLowerCase();
        switch (action) {
            case "close_view" -> {
                if (data.size()>3 && (checkToken(player, data, 2)
                    || views.matchesNonce(player,uiForKey(data.get(1)),data.get(3)))) {
                    String ui = uiForKey(data.get(1));
                    if (ui != null && data.size() > 3) views.closed(player, ui, data.get(3));
                }
                return true;
            }
            case "view_ready" -> {
                // Read-only handshake: only an already authorised open view can be initialised.
                String uiId = data.size() > 1 ? uiForKey(data.get(1)) : null;
                if (uiId != null) views.clientReady(player, uiId);
                return true;
            }
            case "open_confirm" -> {
                if (!checkToken(player, data, 2)) return true;
                String dungeonId = data.size() > 1 ? data.get(1) : null;
                AbyssTemplate template = dungeonId != null
                    ? service.instances().getTemplate(dungeonId) : null;
                if (template != null) openEntryConfirm(player, template);
                return true;
            }
            case "enter" -> {
                if (!checkToken(player, data, 2)) return true;
                String dungeonId = data.size() > 1 ? data.get(1) : null;
                if (dungeonId == null) return true;
                // 组队时整队进本；单人直接进
                AbyssService.EnterResult r = service.enterFromPlayer(player, dungeonId, null);
                if (r.ok()) views.closeForeground(player);
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
                        openLobby(player);
                    }
                }
                return true;
            }
            case "party_disband" -> {
                if (checkToken(player, data, 1)) {
                    Party party = service.party().partyOf(player);
                    if (party != null && party.isLeader(player.getUniqueId())) {
                        List<Player> members = party.onlineMembers();
                        service.party().disband(party);
                        for (Player member : members) openLobby(member);
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
                    AbyssService.EnterResult r = service.enterFromPlayer(player, dungeonId, null);
                    if (r.ok()) for (Player member : party.onlineMembers()) views.closeForeground(member);
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
                            if (ap != null && ap.isOnline()) openParty(ap);
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
                    if (targetUuid != null) {
                        Player removed = org.bukkit.Bukkit.getPlayer(targetUuid);
                        if (removed != null && service.party().partyOf(removed) == null) openLobby(removed);
                    }
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
                    int index = parseInt(data.get(1), -1);
                    if (index >= 0) claimChest(player, index);
                }
                return true;
            }
            case "shop_buy" -> {
                // shop_buy <shopId> <goodsIndex> <token>
                if (checkToken(player, data, 3)) {
                    buyShop(player, data.size() > 1 ? data.get(1) : "",
                        parseInt(data.size() > 2 ? data.get(2) : "-1", -1));
                }
                return true;
            }
            case "reopen_chest" -> {
                if (checkToken(player, data, 1)) {
                    PendingChest pending = pendingChests.get(player.getUniqueId());
                    if (pending != null) {
                        var definition = pending.instance().getTemplate().rewards().chestRewards().get(pending.chestName());
                        if (definition != null && pending.instance().getInstanceId().equals(statsViews.get(player.getUniqueId())))
                            openChest(player,definition,pending.instance(),true);
                    }
                }
                return true;
            }
            case "settlement_leave" -> {
                if(checkToken(player, data, 1)) {
                    AbyssInstance instance=service.instances().getInstance(player);
                    if(instance!=null && instance.isFinished() && instance.getInstanceId().equals(statsViews.get(player.getUniqueId()))
                        && service.playerExit(instance,player,xuanmo.arcartxsuite.abyss.instance.LeaveReason.QUIT))
                        views.close(player,statsUi());
                }
                return true;
            }
            case "open_stats" -> {
                if (checkToken(player, data, 1)) {
                    openStats(player, service.instances().getInstance(player));
                }
                return true;
            }
            case "input_submit" -> {
                // input_submit <puzzleId> <answer> <token>
                if (checkToken(player, data, 3)) {
                    String puzzleId = data.size() > 1 ? data.get(1) : null;
                    String answer = data.size() > 2 ? data.get(2) : "";
                    if (puzzleId != null && service.puzzles() != null) {
                        // 校验玩家确实在副本实例内（防伪造/跨实例提交）
                        AbyssInstance inst = service.instances().getInstance(player);
                        UiOffer offer = inputOffers.get(player.getUniqueId());
                        if (inst != null && !inst.isFinished() && answer.length() <= 4096
                            && new UiOffer(inst.getInstanceId(), puzzleId).equals(offer)
                            && views.isOpen(player, inputUi())) {
                            boolean handled = service.puzzles().submitInput(inst, player, puzzleId, answer);
                            if (handled && views.isOpen(player, inputUi()) && inputOffers.get(player.getUniqueId()) == offer
                                && !service.puzzles().openInput(inst, player, puzzleId)) {
                                inputOffers.remove(player.getUniqueId());
                                views.close(player, inputUi());
                            }
                        }
                    }
                }
                return true;
            }
            case "buff_pick" -> {
                // buff_pick <poolId> <optionId> <token>
                if (checkToken(player, data, 3)) {
                    String poolId = data.size() > 1 ? data.get(1) : null;
                    String optionId = data.size() > 2 ? data.get(2) : "";
                    if (poolId != null && service.buffs() != null) {
                        AbyssInstance inst = service.instances().getInstance(player);
                        if (inst != null && !inst.isFinished()
                            && new UiOffer(inst.getInstanceId(), poolId).equals(buffOffers.get(player.getUniqueId()))
                            && views.isOpen(player, buffUi())) {
                            UiOffer previousOffer = buffOffers.get(player.getUniqueId());
                            boolean accepted = service.buffs().pick(inst, player, poolId, optionId);
                            // A continuation replaces the offer and opens the next selection itself.
                            if (accepted && buffOffers.get(player.getUniqueId()) == previousOffer) {
                                buffOffers.remove(player.getUniqueId());
                                views.close(player, buffUi());
                            }
                        }
                    }
                }
                return true;
            }
            // ═══ 设计模式包（全部要求设计会话 + token）═══
            case "design_node" -> {
                // design_node <nodeKey> <token>
                var s = designSession(player);
                if (s != null && checkToken(player, data, 2)) {
                    s.selectedNode(data.get(1));
                    pushDesignMain(player, s);
                }
                return true;
            }
            case "design_pick_open" -> {
                // design_pick_open <nodeKey> <token>
                var s = designSession(player);
                if (s != null && checkToken(player, data, 2)) {
                    openDesignPick(player, s, data.get(1));
                }
                return true;
            }
            case "design_pick" -> {
                // design_pick <kind> <nodeKey> <token> —— 副面板选类型
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 3)) {
                    var kind = parseKind(data.get(1));
                    if (kind != null && canCreateDesignObject(s, data.get(2), kind)) {
                        s.selectedNode(data.get(2));
                        beginMarkOrCreate(player, s, dm, kind, null);
                    }
                }
                return true;
            }
            case "design_edit" -> {
                var s = designSession(player);
                if (s != null && checkToken(player, data, 2)) {
                    openDesignEdit(player, s, data.get(1));
                }
                return true;
            }
            case "design_back" -> {
                var s = designSession(player);
                if (s != null && checkToken(player, data, 1)) {
                    closeUi(player, designEditUi());
                    closeUi(player, designPickUi());
                    openDesignMain(player, s);
                }
                return true;
            }
            case "design_set" -> {
                // design_set <objKey> <field> <value> <token>
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 4)) {
                    dm.setField(s, data.get(1), data.get(2), data.get(3));
                    pushDesignEdit(player, s, data.get(1));
                }
                return true;
            }
            case "design_cycle" -> {
                // design_cycle <objKey> <field> <token>
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 3)) {
                    dm.cycleNext(s, data.get(1), data.get(2));
                    pushDesignEdit(player, s, data.get(1));
                }
                return true;
            }
            case "design_rename" -> {
                // design_rename <objKey> <newId> <token>
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 3)) {
                    if (!dm.rename(s, data.get(1), data.get(2))) {
                        player.sendMessage(service.msg("design.rename-fail"));
                    }
                    pushDesignEdit(player, s, data.get(1));
                }
                return true;
            }
            case "design_remark" -> {
                // design_remark <objKey> <field> <token> —— 重新标记位置/区域
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 3)) {
                    var obj = s.get(data.get(1));
                    String field = "spawn-point".equals(data.get(2)) ? "monsters" : data.get(2);
                    if (obj != null) {
                        var kind = ("region".equals(field) || "exit".equals(field))
                            ? xuanmo.arcartxsuite.abyss.design.DesignSession.PendingMark.Kind.REGION
                            : xuanmo.arcartxsuite.abyss.design.DesignSession.PendingMark.Kind.POINT;
                        s.pending(new xuanmo.arcartxsuite.abyss.design.DesignSession.PendingMark(
                            kind, obj.key(), field, obj.kind()));
                        closeUi(player, designEditUi());
                        player.sendMessage(service.msg(kind
                            == xuanmo.arcartxsuite.abyss.design.DesignSession.PendingMark.Kind.REGION
                            ? "design.remark-region" : "design.remark-point"));
                    }
                }
                return true;
            }
            case "design_del" -> {
                // design_del <objKey> <token>
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 2)) {
                    if (!s.confirmDeletion(data.get(1))) {
                        player.sendMessage("§e请在10秒内再次点击该对象的删除按钮以确认。");
                        return true;
                    }
                    dm.delete(s, data.get(1));
                    closeUi(player, designEditUi());
                    player.sendMessage(service.msg("design.deleted"));
                    openDesignMain(player, s);
                }
                return true;
            }
            case "design_tp" -> {
                // design_tp <objKey> <token> —— 传送到对象位置
                var s = designSession(player);
                if (s != null && checkToken(player, data, 2)) {
                    var obj = s.get(data.get(1));
                    if (obj != null && obj.anchor() != null && s.instance().getWorld() != null) {
                        int[] a = obj.anchor();
                        player.teleport(new org.bukkit.Location(s.instance().getWorld(),
                            a[0] + 0.5, a[1] + 1, a[2] + 0.5));
                    }
                }
                return true;
            }
            case "design_line_page" -> {
                var s = designSession(player);
                if (s != null && checkToken(player, data, 4)) {
                    s.linePage(data.get(1), data.get(2), parseInt(data.get(3), 0));
                    pushDesignEdit(player, s, data.get(1));
                }
                return true;
            }
            case "design_line_add" -> {
                // design_line_add <objKey> <field> <line> <token>
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 4)) {
                    dm.lineAdd(s, data.get(1), data.get(2), data.get(3));
                    var object = s.get(data.get(1));
                    if (object != null) s.linePage(object.key(), data.get(2),
                        Math.max(0, (dm.lines(object, data.get(2)).size() - 1) / 12));
                    pushDesignEdit(player, s, data.get(1));
                }
                return true;
            }
            case "design_line_set" -> {
                // design_line_set <objKey> <field> <idx> <line> <token>
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 5)) {
                    dm.lineSet(s, data.get(1), data.get(2),
                        parseInt(data.get(3), -1), data.get(4));
                    pushDesignEdit(player, s, data.get(1));
                }
                return true;
            }
            case "design_line_del" -> {
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 4)) {
                    dm.lineDel(s, data.get(1), data.get(2), parseInt(data.get(3), -1));
                    pushDesignEdit(player, s, data.get(1));
                }
                return true;
            }
            case "design_line_move" -> {
                // design_line_move <objKey> <field> <idx> <dir> <token>
                var s = designSession(player);
                var dm = service.designManager();
                if (s != null && dm != null && checkToken(player, data, 5)) {
                    dm.lineMove(s, data.get(1), data.get(2),
                        parseInt(data.get(3), -1), parseInt(data.get(4), 0));
                    pushDesignEdit(player, s, data.get(1));
                }
                return true;
            }
            case "design_trial" -> {
                var session = designSession(player);
                if (session != null && checkToken(player, data, 2)) {
                    switch (data.get(1)) {
                        case "start" -> service.startDesignTrial(player);
                        case "clear" -> {
                            if (session.trial() != null) session.trial().mapCleared();
                            else player.sendMessage("§e请先开启草稿试跑。");
                        }
                        case "stop" -> {
                            session.trial(null);
                            player.sendMessage("§a试跑已结束。");
                        }
                        default -> { }
                    }
                }
                return true;
            }
            case "design_trial_event" -> {
                var session = designSession(player);
                if (session != null && checkToken(player, data, 3)) {
                    var object = session.get(data.get(1));
                    if (session.trial() == null) player.sendMessage("§e请先开启草稿试跑。");
                    else if (object != null)
                        session.trial().event(object.key(), data.get(2), object.id(), object.kind());
                }
                return true;
            }
            case "design_save" -> {
                // design_save <config|world> <token>
                if (designSession(player) != null && checkToken(player, data, 2)) {
                    service.saveDesign(player, "world".equalsIgnoreCase(data.get(1)));
                }
                return true;
            }
            case "design_exit" -> {
                if (designSession(player) != null && checkToken(player, data, 1)) {
                    service.exitDesign(player);
                }
                return true;
            }
            case "design_cancel" -> {
                // 关闭全部设计面板（设计会话保持）
                closeDesignUis(player);
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
            pushLobbyList(target, "", "");
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
            for (Party pending : service.party().allParties()) {
                if (pending.applications().contains(player.getUniqueId())) pushPartyUpdate(pending);
            }
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
        PendingChest pc = pendingChests.get(player.getUniqueId());
        if (pc == null) return;
        List<ItemStack> items = pc.items();
        if (index < 0) {
            String request = "chest:" + pc.instance().getInstanceId() + ":" + player.getUniqueId() + ":"
                + pc.items().stream().map(pc::key).sorted().collect(java.util.stream.Collectors.joining(","));
            if (!service.dispatchItemsConfirmed(player, List.copyOf(items), request)) {
                player.sendMessage("§c奖励尚未确认保存，领取资格已保留，请稍后重试。");
                return;
            }
            pendingChests.remove(player.getUniqueId());
            pc.instance().markTriggeredOnce(
                AbyssService.chestClaimKey(pc.chestName(), player.getUniqueId()));
            if (available()) views.close(player, chestUi());
            return;
        }
        if (index >= 0 && index < items.size()) {
            ItemStack it = items.get(index);
            if (it != null && !service.dispatchItemsConfirmed(player, List.of(it), pc.key(it))) {
                player.sendMessage("§c奖励尚未确认保存，领取资格已保留，请稍后重试。");
                return;
            }
            items.remove(index);
            if (items.isEmpty()) {
                pendingChests.remove(player.getUniqueId());
                pc.instance().markTriggeredOnce(
                    AbyssService.chestClaimKey(pc.chestName(), player.getUniqueId()));
                if (available()) views.close(player, chestUi());
            } else if (available()) {
                // 回推剩余物品：已领槽位从 UI 上清掉
                pushChestUpdate(player, pc);
            }
        }
    }

    /** 单格领取后重发 init：剩余物品前移，空出的尾部槽位填空气 JSON。 */
    private void pushChestUpdate(Player player, PendingChest pc) {
        if(chestUi().equals(foregroundUis.get(player.getUniqueId())) && views.isOpen(player,chestUi()))
            tokens.put(player.getUniqueId(),UUID.randomUUID().toString());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", CHANNEL);
        payload.put("action_token", token(player));
        payload.put("chestTitle", pc.title());
        payload.put("itemCount", pc.items().size());
        for (int i = 0; i < 9; i++) {
            payload.put("itemJson" + i,
                i < pc.items().size() ? itemJsonOf(pc.items().get(i)) : AIR_ITEM_JSON);
            payload.put("itemAmount" + i, i < pc.items().size() ? pc.items().get(i).getAmount() : 0);
        }
        views.send(player, chestUi(), "init", payload);
    }

    /** 玩家掉线/关 UI 未领取：全部发放（不丢奖励）并标记已领。 */
    public void flushPendingChest(Player player) {
        if (pendingChests.containsKey(player.getUniqueId())) claimChest(player, -1);
    }

    /** 玩家完全离开服务时清理会话态（token/HUD 标记/待领宝箱已先发放）。 */
    public void onPlayerGone(Player player) {
        UUID uid = player.getUniqueId();
        tokens.remove(uid);
        foregroundUis.remove(uid);
        partyStates.remove(uid);
        recentStats.remove(uid);
        statsViews.remove(uid);
        boardRequests.remove(uid);
        var query=boardQueries.remove(uid);if(query!=null)query.cancel(false);
        views.clear(player);
        shopOffers.remove(uid); inputOffers.remove(uid); buffOffers.remove(uid);
        hudOpen.remove(uid);
        flushPendingChest(player);
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.length() > 64) return def;
        try { return Integer.parseInt(s); }
        catch (NumberFormatException ignored) {
            // Aria arithmetic serialises integral values as e.g. "1.0".
            try { return new java.math.BigDecimal(s.trim()).intValueExact(); }
            catch (NumberFormatException | ArithmeticException invalid) { return def; }
        }
    }

    private static @Nullable UUID parseUuid(@Nullable String s) {
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    // ── 设计模式 helper ──

    private @Nullable xuanmo.arcartxsuite.abyss.design.DesignSession designSession(Player p) {
        var dm = service.designManager();
        return dm != null ? dm.session(p) : null;
    }

    private static xuanmo.arcartxsuite.abyss.design.DesignObject.Kind parseKind(String s) {
        try {
            return xuanmo.arcartxsuite.abyss.design.DesignObject.Kind.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean canCreateDesignObject(
            xuanmo.arcartxsuite.abyss.design.DesignSession session, String node,
            xuanmo.arcartxsuite.abyss.design.DesignObject.Kind kind) {
        if (node.equals("misc:layout")) {
            var template = session.instance().getTemplate();
            return template.mode() == AbyssTemplate.Mode.FLOORS && template.procgen() == null
                && (kind == xuanmo.arcartxsuite.abyss.design.DesignObject.Kind.FLOOR
                    || kind == xuanmo.arcartxsuite.abyss.design.DesignObject.Kind.ROOM);
        }
        if (node.equals("misc:stages")) return kind == xuanmo.arcartxsuite.abyss.design.DesignObject.Kind.STAGE;
        if (node.equals("misc:settlement") || node.equals("misc:files") || node.equals("misc:assets")) return false;
        boolean inRoom = node.startsWith("room:");
        return switch (kind) {
            case ROOM, FLOOR, STAGE, SETTLEMENT, DOCUMENT -> false;
            case ROOM_GROUP -> inRoom && session.get(node) != null;
            case GROUP -> !inRoom;
            default -> true;
        };
    }

    private void closeUi(Player p, String uiId) {
        if (available()) views.close(p, uiId);
    }

    /**
     * 副面板选类型后：point/region 类进待标记态（关面板走标记杖）；
     * 无定位类（task/reward/puzzle/buff）直接建对象并开编辑。
     */
    private void beginMarkOrCreate(Player p,
            xuanmo.arcartxsuite.abyss.design.DesignSession s,
            xuanmo.arcartxsuite.abyss.design.DesignManager dm,
            xuanmo.arcartxsuite.abyss.design.DesignObject.Kind kind,
            @Nullable String nodeKey) {
        closeDesignUis(p);
        if (kind.pointMarkable()) {
            // 怪组/房内怪组：标记点直接落成 monsters 结构化刷怪项
            String field = (kind == xuanmo.arcartxsuite.abyss.design.DesignObject.Kind.GROUP
                || kind == xuanmo.arcartxsuite.abyss.design.DesignObject.Kind.ROOM_GROUP)
                ? "monsters" : "location";
            s.pending(new xuanmo.arcartxsuite.abyss.design.DesignSession.PendingMark(
                xuanmo.arcartxsuite.abyss.design.DesignSession.PendingMark.Kind.POINT,
                null, field, kind));
            p.sendMessage(service.msg("design.mark-point", "kind", kind.label()));
            return;
        }
        if (kind.regionMarkable()) {
            s.pending(new xuanmo.arcartxsuite.abyss.design.DesignSession.PendingMark(
                xuanmo.arcartxsuite.abyss.design.DesignSession.PendingMark.Kind.REGION,
                null, "region", kind));
            p.sendMessage(service.msg("design.mark-region", "kind", kind.label()));
            return;
        }
        // 无定位对象：直接建
        String id = s.nextId(kind, kind.name().toLowerCase());
        Map<String, Object> vals = new LinkedHashMap<>();
        xuanmo.arcartxsuite.abyss.design.DesignManager.seedDefaults(kind, vals);
        String key = s.uniqueKey(kind, id);
        var obj = new xuanmo.arcartxsuite.abyss.design.DesignObject(
            key, kind, id, kind.file(), kind.path(), kind.label(),
            null, null,
            s.selectedNode() != null ? s.selectedNode() : "misc:global",
            vals, true);
        s.put(obj);
        openDesignEdit(p, s, key);
    }
}
