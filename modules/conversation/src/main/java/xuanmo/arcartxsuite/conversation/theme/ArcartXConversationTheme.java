package xuanmo.arcartxsuite.conversation.theme;

import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.core.conversation.theme.ThemeSettings;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import xuanmo.arcartxsuite.conversation.service.ConversationService;

public final class ArcartXConversationTheme extends Theme<ThemeSettings> {

    private final ConversationService service;

    public ArcartXConversationTheme(ConversationService service) {
        this.service = service;
    }

    @Override
    public ThemeSettings createConfig() {
        return null;
    }

    @Override
    public void reloadConfig() {
        // Chemdah 1.1.8 运行时的 ThemeSettings 依赖 TabooLib 配置类；
        // Conversation 模块不使用该配置面，因此保持空实现，避免额外耦合。
    }

    @Override
    public CompletableFuture<Void> onBegin(Session session) {
        return onDisplay(session, session.getNpcSide(), true);
    }

    @Override
    public CompletableFuture<Void> onDisplay(Session session, List<String> lines, boolean canReply) {
        service.display(session, lines, canReply);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onClose(Session session) {
        service.close(session);
        return CompletableFuture.completedFuture(null);
    }
}
