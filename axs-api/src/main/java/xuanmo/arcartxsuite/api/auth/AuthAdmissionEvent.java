package xuanmo.arcartxsuite.api.auth;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Per-connection server-thread notification. First-admission business facts use confirmed EventBus delivery. */
public final class AuthAdmissionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AuthSnapshot snapshot;
    public AuthAdmissionEvent(AuthSnapshot snapshot) {
        this.snapshot = snapshot;
    }
    public AuthSnapshot snapshot() { return snapshot; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
