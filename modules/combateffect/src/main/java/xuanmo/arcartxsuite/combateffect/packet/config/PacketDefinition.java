package xuanmo.arcartxsuite.combateffect.packet.config;

import java.util.List;
import java.util.Map;

public record PacketDefinition(
    String id,
    boolean enabled,
    List<PacketTrigger> triggers,
    List<PacketRecipient> recipients,
    String uiId,
    String packetHandler,
    Map<String, Object> packTemplate,
    int comboMin,
    int comboMax,
    boolean comboRepeat,
    long cooldownMs,
    // keybind 条件
    String keyName,
    String keyAction,
    String keyType,
    // state / controller 条件
    String stateId,
    String stateAction,
    String controllerId
) {

    public PacketDefinition(
        String id,
        boolean enabled,
        List<PacketTrigger> triggers,
        List<PacketRecipient> recipients,
        String uiId,
        String packetHandler,
        Map<String, Object> packTemplate
    ) {
        this(id, enabled, triggers, recipients, uiId, packetHandler, packTemplate,
            0, Integer.MAX_VALUE, false, 0L,
            null, "press", "client",
            null, "enter", null);
    }

    public PacketDefinition(
        String id,
        boolean enabled,
        List<PacketTrigger> triggers,
        List<PacketRecipient> recipients,
        String uiId,
        String packetHandler,
        Map<String, Object> packTemplate,
        int comboMin,
        int comboMax,
        boolean comboRepeat
    ) {
        this(id, enabled, triggers, recipients, uiId, packetHandler, packTemplate,
            comboMin, comboMax, comboRepeat, 0L,
            null, "press", "client",
            null, "enter", null);
    }

    public boolean hasCooldown() {
        return cooldownMs > 0;
    }

    /**
     * 检查当前 combo 计数是否满足此包定义的触发条件。
     */
    public boolean matchesCombo(int comboCount) {
        if (!triggers.contains(PacketTrigger.COMBO)) {
            return false;
        }
        if (comboCount < comboMin || comboCount > comboMax) {
            return false;
        }
        if (!comboRepeat && comboMin > 0) {
            // 非重复模式: 仅在恰好到达 comboMin 时触发一次
            return comboCount == comboMin;
        }
        return true;
    }

    /**
     * 检查按键名称和动作是否匹配此包定义的 keybind 条件。
     */
    public boolean matchesKeybind(String incomingKeyName, boolean isPress, String incomingKeyType) {
        if (!triggers.contains(PacketTrigger.KEYBIND) || keyName == null) {
            return false;
        }
        if (!wildcardMatch(keyName, incomingKeyName)) {
            return false;
        }
        if (keyType != null && !keyType.equalsIgnoreCase(incomingKeyType)) {
            return false;
        }
        return matchesAction(keyAction, isPress);
    }

    /**
     * 检查 Chronos 状态 ID 和动作是否匹配此包定义的 state 条件。
     */
    public boolean matchesState(String incomingStateId, boolean isEnter, String currentControllerId) {
        if (!triggers.contains(PacketTrigger.STATE) || stateId == null) {
            return false;
        }
        if (!wildcardMatch(stateId, incomingStateId)) {
            return false;
        }
        if (controllerId != null && !controllerId.isEmpty()
            && !wildcardMatch(controllerId, currentControllerId)) {
            return false;
        }
        return matchesAction(stateAction, isEnter);
    }

    /**
     * 检查控制器 ID 是否匹配此包定义的 controller 条件。
     */
    public boolean matchesController(String incomingControllerId) {
        if (!triggers.contains(PacketTrigger.CONTROLLER)) {
            return false;
        }
        if (controllerId == null || controllerId.isEmpty()) {
            return true;
        }
        return wildcardMatch(controllerId, incomingControllerId);
    }

    private static boolean matchesAction(String configAction, boolean isEnterOrPress) {
        if (configAction == null || "both".equalsIgnoreCase(configAction)) {
            return true;
        }
        if (isEnterOrPress) {
            return "press".equalsIgnoreCase(configAction) || "enter".equalsIgnoreCase(configAction);
        } else {
            return "release".equalsIgnoreCase(configAction) || "leave".equalsIgnoreCase(configAction);
        }
    }

    /**
     * 简易通配符匹配：支持 * 匹配任意字符序列。
     * 例如 "attack_*" 匹配 "attack_1"、"attack_combo"。
     */
    static boolean wildcardMatch(String pattern, String input) {
        if (pattern == null || input == null) {
            return false;
        }
        if (!pattern.contains("*")) {
            return pattern.equalsIgnoreCase(input);
        }
        String regex = "(?i)" + pattern.replace(".", "\\.").replace("*", ".*");
        return input.matches(regex);
    }
}
