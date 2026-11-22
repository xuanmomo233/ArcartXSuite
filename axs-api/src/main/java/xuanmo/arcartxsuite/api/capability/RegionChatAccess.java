package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Live Regions rules for public player messages. Call on the Bukkit main thread. */
@PublicCapability
public interface RegionChatAccess {
    boolean canSendPublic(@NotNull Player sender);

    boolean canReceivePublic(@NotNull Player recipient);
}
