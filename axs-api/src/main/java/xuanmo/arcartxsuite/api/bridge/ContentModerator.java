package xuanmo.arcartxsuite.api.bridge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 内容审核桥接，对玩家输入文本执行敏感词过滤。
 * <p>
 * 由宿主实现并通过 {@link xuanmo.arcartxsuite.api.BridgeContext#contentModerator()} 暴露给模块。
 * 统一管理本地词库、云端词库、标点穿透防护、白名单等过滤规则，
 * 供 chat / market / mail 等模块统一调用。
 * <p>
 * 过滤流程：
 * <ol>
 *   <li>剥离 {@code ignoredPunctuations} 中的标点（防止 {@code f.u.c.k} 绕过）</li>
 *   <li>检查白名单，命中则跳过</li>
 *   <li>逐词匹配本地 + 云端屏蔽词</li>
 *   <li>匹配正则屏蔽模式</li>
 *   <li>命中时按 {@code cancelOnMatch} 决定取消或替换</li>
 * </ol>
 *
 * @since 1.3.3
 */
@ApiStability.Stable
public interface ContentModerator {

    /** 桥接是否可用（宿主未初始化或未启用时返回 false） */
    boolean isAvailable();

    /**
     * 对文本执行过滤，返回审核结果。
     * <p>
     * 若审核未启用或桥接不可用，原样返回（{@code blocked=false}）。
     *
     * @param text 原始文本，null 返回未拦截空结果
     * @return 审核结果
     */
    @NotNull ModerationResult filter(@Nullable String text);

    /**
     * 审核结果。
     *
     * @param blocked  是否命中敏感词且应取消操作
     * @param text     处理后的文本（取消时为原文，替换时为替换后文本）
     */
    record ModerationResult(
        boolean blocked,
        @NotNull String text
    ) {
        /** 未拦截的快捷结果 */
        public static ModerationResult pass(@NotNull String text) {
            return new ModerationResult(false, text);
        }

        /** 拦截的快捷结果 */
        public static ModerationResult block(@NotNull String text) {
            return new ModerationResult(true, text);
        }
    }
}
