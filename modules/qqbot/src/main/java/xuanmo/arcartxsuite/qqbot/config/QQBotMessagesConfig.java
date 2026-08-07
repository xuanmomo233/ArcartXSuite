package xuanmo.arcartxsuite.qqbot.config;

/**
 * QQBot 模块的全部可定制消息文本配置 record。
 * <p>
 * 关联模块：{@code qqbot}。集中定义绑定 / 解绑、白名单、签到积分、
 * 商店兑换、黑名单、帮助等场景下发送给 QQ 群与游戏内的消息模板，
 * 支持以 {@code &} 颜色码和 {@code {占位符}} 形式嵌入动态内容。
 */
public record QQBotMessagesConfig(
    /** 消息前缀，统一加在所有 QQBot 消息最前 */
    String prefix,
    /** 验证码生成后发送给 QQ 群的提示文本 */
    String bindCodeSent,
    /** 绑定成功后发送给 QQ 群的提示文本 */
    String bindSuccess,
    /** 绑定成功后发送给游戏内的提示文本 */
    String bindSuccessGame,
    /** QQ 已被其他玩家绑定时发送的提示文本 */
    String alreadyBound,
    /** 解绑成功后发送给 QQ 群的提示文本 */
    String unbindSuccess,
    /** 解绑成功后发送给游戏内的提示文本 */
    String unbindSuccessGame,
    /** 玩家未绑定 QQ 时的提示文本 */
    String notBound,
    /** 验证码过期时的提示文本 */
    String codeExpired,
    /** 验证码无效时的提示文本 */
    String codeInvalid,
    /** 白名单添加成功的提示文本 */
    String whitelistAdded,
    /** 白名单移除成功的提示文本 */
    String whitelistRemoved,
    /** OneBot 客户端连接成功的提示文本 */
    String botConnected,
    /** OneBot 客户端断开连接的提示文本 */
    String botDisconnected,
    /** 无权限执行操作时的提示文本 */
    String noPermission,
    /** 未找到对应玩家时的提示文本 */
    String playerNotFound,
    /** 自定义命令执行成功的提示文本 */
    String commandExecuted,
    /** 管理命令执行成功的提示文本 */
    String adminCommandExecuted,
    /** 管理命令执行失败的提示文本 */
    String adminCommandFailed,
    // ─── 签到积分 ───
    /** 签到成功的提示文本 */
    String signInSuccess,
    /** 当日已签到的提示文本 */
    String signInAlready,
    /** 积分查询的提示文本 */
    String pointsQuery,
    /** 商店列表表头文本 */
    String shopHeader,
    /** 商店列表单条目文本 */
    String shopItem,
    /** 商店列表表尾文本 */
    String shopFooter,
    /** 兑换成功的提示文本 */
    String redeemSuccess,
    /** 积分不足时的提示文本 */
    String redeemNoPoints,
    /** 兑换奖品不存在时的提示文本 */
    String redeemNotFound,
    /** 未绑定 QQ 无法兑换时的提示文本 */
    String redeemNotBound,
    /** 达到兑换次数上限时的提示文本 */
    String redeemLimitReached,
    /** 兑换邮件发送失败时的提示文本 */
    String redeemMailFailed,
    /** 命中黑名单被拦截时的提示文本 */
    String blacklistBlocked,
    /** 黑名单添加成功的提示文本 */
    String blacklistAdded,
    /** 黑名单移除成功的提示文本 */
    String blacklistRemoved,
    /** QQ 已在黑名单中时的提示文本 */
    String blacklistAlready,
    /** QQ 不在黑名单中时的提示文本 */
    String blacklistNotFound,
    /** 黑名单列表表头文本 */
    String blacklistListHeader,
    /** 黑名单列表单条目文本 */
    String blacklistListItem,
    /** 黑名单为空时的提示文本 */
    String blacklistListEmpty,
    /** 无权限操作黑名单时的提示文本 */
    String blacklistNoPermission,
    // ─── 帮助 ───
    /** 帮助信息表头文本 */
    String helpHeader,
    /** 帮助信息表尾文本 */
    String helpFooter
) {}
