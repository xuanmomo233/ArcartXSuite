package xuanmo.arcartxsuite.security;

import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;

public record ClientPacketGuardRule(
    boolean enabled,
    long windowMs,
    int maxHits,
    ClientPacketGuardMode mode,
    String notifyMessage,
    long notifyCooldownMs,
    String punishCommand
) {

    public ClientPacketGuardRule {
        windowMs = Math.max(1L, windowMs);
        maxHits = Math.max(1, maxHits);
        notifyCooldownMs = Math.max(0L, notifyCooldownMs);
        mode = mode == null ? ClientPacketGuardMode.SILENT : mode;
        notifyMessage = notifyMessage == null ? "" : notifyMessage;
        punishCommand = punishCommand == null ? "" : punishCommand.trim();
    }
}
