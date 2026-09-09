package xuanmo.arcartxsuite.mail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ContentModerator;

/**
 * 空实现 ContentModerator，在宿主未提供桥接时用作 fallback。
 */
public final class NoopContentModerator implements ContentModerator {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public @NotNull ModerationResult filter(@Nullable String text) {
        return ModerationResult.pass(text == null ? "" : text);
    }
}
