package xuanmo.arcartxsuite.api.modattr;

import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * Forge Mod 实体属性服务（通用 Mod 属性加成）。
 * <p>
 * 仅在混合核心（Mohist / Arclight 等 Forge+Bukkit 环境）上可用。
 * 按「玩家 → 来源 → 属性修饰器表」登记，本体通过 Forge 属性注册表
 * 给玩家挂载/移除 {@code AttributeModifier}，让任意 Mod 注册的实体属性
 * （如 {@code gunsmithlib:bullet_damage}、{@code apothic_attributes:crit_damage}、
 * {@code irons_spellbooks:spell_power}）获得持续加成。
 * <p>
 * 模块通过 {@code context.modAttributes()} 获取；同一玩家可同时存在多个来源
 * （称号、时装、套装……），同一属性来自不同来源的修饰器互相独立、互不影响。
 * <p>
 * 特性说明：
 * <ul>
 *   <li>修饰器以「来源 + 属性」推导固定 UUID，重复登记同一来源会整体替换而非叠加；</li>
 *   <li>清除来源/玩家退出/本体重载时按 UUID 精确移除，不会误删其他来源的修饰器；</li>
 *   <li>属性 ID 不存在（对应 Mod 未安装或拼写错误）时记日志并跳过，
 *       不影响同来源其他属性的施加；</li>
 *   <li>登记仅保存在内存中，玩家退出后自动解除追踪。</li>
 * </ul>
 *
 * @since 1.7.0
 */
@ApiStability.Stable
public interface ModAttributeService {

    /**
     * @return 服务是否可用（Forge 环境存在且本体 config.yml {@code mod-attributes.enabled} 开启）
     */
    boolean available();

    /**
     * 登记某来源对玩家生效的属性修饰器集合（整体替换该来源的登记）。
     * <p>
     * 传入空集合等价于 {@link #clearSource(Player, String)}。
     * 需在主线程调用（触发 NMS 属性操作）。
     *
     * @param player    目标玩家
     * @param sourceId  来源标识（如 {@code "AXS_TITLE_PERMANENT"}），模块内唯一即可
     * @param modifiers 修饰器表：键仅作标识（建议用属性 ID），值描述属性、数值与运算模式
     */
    void setModifiers(Player player, String sourceId, @NotNull Map<String, ModAttributeModifier> modifiers);

    /**
     * 移除某来源对玩家登记的全部属性修饰器。
     *
     * @param player   目标玩家
     * @param sourceId 来源标识
     */
    void clearSource(Player player, String sourceId);

    /**
     * 移除玩家身上登记的全部来源修饰器（玩家退出、模块卸载时使用）。
     *
     * @param player 目标玩家
     */
    void clearPlayer(Player player);

    /**
     * 移除所有在线玩家身上某来源的登记与修饰器（模块重载/停用时清理残留）。
     *
     * @param sourceId 来源标识
     */
    void clearAll(String sourceId);
}
