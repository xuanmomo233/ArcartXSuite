package xuanmo.arcartxsuite.api.auth;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fires on the server thread once per successful connection admission. */
public final class AuthAdmissionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AuthSnapshot snapshot;
    private final boolean firstAdmission;
    public AuthAdmissionEvent(AuthSnapshot snapshot, boolean firstAdmission) {
        this.snapshot = snapshot;
        this.firstAdmission = firstAdmission;
    }
    public AuthSnapshot snapshot() { return snapshot; }
    public boolean firstAdmission() { return firstAdmission; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
