package xuanmo.arcartxsuite.battlepass.increment;

import java.util.Map;
import org.bukkit.entity.Player;

public final class PayloadValueStrategy implements IncrementStrategy {

    private final String payloadKey;
    private final int maxPerEvent;
    private final double scale;

    public PayloadValueStrategy(String payloadKey, int maxPerEvent, double scale) {
        this.payloadKey = payloadKey;
        this.maxPerEvent = maxPerEvent;
        this.scale = scale;
    }

    @Override
    public int calculateIncrement(Player player, Map<String, String> payload) {
        if (payload == null) return 0;
        String raw = payload.get(payloadKey);
        if (raw == null) return 0;
        try {
            double value = Double.parseDouble(raw) * scale;
            int increment = (int) Math.round(value);
            if (maxPerEvent > 0) {
                increment = Math.min(increment, maxPerEvent);
            }
            return Math.max(0, increment);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String type() {
        return "payload_value";
    }

    public String payloadKey() { return payloadKey; }
    public int maxPerEvent() { return maxPerEvent; }
    public double scale() { return scale; }
}
