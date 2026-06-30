package xuanmo.arcartxsuite.lottery.packet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.lottery.LotteryService;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.config.PoolDefinition;
import xuanmo.arcartxsuite.lottery.config.PoolType;
import xuanmo.arcartxsuite.lottery.model.GachaResult;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;

/**
 * Lottery 模块客户端包处理器。
 * 处理祈愿/开箱界面的客户端交互请求。
 */
public class LotteryPacketHandler implements ClientPacketHandler {

    private static final String GACHA_PACKET_ID = "AXS_LOTTERY_GACHA";
    private static final String CASE_PACKET_ID = "AXS_LOTTERY_CASE";

    private static final String GACHA_UI_FILE = "ui/lottery_gacha.yml";
    private static final String CASE_UI_FILE = "ui/lottery_case.yml";

    private final LotteryService service;
    private final PacketBridgeAPI packetBridge;
    private final Map<UUID, Integer> playerPoolIndex = new ConcurrentHashMap<>();

    public LotteryPacketHandler(@NotNull LotteryService service, @NotNull PacketBridgeAPI packetBridge) {
        this.service = service;
        this.packetBridge = packetBridge;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!GACHA_PACKET_ID.equalsIgnoreCase(packetId) && !CASE_PACKET_ID.equalsIgnoreCase(packetId)) {
            return false;
        }

        String action = data.isEmpty() ? "refresh" : data.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "switch_pool" -> handleSwitchPool(player, data);
            case "pull" -> handlePull(player, data);
            case "open_case" -> handleOpenCase(player, data);
            case "refresh" -> pushGachaData(player, getCurrentIndex(player));
            default -> pushGachaData(player, getCurrentIndex(player));
        }
        return true;
    }

    private void handleSwitchPool(Player player, List<String> data) {
        int index = 0;
        if (data.size() > 1) {
            try {
                index = Integer.parseInt(data.get(1));
            } catch (NumberFormatException ignored) {}
        }
        playerPoolIndex.put(player.getUniqueId(), index);
        pushGachaData(player, index);
    }

    private void handlePull(Player player, List<String> data) {
        int count = 1;
        if (data.size() > 1) {
            try {
                count = Integer.parseInt(data.get(1));
            } catch (NumberFormatException ignored) {}
        }
        if (count != 1 && count != 10) {
            count = 1;
        }

        PoolDefinition pool = getPoolAtIndex(player, getCurrentIndex(player));
        if (pool == null || pool.type() != PoolType.GACHA || pool.gacha() == null) {
            pushGachaData(player, getCurrentIndex(player));
            return;
        }

        try {
            GachaResult result = service.pullGacha(player, pool.id(), count);
            pushGachaData(player, getCurrentIndex(player));
        } catch (Exception e) {
            pushGachaData(player, getCurrentIndex(player));
        }
    }

    private void handleOpenCase(Player player, List<String> data) {
        PoolDefinition pool = getPoolAtIndex(player, getCurrentIndex(player));
        if (pool == null || pool.type() != PoolType.CASE || pool.caseConfig() == null) {
            pushGachaData(player, getCurrentIndex(player));
            return;
        }

        try {
            service.openCase(player, pool.id());
            pushGachaData(player, getCurrentIndex(player));
        } catch (Exception e) {
            pushGachaData(player, getCurrentIndex(player));
        }
    }

    private int getCurrentIndex(Player player) {
        return playerPoolIndex.getOrDefault(player.getUniqueId(), 0);
    }

    private List<PoolDefinition> getOrderedPools() {
        return new ArrayList<>(service.getPools().values());
    }

    private PoolDefinition getPoolAtIndex(Player player, int index) {
        List<PoolDefinition> pools = getOrderedPools();
        if (pools.isEmpty()) return null;
        index = Math.max(0, Math.min(index, pools.size() - 1));
        return pools.get(index);
    }

    /**
     * 构建并推送祈愿界面数据到客户端。
     */
    private void pushGachaData(Player player, int poolIndex) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return;
        }

        List<PoolDefinition> pools = getOrderedPools();
        if (pools.isEmpty()) {
            return;
        }

        poolIndex = Math.max(0, Math.min(poolIndex, pools.size() - 1));
        PoolDefinition pool = pools.get(poolIndex);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", GACHA_PACKET_ID);
        payload.put("poolName", pool.displayName());
        payload.put("poolType", pool.type().name());
        payload.put("poolBanner", ""); // 暂无 banner 配置，留空
        payload.put("poolDescription", ""); // 暂无 description 配置，留空
        payload.put("upItemName", ""); // 暂无 UP 物品名称配置，留空
        payload.put("upItemIcon", ""); // 暂无 UP 物品图标配置，留空
        payload.put("guaranteeText", buildGuaranteeText(pool));
        payload.put("remainingTime", ""); // 暂无限时配置，留空
        payload.put("fateCount", 0); // 暂无命运之契计数，留空
        payload.put("primogemCount", 0); // 暂无原石计数，留空
        payload.put("stardustCount", 0); // 暂无星尘计数，留空

        // 保底计数
        PlayerGachaState state = service.getGachaState(player, pool.id());
        payload.put("pity4", state != null ? state.pity4star() : 0);
        payload.put("pity5", state != null ? state.pity5star() : 0);

        // 卡池列表
        Map<String, String> poolList = new LinkedHashMap<>();
        for (int i = 0; i < pools.size(); i++) {
            poolList.put(Integer.toString(i), pools.get(i).displayName());
        }
        payload.put("poolList", poolList);
        payload.put("currentPoolIndex", poolIndex);

        packetBridge.sendPacket(player, GACHA_UI_FILE, "update", payload);
    }

    private String buildGuaranteeText(PoolDefinition pool) {
        if (pool.type() != PoolType.GACHA || pool.gacha() == null) {
            return "";
        }
        GachaPoolConfig gacha = pool.gacha();
        StringBuilder sb = new StringBuilder();
        sb.append("5星保底: ").append(gacha.pity5star()).append("抽 | ");
        sb.append("4星保底: ").append(gacha.pity4star()).append("抽");
        if (gacha.softPityStart() > 0) {
            sb.append(" | 软保底: ").append(gacha.softPityStart()).append("抽起");
        }
        return sb.toString();
    }
}
