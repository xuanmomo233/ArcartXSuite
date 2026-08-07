package xuanmo.arcartxsuite.qqbot.config;

import java.util.List;

/**
 * QQBot 模块的自定义命令配置 record。
 * <p>
 * 关联模块：{@code qqbot}。描述一条可在 QQ 群内触发的自定义命令，
 * 支持内置功能、服务器命令、PlaceholderAPI 查询三种类型，
 * 并可声明所需权限等级、用法说明、占位符列表及输出格式。
 */
public record QQBotCustomCommand(
    /** 命令名称（键名） */
    String name,
    /** 所需权限等级，{@code 0} 为全员可用 */
    int permission,
    /** 命令类型：{@code builtin}、{@code server-command}、{@code papi-query} */
    String type,
    /** 内置命令标识，仅当类型为 {@code builtin} 时有效 */
    String builtinId,
    /** 服务器命令模板，仅当类型为 {@code server-command} 时有效 */
    String command,
    /** 用法说明文本 */
    String usage,
    /** 占位符列表，用于替换命令模板中的变量 */
    List<String> placeholders,
    /** 输出格式模板 */
    String format
) {
    /**
     * 判断该命令是否为内置类型。
     *
     * @return {@code true} 表示类型为 {@code builtin}
     */
    public boolean isBuiltin() {
        return "builtin".equalsIgnoreCase(type);
    }

    /**
     * 判断该命令是否为服务器命令类型。
     *
     * @return {@code true} 表示类型为 {@code server-command}
     */
    public boolean isServerCommand() {
        return "server-command".equalsIgnoreCase(type);
    }

    /**
     * 判断该命令是否为 PlaceholderAPI 查询类型。
     *
     * @return {@code true} 表示类型为 {@code papi-query}
     */
    public boolean isPapiQuery() {
        return "papi-query".equalsIgnoreCase(type);
    }
}
