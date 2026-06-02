package xuanmo.arcartxsuite.tab.config;

/**
 * 单个 UI 发包目标：指定往哪个 UI 用哪个 handler 发包。
 */
public record UiTarget(String uiId, String packetHandler) {

    public UiTarget {
        if (uiId == null || uiId.isBlank()) {
            throw new IllegalArgumentException("UiTarget.uiId must not be blank");
        }
        if (packetHandler == null || packetHandler.isBlank()) {
            throw new IllegalArgumentException("UiTarget.packetHandler must not be blank");
        }
        uiId = uiId.trim();
        packetHandler = packetHandler.trim();
    }

    @Override
    public String toString() {
        return uiId + ":" + packetHandler;
    }
}
