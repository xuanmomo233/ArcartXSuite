package xuanmo.arcartxsuite.lottery.packet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.item.ItemMatcher;

import xuanmo.arcartxsuite.lottery.LotteryService;
import xuanmo.arcartxsuite.lottery.config.CostConfig;
import xuanmo.arcartxsuite.lottery.config.CasePoolConfig;
import xuanmo.arcartxsuite.lottery.config.ExchangeShopItem;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.config.PoolDefinition;
import xuanmo.arcartxsuite.lottery.config.PoolType;
import xuanmo.arcartxsuite.lottery.config.PointDefinition;
import xuanmo.arcartxsuite.lottery.model.CaseResult;
import xuanmo.arcartxsuite.lottery.model.GachaResult;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.model.PoolItem;
import xuanmo.arcartxsuite.lottery.storage.LotteryRepository;

/** Lottery 客户端数据包处理器。 */
public final class LotteryPacketHandler implements ClientPacketHandler {
    private static final int CASE_PREVIEW_SLOT_LIMIT = 45;
    public static final String GACHA = "AXS_LOTTERY_GACHA";
    public static final String WISH = "AXS_LOTTERY_WISH";
    public static final String CASE = "AXS_LOTTERY_CASE";
    private final LotteryService service;
    private final PacketBridgeAPI packetBridge;
    private final LotteryPreviewIcons previewIcons;
    private final JavaPlugin plugin;
    private final String gachaUiId;
    private final String wishUiId;
    private final String caseUiId;
    private final Map<UUID, Integer> gachaIndex = new HashMap<>();
    private final Map<UUID, Integer> caseIndex = new HashMap<>();
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, View> inFlight = new java.util.HashMap<>();
    private final Set<String> watchedUis = new HashSet<>();
    private final LotteryViewReads reads;
    private final List<PoolDefinition> gachaPools;
    private final List<PoolDefinition> casePools;
    private final Map<String, Object> gachaPoolNames;
    private final Map<String, Object> casePoolNames;
    private final Map<String, PoolPreview> poolPreviews = new HashMap<>();
    private final Map<String, PointDefinition> pointDefinitions = new LinkedHashMap<>();
    private final Set<String> exchangePointIds;
    private final Set<String> exchangeIds;
    private boolean stopped;

    private enum Overlay { DETAILS, HISTORY, EXCHANGE }

    private record PoolPreview(List<String> description, Set<String> pointIds, PoolItem fallbackIcon,
                               String featuredName, int featuredStar, Map<String, HistoryItem> historyItems,
                               LotteryPoolDetails details) {}

    private record HistoryItem(PoolItem item, int star) {}

    private static final class View {
        final Player player;
        final AuthSnapshot auth;
        final String packetId;
        PoolDefinition pool;
        int index;
        long mainRequest;
        long overlayRequest;
        boolean updating;
        Overlay deferredOverlay;
        boolean exchangeReady;
        String token;

        View(Player player, AuthSnapshot auth, String packetId, PoolDefinition pool, int index) {
            this.player = player;
            this.auth = auth;
            this.packetId = packetId;
            this.pool = pool;
            this.index = index;
        }
    }

    public LotteryPacketHandler(@NotNull LotteryService service, @NotNull PacketBridgeAPI packetBridge,
                                @NotNull IconResolver iconResolver, @NotNull JavaPlugin plugin,
                                @NotNull String gachaUiId, @NotNull String wishUiId,
                                @NotNull String caseUiId) {
        this.service = service;
        this.packetBridge = packetBridge;
        this.previewIcons = new LotteryPreviewIcons(iconResolver, service);
        this.plugin = plugin;
        this.gachaUiId = gachaUiId;
        this.wishUiId = wishUiId;
        this.caseUiId = caseUiId;
        this.reads = new LotteryViewReads(plugin);
        this.gachaPools = service.getPools().values().stream()
            .filter(pool -> pool.enabled() && pool.type() == PoolType.GACHA).toList();
        this.casePools = service.getPools().values().stream()
            .filter(pool -> pool.enabled() && pool.type() == PoolType.CASE).toList();
        this.gachaPoolNames = poolList(gachaPools);
        this.casePoolNames = poolList(casePools);
        for (PoolDefinition pool : gachaPools) poolPreviews.put(pool.id(), poolPreview(pool));
        for (PoolDefinition pool : casePools) poolPreviews.put(pool.id(), poolPreview(pool));
        for (PoolDefinition pool : service.getPools().values()) {
            for (PointDefinition point : pool.points().values()) pointDefinitions.putIfAbsent(point.id(), point);
        }
        Set<String> points = new HashSet<>(pointDefinitions.keySet());
        Set<String> items = new HashSet<>();
        for (ExchangeShopItem item : service.getExchangeItems().values()) {
            items.add(item.id());
            collectPoints(item.cost(), points);
        }
        this.exchangePointIds = Set.copyOf(points);
        this.exchangeIds = Set.copyOf(items);
        watchUi(gachaUiId);
        watchUi(wishUiId);
        watchUi(caseUiId);
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId,
                                      @NotNull List<String> data) {
        if (!GACHA.equalsIgnoreCase(packetId) && !WISH.equalsIgnoreCase(packetId) && !CASE.equalsIgnoreCase(packetId)) return false;
        Runnable operation = () -> handle(player, packetId.toUpperCase(Locale.ROOT), data);
        if (Bukkit.isPrimaryThread()) operation.run(); else Bukkit.getScheduler().runTask(plugin, operation);
        return true;
    }

    private void handle(Player player, String packetId, List<String> data) {
        View view = viewForPacket(player, packetId);
        if (view == null) return;
        String action = data.isEmpty() ? "refresh" : data.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "switch_pool" -> update(view, parseInt(data, 1, 0));
            case "pull" -> { if (isGachaType(packetId)) pull(view, data); }
            case "open_case" -> { if (CASE.equals(packetId)) openCase(view, data, false); }
            case "open_case_batch" -> { if (CASE.equals(packetId)) openCase(view, data, true); }
            case "history" -> showOverlay(view, Overlay.HISTORY);
            case "details" -> showOverlay(view, Overlay.DETAILS);
            case "exchange" -> { if (isGachaType(packetId)) showOverlay(view, Overlay.EXCHANGE); }
            case "close_overlay" -> closeOverlay(view);
            case "exchange_buy" -> { if (isGachaType(packetId)) exchangeBuy(view, data); }
            default -> update(view, view.index);
        }
    }

    private void pull(View view, List<String> data) {
        if (data.size() < 3 || !consumeToken(view, data.get(2))) return;
        int count = parseInt(data, 1, 1) == 10 ? 10 : 1;
        var pool = view.pool;
        finishOperation(view, service.pullGacha(view.player, pool.id(), count), result -> {
            if (view.pool == pool && result != null && !result.items().isEmpty())
                sendGachaResult(view.player, view.packetId, pool, result, count);
        }, false);
    }

    private void openCase(View view, List<String> data, boolean batch) {
        int tokenIndex = batch ? 2 : 1;
        if (data.size() <= tokenIndex || (batch && parseInt(data, 1, 10) != 10)
            || !consumeToken(view, data.get(tokenIndex))) return;
        int count = batch ? 10 : 1;
        var pool = view.pool;
        finishOperation(view, service.openCaseBatch(view.player, pool.id(), count), results -> {
            if (view.pool != pool || results.isEmpty()) return;
            if (batch) sendCaseBatch(view.player, pool, results, count);
            else sendCaseResult(view.player, pool, results.get(0));
        }, false);
    }

    private <T> void finishOperation(View view, java.util.concurrent.CompletionStage<T> operation,
                                     java.util.function.Consumer<T> render, boolean refreshExchange) {
        UUID id = view.player.getUniqueId();
        long overlayAtSubmission = view.overlayRequest;
        operation.whenComplete((result, failure) -> service.reply(() -> {
            if (!inFlight.remove(id, view) || stopped || !isCurrent(view)) return;
            try {
                if (failure == null) render.accept(result);
                else {
                    service.getLogger().log(java.util.logging.Level.WARNING, "Lottery operation failed", failure);
                    service.sendFailureMessage(view.player);
                }
            } finally {
                boolean refresh = refreshExchange && view.exchangeReady && view.overlayRequest == overlayAtSubmission;
                update(view, view.index);
                if (refresh && isCurrent(view)) showOverlay(view, Overlay.EXCHANGE);
            }
        }));
    }

    private void sendGachaResult(Player player, String packetId, PoolDefinition pool, GachaResult result, int count) {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<PoolItem> items = result == null ? List.of() : result.items();
        payload.put("resultCount", items.size());
        Map<String, Object> names = new LinkedHashMap<>();
        Map<String, Object> stars = new LinkedHashMap<>();
        Map<String, Object> ups = new LinkedHashMap<>();
        int highest = 0;
        for (int i = 0; i < items.size(); i++) {
            PoolItem item = items.get(i);
            String key = Integer.toString(i + 1);
            LotteryService.GachaItemInfo info = service.describeGachaItem(pool, item);
            // itemJson 分离模式（§7）：放在 packet 顶层字段，UI 端遍历构建 var.resultItemJsons
            payload.put("resultItemJson" + key, poolItemJson(player, item));
            names.put(key, previewName(item));
            stars.put(key, info.star());
            ups.put(key, info.up());
            highest = Math.max(highest, info.star());
        }
        payload.put("resultItemNames", names);
        payload.put("resultItemStars", stars);
        payload.put("resultItemUps", ups);
        payload.put("resultCountRequested", count);
        payload.put("guaranteed", result != null && result.guaranteed());
        payload.put("fatePointTriggered", result != null && result.fatePointTriggered());
        payload.put("pity4", result == null ? 0 : result.finalState().pity4star());
        payload.put("pity5", result == null ? 0 : result.finalState().pity5star());
        payload.put("highestStar", highest);
        packetBridge.sendPacket(player, uiIdFor(packetId), count == 1 ? "result" : "batch_result", payload);
    }

    private void sendCaseResult(Player player, PoolDefinition pool, CaseResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        PoolItem item = result == null ? null : result.item();
        payload.put("resultCount", item == null ? 0 : 1);
        // itemJson 分离模式（§7）：放在 packet 顶层字段，UI 端遍历构建 var.resultItemJsons
        payload.put("resultItemJson1", item == null ? "" : poolItemJson(player, item));
        payload.put("resultItemName", item == null ? "" : previewName(item));
        payload.put("resultRarity", result == null ? "" : result.rarity());
        // 动画带伪随机物品: 50 个物品填满动画带，第 39 个（card 38）是中奖物品
        fillAnimationItems(player, pool, payload, item);
        packetBridge.sendPacket(player, caseUiId, "result", payload);
    }

    /**
     * 生成 50 个伪随机物品填满动画带，不能连续同一个物品相邻，第 39 个（card 38）是中奖物品。
     */
    private void fillAnimationItems(Player player, PoolDefinition pool, Map<String, Object> payload, PoolItem winner) {
        List<PoolItem> poolItems = pool.caseConfig() == null ? List.of() : pool.caseConfig().items();
        int animCount = 50;
        int winnerSlot = 38; // card 38 (0-based), 对应 animItemJson39 (1-based)
        Map<String, Object> names = new LinkedHashMap<>();
        Map<String, Object> rarities = new LinkedHashMap<>();
        java.util.Random rng = new java.util.Random();
        PoolItem prev = null;
        for (int i = 0; i < animCount; i++) {
            PoolItem picked;
            if (i == winnerSlot && winner != null) {
                picked = winner;
            } else {
                // 随机选一个，避免和上一个相同
                picked = randomItemExcept(poolItems, prev, rng);
            }
            String key = Integer.toString(i + 1);
            payload.put("animItemJson" + key, picked == null ? "" : poolItemJson(player, picked));
            names.put(key, picked == null ? "" : picked.name());
            rarities.put(key, picked == null ? "" : (picked.rarity() == null ? "" : picked.rarity()));
            prev = picked;
        }
        payload.put("animItemNames", names);
        payload.put("animItemRarities", rarities);
        payload.put("animCount", animCount);
        payload.put("animWinnerSlot", winnerSlot + 1);
    }

    private PoolItem randomItemExcept(List<PoolItem> items, PoolItem exclude, java.util.Random rng) {
        if (items == null || items.isEmpty()) return null;
        if (items.size() == 1) return items.get(0);
        PoolItem picked = items.get(rng.nextInt(items.size()));
        if (picked == exclude) {
            for (int attempt = 0; attempt < 5 && picked == exclude; attempt++) {
                picked = items.get(rng.nextInt(items.size()));
            }
            if (picked == exclude) {
                for (PoolItem candidate : items) {
                    if (candidate != exclude) return candidate;
                }
            }
        }
        return picked;
    }

    private void sendCaseBatch(Player player, PoolDefinition pool, List<CaseResult> results, int count) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> names = new LinkedHashMap<>();
        Map<String, Object> rarities = new LinkedHashMap<>();
        for (int i = 0; i < results.size(); i++) {
            CaseResult result = results.get(i);
            PoolItem item = result == null ? null : result.item();
            String key = Integer.toString(i + 1);
            // itemJson 分离模式（§7）：放在 packet 顶层字段，UI 端遍历构建 var.batchItemJsons
            payload.put("batchItemJson" + key, item == null ? "" : poolItemJson(player, item));
            names.put(key, item == null ? "" : previewName(item));
            rarities.put(key, result == null ? "" : result.rarity());
        }
        payload.put("batchCount", results.size());
        payload.put("batchRequested", count);
        payload.put("batchItemNames", names);
        payload.put("batchItemRarities", rarities);
        // 十连动画带: 选最高稀有度的中奖物品作为动画停止位置展示
        PoolItem batchWinner = null;
        String batchWinnerRarity = "";
        for (CaseResult r : results) {
            if (r == null || r.item() == null) continue;
            if (batchWinner == null || rarityRank(r.rarity()) > rarityRank(batchWinnerRarity)) {
                batchWinner = r.item();
                batchWinnerRarity = r.rarity();
            }
        }
        fillAnimationItems(player, pool, payload, batchWinner);
        packetBridge.sendPacket(player, caseUiId, "batch_result", payload);
    }

    /**
     * 稀有度排序权重，数值越大越稀有。
     */
    private int rarityRank(String rarity) {
        if (rarity == null) return 0;
        switch (rarity) {
            case "COMMON": return 1;
            case "UNCOMMON": return 2;
            case "RARE": return 3;
            case "EPIC": return 4;
            case "LEGENDARY": return 5;
            case "MYTHIC": return 6;
            case "SPECIAL": return 7;
            default: return 0;
        }
    }

    private void update(View view, int index) {
        if (!isCurrent(view)) return;
        List<PoolDefinition> pools = pools(view.packetId);
        view.index = Math.max(0, Math.min(index, pools.size() - 1));
        view.pool = pools.get(view.index);
        indexMap(view.packetId).put(view.player.getUniqueId(), view.index);
        long request = ++view.mainRequest;
        closeOverlay(view);
        view.token = null;
        view.updating = true;
        UUID playerId = view.player.getUniqueId();
        reads.discardReplacement(playerId);
        PoolDefinition pool = view.pool;
        Set<String> pointIds = poolPreviews.get(pool.id()).pointIds();
        BooleanSupplier current = () -> view.mainRequest == request;
        Runnable failed = () -> failUpdate(view);
        if (pool.type() == PoolType.GACHA) {
            query(view, () -> service.loadGachaView(playerId, pool.id(), pointIds), current,
                result -> publishUpdate(view, request, result.points(), result.state()), failed);
        } else if (!pointIds.isEmpty()) {
            query(view, () -> service.loadPointBalances(playerId, pointIds), current,
                result -> publishUpdate(view, request, result, null), failed);
        } else {
            try {
                publishUpdate(view, request, Map.of(), null);
            } catch (RuntimeException failure) {
                readFailure(view, current, failure, failed);
            }
        }
    }

    private void publishUpdate(View view, long request, Map<String, Long> points, PlayerGachaState state) {
        Player player = view.player;
        PoolDefinition pool = view.pool;
        PoolPreview preview = poolPreviews.get(pool.id());
        List<PoolDefinition> pools = pools(view.packetId);
        String token = UUID.randomUUID().toString();
        Balances balances = new Balances(player, points);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", view.packetId);
        payload.put("poolName", pool.displayName());
        payload.put("poolType", pool.type().name());
        payload.put("poolDescription", preview.description());
        payload.put("poolList", isGachaType(view.packetId) ? gachaPoolNames : casePoolNames);
        Map<String, String> poolIcons = poolUpItemIcons(player, pools);
        payload.put("poolUpItemIcons", poolIcons);
        String upItemIcon = poolIcons.get(Integer.toString(view.index));
        payload.put("currentPoolIndex", view.index);
        payload.put("poolCount", pools.size());
        payload.put("action_token", token);
        if (pool.type() == PoolType.GACHA) updateGacha(player, pool, preview, upItemIcon, payload, state, balances);
        else updateCase(player, pool, upItemIcon, payload, balances);
        if (!isCurrent(view) || view.mainRequest != request) return;
        if (!packetBridge.sendPacket(player, uiIdFor(view.packetId), "update", payload)) {
            failUpdate(view);
            return;
        }
        if (!isCurrent(view) || view.mainRequest != request) return;
        view.token = token;
        view.updating = false;
        Overlay overlay = view.deferredOverlay;
        view.deferredOverlay = null;
        if (overlay != null) showOverlay(view, overlay);
    }

    private void failUpdate(View view) {
        view.token = null;
        view.updating = false;
        view.deferredOverlay = null;
        packetBridge.sendPacket(view.player, uiIdFor(view.packetId), "read_failed", Map.of("packetId", view.packetId));
        service.sendFailureMessage(view.player);
    }

    private void updateGacha(Player player, PoolDefinition pool, PoolPreview preview, String upItemIcon,
                             Map<String, Object> payload, PlayerGachaState state, Balances balances) {
        GachaPoolConfig gacha = pool.gacha();
        payload.put("upItemName", preview.featuredName());
        payload.put("upItemStar", preview.featuredStar());
        payload.put("upItemDesc", pool.upItemDesc());
        payload.put("upItemIcon", upItemIcon);
        payload.put("pity4", state.pity4star());
        payload.put("pity5", state.pity5star());
        payload.put("pity4Limit", gacha.pity4star());
        payload.put("pity5Limit", gacha.pity5star());
        boolean guaranteedHere = gacha.hasUp5starItems() && state.guaranteedUp();
        payload.put("guaranteedUp", guaranteedHere);
        String guaranteedUpText = service.message("packet.guaranteed-up");
        String guaranteedNotUpText = service.message("packet.guaranteed-not-up");
        payload.put("guaranteedUpText", guaranteedUpText);
        payload.put("guaranteedNotUpText", guaranteedNotUpText);
        payload.put("guaranteedUnknownText", service.message("packet.guaranteed-unknown"));
        payload.put("guaranteedStatusText", guaranteedHere ? guaranteedUpText : guaranteedNotUpText);
        payload.put("fatePoints", state.fatePoints());
        payload.put("fateTarget", state.fateTarget());
        payload.put("fatePointCap", gacha.fatePointCap());
        payload.put("softPityStart", gacha.softPityStart());
        payload.put("softPityIncrement", gacha.softPityIncrement());
        payload.put("upRate", gacha.upRate());
        payload.put("base5Rate", gacha.base5starRate());
        payload.put("base4Rate", gacha.base4starRate());
        payload.put("guaranteeDesc", service.message("packet.guarantee-desc",
            gacha.pity5star(), gacha.pity4star()));
        payload.put("costRows", costRows(player, pool.cost().single(), payload, "costIconJson", balances));
        payload.put("costTenRows", costRows(player, pool.cost().ten(), payload, "costTenIconJson", balances));
        payload.put("tenAvailable", !pool.cost().ten().isEmpty());
        payload.put("resourceRows", resourceRows(player, pool.cost(), payload, balances));
    }

    private static List<String> descriptionLines(String description) {
        if (description == null || description.isEmpty()) return List.of();
        String normalized = description.replace("\\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\\\n|\\n", -1);
        List<String> colored = new ArrayList<>(lines.length);
        for (String line : lines) {
            if (!line.isBlank()) colored.add("&7" + line);
        }
        return List.copyOf(colored);
    }

    private void updateCase(Player player, PoolDefinition pool, String upItemIcon,
                            Map<String, Object> payload, Balances balances) {
        payload.put("rarityNames", caseRarityNames());
        payload.put("requiresKey", pool.requiresKey());
        payload.put("costRows", costRows(player, pool.cost().single(), payload, "costIconJson", balances));
        payload.put("costTenRows", costRows(player, pool.cost().ten(), payload, "costTenIconJson", balances));
        payload.put("tenAvailable", !pool.cost().ten().isEmpty());
        payload.put("resourceRows", resourceRows(player, pool.cost(), payload, balances));
        payload.put("upItemIcon", upItemIcon);
        if (pool.caseConfig() == null) return;
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> itemJsons = new LinkedHashMap<>();
        List<PoolItem> poolItems = pool.caseConfig().items();
        for (int i = 0; i < poolItems.size() && i < CASE_PREVIEW_SLOT_LIMIT; i++) {
            PoolItem item = poolItems.get(i);
            String key = Integer.toString(i + 1);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", previewName(item));
            entry.put("amount", item.amount());
            entry.put("rarity", item.rarity() == null ? "" : item.rarity());
            items.put(key, entry);
            itemJsons.put(key, poolItemJson(player, item));
        }
        payload.put("items", items);
        payload.put("itemJsons", itemJsons);
        payload.put("itemCount", Math.min(poolItems.size(), CASE_PREVIEW_SLOT_LIMIT));
    }

    private Map<String, Object> caseRarityNames() {
        Map<String, Object> names = new LinkedHashMap<>();
        names.put("COMMON", service.message("rarity.common"));
        names.put("UNCOMMON", service.message("rarity.uncommon"));
        names.put("RARE", service.message("rarity.rare"));
        names.put("EPIC", service.message("rarity.epic"));
        names.put("LEGENDARY", service.message("rarity.legendary"));
        names.put("MYTHIC", service.message("rarity.mythic"));
        names.put("SPECIAL", service.message("rarity.special"));
        names.put("UNKNOWN", service.message("rarity.unknown"));
        return names;
    }

    /** 构建消耗条目数据，使用自定义图标前缀，避免循环调用时 key 覆盖。 */
    private Map<String, Object> costRows(Player player, List<CostConfig.CostEntry> entries,
                                        Map<String, Object> payload, String iconPrefix, Balances balances) {
        Map<String, Object> rows = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CostConfig.CostEntry entry = entries.get(i);
            String key = Integer.toString(i);
            Map<String, Object> row = new LinkedHashMap<>();
            var point = entry.type() == CostConfig.CostMode.POINT
                ? pointDefinitions.get(entry.id()) : null;
            row.put("type", entry.type().name());
            row.put("id", entry.id());
            row.put("amount", entry.amount());
            row.put("displayName", displayName(entry, point));
            row.put("owned", balances.owned(entry));
            rows.put(key, row);
            // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
            payload.put(iconPrefix + key, icon(player, entry, point));
        }
        return rows;
    }

    /** 汇总当前奖池涉及的资源余额，供界面资源栏展示。 */
    private Map<String, Object> resourceRows(Player player, CostConfig cost, Map<String, Object> payload, Balances balances) {
        Map<String, Object> rows = new LinkedHashMap<>();
        List<CostConfig.CostEntry> all = new ArrayList<>(cost.single());
        all.addAll(cost.ten());
        int rowIndex = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (CostConfig.CostEntry entry : all) {
            if (!seen.add(entry.type().name() + ":" + entry.id())) continue;
            String key = Integer.toString(rowIndex);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", entry.type().name());
            row.put("id", entry.id());
            row.put("displayName", entry.displayName() == null ? entry.id() : entry.displayName());
            row.put("owned", balances.owned(entry));
            rows.put(key, row);
            // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
            payload.put("resourceIconJson" + key, previewIcons.resolve(player, entry.icon()));
            rowIndex++;
        }
        return rows;
    }

    private String displayName(
            CostConfig.CostEntry entry,
            xuanmo.arcartxsuite.lottery.config.PointDefinition point
    ) {
        if (entry.displayName() != null) return entry.displayName();
        return point == null ? entry.id() : point.displayName();
    }

    private String icon(
            Player player,
            CostConfig.CostEntry entry,
            xuanmo.arcartxsuite.lottery.config.PointDefinition point
    ) {
        IconDefinition icon = entry.icon() == null ? (point == null ? null : point.icon()) : entry.icon();
        return previewIcons.resolve(player, icon);
    }

    private static String previewName(PoolItem item) {
        return item.name() + (item.amount() > 1 ? " x" + item.amount() : "");
    }

    private String poolItemJson(Player player, PoolItem item) {
        return previewIcons.resolve(player, item);
    }

    private String upItemIconJson(Player player, PoolDefinition pool) {
        if (pool.upItemIcon() != null) {
            return previewIcons.resolve(player, pool.upItemIcon());
        }
        PoolItem fallback = poolPreviews.get(pool.id()).fallbackIcon();
        return fallback == null ? "" : poolItemJson(player, fallback);
    }

    private PoolItem fallbackUpItem(PoolDefinition pool) {
        if (pool.type() == PoolType.GACHA) {
            GachaPoolConfig gacha = pool.gacha();
            if (gacha == null) return null;
            if (!gacha.up5starItems().isEmpty()) {
                return gacha.up5starItems().get(0);
            }
            if (!gacha.standard5starItems().isEmpty()) {
                return gacha.standard5starItems().get(0);
            }
            return null;
        }

        CasePoolConfig caseConfig = pool.caseConfig();
        if (caseConfig == null || caseConfig.items().isEmpty()) return null;
        List<String> rarityOrder = new ArrayList<>(caseConfig.raritySettings().keySet());
        for (int i = rarityOrder.size() - 1; i >= 0; i--) {
            String rarity = rarityOrder.get(i);
            for (PoolItem item : caseConfig.items()) {
                if (item.rarity() != null && item.rarity().equalsIgnoreCase(rarity)) {
                    return item;
                }
            }
        }
        return caseConfig.items().get(caseConfig.items().size() - 1);
    }

    private static void collectPoints(List<CostConfig.CostEntry> entries, Set<String> points) {
        for (CostConfig.CostEntry entry : entries) {
            if (entry.type() == CostConfig.CostMode.POINT) points.add(entry.id());
        }
    }

    private final class Balances {
        private final Player player;
        private final Map<String, Long> points;
        private final Map<ItemMatcher, Long> items = new HashMap<>();
        private final Map<String, Long> currencies = new HashMap<>();

        private Balances(Player player, Map<String, Long> points) {
            this.player = player;
            this.points = points;
        }

        long owned(CostConfig.CostEntry entry) {
            return switch (entry.type()) {
                case POINT -> points.getOrDefault(entry.id(), 0L);
                case ITEM -> items.computeIfAbsent(entry.matcher(), matcher -> service.getItemBalance(player, matcher));
                case CURRENCY -> currencies.computeIfAbsent(entry.id(), id -> service.getCurrencyBalance(player, id));
            };
        }
    }

    private void closeOverlay(View view) {
        view.overlayRequest++;
        view.deferredOverlay = null;
        view.exchangeReady = false;
    }

    private void showOverlay(View view, Overlay overlay) {
        if (!isCurrent(view)) return;
        closeOverlay(view);
        if (view.updating) {
            view.deferredOverlay = overlay;
            return;
        }
        long request = view.overlayRequest;
        UUID playerId = view.player.getUniqueId();
        PoolDefinition pool = view.pool;
        BooleanSupplier current = () -> view.overlayRequest == request;
        Runnable failed = () -> service.sendFailureMessage(view.player);
        switch (overlay) {
            case HISTORY -> {
                if (pool.type() == PoolType.GACHA) {
                    query(view, () -> service.loadGachaHistory(playerId, pool.id(), 100), current,
                        result -> publishHistory(view, request, result, List.of()), failed);
                } else {
                    query(view, () -> service.loadCaseHistory(playerId, pool.id(), 20), current,
                        result -> publishHistory(view, request, List.of(), result), failed);
                }
            }
            case EXCHANGE -> query(view, () -> service.loadExchangeView(playerId, exchangePointIds, exchangeIds),
                current, result -> publishExchange(view, request, result), failed);
            case DETAILS -> details(view, request);
        }
    }

    private <T> void query(View view, LotteryViewReads.Query<T> query, BooleanSupplier current,
                           Consumer<T> publish, Runnable failed) {
        reads.submit(view.player.getUniqueId(), query, (result, error) -> {
            if (!current.getAsBoolean() || !isCurrent(view)) return;
            if (error != null) {
                readFailure(view, current, error, failed);
                return;
            }
            try {
                publish.accept(result);
            } catch (RuntimeException failure) {
                readFailure(view, current, failure, failed);
            }
        });
    }

    private void readFailure(View view, BooleanSupplier current, Throwable error, Runnable failed) {
        if (!current.getAsBoolean() || !isCurrent(view)) return;
        service.getLogger().log(java.util.logging.Level.WARNING, "Lottery view read failed", error);
        failed.run();
    }

    /** 下发当前奖池的抽奖或开箱历史记录。 */
    private void publishHistory(View view, long request, List<LotteryRepository.GachaLogEntry> gachaEntries,
                                List<LotteryRepository.CaseLogEntry> caseEntries) {
        Player player = view.player;
        PoolDefinition pool = view.pool;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("historyPoolName", pool.displayName());
        Map<String, HistoryItem> historyItems = poolPreviews.get(pool.id()).historyItems();
        SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        Map<String, Object> rows = new LinkedHashMap<>();
        if (pool.type() == PoolType.GACHA) {
            int index = 0;
            for (LotteryRepository.GachaLogEntry entry : gachaEntries) {
                if (index >= 100) break;
                String timeText = timestamp.format(new Date(entry.pullTime()));
                for (String key : parseItemKeys(entry.itemsJson())) {
                    if (index >= 100) break;
                    HistoryItem match = historyItems.get(key);
                    PoolItem item = match == null ? null : match.item();
                    int star = match == null ? 0 : match.star();
                    String name = item != null ? previewName(item) : key;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("timeText", timeText);
                    row.put("star", star);
                    row.put("summary", starColor(star) + name
                        + (entry.guaranteed() && star >= 5 ? " &6·保底" : ""));
                    rows.put(Integer.toString(index), row);
                    // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
                    payload.put("historyIconJson" + index,
                        item != null ? poolItemJson(player, item) : "");
                    index++;
                }
            }
        } else {
            for (int i = 0; i < caseEntries.size(); i++) {
                LotteryRepository.CaseLogEntry entry = caseEntries.get(i);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("openTime", entry.openTime());
                row.put("itemId", entry.itemId());
                row.put("rarity", entry.rarity());
                HistoryItem match = historyItems.get(entry.itemId());
                PoolItem caseItem = match == null ? null : match.item();
                String name = caseItem == null ? entry.itemId() : previewName(caseItem);
                row.put("itemName", name);
                row.put("timeText", timestamp.format(new Date(entry.openTime())));
                row.put("summary", service.message("packet.history-case", name, entry.rarity()));
                rows.put(Integer.toString(i), row);
                // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
                payload.put("historyIconJson" + i,
                    caseItem != null ? poolItemJson(player, caseItem) : "");
            }
        }
        payload.put("historyCount", rows.size());
        payload.put("historyRows", rows);
        if (isCurrent(view) && view.overlayRequest == request) {
            packetBridge.sendPacket(player, uiIdFor(view.packetId), "history", payload);
        }
    }

    /** 下发当前奖池的概率、保底与条目详情。 */
    private void details(View view, long request) {
        Player player = view.player;
        LotteryPoolDetails plan = poolPreviews.get(view.pool.id()).details();
        Map<String, Object> payload = new LinkedHashMap<>(plan.fields());
        for (int i = 0; i < plan.icons().size(); i++) {
            payload.put("detailIconJson" + i, poolItemJson(player, plan.icons().get(i)));
        }
        if (isCurrent(view) && view.overlayRequest == request) {
            packetBridge.sendPacket(player, uiIdFor(view.packetId), "details", payload);
        }
    }

    private void publishExchange(View view, long request, LotteryRepository.ExchangeView result) {
        Player player = view.player;
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> pointRows = new LinkedHashMap<>();
        int pointIndex = 0;
        for (PointDefinition definition : pointDefinitions.values()) {
            String key = Integer.toString(pointIndex);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("displayName", definition.displayName());
            row.put("amount", result.points().getOrDefault(definition.id(), 0L));
            pointRows.put(key, row);
            // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
            payload.put("pointIconJson" + key, previewIcons.resolve(player, definition.icon()));
            pointIndex++;
        }
        payload.put("pointBalanceRows", pointRows);
        payload.put("pointBalanceCount", pointRows.size());
        Map<String, Object> rows = new LinkedHashMap<>();
        Balances balances = new Balances(player, result.points());
        int rowIndex = 0;
        for (ExchangeShopItem item : service.getExchangeItems().values()) {
            int used = result.counts().getOrDefault(item.id(), 0);
            String key = Integer.toString(rowIndex);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.id());
            row.put("name", item.displayName());
            row.put("costRows", costRows(player, item.cost(), payload, "exchangeCostIconJson" + key + "_", balances));
            row.put("limit", item.limit());
            row.put("used", used);
            row.put("remaining", item.limit() == 0 ? -1 : Math.max(0, item.limit() - used));
            row.put("canExchange", item.limit() == 0 || used < item.limit());
            rows.put(key, row);
            // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
            payload.put("exchangeIconJson" + key, previewIcons.resolve(player, item.icon()));
            rowIndex++;
        }
        payload.put("exchangeCount", rows.size());
        payload.put("exchangeRows", rows);
        if (isCurrent(view) && view.overlayRequest == request
                && packetBridge.sendPacket(player, uiIdFor(view.packetId), "exchange", payload)
                && isCurrent(view) && view.overlayRequest == request) {
            view.exchangeReady = true;
        }
    }

    private void exchangeBuy(View view, List<String> data) {
        if (data.size() < 3 || !view.exchangeReady || !exchangeIds.contains(data.get(1))
                || !consumeToken(view, data.get(2))) return;
        finishOperation(view, service.exchange(view.player, data.get(1)), ignored -> {}, true);
    }

    // ─── 命令入口：打开 UI ─────────────────────────────────

    /**
     * 根据奖池 ID 打开对应类型的抽奖 UI（GACHA 或 CASE）。
     *
     * @param player 玩家
     * @param poolId 奖池 ID
     * @return {@code true} 表示成功打开；奖池不存在、桥接不可用时返回 {@code false}
     */
    public boolean openPoolUi(@NotNull Player player, @NotNull String poolId) {
        return openPoolUi(player, poolId, false);
    }

    /**
     * 打开指定奖池的 UI。
     *
     * @param player   玩家
     * @param poolId   奖池 ID
     * @param useWish  是否使用 Wish 界面（仅对 GACHA 类型奖池有效）
     * @return {@code true} 表示成功打开；奖池不存在、桥接不可用时返回 {@code false}
     */
    public boolean openPoolUi(@NotNull Player player, @NotNull String poolId, boolean useWish) {
        PoolDefinition pool = service.getPool(poolId);
        if (pool == null || !pool.enabled()) return false;
        AuthSnapshot auth = authenticated(player);
        if (auth == null || !packetBridge.isAvailable()) return false;
        boolean gachaType = pool.type() == PoolType.GACHA;
        String packetId = gachaType ? (useWish ? WISH : GACHA) : CASE;
        String uiId = uiIdFor(packetId);
        List<PoolDefinition> poolList = pools(packetId);
        int index = -1;
        for (int i = 0; i < poolList.size(); i++) {
            if (poolList.get(i).id().equals(poolId)) { index = i; break; }
        }
        if (index < 0) return false;
        if (!packetBridge.openUi(player, uiId)) return false;
        View view = new View(player, auth, packetId, pool, index);
        views.put(player.getUniqueId(), view);
        update(view, index);
        return true;
    }

    /**
     * 打开指定奖池的 UI 并自动跳转到详情页面。
     *
     * @param player 玩家
     * @param poolId 奖池 ID
     * @return {@code true} 表示成功
     */
    public boolean openPoolDetails(@NotNull Player player, @NotNull String poolId) {
        if (!openPoolUi(player, poolId)) return false;
        showOverlay(views.get(player.getUniqueId()), Overlay.DETAILS);
        return true;
    }

    /**
     * 打开指定奖池的 UI 并自动跳转到历史记录页面。
     *
     * @param player 玩家
     * @param poolId 奖池 ID
     * @return {@code true} 表示成功
     */
    public boolean openPoolHistory(@NotNull Player player, @NotNull String poolId) {
        if (!openPoolUi(player, poolId)) return false;
        showOverlay(views.get(player.getUniqueId()), Overlay.HISTORY);
        return true;
    }

    public void onPlayerQuit(@NotNull UUID playerUuid) {
        gachaIndex.remove(playerUuid);
        caseIndex.remove(playerUuid);
        views.remove(playerUuid);
        reads.discardReplacement(playerUuid);
        inFlight.remove(playerUuid);
    }

    public void shutdown() {
        stopped = true;
        reads.stop();
        for (String uiId : watchedUis) packetBridge.unregisterUiCloseCallback(uiId);
        watchedUis.clear();
        views.clear();
        gachaIndex.clear();
        caseIndex.clear();
        inFlight.clear();
    }

    public java.util.concurrent.CompletionStage<Void> stopped() {
        return reads.stopped();
    }

    private void watchUi(String uiId) {
        if (watchedUis.contains(uiId)) return;
        if (packetBridge.registerUiCloseCallback(uiId, player -> {
            UUID playerId = player.getUniqueId();
            View view = views.get(playerId);
            if (view != null && view.player == player && uiId.equals(uiIdFor(view.packetId))
                    && !packetBridge.isUiOpen(player, uiId)) {
                views.remove(playerId);
                reads.discardReplacement(playerId);
            }
        })) watchedUis.add(uiId);
    }

    private AuthSnapshot authenticated(Player player) {
        if (stopped || !player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
                || !player.hasPermission("arcartxsuite.lottery.use")) return null;
        AuthSnapshot auth = AuthAccess.snapshot(player.getUniqueId());
        return auth.admitted() ? auth : null;
    }

    private static boolean sameConnection(View view, Player player, AuthSnapshot auth) {
        return view.player == player && view.auth.generation() == auth.generation()
            && Objects.equals(view.auth.connectionId(), auth.connectionId());
    }

    private boolean isCurrent(View view) {
        if (view == null || views.get(view.player.getUniqueId()) != view) return false;
        AuthSnapshot auth = authenticated(view.player);
        return auth != null && sameConnection(view, view.player, auth)
            && packetBridge.isUiOpen(view.player, uiIdFor(view.packetId));
    }

    private View viewForPacket(Player player, String packetId) {
        AuthSnapshot auth = authenticated(player);
        if (auth == null || !packetBridge.isUiOpen(player, uiIdFor(packetId))) return null;
        UUID playerId = player.getUniqueId();
        View view = views.get(playerId);
        if (view != null && sameConnection(view, player, auth)
                && packetBridge.isUiOpen(player, uiIdFor(view.packetId))) {
            return view.packetId.equals(packetId) ? view : null;
        }
        List<PoolDefinition> available = pools(packetId);
        if (available.isEmpty()) return null;
        int index = Math.max(0, Math.min(indexMap(packetId).getOrDefault(playerId, 0), available.size() - 1));
        view = new View(player, auth, packetId, available.get(index), index);
        views.put(playerId, view);
        reads.discardReplacement(playerId);
        return view;
    }

    /** 解析日志中 itemsJson（形如 [key1,key2,...]）为奖品 key 列表。 */
    private List<String> parseItemKeys(String itemsJson) {
        String body = itemsJson == null ? "" : itemsJson.trim();
        if (body.startsWith("[")) body = body.substring(1);
        if (body.endsWith("]")) body = body.substring(0, body.length() - 1);
        List<String> keys = new ArrayList<>();
        for (String part : body.split(",")) {
            String key = part.trim();
            if (!key.isEmpty() && !"null".equals(key)) keys.add(key);
        }
        return keys;
    }

    private PoolPreview poolPreview(PoolDefinition pool) {
        Set<String> points = new HashSet<>();
        collectPoints(pool.cost().single(), points);
        collectPoints(pool.cost().ten(), points);

        Map<String, HistoryItem> history = new HashMap<>();
        GachaPoolConfig gacha = pool.gacha();
        if (gacha != null) {
            indexHistory(history, gacha.up5starItems(), 5);
            indexHistory(history, gacha.standard5starItems(), 5);
            indexHistory(history, gacha.up4starItems(), 4);
            indexHistory(history, gacha.standard4starItems(), 4);
            indexHistory(history, gacha.star3Items(), 3);
        } else if (pool.caseConfig() != null) {
            indexHistory(history, pool.caseConfig().items(), 0);
        }
        PoolItem featured = firstUp(gacha);
        return new PoolPreview(descriptionLines(pool.description()), Set.copyOf(points),
            pool.upItemIcon() == null ? fallbackUpItem(pool) : null,
            featured == null ? "" : featured.name(), featured == null ? 0 : history.get(featured.key()).star(),
            Collections.unmodifiableMap(history), LotteryPoolDetails.compile(pool, LotteryPacketHandler::previewName));
    }

    private static void indexHistory(Map<String, HistoryItem> history, List<PoolItem> items, int star) {
        for (PoolItem item : items) {
            history.putIfAbsent(item.key(), new HistoryItem(item, star));
        }
    }

    private String starColor(int star) {
        return switch (star) {
            case 5 -> "&6";
            case 4 -> "&5";
            case 3 -> "&b";
            default -> "&f";
        };
    }
    /** 取奖池中优先展示的 UP 物品。 */
    private PoolItem firstUp(GachaPoolConfig gacha) {
        if (gacha == null) return null;
        if (!gacha.up5starItems().isEmpty()) {
            return gacha.up5starItems().get(0);
        }
        if (!gacha.up4starItems().isEmpty()) {
            return gacha.up4starItems().get(0);
        }
        return null;
    }

    /** 读取数据包中的整数参数，缺失或非法时返回默认值。 */
    private int parseInt(List<String> data, int index, int fallback) {
        if (data.size() <= index) return fallback;
        String s = data.get(index);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {}
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 判断包 ID 是否为祈愿类型（GACHA 或 WISH）。 */
    private boolean isGachaType(String packetId) {
        return packetId.equals(GACHA) || packetId.equals(WISH);
    }

    /** 根据包 ID 返回对应的目标 UI ID。 */
    private String uiIdFor(String packetId) {
        if (packetId.equals(WISH)) return wishUiId;
        if (packetId.equals(GACHA)) return gachaUiId;
        return caseUiId;
    }

    /** 按数据包 ID 取出对应类型且已启用的奖池列表。 */
    private List<PoolDefinition> pools(String packetId) {
        return isGachaType(packetId) ? gachaPools : casePools;
    }

    /** 构建以数字字符串为 key 的奖池名称列表。 */
    private Map<String, Object> poolList(List<PoolDefinition> pools) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pools.size(); i++) {
            result.put(Integer.toString(i), pools.get(i).displayName());
        }
        return Collections.unmodifiableMap(result);
    }

    /** 构建以数字字符串为 key 的奖池 UP 物品图标 JSON 列表，供卡池按钮 Slot 显示。 */
    private Map<String, String> poolUpItemIcons(Player player, List<PoolDefinition> pools) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pools.size(); i++) {
            result.put(Integer.toString(i), upItemIconJson(player, pools.get(i)));
        }
        return result;
    }

    /** 按 gacha 与 case 分别取索引缓存（WISH 共享 gachaIndex）。 */
    private Map<UUID, Integer> indexMap(String packetId) {
        return isGachaType(packetId) ? gachaIndex : caseIndex;
    }

    /** 校验 token 并占用本次操作，防止并发重复请求。 */
    private boolean consumeToken(View view, String supplied) {
        if (view.token == null || !view.token.equals(supplied) || !isCurrent(view)
                || inFlight.putIfAbsent(view.player.getUniqueId(), view) != null) return false;
        view.token = null;
        return true;
    }

}
