package xuanmo.arcartxsuite.lottery.packet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.util.AxsScheduler;
import xuanmo.arcartxsuite.lottery.LotteryService;
import xuanmo.arcartxsuite.lottery.config.CostConfig;
import xuanmo.arcartxsuite.lottery.config.CasePoolConfig;
import xuanmo.arcartxsuite.lottery.config.ExchangeShopItem;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.config.PoolDefinition;
import xuanmo.arcartxsuite.lottery.config.PoolType;
import xuanmo.arcartxsuite.lottery.model.CaseResult;
import xuanmo.arcartxsuite.lottery.model.GachaResult;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.model.PoolItem;
import xuanmo.arcartxsuite.lottery.storage.LotteryRepository;

/** Lottery 客户端数据包处理器。 */
public final class LotteryPacketHandler implements ClientPacketHandler {
    private static final int CASE_PREVIEW_SLOT_LIMIT = 45;
    private static final String GACHA = "AXS_LOTTERY_GACHA";
    private static final String WISH = "AXS_LOTTERY_WISH";
    private static final String CASE = "AXS_LOTTERY_CASE";
    private final LotteryService service;
    private final PacketBridgeAPI packetBridge;
    private final IconResolver iconResolver;
    private final JavaPlugin plugin;
    private final String gachaUiId;
    private final String wishUiId;
    private final String caseUiId;
    private final ConcurrentMap<UUID, Integer> gachaIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> caseIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> tokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Object> tokenLocks = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public LotteryPacketHandler(@NotNull LotteryService service, @NotNull PacketBridgeAPI packetBridge,
                                @NotNull IconResolver iconResolver, @NotNull JavaPlugin plugin,
                                @NotNull String gachaUiId, @NotNull String wishUiId,
                                @NotNull String caseUiId) {
        this.service = service;
        this.packetBridge = packetBridge;
        this.iconResolver = iconResolver;
        this.plugin = plugin;
        this.gachaUiId = gachaUiId;
        this.wishUiId = wishUiId;
        this.caseUiId = caseUiId;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId,
                                      @NotNull List<String> data) {
        if (!GACHA.equalsIgnoreCase(packetId) && !WISH.equalsIgnoreCase(packetId) && !CASE.equalsIgnoreCase(packetId)) return false;
        Runnable operation = () -> handle(player, packetId.toUpperCase(Locale.ROOT), data);
        if (Bukkit.isPrimaryThread()) operation.run(); else AxsScheduler.runTask(plugin, operation);
        return true;
    }

    private void handle(Player player, String packetId, List<String> data) {
        String action = data.isEmpty() ? "refresh" : data.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "switch_pool" -> switchPool(player, packetId, data);
            case "pull" -> pull(player, packetId, data);
            case "open_case" -> openCase(player, data, false);
            case "open_case_batch" -> openCase(player, data, true);
            case "history" -> history(player, packetId);
            case "details" -> details(player, packetId);
            case "exchange" -> exchange(player, packetId);
            case "exchange_buy" -> exchangeBuy(player, data);
            default -> update(player, packetId, currentIndex(player, packetId));
        }
    }

    private void switchPool(Player player, String packetId, List<String> data) {
        int index = parseInt(data, 1, 0);
        List<PoolDefinition> pools = pools(packetId);
        if (pools.isEmpty()) return;
        index = Math.max(0, Math.min(index, pools.size() - 1));
        indexMap(packetId).put(player.getUniqueId(), index);
        update(player, packetId, index);
    }

    private void pull(Player player, String packetId, List<String> data) {
        if (data.size() < 3 || !checkToken(player, data, 2)) return;
        try {
            int count = parseInt(data, 1, 1);
            if (count != 1 && count != 10) count = 1;
            PoolDefinition pool = currentPool(player, packetId);
            if (pool == null) return;
            GachaResult result = service.pullGacha(player, pool.id(), count);
            if (result != null && !result.items().isEmpty()) {
                sendGachaResult(player, packetId, pool, result, count);
            }
        } catch (RuntimeException e) {
            service.getLogger().log(java.util.logging.Level.WARNING, "Lottery pull failed", e);
            service.sendFailureMessage(player);
        } finally {
            inFlight.remove(player.getUniqueId());
            update(player, packetId, currentIndex(player, packetId));
        }
    }

    private void openCase(Player player, List<String> data, boolean batch) {
        int tokenIndex = batch ? 2 : 1;
        if (data.size() <= tokenIndex || !checkToken(player, data, tokenIndex)) return;
        try {
            int count = 1;
            if (batch) {
                count = parseInt(data, 1, 10);
                if (count != 10) return;
            }
            PoolDefinition pool = currentPool(player, CASE);
            if (pool == null) return;
            if (batch) {
                List<CaseResult> results = service.openCaseBatch(player, pool.id(), count);
                if (results != null && !results.isEmpty()) sendCaseBatch(player, results, count);
            } else {
                CaseResult result = service.openCase(player, pool.id());
                if (result != null && result.item() != null) sendCaseResult(player, result);
            }
        } catch (RuntimeException e) {
            service.getLogger().log(java.util.logging.Level.WARNING, "Lottery case opening failed", e);
            service.sendFailureMessage(player);
        } finally {
            inFlight.remove(player.getUniqueId());
            update(player, CASE, currentIndex(player, CASE));
        }
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
            names.put(key, item.name());
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

    private void sendCaseResult(Player player, CaseResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        PoolItem item = result == null ? null : result.item();
        payload.put("resultCount", item == null ? 0 : 1);
        // itemJson 分离模式（§7）：放在 packet 顶层字段，UI 端遍历构建 var.resultItemJsons
        payload.put("resultItemJson1", item == null ? "" : poolItemJson(player, item));
        payload.put("resultItemName", item == null ? "" : item.name());
        payload.put("resultRarity", result == null ? "" : result.rarity());
        packetBridge.sendPacket(player, caseUiId, "result", payload);
    }

    private void sendCaseBatch(Player player, List<CaseResult> results, int count) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> names = new LinkedHashMap<>();
        Map<String, Object> rarities = new LinkedHashMap<>();
        for (int i = 0; i < results.size(); i++) {
            CaseResult result = results.get(i);
            PoolItem item = result == null ? null : result.item();
            String key = Integer.toString(i + 1);
            // itemJson 分离模式（§7）：放在 packet 顶层字段，UI 端遍历构建 var.batchItemJsons
            payload.put("batchItemJson" + key, item == null ? "" : poolItemJson(player, item));
            names.put(key, item == null ? "" : item.name());
            rarities.put(key, result == null ? "" : result.rarity());
        }
        payload.put("batchCount", results.size());
        payload.put("batchRequested", count);
        payload.put("batchItemNames", names);
        payload.put("batchItemRarities", rarities);
        packetBridge.sendPacket(player, caseUiId, "batch_result", payload);
    }

    private void update(Player player, String packetId, int index) {
        List<PoolDefinition> pools = pools(packetId);
        if (pools.isEmpty() || !packetBridge.isAvailable()) return;
        index = Math.max(0, Math.min(index, pools.size() - 1));
        PoolDefinition pool = pools.get(index);
        String token = UUID.randomUUID().toString();
        tokens.put(player.getUniqueId(), token);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", packetId);
        payload.put("poolName", pool.displayName());
        payload.put("poolType", pool.type().name());
        payload.put("poolDescription", descriptionLines(pool.description()));
        payload.put("poolList", poolList(pools));
        payload.put("poolUpItemIcons", poolUpItemIcons(player, pools));
        payload.put("currentPoolIndex", index);
        payload.put("poolCount", pools.size());
        payload.put("action_token", token);
        if (pool.type() == PoolType.GACHA) updateGacha(player, pool, payload);
        else updateCase(player, pool, payload);
        packetBridge.sendPacket(player, uiIdFor(packetId), "update", payload);
    }

    private void updateGacha(Player player, PoolDefinition pool, Map<String, Object> payload) {
        GachaPoolConfig gacha = pool.gacha();
        PoolItem up = firstUp(gacha);
        payload.put("upItemName", up == null ? "" : up.name());
        payload.put("upItemStar", up == null ? 0 : service.describeGachaItem(pool, up).star());
        payload.put("upItemDesc", pool.upItemDesc());
        payload.put("upItemIcon", upItemIconJson(player, pool));
        PlayerGachaState state = service.getGachaState(player, pool.id());
        payload.put("pity4", state.pity4star());
        payload.put("pity5", state.pity5star());
        payload.put("pity4Limit", gacha.pity4star());
        payload.put("pity5Limit", gacha.pity5star());
        payload.put("guaranteedUp", state.guaranteedUp());
        String guaranteedUpText = service.message("packet.guaranteed-up");
        String guaranteedNotUpText = service.message("packet.guaranteed-not-up");
        payload.put("guaranteedUpText", guaranteedUpText);
        payload.put("guaranteedNotUpText", guaranteedNotUpText);
        payload.put("guaranteedUnknownText", service.message("packet.guaranteed-unknown"));
        payload.put("guaranteedStatusText", state.guaranteedUp() ? guaranteedUpText : guaranteedNotUpText);
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
        payload.put("costRows", costRows(player, pool.cost(), false, payload));
        payload.put("costTenRows", costRows(player, pool.cost(), true, payload));
        payload.put("tenAvailable", !pool.cost().ten().isEmpty());
        payload.put("resourceRows", resourceRows(player, pool.cost(), payload));
    }

    private List<String> descriptionLines(String description) {
        if (description == null || description.isEmpty()) return List.of();
        String normalized = description.replace("\\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\\\n|\\n", -1);
        List<String> colored = new ArrayList<>(lines.length);
        for (String line : lines) {
            if (!line.isBlank()) colored.add("&7" + line);
        }
        return colored;
    }

    private void updateCase(Player player, PoolDefinition pool, Map<String, Object> payload) {
        payload.put("rarityNames", caseRarityNames());
        payload.put("requiresKey", pool.requiresKey());
        payload.put("costRows", costRows(player, pool.cost(), false, payload));
        payload.put("costTenRows", costRows(player, pool.cost(), true, payload));
        payload.put("tenAvailable", !pool.cost().ten().isEmpty());
        payload.put("resourceRows", resourceRows(player, pool.cost(), payload));
        payload.put("upItemIcon", upItemIconJson(player, pool));
        if (pool.caseConfig() == null) return;
        Map<String, Object> names = new LinkedHashMap<>();
        Map<String, Object> rarities = new LinkedHashMap<>();
        List<PoolItem> items = pool.caseConfig().items();
        for (int i = 0; i < items.size() && i < CASE_PREVIEW_SLOT_LIMIT; i++) {
            PoolItem item = items.get(i);
            String key = Integer.toString(i + 1);
            // itemJson 分离模式（§7）：放在 packet 顶层字段，UI 端遍历构建 var.itemJsons
            payload.put("itemJson" + key, poolItemJson(player, item));
            names.put(key, item.name());
            rarities.put(key, item.rarity() == null ? "" : item.rarity());
        }
        payload.put("itemNames", names);
        payload.put("itemRarities", rarities);
        payload.put("itemCount", Math.min(items.size(), CASE_PREVIEW_SLOT_LIMIT));
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

    /** 构建单抽或十连的消耗条目数据，供界面展示。 */
    private Map<String, Object> costRows(Player player, CostConfig cost, boolean ten, Map<String, Object> payload) {
        return costRows(player, cost, ten, payload, ten ? "costTenIconJson" : "costIconJson");
    }

    /** 构建消耗条目数据，使用自定义图标前缀，避免循环调用时 key 覆盖。 */
    private Map<String, Object> costRows(Player player, CostConfig cost, boolean ten, Map<String, Object> payload, String iconPrefix) {
        Map<String, Object> rows = new LinkedHashMap<>();
        List<CostConfig.CostEntry> entries = ten ? cost.ten() : cost.single();
        for (int i = 0; i < entries.size(); i++) {
            CostConfig.CostEntry entry = entries.get(i);
            String key = Integer.toString(i);
            Map<String, Object> row = new LinkedHashMap<>();
            var point = entry.type() == CostConfig.CostMode.POINT
                ? pointDefinition(entry.id()) : null;
            row.put("type", entry.type().name());
            row.put("id", entry.id());
            row.put("amount", entry.amount());
            row.put("displayName", displayName(entry, point));
            row.put("owned", owned(player, entry));
            rows.put(key, row);
            // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
            payload.put(iconPrefix + key, icon(player, entry, point));
        }
        return rows;
    }

    /** 汇总当前奖池涉及的资源余额，供界面资源栏展示。 */
    private Map<String, Object> resourceRows(Player player, CostConfig cost, Map<String, Object> payload) {
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
            row.put("owned", owned(player, entry));
            rows.put(key, row);
            // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
            payload.put("resourceIconJson" + key, iconResolver.resolveItemJson(player, entry.icon()));
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
        return iconResolver.resolveItemJson(player, icon);
    }

    private String poolItemJson(Player player, PoolItem item) {
        if (item.icon() != null) return iconResolver.resolveItemJson(player, item.icon());
        return iconResolver.itemToJson(service.generateItem(item, player));
    }

    private String upItemIconJson(Player player, PoolDefinition pool) {
        if (pool.upItemIcon() != null) {
            return iconResolver.resolveItemJson(player, pool.upItemIcon());
        }
        PoolItem fallback = fallbackUpItem(pool);
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

    private xuanmo.arcartxsuite.lottery.config.PointDefinition pointDefinition(String id) {
        for (PoolDefinition pool : service.getPools().values()) {
            var definition = pool.points().get(id);
            if (definition != null) return definition;
        }
        return null;
    }

    private long owned(Player player, CostConfig.CostEntry entry) {
        return switch (entry.type()) {
            case POINT -> service.getPoint(player.getUniqueId(), entry.id());
            case ITEM -> service.getItemBalance(player, entry.matcher());
            case CURRENCY -> service.getCurrencyBalance(player, entry.id());
        };
    }

    /** 下发当前奖池的抽奖或开箱历史记录。 */
    private void history(Player player, String packetId) {
        PoolDefinition pool = currentPool(player, packetId);
        if (pool == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("historyPoolName", pool.displayName());
        Map<String, Object> rows = new LinkedHashMap<>();
        if (pool.type() == PoolType.GACHA) {
            List<LotteryRepository.GachaLogEntry> entries =
                service.getGachaHistory(player.getUniqueId(), pool.id(), 100);
            Map<String, PoolItem> itemMap = gachaItemMap(pool);
            int index = 0;
            for (LotteryRepository.GachaLogEntry entry : entries) {
                if (index >= 100) break;
                String timeText = time(entry.pullTime());
                for (String key : parseItemKeys(entry.itemsJson())) {
                    if (index >= 100) break;
                    PoolItem item = itemMap.get(key);
                    int star = gachaStarOf(pool, key);
                    String name = item != null ? item.name() : key;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("timeText", timeText);
                    row.put("star", star);
                    row.put("summary", starColor(star) + name
                        + (entry.guaranteed() && star >= 5 ? " &6·保底" : ""));
                    rows.put(Integer.toString(index++), row);
                }
            }
        } else {
            List<LotteryRepository.CaseLogEntry> entries =
                service.getCaseHistory(player.getUniqueId(), pool.id(), 20);
            for (int i = 0; i < entries.size(); i++) {
                LotteryRepository.CaseLogEntry entry = entries.get(i);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("openTime", entry.openTime());
                row.put("itemId", entry.itemId());
                row.put("rarity", entry.rarity());
                row.put("timeText", time(entry.openTime()));
                row.put("summary", service.message("packet.history-case",
                    itemName(pool, entry.itemId()), entry.rarity()));
                rows.put(Integer.toString(i), row);
            }
        }
        payload.put("historyCount", rows.size());
        payload.put("historyRows", rows);
        packetBridge.sendPacket(player, uiIdFor(packetId),
            "history", payload);
    }

    /** 下发当前奖池的概率、保底与条目详情。 */
    private void details(Player player, String packetId) {
        PoolDefinition pool = currentPool(player, packetId);
        if (pool == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("detailPoolName", pool.displayName());
        Map<String, Object> rows = new LinkedHashMap<>();
        List<PoolItem> items = new ArrayList<>();
        if (pool.type() == PoolType.CASE && pool.caseConfig() != null) {
            items.addAll(pool.caseConfig().items());
        }
        if (pool.type() == PoolType.GACHA && pool.gacha() != null) {
            GachaPoolConfig gacha = pool.gacha();
            items.addAll(gacha.up5starItems());
            items.addAll(gacha.standard5starItems());
            items.addAll(gacha.up4starItems());
            items.addAll(gacha.standard4starItems());
            items.addAll(gacha.star3Items());
            payload.put("detailPity5", gacha.pity5star());
            payload.put("detailPity4", gacha.pity4star());
            payload.put("detailBase5Rate", gacha.base5starRate());
            payload.put("detailBase4Rate", gacha.base4starRate());
            payload.put("detailUpRate", gacha.upRate());
        }
        for (int i = 0; i < items.size(); i++) {
            PoolItem item = items.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", item.name());
            row.put("rarity", item.rarity() == null ? "" : item.rarity());
            row.put("weight", item.weight());
            rows.put(Integer.toString(i), row);
        }
        payload.put("detailCount", rows.size());
        payload.put("detailRows", rows);
        packetBridge.sendPacket(player, uiIdFor(packetId),
            "details", payload);
    }

    private void exchange(Player player, String packetId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> pointRows = new LinkedHashMap<>();
        java.util.Set<String> pointIds = new java.util.HashSet<>();
        int pointIndex = 0;
        for (PoolDefinition pool : service.getPools().values()) {
            for (var definition : pool.points().values()) {
                if (!pointIds.add(definition.id())) continue;
                String key = Integer.toString(pointIndex);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", definition.id());
                row.put("displayName", definition.displayName());
                row.put("amount", service.getPoint(player.getUniqueId(), definition.id()));
                pointRows.put(key, row);
                // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
                payload.put("pointIconJson" + key, iconResolver.resolveItemJson(player, definition.icon()));
                pointIndex++;
            }
        }
        payload.put("pointBalanceRows", pointRows);
        payload.put("pointBalanceCount", pointRows.size());
        Map<String, Object> rows = new LinkedHashMap<>();
        int rowIndex = 0;
        for (ExchangeShopItem item : service.getExchangeItems().values()) {
            int used = service.getExchangeCount(player.getUniqueId(), item.id());
            String key = Integer.toString(rowIndex);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.id());
            row.put("name", item.displayName());
            row.put("costRows", costRows(player, new CostConfig(item.cost(), item.cost()), false, payload, "exchangeCostIconJson" + key + "_"));
            row.put("limit", item.limit());
            row.put("used", used);
            row.put("remaining", item.limit() == 0 ? -1 : Math.max(0, item.limit() - used));
            row.put("canExchange", item.limit() == 0 || used < item.limit());
            rows.put(key, row);
            // itemJson 分离模式（§7）：图标 JSON 放在 packet 顶层字段
            payload.put("exchangeIconJson" + key, iconResolver.resolveItemJson(player, item.icon()));
            rowIndex++;
        }
        payload.put("exchangeCount", rows.size());
        payload.put("exchangeRows", rows);
        packetBridge.sendPacket(player, uiIdFor(packetId), "exchange", payload);
    }

    private void exchangeBuy(Player player, List<String> data) {
        if (data.size() < 3 || !checkToken(player, data, 2)) return;
        try {
            service.exchange(player, data.get(1));
        } finally {
            inFlight.remove(player.getUniqueId());
            update(player, GACHA, currentIndex(player, GACHA));
        }
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
        if (!packetBridge.isAvailable()) return false;
        boolean gachaType = pool.type() == PoolType.GACHA;
        String packetId = gachaType ? (useWish ? WISH : GACHA) : CASE;
        String uiId = uiIdFor(packetId);
        List<PoolDefinition> poolList = pools(packetId);
        int index = -1;
        for (int i = 0; i < poolList.size(); i++) {
            if (poolList.get(i).id().equals(poolId)) { index = i; break; }
        }
        if (index < 0) return false;
        indexMap(packetId).put(player.getUniqueId(), index);
        if (!packetBridge.openUi(player, uiId)) return false;
        update(player, packetId, index);
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
        PoolDefinition pool = service.getPool(poolId);
        if (pool == null) return false;
        String packetId = pool.type() == PoolType.GACHA ? GACHA : CASE;
        details(player, packetId);
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
        PoolDefinition pool = service.getPool(poolId);
        if (pool == null) return false;
        String packetId = pool.type() == PoolType.GACHA ? GACHA : CASE;
        history(player, packetId);
        return true;
    }

    public void onPlayerQuit(@NotNull UUID playerUuid) {
        gachaIndex.remove(playerUuid);
        caseIndex.remove(playerUuid);
        tokens.remove(playerUuid);
        tokenLocks.remove(playerUuid);
        inFlight.remove(playerUuid);
    }

    private String time(long timestamp) { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(timestamp)); }
    private String itemName(PoolDefinition pool, String itemId) {
        if (pool.caseConfig() != null) {
            for (PoolItem item : pool.caseConfig().items()) {
                if (item.key().equals(itemId)) return item.name();
            }
        }
        return itemId;
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

    /** 建立 GACHA 奖池的 key → PoolItem 映射。 */
    private Map<String, PoolItem> gachaItemMap(PoolDefinition pool) {
        Map<String, PoolItem> map = new LinkedHashMap<>();
        GachaPoolConfig gacha = pool.gacha();
        if (gacha == null) return map;
        for (List<PoolItem> items : List.of(gacha.up5starItems(), gacha.standard5starItems(),
            gacha.up4starItems(), gacha.standard4starItems(), gacha.star3Items())) {
            for (PoolItem item : items) {
                map.putIfAbsent(item.key(), item);
            }
        }
        return map;
    }

    /** 查询奖品 key 在 GACHA 奖池中的星级，未命中返回 0。 */
    private int gachaStarOf(PoolDefinition pool, String key) {
        GachaPoolConfig gacha = pool.gacha();
        if (gacha == null || key == null) return 0;
        if (containsItemKey(gacha.up5starItems(), key) || containsItemKey(gacha.standard5starItems(), key)) return 5;
        if (containsItemKey(gacha.up4starItems(), key) || containsItemKey(gacha.standard4starItems(), key)) return 4;
        if (containsItemKey(gacha.star3Items(), key)) return 3;
        return 0;
    }

    private boolean containsItemKey(List<PoolItem> items, String key) {
        for (PoolItem item : items) {
            if (item.key().equals(key)) return true;
        }
        return false;
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
        PoolType type = isGachaType(packetId) ? PoolType.GACHA : PoolType.CASE;
        return service.getPools().values().stream()
            .filter(pool -> pool.enabled() && pool.type() == type)
            .toList();
    }

    /** 构建以数字字符串为 key 的奖池名称列表。 */
    private Map<String, Object> poolList(List<PoolDefinition> pools) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pools.size(); i++) {
            result.put(Integer.toString(i), pools.get(i).displayName());
        }
        return result;
    }

    /** 构建以数字字符串为 key 的奖池 UP 物品图标 JSON 列表，供卡池按钮 Slot 显示。 */
    private Map<String, Object> poolUpItemIcons(Player player, List<PoolDefinition> pools) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pools.size(); i++) {
            result.put(Integer.toString(i), upItemIconJson(player, pools.get(i)));
        }
        return result;
    }

    /** 取玩家当前选中的奖池，索引越界时自动收敛。 */
    private PoolDefinition currentPool(Player player, String packetId) {
        List<PoolDefinition> available = pools(packetId);
        if (available.isEmpty()) return null;
        int index = Math.max(0, Math.min(
            currentIndex(player, packetId), available.size() - 1));
        return available.get(index);
    }

    /** 取玩家当前选中的奖池索引。 */
    private int currentIndex(Player player, String packetId) {
        return indexMap(packetId).getOrDefault(player.getUniqueId(), 0);
    }

    /** 按 gacha 与 case 分别取索引缓存（WISH 共享 gachaIndex）。 */
    private ConcurrentMap<UUID, Integer> indexMap(String packetId) {
        return isGachaType(packetId) ? gachaIndex : caseIndex;
    }

    /** 校验 token 并占用本次操作，防止并发重复请求。 */
    private boolean checkToken(Player player, List<String> data, int tokenIndex) {
        UUID playerUuid = player.getUniqueId();
        String supplied = data.size() > tokenIndex ? data.get(tokenIndex) : "";
        Object lock = tokenLocks.computeIfAbsent(playerUuid, ignored -> new Object());
        synchronized (lock) {
            String expected = tokens.get(playerUuid);
            return expected != null
                && expected.equals(supplied)
                && inFlight.add(playerUuid);
        }
    }

}
