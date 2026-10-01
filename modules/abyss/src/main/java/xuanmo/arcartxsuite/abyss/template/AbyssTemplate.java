package xuanmo.arcartxsuite.abyss.template;

import java.io.File;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 地牢模板：一个地牢目录的完整定义。
 *
 * @param id             地牢 id（目录名）
 * @param name           显示名（可含 & 颜色）
 * @param icon           图标 material（UI 用）
 * @param difficulty     难度标签
 * @param description    描述（可多行）
 * @param worldSource    世界模板来源
 * @param spawn          出生点 "x,y,z,yaw,pitch"
 * @param timeLimitSeconds 时间限制（0=不限）
 * @param timeoutAction  超时动作 KICK/FAIL
 * @param maxInstances   同地牢最大实例数（0=不限）
 * @param requirements   进本条件
 * @param monsters       怪物组配置
 * @param obstacles      障碍物配置
 * @param interacts      交互点配置
 * @param tasks          定时任务配置
 * @param rewards        奖励配置
 * @param scripts        脚本配置
 * @param npcs           NPC 配置
 * @param links          跨模块联动配置（announce/qq/entrance 等）
 * @param functions      functions.js 等自定义函数字符串（可空）
 * @param dir            地牢目录
 */
public record AbyssTemplate(
    String id,
    String name,
    String icon,
    String difficulty,
    List<String> description,
    WorldSource worldSource,
    String spawn,
    int timeLimitSeconds,
    TimeoutAction timeoutAction,
    int maxInstances,
    EntryRequirements requirements,
    WorldRules worldRules,
    MonsterConfig monsters,
    ObstacleConfig obstacles,
    InteractConfig interacts,
    TaskConfig tasks,
    RewardConfig rewards,
    ScriptConfig scripts,
    NpcConfig npcs,
    QuestConfig quests,
    DungeonLinks links,
    @Nullable String functions,
    File dir
) {

    public enum TimeoutAction { KICK, FAIL }

    /**
     * 跨模块联动（声明式，无需脚本）。
     *
     * @param announceClear   通关全服播报文本（null/空=不播报），支持 {player}/{dungeon}/{time}/{party}
     * @param announceForward 播报是否跨服转发
     * @param qqNotifyClear   通关是否推 QQ 群
     * @param qqGroup         QQ 群号（<=0=全部已配置群）
     * @param entrance        地牢入口标记 "world;x,y,z"（map 模块外部目标；null=不推）
     */
    public record DungeonLinks(
        @Nullable String announceClear,
        boolean announceForward,
        boolean qqNotifyClear,
        long qqGroup,
        @Nullable String entrance
    ) {
        public static final DungeonLinks NONE = new DungeonLinks(null, false, false, 0, null);

        public static DungeonLinks load(org.bukkit.configuration.ConfigurationSection s,
                                        org.bukkit.configuration.ConfigurationSection root) {
            if (s == null && root == null) return NONE;
            // entrance 主格式写在 links 节内；顶层 entrance 键作兼容回退
            String entrance = s != null ? s.getString("entrance", null) : null;
            if (entrance == null && root != null) {
                entrance = root.getString("entrance", null);
            }
            if (s == null) {
                return new DungeonLinks(null, false, false, 0, entrance);
            }
            String announce = s.getString("announce-clear", s.getString("announce_clear", null));
            long group = s.getLong("qq-group", s.getLong("qq_group", 0));
            return new DungeonLinks(
                (announce == null || announce.isBlank()) ? null : announce,
                s.getBoolean("announce-forward", s.getBoolean("announce_forward", false)),
                s.getBoolean("qq-notify-clear", s.getBoolean("qq_notify_clear", false)),
                group,
                entrance
            );
        }
    }

    /**
     * 世界模板来源。
     *
     * @param kind FOLDER=世界文件夹 / SCHEMATIC=WE剪贴板文件 / VOID=虚空世界（无模板）
     * @param file 源文件（文件夹或 .schem/.schematic；VOID 时为地牢目录占位）
     */
    public record WorldSource(Kind kind, File file) {
        public enum Kind { FOLDER, SCHEMATIC, VOID }
    }
}
