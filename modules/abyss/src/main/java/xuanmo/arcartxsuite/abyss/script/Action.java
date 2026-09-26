package xuanmo.arcartxsuite.abyss.script;

import org.jetbrains.annotations.Nullable;

/**
 * 动作：脚本行的一个可执行单元。
 * <p>
 * 调用形式：{@code <action> <args...>}，args 原样传给 {@link #execute}。
 */
public interface Action {

    /**
     * 执行动作。
     *
     * @param context 脚本上下文（实例、触发者、变量、选择器解析）
     * @param args    动作参数（原始字符串，未分割，动作自行解析）
     * @param session 当前脚本会话（wait 类动作用来挂起/恢复）
     */
    void execute(ScriptContext context, @Nullable String args, ScriptSession session);
}
