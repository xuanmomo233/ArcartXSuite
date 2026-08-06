package xuanmo.arcartxsuite.bridge.potion;

import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

/**
 * 1.20.2+ 版本的 PotionAccessor 实现。
 * 使用 getBasePotionType()（1.20.2+ 新增，替代 deprecated 的 getBasePotionData()）。
 * <p>
 * 此类用 spigot-api 1.21 编译，不能在 1.20.1 运行时直接加载
 * （getBasePotionType 方法不存在），由 {@link PotionAccessorFactory} 反射加载。
 */
public final class PotionAccessorModern implements PotionAccessor {

    @Override
    public @Nullable PotionType getBasePotionType(PotionMeta potionMeta) {
        try {
            return potionMeta.getBasePotionType();
        } catch (NullPointerException ignored) {
            return null;
        }
    }
}
