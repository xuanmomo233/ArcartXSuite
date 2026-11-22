package xuanmo.arcartxsuite.api.bridge;

import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Optional slot-change feed for Prop. The callback only signals which slot changed. */
@ApiStability.Internal
public interface PropSlotUpdates {
    /** Returns a listener removal action, or null when the feed is unavailable. */
    @Nullable Runnable observeSlots(BiConsumer<Player, String> changed);
}
