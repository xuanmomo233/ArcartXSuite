package xuanmo.arcartxsuite.abyss.design;

/** Translates form selections into the existing StageConfig and built-in action contracts. */
final class StageForm {
    private StageForm() { }

    static String[] actionNames() {
        return new String[]{"提示", "等待", "刷怪", "清除怪组", "开门", "关门", "发奖励", "开奖励箱",
            "设置变量", "增减变量", "随机整数", "变量守卫", "调用流程段", "延时调用流程段", "跳到阶段",
            "标题", "动作栏", "音效", "烟花", "首领条", "导航点", "清除导航", "打开商店",
            "选择增益", "推进任务", "发放物品", "药水效果", "属性修饰", "治疗", "复活", "传送",
            "设置方块", "填充方块", "通关", "判负"};
    }

    static DesignObject.Kind actionTarget(String action) {
        return switch (action) {
            case "刷怪" -> DesignObject.Kind.GROUP;
            case "清除怪组" -> DesignObject.Kind.GROUP;
            case "开门", "关门" -> DesignObject.Kind.ROOM;
            case "发奖励" -> DesignObject.Kind.REWARD;
            case "开奖励箱" -> DesignObject.Kind.CHEST;
            case "跳到阶段" -> DesignObject.Kind.STAGE;
            case "调用流程段" -> DesignObject.Kind.SCRIPT;
            case "选择增益" -> DesignObject.Kind.BUFF;
            default -> null;
        };
    }

    static String parameterLabel(String action, String field) {
        int index = switch (field) {
            case "design-action-arg" -> 0;
            case "design-action-value" -> 1;
            case "design-action-extra-1" -> 2;
            case "design-action-extra-2" -> 3;
            case "design-action-extra-3" -> 4;
            default -> -1;
        };
        if (index < 0) return "";
        String[] labels = switch (action) {
            case "", "提示", "动作栏" -> new String[]{"提示文字"};
            case "等待" -> new String[]{"秒数"};
            case "设置变量", "增减变量" -> new String[]{"变量名", "数值或文本"};
            case "随机整数" -> new String[]{"变量名", "最小值", "最大值"};
            case "变量守卫" -> new String[]{"变量名", "比较符", "比较值"};
            case "延时调用流程段" -> new String[]{"秒数", "自定义流程段"};
            case "标题" -> new String[]{"主标题", "副标题", "淡入 tick", "停留 tick", "淡出 tick"};
            case "音效" -> new String[]{"音效 ID", "音量", "音调"};
            case "烟花" -> new String[]{"烟花类型", "颜色"};
            case "首领条" -> new String[]{"标题", "持续秒数", "颜色", "样式"};
            case "导航点" -> new String[]{"导航点 ID", "坐标 x,y,z", "到达半径", "提示文字"};
            case "发放物品" -> new String[]{"物品来源", "物品 ID", "数量"};
            case "药水效果" -> new String[]{"效果 ID", "持续秒数", "等级"};
            case "属性修饰" -> new String[]{"属性 ID", "数值", "flat 或 percent"};
            case "治疗" -> new String[]{"生命值"};
            case "传送" -> new String[]{"坐标 x,y,z"};
            case "设置方块" -> new String[]{"坐标 x,y,z", "方块"};
            case "填充方块" -> new String[]{"起点 x,y,z", "终点 x,y,z", "方块"};
            case "复活", "通关", "判负" -> new String[0];
            default -> new String[]{"目标 ID"};
        };
        return index < labels.length ? labels[index] : null;
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
        String value = stage.str("design-action-value").trim();
        String third = stage.str("design-action-extra-1").trim();
        String fourth = stage.str("design-action-extra-2").trim();
        String fifth = stage.str("design-action-extra-3").trim();
        String selector = stage.str("design-action-selector");
        if (selector.isBlank()) selector = "@all";
        if (!java.util.Set.of("@all", "@trigger", "@random", "@dead").contains(selector))
            throw new IllegalArgumentException("请选择副本内的执行对象");
        return switch (stage.str("design-action")) {
            case "", "提示" -> "message " + selector + " " + text(argument);
            case "等待" -> "wait " + seconds(argument);
            case "刷怪" -> "spawn_group " + identifier(argument);
            case "清除怪组" -> "kill_group " + identifier(argument);
            case "开门" -> "room_unlock " + identifier(argument);
            case "关门" -> "room_lock " + identifier(argument);
            case "发奖励" -> "grant_reward " + selector + " " + identifier(argument);
            case "开奖励箱" -> "open_chest_ui " + selector + " " + identifier(argument);
            case "跳到阶段" -> "flow_jump " + identifier(argument);
            case "设置变量" -> "setvar " + identifier(argument) + " " + text(stage.str("design-action-value"));
            case "增减变量" -> "addvar " + identifier(argument) + " " + number(value, -1e9, 1e9);
            case "随机整数" -> "random " + identifier(argument) + " " + integer(value, -1000000, 1000000)
                + " " + integer(third, -1000000, 1000000);
            case "变量守卫" -> "if_var " + identifier(argument) + " " + option(value, "==", "!=", ">=", "<=", ">", "<")
                + " " + text(third);
            case "调用流程段" -> "run_script " + identifier(argument);
            case "延时调用流程段" -> "delay " + seconds(argument) + " run_script " + identifier(value);
            case "标题" -> "title " + selector + " " + integer(third, 0, 1200) + " "
                + integer(fourth, 0, 1200) + " " + integer(fifth, 0, 1200) + " "
                + text(argument.replace('|', ' ')) + "|" + (value.isBlank() ? "" : text(value));
            case "动作栏" -> "actionbar " + selector + " " + text(argument);
            case "音效" -> "sound " + selector + " " + enumName(org.bukkit.Sound.class, argument) + " "
                + number(value, 0, 16) + " " + number(third, 0.01, 2);
            case "烟花" -> "firework " + selector + " " + enumName(org.bukkit.FireworkEffect.Type.class, argument)
                + " " + identifier(value);
            case "首领条" -> "bossbar " + selector + " " + seconds(value) + " "
                + enumName(org.bukkit.boss.BarColor.class, third) + " " + enumName(org.bukkit.boss.BarStyle.class, fourth)
                + " " + text(argument);
            case "导航点" -> "guide_point " + identifier(argument) + " " + point(value) + " "
                + number(third, 0, 256) + " " + text(fourth);
            case "清除导航" -> "guide_clear " + identifier(argument);
            case "打开商店" -> "open_shop_ui " + selector + " " + identifier(argument);
            case "选择增益" -> "buff_offer " + identifier(argument);
            case "推进任务" -> "quest_step " + identifier(argument);
            case "发放物品" -> "give_item " + selector + " " + item(argument, value, third);
            case "药水效果" -> "apply_potion " + selector + " " + identifier(argument) + " "
                + seconds(value) + " " + integer(third, 1, 255);
            case "属性修饰" -> "apply_attribute " + selector + " " + identifier(argument) + " "
                + number(value, -1e9, 1e9) + " " + option(third, "flat", "percent");
            case "治疗" -> "heal " + selector + " " + number(argument, 0, 2048);
            case "复活" -> "revive_player " + selector;
            case "传送" -> "teleport " + selector + " " + point(argument);
            case "设置方块" -> "setblock " + point(argument) + " " + material(value);
            case "填充方块" -> "fillblocks " + point(argument) + " " + point(value) + " " + material(third);
            case "通关" -> "complete_dungeon";
            case "判负" -> "fail_dungeon";
            default -> throw new IllegalArgumentException("动作类型无效");
        };
    }

    private static String option(String input, String... values) {
        if (!java.util.List.of(values).contains(input)) throw new IllegalArgumentException("请从允许的参数中选择");
        return input;
    }

    private static <E extends Enum<E>> String enumName(Class<E> type, String raw) {
        try { return Enum.valueOf(type, raw.toUpperCase(java.util.Locale.ROOT)).name(); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("无效的类型：" + raw); }
    }

    private static String point(String raw) {
        if (raw.split(",", -1).length != 3 || DesignCoords.point(raw) == null)
            throw new IllegalArgumentException("请输入有效的 x,y,z 坐标");
        return raw.replace(" ", "");
    }

    private static String material(String input) {
        var value = org.bukkit.Material.matchMaterial(input);
        if (value == null || !value.isBlock()) throw new IllegalArgumentException("请选择有效的方块");
        return value.name();
    }

    private static String item(String provider, String id, String count) {
        return option(provider, "material", "mythic", "neige", "overture")
            + ":" + identifier(id) + ":" + integer(count, 1, 4096);
    }

    private static String number(String input, double min, double max) {
        try {
            double value = Double.parseDouble(input);
            if (Double.isFinite(value) && value >= min && value <= max) return input;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("数值超出范围：" + min + " 至 " + max);
    }

    private static int integer(String input, int min, int max) {
        try {
            int value = Integer.parseInt(input);
            if (value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("请输入 " + min + " 至 " + max + " 的整数");
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
