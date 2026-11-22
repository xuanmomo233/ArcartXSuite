package xuanmo.arcartxsuite.api.capability;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Capability for checking and verifying a player's secondary password.
 * <p>
 * Public capability: external plugins may obtain it via
 * {@code AxsCapabilities.get(SecondaryPasswordAccess.class)} to gate
 * sensitive operations behind the unified secondary password.
 */
@PublicCapability
public interface SecondaryPasswordAccess {

    /** The stored password identity and its associated authorization at the time of the read. */
    record Status(boolean passwordSet, BooleanSupplier authorization, Supplier<CompletionStage<Boolean>> identityCheck) {
        public static final Status LOCKED = new Status(true, () -> false, () -> CompletableFuture.completedFuture(false));

        /** Checks the local authorization without I/O. */
        public boolean unlocked() {
            return authorization.getAsBoolean();
        }

        /** Rechecks the same authorization and its stored identity before a deferred action. */
        public CompletionStage<Boolean> revalidateAsync() {
            if (!unlocked()) return CompletableFuture.completedFuture(false);
            return identityCheck.get().thenApply(current -> current && unlocked());
        }
    }

    /**
     * Returns whether the player has configured a secondary password.
     * A provider may conservatively return true if storage is unavailable.
     */
    boolean isPasswordSet(@NotNull Player player);

    /**
     * Returns whether the player's current secondary-password session is unlocked.
     * Players with no configured password are unlocked. Providers must deny access
     * when they cannot determine the state.
     */
    boolean isUnlocked(@NotNull Player player);

    CompletionStage<Status> statusAsync(@NotNull Player player);

    /**
     * Verifies the supplied password and may unlock the current session.
     *
     * <p>Providers should treat players without a configured password as
     * already authorized, matching the warehouse semantics.</p>
     */
    boolean verify(@NotNull Player player, @NotNull String password);

    /**
     * Starts password verification from the server thread. Callers must dispatch
     * Bukkit work back to the server thread after completion.
     */
    CompletionStage<Boolean> verifyAsync(@NotNull Player player, @NotNull String password);

    /**
     * Sets or replaces the player's password. When replacing an existing
     * password, the old password must be supplied.
     */
    boolean set(@NotNull Player player, @NotNull String oldPassword, @NotNull String newPassword);

    CompletionStage<Boolean> setAsync(@NotNull Player player, @NotNull String oldPassword, @NotNull String newPassword);

    /**
     * Clears the player's password after verifying the current password.
     */
    boolean clear(@NotNull Player player, @NotNull String currentPassword);

    CompletionStage<Boolean> clearAsync(@NotNull Player player, @NotNull String currentPassword);

    /**
     * Minimum accepted password length, configured centrally by the host
     * ({@code security.secondary-password.min-length}).
     */
    default int minLength() {
        return 4;
    }

    /**
     * Maximum accepted password length, configured centrally by the host
     * ({@code security.secondary-password.max-length}).
     */
    default int maxLength() {
        return 32;
    }

    /**
     * Locks the player's current secondary-password session, requiring re-verification.
     */
    default void lock(@NotNull Player player) {
        // Default no-op for implementations that do not support session locking.
    }
}
