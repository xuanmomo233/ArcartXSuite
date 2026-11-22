package xuanmo.arcartxsuite.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;

/**
 * {@link ClientPacketGuard} 的行为测试。
 * <p>
 * 核心回归场景：某类 packetId 的高基数 action（如 AXS_TOOLTIP_DATA 的物品指纹）
 * 塞满 64 键上限后，不得影响同一模块路由下其他 packetId（如 AXS_WAREHOUSE 的
 * UI 按钮 action）——旧实现按 (module|player) 共享一个键集，会导致受影响玩家
 * 全部 UI 按钮被静默丢弃直到服务器重启。
 */
final class ClientPacketGuardTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER_PLAYER = UUID.randomUUID();

    private static final ClientPacketGuardRule DEFAULT_RULE = new ClientPacketGuardRule(
        true, 1000L, 20, ClientPacketGuardMode.SILENT, "", 3000L, "", 5000L);

    private static final ClientPacketGuard.FeedbackSink NOOP_SINK = new ClientPacketGuard.FeedbackSink() {
        @Override
        public void notifyPlayer(String message) {
        }

        @Override
        public void punish(String command) {
        }
    };

    private static ClientPacketGuard newGuard() {
        return newGuard(new ClientPacketGuardConfiguration(
            true, false, 200L, DEFAULT_RULE, Map.of(), Map.of()), new AtomicLong());
    }

    private static ClientPacketGuard newGuard(ClientPacketGuardConfiguration config, AtomicLong clock) {
        return new ClientPacketGuard(null, config, clock::get);
    }

    private static boolean allow(ClientPacketGuard guard, UUID player, String packetId, String action) {
        return guard.allow(player, "Steve", "menu", packetId, action, false, NOOP_SINK);
    }

    @Test
    void ingressCountsAcrossPacketIdsEvenWhenActionRulesAreDisabled() {
        AtomicLong clock = new AtomicLong();
        ClientPacketGuard guard = newGuard(new ClientPacketGuardConfiguration(
            false, false, 200L, DEFAULT_RULE, Map.of(), Map.of()), clock);
        for (int index = 0; index < 128; index++) {
            assertTrue(guard.allowIngress(PLAYER, "packet_" + index, java.util.List.of("action_" + index)));
        }
        assertFalse(guard.allowIngress(PLAYER, "different_packet", java.util.List.of()));
        assertTrue(guard.allowIngress(OTHER_PLAYER, "different_packet", java.util.List.of()));
        clock.set(1000);
        assertTrue(guard.allowIngress(PLAYER, "different_packet", java.util.List.of()));
    }

    @Test
    void ingressRejectsOversizedPacketsAndAggregateBytes() {
        ClientPacketGuard guard = newGuard();
        assertFalse(guard.allowIngress(PLAYER, "packet", java.util.List.of("x".repeat(90_000))));
        assertFalse(guard.allowIngress(PLAYER, "packet", java.util.Collections.nCopies(129, "x")));
        String payload = "界".repeat(80_000);
        for (int index = 0; index < 4; index++) {
            assertTrue(guard.allowIngress(PLAYER, "packet", java.util.List.of(payload)));
        }
        assertFalse(guard.allowIngress(PLAYER, "packet", java.util.List.of(payload)));
        guard.clearPlayer(PLAYER);
        assertTrue(guard.allowIngress(PLAYER, "packet", java.util.List.of(payload)));
    }

    @Test
    void highCardinalityPacketDoesNotStarveOtherPacketIds() {
        ClientPacketGuard guard = newGuard();
        // 指纹洪泛：同一模块路由下塞入远超 64 上限的 tooltip 指纹 action
        for (int i = 0; i < 100; i++) {
            allow(guard, PLAYER, "axs_tooltip_data", "fingerprint_" + i);
        }
        // 键桶已塞满：tooltip 的新指纹被拒
        assertFalse(allow(guard, PLAYER, "axs_tooltip_data", "fingerprint_overflow"));
        // 但其他 packetId 的 action 不受影响——仓库/菜单按钮可正常放行
        assertTrue(allow(guard, PLAYER, "axs_warehouse", "storage"));
        assertTrue(allow(guard, PLAYER, "axs_menu", "open"));
    }

    @Test
    void samePacketIdKeysetIsStillCapped() {
        ClientPacketGuard guard = newGuard();
        for (int i = 0; i < 64; i++) {
            assertTrue(allow(guard, PLAYER, "axs_tooltip_data", "fp_" + i));
        }
        assertFalse(allow(guard, PLAYER, "axs_tooltip_data", "fp_64"));
        // 已见过的 action 仍放行（上限只挡新 action）
        assertTrue(allow(guard, PLAYER, "axs_tooltip_data", "fp_0"));
    }

    @Test
    void saturationIsPerPlayer() {
        ClientPacketGuard guard = newGuard();
        for (int i = 0; i < 100; i++) {
            allow(guard, PLAYER, "axs_tooltip_data", "fp_" + i);
        }
        assertTrue(allow(guard, OTHER_PLAYER, "axs_tooltip_data", "fp_new"));
        assertTrue(allow(guard, OTHER_PLAYER, "axs_warehouse", "storage"));
    }

    @Test
    void legacyModuleLevelCallsShareOneBucket() {
        ClientPacketGuard guard = newGuard();
        // 旧签名（无 packetId）共享 "" 桶——模块内部调用只看到自己的 action
        for (int i = 0; i < 64; i++) {
            assertTrue(guard.allow(PLAYER, "Steve", "menu", null, "act_" + i, false, NOOP_SINK));
        }
        assertFalse(guard.allow(PLAYER, "Steve", "menu", null, "act_64", false, NOOP_SINK));
    }

    @Test
    void clearPlayerResetsAllBuckets() {
        ClientPacketGuard guard = newGuard();
        for (int i = 0; i < 100; i++) {
            allow(guard, PLAYER, "axs_tooltip_data", "fp_" + i);
        }
        assertFalse(allow(guard, PLAYER, "axs_tooltip_data", "fp_new"));
        guard.clearPlayer(PLAYER);
        assertTrue(guard.actionKeys(PLAYER).isEmpty());
        assertTrue(allow(guard, PLAYER, "axs_tooltip_data", "fp_new"));
    }

    @Test
    void updateConfigurationHotSwapsRules() {
        AtomicLong clock = new AtomicLong();
        ClientPacketGuard guard = newGuard(new ClientPacketGuardConfiguration(
            true, false, 200L, DEFAULT_RULE, Map.of(), Map.of()), clock);
        assertTrue(allow(guard, PLAYER, "axs_warehouse", "storage"));

        // 禁用后全放行（禁用期间不写键集）
        guard.updateConfiguration(new ClientPacketGuardConfiguration(
            false, false, 200L, DEFAULT_RULE, Map.of(), Map.of()));
        for (int i = 0; i < 100; i++) {
            assertTrue(allow(guard, PLAYER, "axs_tooltip_data", "fp_" + i));
        }

        // 重新启用后限流恢复，且旧实例引用依然有效
        guard.updateConfiguration(new ClientPacketGuardConfiguration(
            true, false, 200L, DEFAULT_RULE, Map.of(), Map.of()));
        for (int i = 0; i < 64; i++) {
            assertTrue(allow(guard, PLAYER, "axs_tooltip_data", "fp_" + i));
        }
        assertFalse(allow(guard, PLAYER, "axs_tooltip_data", "fp_overflow"));
        assertTrue(allow(guard, PLAYER, "axs_warehouse", "storage"));
    }

    @Test
    void rateWindowIsNamespacedByPacketId() {
        AtomicLong clock = new AtomicLong();
        ClientPacketGuardRule tight = new ClientPacketGuardRule(
            true, 1000L, 2, ClientPacketGuardMode.SILENT, "", 3000L, "", 5000L);
        ClientPacketGuard guard = newGuard(new ClientPacketGuardConfiguration(
            true, false, 200L, tight, Map.of(), Map.of()), clock);

        assertTrue(allow(guard, PLAYER, "axs_menu", "click"));
        assertTrue(allow(guard, PLAYER, "axs_menu", "click"));
        // 同 action 同 packetId 超限被拒
        assertFalse(allow(guard, PLAYER, "axs_menu", "click"));
        // 同 action 换 packetId 是独立窗口
        assertTrue(allow(guard, PLAYER, "axs_warehouse", "click"));

        // 窗口滑动后恢复
        clock.addAndGet(1001);
        assertTrue(allow(guard, PLAYER, "axs_menu", "click"));
    }

    @Test
    void concurrentAdmissionKeepsTheActionQuotaExact() throws Exception {
        ClientPacketGuard guard = newGuard();
        var producers = Executors.newFixedThreadPool(4);
        try {
            var cases = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 128; i++) {
                String action = "action_" + i;
                cases.add(() -> allow(guard, PLAYER, "example", action));
            }
            int accepted = 0;
            for (var result : producers.invokeAll(cases, 5, TimeUnit.SECONDS)) {
                if (result.get()) accepted++;
            }
            assertEquals(64, accepted);
            assertEquals(64, guard.actionKeys(PLAYER).get("menu|example").size());
        } finally {
            producers.shutdownNow();
        }
    }

    @Test
    void growingAWrappedWindowPreservesItsTimestampsAndExpiredBucketsAreRemoved() {
        var clock = new AtomicLong();
        var rule = new ClientPacketGuardRule(true, 1000L, 6, ClientPacketGuardMode.SILENT, "", 3000L, "", 5000L);
        var guard = newGuard(new ClientPacketGuardConfiguration(true, false, 200L, rule, Map.of(), Map.of()), clock);
        for (int timestamp = 0; timestamp < 4; timestamp++) {
            clock.set(timestamp);
            assertTrue(allow(guard, PLAYER, "example", "click"));
        }
        clock.set(1000L);
        for (int i = 0; i < 3; i++) assertTrue(allow(guard, PLAYER, "example", "click"));
        assertFalse(allow(guard, PLAYER, "example", "click"));
        clock.set(1001L);
        assertTrue(allow(guard, PLAYER, "example", "click"));
        assertFalse(allow(guard, PLAYER, "example", "click"));
        clock.set(6002L);
        guard.cleanup();
        assertTrue(guard.actionKeys(PLAYER).isEmpty());
        assertTrue(allow(guard, PLAYER, "example", "click"));
    }

    @Test
    void reservesFeedbackCooldownBeforeInvokingAReentrantListener() {
        var clock = new AtomicLong();
        var rule = new ClientPacketGuardRule(true, 1000L, 1, ClientPacketGuardMode.NOTIFY, "slow", 3000L, "", 5000L);
        var guard = newGuard(new ClientPacketGuardConfiguration(true, false, 200L, rule, Map.of(), Map.of()), clock);
        var notifications = new AtomicInteger();
        ClientPacketGuard.FeedbackSink sink = new ClientPacketGuard.FeedbackSink() {
            @Override public void notifyPlayer(String message) {
                if (notifications.incrementAndGet() == 1) {
                    assertFalse(guard.allow(PLAYER, "Steve", "menu", "example", "click", false, this));
                }
            }
            @Override public void punish(String command) {}
        };
        assertTrue(allow(guard, PLAYER, "example", "click"));
        assertFalse(guard.allow(PLAYER, "Steve", "menu", "example", "click", false, sink));
        assertEquals(1, notifications.get());
    }

    @Test
    void actionKeysSnapshotIsNamespaced() {
        ClientPacketGuard guard = newGuard();
        allow(guard, PLAYER, "axs_tooltip_data", "fp_a");
        allow(guard, PLAYER, "axs_warehouse", "storage");
        allow(guard, PLAYER, null, "legacy_action");

        Map<String, ? extends java.util.Collection<String>> keys = guard.actionKeys(PLAYER);
        assertTrue(keys.get("menu|axs_tooltip_data").contains("fp_a"));
        assertTrue(keys.get("menu|axs_warehouse").contains("storage"));
        assertTrue(keys.get("menu|").contains("legacy_action"));
        assertTrue(guard.actionKeys(OTHER_PLAYER).isEmpty());
    }

    @Test
    void nullPlayerIsRejected() {
        ClientPacketGuard guard = newGuard();
        assertFalse(guard.allow(null, "Steve", "menu", "axs_menu", "open", false, NOOP_SINK));
    }
}
