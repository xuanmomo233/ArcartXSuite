package xuanmo.arcartxsuite.title.service;

import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.modattr.ModAttributeModifier;
import xuanmo.arcartxsuite.api.modattr.ModAttributeService;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

/**
 * 称号永久 Mod 属性加成同步服务。
 * <p>
 * 将「已装备且为永久拥有」称号聚合出的 {@code permanent-display-bonuses}
 * 以 {@code AXS_TITLE_PERMANENT} 为来源 ID 登记到本体的
 * {@link ModAttributeService}，由本体向玩家挂载 Forge 实体属性修饰器；
 * 卸下称号、称号到期或玩家退出后加成自动消失。
 * 属于 title 模块属性桥接层。
 */
public final class TitleModAttributeService {

    private static final String PERMANENT_SOURCE = "AXS_TITLE_PERMANENT";

    private final ModAttributeService modAttributeService;

    /**
     * @param modAttributeService 本体 Mod 属性服务，为 {@code null} 时所有操作为空操作
     */
    public TitleModAttributeService(@Nullable ModAttributeService modAttributeService) {
        this.modAttributeService = modAttributeService;
    }

    /**
     * @return Mod 属性服务是否可用（需要 Forge 混合核心环境）
     */
    public boolean hooked() {
        return modAttributeService != null && modAttributeService.available();
    }

    /**
     * 同步玩家展示称号的永久 Mod 属性加成：无加成时清除来源登记。
     *
     * @param player        目标玩家
     * @param resolvedState 解析后的称号状态
     */
    public void sync(Player player, ResolvedTitleState resolvedState) {
        if (modAttributeService == null || player == null) {
            return;
        }
        Map<String, ModAttributeModifier> bonuses =
            resolvedState == null ? Map.of() : resolvedState.permanentDisplayBonuses();
        if (bonuses.isEmpty()) {
            modAttributeService.clearSource(player, PERMANENT_SOURCE);
        } else {
            modAttributeService.setModifiers(player, PERMANENT_SOURCE, bonuses);
        }
    }

    /**
     * 清除玩家在称号来源登记的 Mod 属性加成。
     *
     * @param player 目标玩家
     */
    public void clear(Player player) {
        if (modAttributeService == null || player == null) {
            return;
        }
        modAttributeService.clearSource(player, PERMANENT_SOURCE);
    }

    /**
     * 模块停用/重载时清除所有在线玩家的称号来源加成登记。
     */
    public void shutdown() {
        if (modAttributeService == null) {
            return;
        }
        modAttributeService.clearAll(PERMANENT_SOURCE);
    }
}
