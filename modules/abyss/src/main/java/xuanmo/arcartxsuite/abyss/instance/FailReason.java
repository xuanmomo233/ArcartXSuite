package xuanmo.arcartxsuite.abyss.instance;

/** 副本失败原因。 */
public enum FailReason {
    /** 全队死亡 */
    TEAM_WIPE,
    /** 时间限制超时 */
    TIMEOUT,
    /** 玩家逃逸（防逃逸模式 FAIL） */
    ESCAPE,
    /** 脚本主动判负 fail_dungeon */
    SCRIPTED,
    /** 世界/内部错误 */
    ERROR,
    /** 管理员/插件强制判负 */
    ADMIN
}
