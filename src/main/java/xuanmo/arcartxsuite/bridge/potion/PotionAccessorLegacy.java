package xuanmo.arcartxsuite.bridge.potion;

import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

/**
 * 1.20.1 及以下版本的 PotionAccessor 实现。
 * 使用 getBasePotionData().getType()（1.21+ deprecated，未来可能移除）。
 */
public final class PotionAccessorLegacy implements PotionAccessor {

    @Override
    public @Nullable PotionType getBasePotionType(PotionMeta potionMeta) {
        try {
            return potionMeta.getBasePotionData().getType();
        } catch (NoSuchMethodError | NullPointerException ignored) {
            return null;
        }
    }
}
