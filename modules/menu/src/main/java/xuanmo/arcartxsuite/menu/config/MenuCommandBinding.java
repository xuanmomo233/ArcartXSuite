package xuanmo.arcartxsuite.menu.config;

import java.util.regex.Pattern;

/**
 * 菜单命令绑定，将一条命令（或正则）映射到指定菜单。
 * <p>
 * 由 {@link xuanmo.arcartxsuite.menu.service.MenuBindingRegistry} 构建，
 * 在 {@link xuanmo.arcartxsuite.menu.listener.MenuBindingListener} 中用于拦截命令并打开菜单。
 *
 * @param menuId        目标菜单 ID
 * @param pattern       命令匹配模式（精确匹配或正则表达式）
 * @param regex         是否使用正则匹配
 * @param compiledRegex 编译后的正则 Pattern，仅当 {@code regex} 为 true 时非 null
 * @param permission    触发所需的权限节点，空字符串表示无限制
 */
public record MenuCommandBinding(
    String menuId,
    String pattern,
    boolean regex,
    Pattern compiledRegex,
    String permission
) {

    /**
     * 判断给定命令行是否匹配此绑定。
     *
     * @param commandLine 命令行文本（不含前导 {@code /}）
     * @return {@code true} 表示匹配成功
     */
    public boolean matches(String commandLine) {
        if (commandLine == null) {
            return false;
        }
        if (regex) {
            return compiledRegex != null && compiledRegex.matcher(commandLine).matches();
        }
        int length = pattern.length();
        return pattern.equalsIgnoreCase(commandLine)
            || commandLine.length() > length && commandLine.charAt(length) == ' '
                && commandLine.regionMatches(true, 0, pattern, 0, length);
    }
}
