package xuanmo.arcartxsuite.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.ContentModerator;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.SoundPlayerBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.RondoBridge;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.bridge.ApiStability;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.script.AriaBridge;

/**
 * 桥接上下文：提供 ArcartX UI/Packet/Item 桥接、物品来源、经济、属性、脚本等全局桥接能力。
 *
 * @since 1.5.0
 */
public interface BridgeContext {

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @Nullable PacketBridgeAPI packetBridge();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @Nullable ClientBridgeAPI clientBridge();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @Nullable ItemBridgeAPI itemStackBridge();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    ItemSourceRegistry itemSourceRegistry();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @NotNull ItemRewardDispatcher itemRewardDispatcher();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @NotNull PendingRewardService pendingRewardService();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @NotNull VanillaItemNameBridge vanillaItemNameBridge();

    /**
     * 内容审核桥接（敏感词过滤）。
     * <p>
     * 统一管理本地词库、云端词库、标点穿透防护、白名单等过滤规则，
     * 供 chat / market / mail 等模块调用。
     *
     * @since 1.3.3
     */
    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @NotNull ContentModerator contentModerator();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    ItemMatcherAPI itemMatcher();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    CurrencyBridgeAPI currencyManager();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @NotNull RondoBridge rondoBridge();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    AttributeBridgeRegistry attributeBridge();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @NotNull AriaBridge ariaBridge();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    @NotNull ScriptConditionEvaluator scriptConditionEvaluator();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Stable
    boolean taczActive();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Internal
    @Nullable WorldTextureBridgeAPI worldTextureBridge();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Internal
    @NotNull WaypointBridgeAPI createWaypointBridge();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Internal
    @NotNull AdyeshachNpcBridgeAPI createAdyeshachNpcBridge();

    @xuanmo.arcartxsuite.api.bridge.ApiStability.Internal
    @NotNull SoundPlayerBridgeAPI createSoundPlayerBridge();

    /**
     * 宿主统一调度 API（固定间隔 / 日历触发 / 跨服单点）。
     *
     * @since 1.6.0
     */
    @ApiStability.Stable
    @NotNull SchedulerAPI scheduler();

    /**
     * 持续药水效果（光环）服务。
     * <p>
     * 按「玩家 → 来源 → 药水效果表」登记，由本体统一定期刷新施加；
     * 供称号等模块实现「佩戴期间持续生效的药水 buff」。
     *
     * @since 1.7.0
     */
    @ApiStability.Stable
    @NotNull xuanmo.arcartxsuite.api.potion.PotionAuraService potionAura();
}
