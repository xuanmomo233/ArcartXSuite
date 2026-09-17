package xuanmo.arcartxsuite.api.capability;

import org.jetbrains.annotations.NotNull;

/**
 * 公告广播能力接口（公开 capability）。
 * <p>
 * 由 Announcer 模块实现，供外部插件推送一条即时/排队公告。
 * 文本支持 PlaceholderAPI 变量。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(AnnouncerBroadcastable.class)} 获取。
 */
@PublicCapability
public interface AnnouncerBroadcastable {

    /**
     * 立即播报一条公告（强制打断当前展示，本服生效）。
     *
     * @param text 公告文本
     */
    void broadcastNow(@NotNull String text);

    /**
     * 立即播报一条公告。
     *
     * @param text    公告文本
     * @param forward 是否跨服转发到其他子服
     */
    void broadcastNow(@NotNull String text, boolean forward);

    /**
     * 将公告加入队列，当前展示结束后播报。
     *
     * @param text    公告文本
     * @param forward 是否跨服转发
     */
    void enqueue(@NotNull String text, boolean forward);
}
