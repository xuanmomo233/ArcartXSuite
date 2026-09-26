package xuanmo.arcartxsuite.abyss.instance;

/** 副本实例状态机。 */
public enum AbyssStatus {
    /** 准备中：世界已建/正在加载，init 脚本执行，等待全员进图 */
    PREPARING,
    /** 进行中 */
    ACTIVE,
    /** 已通关 */
    COMPLETED,
    /** 失败 */
    FAILED,
    /** 取消（无人/管理命令/插件卸载） */
    CANCELLED
}
