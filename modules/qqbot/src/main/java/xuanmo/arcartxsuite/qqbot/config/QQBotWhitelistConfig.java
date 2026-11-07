package xuanmo.arcartxsuite.qqbot.config;

/**
 * 独立的群管理白名单命令配置，不随认证绑定自动增删白名单。
 */
public record QQBotWhitelistConfig(
    /** 白名单联动是否启用 */
    boolean enabled,
    /** 添加白名单的命令模板，{@code {name}} 占位符替换为玩家名 */
    String addCommand,
    /** 移除白名单的命令模板，{@code {name}} 占位符替换为玩家名 */
    String removeCommand,
    /** 群内添加白名单的指令前缀 */
    String addPrefix,
    /** 群内移除白名单的指令前缀 */
    String removePrefix,
    /** 群内查询白名单列表的指令前缀 */
    String listPrefix
) {}
