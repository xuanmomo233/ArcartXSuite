package xuanmo.arcartxsuite.tab.config;

import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;

public record TabClientRefreshGuardConfiguration(
    boolean enabled,
    long windowMs,
    int maxHits,
    ClientPacketGuardMode mode,
    String notifyMessage,
    long notifyCooldownMs
) {

    public TabClientRefreshGuardConfiguration {
        windowMs = Math.max(1L, windowMs);
        maxHits = Math.max(1, maxHits);
        notifyCooldownMs = Math.max(0L, notifyCooldownMs);
        mode = mode == null ? ClientPacketGuardMode.SILENT : mode;
        if (mode == ClientPacketGuardMode.PUNISH) {
            mode = ClientPacketGuardMode.NOTIFY;
        }
        notifyMessage = notifyMessage == null ? "" : notifyMessage;
    }
}
