package xuanmo.arcartxsuite.api.potion;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 持续药水效果（光环）服务。
 * <p>
 * 本体统一维护「玩家 → 来源 → 药水效果表」登记关系，并按固定周期
 * 为在线玩家重复施加短时长的药水效果，实现"来源持续生效"的光环式 buff。
 * <p>
 * 模块通过 {@code context.potionAura()} 获取；同一玩家可同时存在多个来源
 * （如称号、时装、套装），同类型效果按各来源中最大等级施加。
 * <p>
 * 特性说明：
 * <ul>
 *   <li>所有登记的效果以 ambient 模式施加，默认不显示粒子（可由本体 config.yml 调整）。</li>
 *   <li>效果时长固定且较短，来源被移除/玩家下线后剩余效果会自然衰减消失；
 *       清除来源时也会按"ambient + 等级一致 + 剩余时长不超过本服务施加时长"
 *       的启发式规则主动移除，尽量避免误删其他插件授予的同类效果。</li>
 *   <li>登记的效果仅在玩家在线时被周期刷新；玩家退出后自动解除追踪。</li>
 * </ul>
 *
 * @since 1.7.0
 */
@ApiStability.Stable
public interface PotionAuraService {

    /**
     * @return 服务是否可用（本体 config.yml {@code potion-aura.enabled} 控制）
     */
    boolean available();

    /**
     * 登记某来源对玩家持续生效的药水效果集合（整体替换该来源的登记）。
     * <p>
     * 调用后效果立即施加一次，之后由本体按刷新周期持续补充。
     * 传入空集合等价于 {@link #clearSource(Player, String)}。
     * <p>
     * 需在主线程调用（直接触发 Bukkit 药水 API）。
     *
     * @param player   目标玩家
     * @param sourceId 来源标识（如 {@code "AXS_TITLE_DISPLAY"}），模块内唯一即可
     * @param effects  效果表：效果类型 → 等级（1 起，1 = I 级，内部会转换为 Bukkit amplifier）
     */
    void setEffects(Player player, String sourceId, Map<PotionEffectType, Integer> effects);

    /**
     * 移除某来源对玩家登记的全部效果，并按启发式规则移除仍在生效的对应药水效果。
     *
     * @param player   目标玩家
     * @param sourceId 来源标识
     */
    void clearSource(Player player, String sourceId);

    /**
     * 移除玩家身上登记的全部来源效果（玩家退出、模块卸载时使用）。
     *
     * @param player 目标玩家
     */
    void clearPlayer(Player player);

    /**
     * 移除所有在线玩家身上某来源的登记与效果（模块重载/停用时清理残留）。
     *
     * @param sourceId 来源标识
     */
    void clearAll(String sourceId);
}
