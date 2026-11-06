package xuanmo.arcartxsuite.api.auth;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Server-thread notification; the snapshot identifies the connection whose business state must pause. */
public final class AuthRestrictionEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final AuthSnapshot previous;
    private final String reason;
    public AuthRestrictionEvent(AuthSnapshot previous,String reason) { this.previous=previous; this.reason=reason; }
    public AuthSnapshot previous() { return previous; }
    public String reason() { return reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
