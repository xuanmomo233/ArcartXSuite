package xuanmo.arcartxsuite.abyss.design;

/** Translates form selections into the existing StageConfig and built-in action contracts. */
final class StageForm {
    private StageForm() { }

    static DesignObject.Kind actionTarget(String action) {
        return switch (action) {
            case "刷怪" -> DesignObject.Kind.GROUP;
            case "开门", "关门" -> DesignObject.Kind.ROOM;
            case "发奖励" -> DesignObject.Kind.REWARD;
            case "开奖励箱" -> DesignObject.Kind.CHEST;
            default -> null;
        };
    }

    static String condition(String kind, String reference) {
        return switch (kind) {
            case "", "手动推进", "无条件" -> "";
            case "地图清空" -> "var:map_cleared";
            case "怪组清空" -> "group_cleared:" + identifier(reference);
            case "房间清空" -> "room_cleared:" + identifier(reference);
            case "变量成立" -> "var:" + identifier(reference);
            default -> throw new IllegalArgumentException("条件类型无效");
        };
    }

    static String action(DesignObject stage) {
        String argument = stage.str("design-action-arg").trim();
        return switch (stage.str("design-action")) {
            case "", "提示" -> "message @all " + text(argument);
            case "等待" -> "wait " + seconds(argument);
            case "刷怪" -> "spawn_group " + identifier(argument);
            case "开门" -> "room_unlock " + identifier(argument);
            case "关门" -> "room_lock " + identifier(argument);
            case "发奖励" -> "grant_reward @all " + identifier(argument);
            case "开奖励箱" -> "open_chest_ui @all " + identifier(argument);
            case "设置变量" -> "setvar " + identifier(argument) + " " + text(stage.str("design-action-value"));
            case "通关" -> "complete_dungeon";
            case "判负" -> "fail_dungeon";
            default -> throw new IllegalArgumentException("动作类型无效");
        };
    }

    private static String identifier(String raw) {
        String value = raw.trim();
        if (!value.matches("[\\p{L}\\p{N}_.-]{1,64}")) throw new IllegalArgumentException("请输入有效的目标ID");
        return value;
    }

    private static String text(String value) {
        if (value.isBlank() || value.length() > 512 || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("请输入有效的单行文本");
        return value;
    }

    private static int seconds(String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value >= 1 && value <= 3600) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("等待必须为1到3600秒");
    }
}
