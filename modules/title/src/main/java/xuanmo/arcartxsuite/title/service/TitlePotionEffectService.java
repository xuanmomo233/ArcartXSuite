package xuanmo.arcartxsuite.title.service;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.potion.PotionAuraService;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

/**
 * 称号持续药水效果同步服务。
 * <p>
 * 将已装备称号聚合出的 {@code display-effects} 以
 * {@code AXS_TITLE_DISPLAY} 为来源 ID 登记到本体的
 * {@link PotionAuraService}，由本体周期刷新施加；
 * 卸下称号或玩家退出后效果自动消失。
 * 属于 title 模块属性桥接层。
 */
public final class TitlePotionEffectService {

    private static final String DISPLAY_SOURCE = "AXS_TITLE_DISPLAY";

    private final PotionAuraService auraService;

    /**
     * @param auraService 本体持续药水效果服务，为 {@code null} 时所有操作为空操作
     */
    public TitlePotionEffectService(@Nullable PotionAuraService auraService) {
        this.auraService = auraService;
    }

    /**
     * @return 持续药水效果服务是否可用
     */
    public boolean hooked() {
        return auraService != null && auraService.available();
    }

    /**
     * 同步玩家展示称号的药水效果：无效果时清除来源登记。
     *
     * @param player        目标玩家
     * @param resolvedState 解析后的称号状态
     */
    public void sync(Player player, ResolvedTitleState resolvedState) {
        if (auraService == null || player == null) {
            return;
        }
        Map<PotionEffectType, Integer> effects =
            resolvedState == null ? Map.of() : resolvedState.displayEffects();
        if (effects.isEmpty()) {
            auraService.clearSource(player, DISPLAY_SOURCE);
        } else {
            auraService.setEffects(player, DISPLAY_SOURCE, effects);
        }
    }

    /**
     * 清除玩家在称号来源登记的药水效果。
     *
     * @param player 目标玩家
     */
    public void clear(Player player) {
        if (auraService == null || player == null) {
            return;
        }
        auraService.clearSource(player, DISPLAY_SOURCE);
    }

    /**
     * 模块停用/重载时清除所有在线玩家的称号来源效果登记。
     */
    public void shutdown() {
        if (auraService == null) {
            return;
        }
        auraService.clearAll(DISPLAY_SOURCE);
    }
}
