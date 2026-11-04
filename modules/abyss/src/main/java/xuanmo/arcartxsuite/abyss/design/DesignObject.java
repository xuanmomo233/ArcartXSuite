package xuanmo.arcartxsuite.abyss.design;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 设计对象：设计模式下各类地牢配置的统一可编辑视图。
 * <p>
 * 字段值以 {@code Map<String, Object>} 快照持有（与 YAML 节同构），
 * 编辑直接改快照，保存时整节写回——天然保留未在编辑器暴露的字段。
 * {@link #dirty} 标记本对象相对模板有改动。
 *
 * @param key     唯一键 {@code "<kind>:<id>"}（房内怪组为 {@code "group:<room>.<gid>"}）
 * @param kind    对象类型
 * @param id      YAML 键名（可重命名：保存时以新 id 落盘）
 * @param file    回写目标文件名（{@code interacts.yml} 等）
 * @param path    YAML 内根路径前缀（{@code "rewards"} / {@code "rooms"}；根级为 {@code ""}）
 * @param summary 行摘要（主面板显示）
 * @param anchor  世界内标记点 {@code [x,y,z]}；null=无定位
 * @param box     区域两角 {@code [x1,y1,z1,x2,y2,z2]}；null=非区域对象
 * @param node    归属节点 key（{@code "room:<id>"} / {@code "floor:<id>"} / {@code "misc:<桶>"}）
 * @param values  字段值快照（kebab-case 键，与 YAML 节同构）
 */
public record DesignObject(
    String key,
    Kind kind,
    String id,
    String file,
    String path,
    String summary,
    @Nullable int[] anchor,
    @Nullable int[] box,
    String node,
    Map<String, Object> values,
    boolean dirty
) {

    public enum Kind {
        INTERACT("◆", "交互点", "interacts.yml", "", "245,205,130"),
        OBSTACLE("▣", "障碍物", "obstacles.yml", "", "120,200,200"),
        GROUP("⚔", "怪组", "monsters.yml", "", "232,110,110"),
        ROOM_GROUP("⚔", "房内怪组", "rooms.yml", "rooms", "232,110,110"),
        NPC("♦", "NPC", "npcs.yml", "", "190,140,240"),
        PUZZLE("⚙", "机关", "puzzles.yml", "", "120,160,235"),
        TASK("☰", "任务", "tasks.yml", "", "130,210,140"),
        REWARD("❖", "奖励", "rewards.yml", "rewards", "245,200,110"),
        CHEST("▤", "宝箱", "rewards.yml", "chest-rewards", "230,160,90"),
        ROOM("⌂", "房间", "rooms.yml", "rooms", "170,170,190"),
        FLOOR("⌂", "楼层", "floors.yml", "floors", "170,170,190"),
        STAGE("➜", "阶段", "stages.yml", "", "130,210,140"),
        SETTLEMENT("❖", "结算策略", "config.yml", "", "245,200,110"),
        BUFF("✦", "增益池", "buffs.yml", "pools", "240,150,200"),
        DOCUMENT("≡", "配置文件", "", "", "150,200,220");

        private final String icon;
        private final String label;
        private final String file;
        private final String path;
        /** UI 类型色 "r,g,b"（色条/徽标描边用）。 */
        private final String color;

        Kind(String icon, String label, String file, String path, String color) {
            this.icon = icon;
            this.label = label;
            this.file = file;
            this.path = path;
            this.color = color;
        }

        public String icon() { return icon; }
        public String label() { return label; }
        public String file() { return file; }
        public String path() { return path; }
        public String color() { return color; }

        /** 是否可用"标记杖右键方块"定位。 */
        public boolean pointMarkable() {
            return this == INTERACT || this == NPC || this == CHEST || this == GROUP
                || this == ROOM_GROUP;
        }

        /** 是否可用选区工具圈 box。 */
        public boolean regionMarkable() {
            return this == OBSTACLE || this == INTERACT || this == ROOM;
        }
    }

    /** 字段编辑器类型（UI 按此渲染控件）。 */
    public enum Editor { TEXT, NUMBER, TOGGLE, CYCLE, COORD, REGION, SCRIPT, LIST, YAML }

    /** 字段描述：name=YAML 键 / label=显示名 / editor / options(CYCLE 的可选值)。 */
    public record FieldSpec(String name, String label, Editor editor, List<String> options) {
        static FieldSpec text(String n, String l) { return new FieldSpec(n, l, Editor.TEXT, List.of()); }
        static FieldSpec num(String n, String l) { return new FieldSpec(n, l, Editor.NUMBER, List.of()); }
        static FieldSpec toggle(String n, String l) { return new FieldSpec(n, l, Editor.TOGGLE, List.of()); }
        static FieldSpec cycle(String n, String l, String... opts) {
            return new FieldSpec(n, l, Editor.CYCLE, List.of(opts));
        }
        static FieldSpec coord(String n, String l) { return new FieldSpec(n, l, Editor.COORD, List.of()); }
        static FieldSpec region(String n, String l) { return new FieldSpec(n, l, Editor.REGION, List.of()); }
        static FieldSpec script(String n, String l) { return new FieldSpec(n, l, Editor.SCRIPT, List.of()); }
        static FieldSpec list(String n, String l) { return new FieldSpec(n, l, Editor.LIST, List.of()); }
        static FieldSpec yaml(String n, String l) { return new FieldSpec(n, l, Editor.YAML, List.of()); }
    }

    /** 各类型字段 schema（编辑器显隐/控件由服务端决定，UI 纯渲染）。 */
    public static List<FieldSpec> schema(Kind kind) {
        var base = baseSchema(kind);
        if (kind == Kind.STAGE) return base;
        var hooks = base.stream().filter(field -> field.editor() == Editor.SCRIPT)
            .map(FieldSpec::name).toArray(String[]::new);
        if (hooks.length == 0) return base;
        var fields = new java.util.ArrayList<>(base);
        fields.add(FieldSpec.cycle("design-action-hook", "触发时机", hooks));
        fields.add(FieldSpec.cycle("design-action", "动作类型", "提示", "等待", "刷怪", "开门", "关门",
            "发奖励", "开奖励箱", "设置变量", "跳到阶段", "通关", "判负"));
        fields.add(FieldSpec.text("design-action-arg", "动作目标、文案或等待秒数"));
        fields.add(FieldSpec.text("design-action-value", "变量值"));
        fields.add(FieldSpec.cycle("design-action-condition", "动作条件", "无条件", "地图清空", "怪组清空", "房间清空", "变量成立"));
        fields.add(FieldSpec.text("design-action-condition-ref", "条件目标或变量"));
        fields.add(FieldSpec.cycle("design-append-action", "追加到动作列表", "添加动作"));
        return List.copyOf(fields);
    }

    private static List<FieldSpec> baseSchema(Kind kind) {
        return switch (kind) {
            case INTERACT -> List.of(
                FieldSpec.cycle("type", "触发方式",
                    "RIGHT_CLICK_BLOCK", "LEFT_CLICK_BLOCK", "BREAK_BLOCK",
                    "PLACE_BLOCK", "STEP_ON", "ENTER_REGION", "RIGHT_CLICK_ENTITY"),
                FieldSpec.coord("location", "点位"),
                FieldSpec.region("region", "区域"),
                FieldSpec.text("required-block", "目标方块"),
                FieldSpec.text("required-item", "需求物品"),
                FieldSpec.text("entity-name", "实体名"),
                FieldSpec.toggle("once", "一次性"),
                FieldSpec.text("fail-message", "失败提示"),
                FieldSpec.list("conditions", "触发条件"),
                FieldSpec.script("script", "触发脚本"));
            case OBSTACLE -> List.of(
                FieldSpec.list("blocks", "方块清单"),
                FieldSpec.toggle("auto-create", "进本自动生成"),
                FieldSpec.cycle("trigger-mode", "触发模式", "NONE", "ENTER", "LEAVE", "PERSIST"),
                FieldSpec.num("trigger-cooldown", "触发冷却(秒)"),
                FieldSpec.toggle("trigger-once", "触发一次"),
                FieldSpec.script("trigger-script", "触发脚本"),
                FieldSpec.script("on-create", "生成时"),
                FieldSpec.script("on-delete", "消除时"));
            case GROUP, ROOM_GROUP -> List.of(
                FieldSpec.cycle("spawn-timing", "刷新时机",
                    "AUTO_START", "DELAYED", "TRIGGERED", "MANUAL", "SCRIPTED"),
                FieldSpec.num("delay-seconds", "延迟(秒)"),
                FieldSpec.list("trigger-after", "前置组"),
                // monsters 是 List<Map>（mob-id/location/amount/level），
                // 不走行编辑；编辑器上此字段渲染为"标记追加刷怪点"按钮。
                FieldSpec.coord("spawn-point", "追加刷怪点"),
                FieldSpec.yaml("monsters", "怪物条目(JSON对象)"),
                FieldSpec.num("complete-when-alive-below", "完成存活线"),
                FieldSpec.list("plans", "限定方案"),
                FieldSpec.script("on-start", "开刷时"),
                FieldSpec.script("on-end", "清完时"));
            case NPC -> List.of(
                FieldSpec.text("name", "显示名"),
                FieldSpec.cycle("type", "类型",
                    "PLAYER", "VILLAGER", "ZOMBIE", "SKELETON", "WITCH", "CREEPER",
                    "ENDERMAN", "BLAZE", "PILLAGER", "WANDERING_TRADER", "IRON_GOLEM"),
                FieldSpec.coord("location", "站位(x,y,z,yaw,pitch)"),
                FieldSpec.text("skin", "皮肤"),
                FieldSpec.toggle("look-at-player", "看向玩家"),
                FieldSpec.list("dialogs", "对话"),
                FieldSpec.num("dialog-interval", "对话间隔(tick)"),
                FieldSpec.script("click-script", "点击脚本"),
                FieldSpec.toggle("once", "一次性"));
            case PUZZLE -> List.of(
                FieldSpec.text("name", "显示名"),
                FieldSpec.cycle("type", "类型", "sequence", "input"),
                FieldSpec.list("steps", "步骤点(interact名)"),
                FieldSpec.toggle("reset-on-miss", "错序重置"),
                FieldSpec.text("answer", "答案(input)"),
                FieldSpec.toggle("case-sensitive", "大小写敏感"),
                FieldSpec.num("length", "输入长度"),
                FieldSpec.text("input-pattern", "输入正则"),
                FieldSpec.text("title", "面板标题"),
                FieldSpec.text("hint", "输入提示"),
                FieldSpec.toggle("masked", "掩码"),
                FieldSpec.num("max-attempts", "最大错误次数"),
                FieldSpec.toggle("per-player", "独立计次"),
                FieldSpec.script("on-step", "每步正确"),
                FieldSpec.script("on-miss", "错误时"),
                FieldSpec.script("on-complete", "完成时"),
                FieldSpec.script("on-fail", "失败时"));
            case TASK -> List.of(
                FieldSpec.cycle("type", "类型", "TIMED", "INTERVAL", "EVENT"),
                FieldSpec.list("times", "触发秒点"),
                FieldSpec.num("interval", "间隔(秒)"),
                FieldSpec.text("event", "事件名"),
                FieldSpec.script("script", "脚本"));
            case REWARD -> List.of(
                FieldSpec.cycle("type", "奖励类型", "fixed", "random"),
                FieldSpec.num("picks", "抽取次数"),
                FieldSpec.yaml("pool", "奖励权重池(JSON对象)"),
                FieldSpec.list("items", "物品"),
                FieldSpec.list("mythic-items", "Mythic物品"),
                FieldSpec.num("money", "金币"),
                FieldSpec.text("currency", "货币"),
                FieldSpec.num("exp", "经验"),
                FieldSpec.list("commands", "命令"));
            case CHEST -> List.of(
                FieldSpec.text("title", "界面标题"),
                FieldSpec.coord("location", "宝箱点位"),
                FieldSpec.num("rows", "行数(1-6)"),
                FieldSpec.yaml("options", "宝箱选项(JSON对象)"));
            case ROOM -> List.of(
                FieldSpec.cycle("type", "类型", "combat", "safe", "boss", "shop"),
                FieldSpec.coord("origin", "原点"),
                FieldSpec.region("region", "房间区"),
                FieldSpec.coord("spawn", "进房站位"),
                FieldSpec.region("exit", "出口区域"),
                FieldSpec.yaml("doors", "门盒(JSON对象)"),
                FieldSpec.script("on-enter", "进房时"),
                FieldSpec.script("on-lock", "锁门时"),
                FieldSpec.script("on-clear", "清完时"));
            case FLOOR -> List.of(
                FieldSpec.text("name", "楼层名称"),
                FieldSpec.num("design-order", "楼层顺序"),
                FieldSpec.text("spawn", "入口坐标"),
                FieldSpec.cycle("design-room-ref", "可用房间"),
                FieldSpec.cycle("design-append-room", "追加到路线", "添加房间"),
                FieldSpec.list("rooms", "房间顺序（每行一个房间ID）"),
                FieldSpec.cycle("boss-room", "Boss 房间"),
                FieldSpec.cycle("advance", "进下一层", "portal", "auto"),
                FieldSpec.num("advance-timeout", "出口等待上限（秒）"),
                FieldSpec.script("on-enter", "进入本层"),
                FieldSpec.script("on-clear", "清空本层"));
            case STAGE -> List.of(
                FieldSpec.text("name", "阶段名称"),
                FieldSpec.toggle("design-entry", "设为入口阶段"),
                FieldSpec.num("timeout", "阶段超时（秒）"),
                FieldSpec.cycle("design-condition", "完成条件",
                    "手动推进", "地图清空", "怪组清空", "房间清空", "变量成立"),
                FieldSpec.text("design-condition-ref", "条件目标ID或变量"),
                FieldSpec.cycle("design-action-hook", "动作时机", "on-start", "on-complete", "on-timeout"),
                FieldSpec.cycle("design-action", "动作类型", "提示", "等待", "刷怪", "开门", "关门",
                    "发奖励", "开奖励箱", "设置变量", "通关", "判负"),
                FieldSpec.text("design-action-arg", "动作目标、文案或等待秒数"),
                FieldSpec.text("design-action-value", "变量值"),
                FieldSpec.cycle("design-append-action", "追加到动作列表", "添加动作"),
                FieldSpec.text("design-branch-target", "后继阶段ID"),
                FieldSpec.cycle("design-branch-condition", "分支条件", "无条件", "地图清空", "怪组清空", "房间清空", "变量成立"),
                FieldSpec.text("design-branch-ref", "分支条件目标ID或变量"),
                FieldSpec.cycle("design-append-branch", "追加到分支列表", "添加分支"),
                FieldSpec.script("on-start", "进入动作"),
                FieldSpec.script("on-complete", "完成动作"),
                FieldSpec.script("on-timeout", "超时动作"),
                FieldSpec.yaml("next", "分支顺序"));
            case SETTLEMENT -> List.of(
                FieldSpec.cycle("exit-mode", "离开方式", "IMMEDIATE", "COUNTDOWN", "MANUAL"),
                FieldSpec.num("exit-delay-seconds", "倒计时（秒）"),
                FieldSpec.num("manual-timeout-seconds", "手动离开上限（秒）"),
                FieldSpec.toggle("show-stats", "显示结算"),
                FieldSpec.toggle("show-stats-after-exit", "退出后再显示"),
                FieldSpec.toggle("protect-players", "保护玩家"),
                FieldSpec.toggle("revive-players", "复活旁观者"),
                FieldSpec.num("script-timeout-seconds", "脚本收尾上限（秒）"));
            case BUFF -> List.of(
                FieldSpec.text("name", "池名"),
                FieldSpec.num("pick", "选取数"),
                FieldSpec.num("candidates", "候选数"),
                FieldSpec.cycle("presentation", "呈现", "panel", "hud", "random"),
                FieldSpec.num("timeout", "超时(秒)"),
                FieldSpec.yaml("options", "增益选项(JSON对象)"));
            case DOCUMENT -> List.of(FieldSpec.list("content", "YAML文件行(保留缩进，保存时校验)"));
        };
    }

    /** 生成改脏后的新对象。 */
    public DesignObject withValue(String field, Object value) {
        Map<String, Object> next = DesignValues.copy(values);
        next.remove(field.replace('-', '_'));
        if (value == null) {
            next.remove(field);
        } else {
            next.put(field, value);
        }
        return new DesignObject(key, kind, id, file, path, summary, anchor, box, node,
            next, true);
    }

    /** 更新定位（anchor/box/node/summary 重算由调用方负责传入）。 */
    public DesignObject withLocation(@Nullable int[] newAnchor, @Nullable int[] newBox,
                                     String newNode, String newSummary) {
        Map<String, Object> next = new LinkedHashMap<>(values);
        if (newAnchor != null && kind == Kind.INTERACT) {
            next.put("location", newAnchor[0] + "," + newAnchor[1] + "," + newAnchor[2]);
        }
        if (newBox != null) {
            next.put("region", List.of(
                newBox[0] + "," + newBox[1] + "," + newBox[2],
                newBox[3] + "," + newBox[4] + "," + newBox[5]));
        }
        return new DesignObject(key, kind, id, file, path, newSummary, newAnchor, newBox,
            newNode, next, true);
    }

    /** 重命名（key 不变，id 变——写回时旧键删除新键落盘由 Writer 处理）。 */
    public DesignObject withId(String newId) {
        return new DesignObject(key, kind, newId, file, path, summary, anchor, box, node,
            values, true);
    }

    /** 改归属节点（交叉引用吸附用，不标脏——归属只是展示层分组）。 */
    public DesignObject withNode(String newNode) {
        return new DesignObject(key, kind, id, file, path, summary, anchor, box,
            newNode, values, dirty);
    }

    public List<String> strList(String field) {
        Object v = values.get(field);
        if (v == null) v = values.get(field.replace('-', '_'));
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object o : l) out.add(String.valueOf(o));
            return out;
        }
        return v != null ? List.of(String.valueOf(v)) : List.of();
    }

    public String str(String field) {
        Object v = values.get(field);
        if (v == null) v = values.get(field.replace('-', '_'));
        return v != null ? String.valueOf(v) : "";
    }
}
