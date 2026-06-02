package xuanmo.arcartxsuite.eventpacket.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record EventPacketRule(
    String id,
    boolean enabled,
    EventPacketTrigger trigger,
    String signal,
    String placeholder,
    BigDecimal threshold,
    boolean requireNonEmpty,
    int requiredCount,
    Set<String> worlds,
    Set<String> entityTypes,
    Set<String> mythicMobIds,
    boolean repeatable,
    long cooldownMillis,
    List<EventPacketCondition> conditions,
    List<EventPacketAction> actions,
    String clientPacketId
) {
    public EventPacketRule(
        String id, boolean enabled, EventPacketTrigger trigger,
        String signal, String placeholder, BigDecimal threshold,
        boolean requireNonEmpty, int requiredCount,
        Set<String> worlds, Set<String> entityTypes, Set<String> mythicMobIds,
        boolean repeatable, long cooldownMillis, List<EventPacketCondition> conditions, List<EventPacketAction> actions
    ) {
        this(id, enabled, trigger, signal, placeholder, threshold,
            requireNonEmpty, requiredCount, worlds, entityTypes, mythicMobIds,
            repeatable, cooldownMillis, conditions, actions, "");
    }

    public boolean papiTrigger() {
        return trigger != null && trigger.papiTrigger();
    }

    public boolean isClientPacketTrigger() {
        return trigger == EventPacketTrigger.CLIENT_PACKET;
    }
}
