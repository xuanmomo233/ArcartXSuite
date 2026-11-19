package xuanmo.arcartxsuite.api.bridge;

import java.util.List;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Synchronous lookups for the conversation selector. */
@ApiStability.Internal
public interface AdyeshachNpcScanBridge {

    /** Query the viewer's visible public NPCs and restrict the scan to eligible NPC IDs. */
    @NotNull List<AdyeshachNearbyNpc> findVisibleNearby(@NotNull Player player, double range,
                                                       @NotNull Predicate<String> eligibleId);

    /** Revalidate the exact selected entity against current manager, viewer, world and range. */
    boolean canInteract(@NotNull Player player, double range, @NotNull String npcId,
                        @NotNull Object conversationEntity);
}
