package xuanmo.arcartxsuite.regions.model;

import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 区域标志枚举 — 完整版 (40+ flags)。
 * <p>
 * 每个标志有 ALLOW / DENY / NONE 三态。NONE 表示未设置(继承父区域或全局)。
 */
public enum RegionFlag {

    // ─── 战斗 ───
    PVP("pvp", Category.COMBAT, "玩家间伤害"),
    MOB_DAMAGE("mob-damage", Category.COMBAT, "怪物对玩家伤害"),
    DAMAGE_ANIMALS("damage-animals", Category.COMBAT, "玩家伤害动物"),
    INVINCIBILITY("invincibility", Category.COMBAT, "玩家无敌"),
    FALL_DAMAGE("fall-damage", Category.COMBAT, "摔落伤害"),

    // ─── 方块 ───
    BLOCK_BREAK("block-break", Category.BLOCK, "方块破坏"),
    BLOCK_PLACE("block-place", Category.BLOCK, "方块放置"),
    USE("use", Category.BLOCK, "右键交互(门/按钮/拉杆等)"),
    CHEST_ACCESS("chest-access", Category.BLOCK, "容器访问"),
    TRAMPLE("trample", Category.BLOCK, "踩踏耕地"),
    VEHICLE_DESTROY("vehicle-destroy", Category.BLOCK, "破坏矿车/船"),
    VEHICLE_PLACE("vehicle-place", Category.BLOCK, "放置矿车/船"),

    // ─── 环境 ───
    TNT("tnt", Category.ENVIRONMENT, "TNT 爆炸"),
    CREEPER_EXPLOSION("creeper-explosion", Category.ENVIRONMENT, "苦力怕爆炸"),
    OTHER_EXPLOSION("other-explosion", Category.ENVIRONMENT, "其他爆炸(末影水晶等)"),
    FIRE_SPREAD("fire-spread", Category.ENVIRONMENT, "火焰蔓延"),
    LAVA_FIRE("lava-fire", Category.ENVIRONMENT, "岩浆点火"),
    LIGHTNING("lightning", Category.ENVIRONMENT, "雷击起火"),
    SNOW_FALL("snow-fall", Category.ENVIRONMENT, "雪覆盖"),
    SNOW_MELT("snow-melt", Category.ENVIRONMENT, "雪融化"),
    ICE_FORM("ice-form", Category.ENVIRONMENT, "冰生成"),
    ICE_MELT("ice-melt", Category.ENVIRONMENT, "冰融化"),
    LEAF_DECAY("leaf-decay", Category.ENVIRONMENT, "树叶腐烂"),
    GRASS_SPREAD("grass-spread", Category.ENVIRONMENT, "草方块蔓延"),
    MUSHROOM_SPREAD("mushroom-spread", Category.ENVIRONMENT, "蘑菇蔓延"),
    VINE_GROWTH("vine-growth", Category.ENVIRONMENT, "藤蔓生长"),
    CROP_GROWTH("crop-growth", Category.ENVIRONMENT, "作物生长"),
    WATER_FLOW("water-flow", Category.ENVIRONMENT, "水流动"),
    LAVA_FLOW("lava-flow", Category.ENVIRONMENT, "岩浆流动"),
    PISTONS("pistons", Category.ENVIRONMENT, "活塞移动"),
    SOIL_DRY("soil-dry", Category.ENVIRONMENT, "耕地干燥"),

    // ─── 生物 ───
    MOB_SPAWNING("mob-spawning", Category.MOB, "怪物生成"),
    ANIMAL_SPAWNING("animal-spawning", Category.MOB, "动物生成"),
    ENDERMAN_GRIEF("enderman-grief", Category.MOB, "末影人搬方块"),
    GHAST_FIREBALL("ghast-fireball", Category.MOB, "恶魂火球"),
    WITHER_DAMAGE("wither-damage", Category.MOB, "凋灵破坏"),

    // ─── 玩家 ───
    ENTRY("entry", Category.PLAYER, "玩家进入"),
    EXIT("exit", Category.PLAYER, "玩家离开"),
    ENDERPEARL("enderpearl", Category.PLAYER, "末影珍珠传送"),
    CHORUS_FRUIT("chorus-fruit", Category.PLAYER, "紫颂果传送"),
    ITEM_DROP("item-drop", Category.PLAYER, "丢弃物品"),
    ITEM_PICKUP("item-pickup", Category.PLAYER, "拾取物品"),
    EXP_DROP("exp-drop", Category.PLAYER, "经验球掉落"),
    HUNGER("hunger", Category.PLAYER, "饥饿消耗"),
    HEAL("heal", Category.PLAYER, "自然恢复"),
    FLY("fly", Category.PLAYER, "允许飞行"),
    RIDE("ride", Category.PLAYER, "骑乘实体"),
    SLEEP("sleep", Category.PLAYER, "使用床"),
    RESPAWN_ANCHORS("respawn-anchors", Category.PLAYER, "重生锚"),

    // ─── 杂项 ───
    NOTIFY_ENTER("notify-enter", Category.MISC, "进入时通知区域成员"),
    NOTIFY_EXIT("notify-exit", Category.MISC, "离开时通知区域成员"),
    GREETING("greeting", Category.MISC, "进入消息(自定义文本)"),
    FAREWELL("farewell", Category.MISC, "离开消息(自定义文本)"),
    POTION_SPLASH("potion-splash", Category.MISC, "药水瓶投掷"),
    SEND_CHAT("send-chat", Category.MISC, "发送聊天"),
    RECEIVE_CHAT("receive-chat", Category.MISC, "接收聊天");

    private final String configKey;
    private final Category category;
    private final String description;

    RegionFlag(String configKey, Category category, String description) {
        this.configKey = configKey;
        this.category = category;
        this.description = description;
    }

    public String configKey() { return configKey; }
    public Category category() { return category; }
    public String description() { return description; }

    private static final Map<String, RegionFlag> BY_KEY = new LinkedHashMap<>();
    static {
        for (RegionFlag flag : values()) {
            BY_KEY.put(flag.configKey, flag);
        }
    }

    public static RegionFlag fromKey(String key) {
        return key == null ? null : BY_KEY.get(key.toLowerCase(Locale.ROOT));
    }

    public static Map<String, RegionFlag> all() {
        return BY_KEY;
    }

    public enum Category {
        COMBAT, BLOCK, ENVIRONMENT, MOB, PLAYER, MISC
    }

    /**
     * 标志的三态值。
     */
    public enum State {
        ALLOW, DENY, NONE;

        public static State fromString(String s) {
            if (s == null) return NONE;
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "allow", "true", "yes" -> ALLOW;
                case "deny", "false", "no" -> DENY;
                default -> NONE;
            };
        }
    }
}
