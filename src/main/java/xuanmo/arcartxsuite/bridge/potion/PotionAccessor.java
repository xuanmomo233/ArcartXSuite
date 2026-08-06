package xuanmo.arcartxsuite.bridge.potion;

import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

/**
 * 跨版本获取 PotionMeta 基础 PotionType 的访问器接口。
 * <p>
 * 1.20.1 使用 {@link PotionAccessorLegacy}（getBasePotionData().getType()），
 * 1.20.2+ 使用 {@link PotionAccessorModern}（getBasePotionType()）。
 * 运行时由 {@link PotionAccessorFactory} 按服务端版本选择实现。
 */
public interface PotionAccessor {

    /** 获取 PotionMeta 的基础 PotionType，无药水数据时返回 null */
    @Nullable PotionType getBasePotionType(PotionMeta potionMeta);
}
