package xuanmo.arcartxsuite.abyss.buff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.BuffConfig;
import xuanmo.arcartxsuite.abyss.template.BuffConfig.Option;
import xuanmo.arcartxsuite.abyss.template.BuffConfig.Pool;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

/**
 * 增益管理器：buffs.yml 选择池的运行时状态机（roguelike 祝福）。
 * <p>
 * {@link #offer} 由脚本动作 {@code buff_offer} 调用——任意生命周期段/interact/机关脚本
 * 均可触发。{@code panel} 池走 AX 选择卡片（回包 {@code buff_pick}）；
 * {@code hud} 池直接按权重生效一项并广播提示。
 * <p>
 * 实例变量：{@code buff.<poolId>}=玩家最近所选 option id（hud/直接生效同样写）、
 * {@code buff.<poolId>.count}=本池已发放人次。
 * 事件：axs.abyss.buff_offer / buff_pick。
 */
public final class BuffManager {

    /** 实例 → 池id → 进行中 offer（每玩家各自 pending）。 */
    private final Map<UUID, Map<String, Offer>> offers = new ConcurrentHashMap<>();
    /** 实例 → 池id → 是否已 offer 过（once-per-instance）。 */
    private final Map<UUID, Map<String, Boolean>> offered = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final SchedulerAPI scheduler;
    private final Logger logger;
    private Callback callback;

    /** 一次进行中的增益选择；picked 记录已选玩家（防回包重放重复领取）。 */
    private record Offer(Pool pool, List<Option> candidates, long deadlineEpochMs,
                         java.util.Set<UUID> picked) { }

    public interface Callback {
        /** 执行效果脚本行（effects 逐行，trigger=选择者）。 */
        void runScript(AbyssInstance instance, List<String> lines, @Nullable Player trigger);

        /** 打开增益选择面板（panel 池）。 */
        void openBuffUi(Player player, AbyssInstance instance, Pool pool,
                        List<Option> candidates, int timeoutSeconds);

        void publishEvent(String topic, @Nullable Player player, Map<String, Object> data);

        void broadcast(AbyssInstance instance, String message);

        void msg(Player player, String message);
    }

    public BuffManager(Plugin plugin, SchedulerAPI scheduler, Logger logger) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void cleanup(AbyssInstance instance) {
        offers.remove(instance.getInstanceId());
        offered.remove(instance.getInstanceId());
    }

    private @Nullable Pool pool(AbyssInstance instance, String poolId) {
        return instance.getTemplate().buffs().pools().get(poolId);
    }

    // ═══ offer：发起选择 ═══

    /**
     * 发起增益选择（脚本 buff_offer）。
     * @return false=池不存在/once-per-instance 已发过
     */
    public boolean offer(AbyssInstance instance, String poolId, @Nullable Player trigger) {
        Pool pool = pool(instance, poolId);
        if (pool == null) {
            logger.warning("[abyss] buff 池不存在: " + poolId);
            return false;
        }
        Map<String, Boolean> seen = offered.computeIfAbsent(
            instance.getInstanceId(), k -> new ConcurrentHashMap<>());
        if (pool.oncePerInstance() && seen.putIfAbsent(poolId, Boolean.TRUE) != null) {
            return false;
        }
        if (callback == null) return false;
        callback.publishEvent("axs.abyss.buff_offer", trigger, Map.of(
            "dungeon", instance.getTemplate().id(),
            "instance", instance.getInstanceId().toString(),
            "pool", poolId));

        if (!pool.isPanel()) {
            // hud：直接按权重生效一项（每玩家独立 roll）
            for (Player p : instance.getPlayers()) {
                Option opt = drawWeighted(pool.options(), 1).stream().findFirst().orElse(null);
                if (opt != null) grant(instance, p, pool, opt);
            }
            return true;
        }

        // panel：抽候选 → 每玩家开面板 + 超时任务
        List<Option> candidates = drawWeighted(pool.options(),
            pool.candidates() > 0 ? pool.candidates() : pool.options().size());
        if (candidates.isEmpty()) return false;

        Map<String, Offer> map = offers.computeIfAbsent(
            instance.getInstanceId(), k -> new ConcurrentHashMap<>());
        long deadline = pool.timeout() > 0
            ? System.currentTimeMillis() + pool.timeout() * 1000L : 0L;
        map.put(poolId, new Offer(pool, candidates, deadline,
            ConcurrentHashMap.newKeySet()));

        for (Player p : instance.getPlayers()) {
            callback.openBuffUi(p, instance, pool, candidates, pool.timeout());
        }
        if (pool.timeout() > 0) {
            scheduleTimeout(instance, poolId);
        }
        return true;
    }

    // ═══ pick：面板回包 ═══

    /**
     * 玩家选择一项（UI 回包 buff_pick 路由到这里）。
     * @return true=已生效；false=offer 已过期/选项无效
     */
    public boolean pick(AbyssInstance instance, Player player,
                        String poolId, String optionId) {
        Map<String, Offer> map = offers.get(instance.getInstanceId());
        Offer offer = map != null ? map.get(poolId) : null;
        if (offer == null) return false;
        // 每玩家限领一次：buff_pick 回包可能被重放/快速双击
        if (!offer.picked().add(player.getUniqueId())) return false;
        Option opt = null;
        for (Option o : offer.candidates()) {
            if (o.id().equals(optionId)) { opt = o; break; }
        }
        if (opt == null) {
            offer.picked().remove(player.getUniqueId()); // 无效选项不消耗领取资格
            return false;
        }
        grant(instance, player, offer.pool(), opt);
        return true;
    }

    /** 玩家是否还有待选的该池 offer（面板重开/校验用）。 */
    public boolean pending(AbyssInstance instance, String poolId) {
        Map<String, Offer> map = offers.get(instance.getInstanceId());
        return map != null && map.containsKey(poolId);
    }

    /**
     * 脚本动作 buff_grant：直接赋予指定增益（不弹窗）。
     * optionId 可为空——空则按权重随机一项。
     */
    public boolean grant(AbyssInstance instance, Player player,
                         String poolId, @Nullable String optionId) {
        Pool pool = pool(instance, poolId);
        if (pool == null) return false;
        Option opt = null;
        if (optionId != null && !optionId.isBlank()) {
            for (Option o : pool.options()) {
                if (o.id().equals(optionId)) { opt = o; break; }
            }
            if (opt == null) return false;
        } else {
            opt = drawWeighted(pool.options(), 1).stream().findFirst().orElse(null);
        }
        if (opt == null) return false;
        grant(instance, player, pool, opt);
        return true;
    }

    /** 脚本动作 buff_clear：撤下未完成的 offer（关闭面板状态）。 */
    public void clearOffer(AbyssInstance instance, String poolId) {
        Map<String, Offer> map = offers.get(instance.getInstanceId());
        if (map != null) map.remove(poolId);
    }

    // ═══ 生效 ═══

    private void grant(AbyssInstance instance, Player player, Pool pool, Option opt) {
        instance.getVariables().put("buff." + pool.id(), opt.id());
        Object count = instance.getVariables().get("buff." + pool.id() + ".count");
        int n = count instanceof Number num ? num.intValue()
            : parseInt(String.valueOf(count), 0);
        instance.getVariables().put("buff." + pool.id() + ".count", n + 1);
        if (callback == null) return;
        callback.publishEvent("axs.abyss.buff_pick", player, Map.of(
            "dungeon", instance.getTemplate().id(),
            "instance", instance.getInstanceId().toString(),
            "pool", pool.id(), "option", opt.id()));
        if (!opt.effects().isEmpty()) {
            callback.runScript(instance, opt.effects(), player);
        }
    }

    // ═══ 超时 ═══

    private void scheduleTimeout(AbyssInstance instance, String poolId) {
        Pool pool = pool(instance, poolId);
        if (pool == null) return;
        // 一次性延迟（自取消 + 挂实例）：period≈MAX_VALUE 会溢出 nextRun → 每 tick 重跑
        xuanmo.arcartxsuite.api.scheduler.ScheduledTask[] h =
            new xuanmo.arcartxsuite.api.scheduler.ScheduledTask[1];
        h[0] = scheduler.scheduleEverySeconds(plugin, () -> {
            if (h[0] != null) {
                try { h[0].cancel(); } catch (Throwable ignored) { }
            }
            Map<String, Offer> map = offers.get(instance.getInstanceId());
            Offer offer = map != null ? map.get(poolId) : null;
            if (offer == null) return;
            // 超时兜底：first/random/none
            for (Player p : instance.getPlayers()) {
                Option pick = switch (pool.timeoutAuto()) {
                    case "first" -> offer.candidates().get(0);
                    case "none" -> null;
                    default -> offer.candidates().get(
                        ThreadLocalRandom.current().nextInt(offer.candidates().size()));
                };
                if (pick != null) grant(instance, p, offer.pool(), pick);
            }
            map.remove(poolId);
        }, pool.timeout(), Long.MAX_VALUE / 20);
        instance.registerTask(h[0]);
    }

    // ═══ 加权抽取（不重复） ═══

    private List<Option> drawWeighted(List<Option> options, int count) {
        if (count >= options.size()) return List.copyOf(options);
        List<Option> pool = new ArrayList<>(options);
        List<Option> out = new ArrayList<>(count);
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            int total = pool.stream().mapToInt(o -> Math.max(1, o.weight())).sum();
            int roll = ThreadLocalRandom.current().nextInt(total);
            for (Option o : pool) {
                roll -= Math.max(1, o.weight());
                if (roll < 0) {
                    out.add(o);
                    pool.remove(o);
                    break;
                }
            }
        }
        return out;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    /** 进行中 offer 详情（UI 重开用）。 */
    public @Nullable List<Option> candidatesOf(AbyssInstance instance, String poolId) {
        Map<String, Offer> map = offers.get(instance.getInstanceId());
        Offer offer = map != null ? map.get(poolId) : null;
        return offer != null ? offer.candidates() : null;
    }
}
