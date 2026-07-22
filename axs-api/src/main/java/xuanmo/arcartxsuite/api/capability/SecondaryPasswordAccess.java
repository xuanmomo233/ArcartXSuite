package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Capability for checking and verifying a player's secondary password.
 */
public interface SecondaryPasswordAccess {

    /**
     * Returns whether the player has configured a secondary password.
     */
    boolean isPasswordSet(@NotNull Player player);

    /**
     * Returns whether the player's current secondary-password session is unlocked.
     */
    boolean isUnlocked(@NotNull Player player);

    /**
     * Verifies the supplied password and may unlock the current session.
     *
     * <p>Providers should treat players without a configured password as
     * already authorized, matching the warehouse semantics.</p>
     */
    boolean verify(@NotNull Player player, @NotNull String password);

    /**
     * Sets or replaces the player's password. When replacing an existing
     * password, the old password must be supplied.
     */
    boolean set(@NotNull Player player, @NotNull String oldPassword, @NotNull String newPassword);

    /**
     * Clears the player's password after verifying the current password.
     */
    boolean clear(@NotNull Player player, @NotNull String currentPassword);
}
