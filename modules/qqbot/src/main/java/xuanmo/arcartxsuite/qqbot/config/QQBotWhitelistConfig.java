package xuanmo.arcartxsuite.qqbot.config;

/**
 * QQBot 模块的白名单联动配置 record。
 * <p>
 * 关联模块：{@code qqbot}。控制绑定 / 解绑时是否自动操作 Minecraft 白名单，
 * 定义白名单增删命令模板及群内管理指令前缀。
 */
public record QQBotWhitelistConfig(
    /** 白名单联动是否启用 */
    boolean enabled,
    /** 绑定成功时是否自动将玩家加入白名单 */
    boolean autoAddOnBind,
    /** 解绑时是否自动将玩家移出白名单 */
    boolean autoRemoveOnUnbind,
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
