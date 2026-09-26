package xuanmo.arcartxsuite.abyss.instance;

/** 玩家离开副本原因。 */
public enum LeaveReason {
    /** 主动退出（命令/脚本） */
    QUIT,
    /** 断线 */
    DISCONNECT,
    /** 副本结束（通关/失败/取消统一撤出） */
    END,
    /** 超时踢出 */
    TIMEOUT_KICK,
    /** 管理员移除 */
    ADMIN
}
