/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.module.chat.RawMessage
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether.conversation;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.core.conversation.theme.ThemeChatSettings;
import ink.ptms.chemdah.core.quest.QuestDevelopment;
import ink.ptms.chemdah.module.kether.conversation.ConversationRelease;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.chat.RawMessage;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0000\u0018\u0000 \u000e2\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u000eB\u0005\u00a2\u0006\u0002\u0010\u0003J\b\u0010\u0006\u001a\u00020\u0007H\u0002J\u001a\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00020\t2\n\u0010\n\u001a\u00060\u000bj\u0002`\fH\u0016J\f\u0010\r\u001a\u00020\u0007*\u00020\u0007H\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationRelease;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "()V", "settings", "Link/ptms/chemdah/core/conversation/theme/ThemeChatSettings;", "newJson", "Link/ptms/chemdah/taboolib/module/chat/RawMessage;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "fixed", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConversationRelease.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConversationRelease.kt\nink/ptms/chemdah/module/kether/conversation/ConversationRelease\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,45:1\n1#2:46\n*E\n"})
public final class ConversationRelease
extends ScriptAction<Void> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final ThemeChatSettings settings;

    public ConversationRelease() {
        Theme<?> theme = ChemdahAPI.INSTANCE.getConversationTheme("chat");
        Intrinsics.checkNotNull(theme);
        Object obj = theme.getSettings();
        Intrinsics.checkNotNull(obj, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.conversation.theme.ThemeChatSettings");
        this.settings = (ThemeChatSettings)obj;
    }

    /*
     * WARNING - void declaration
     */
    private final RawMessage newJson() {
        RawMessage rawMessage;
        RawMessage rawMessage2 = rawMessage = new RawMessage(null, 1, null);
        ConversationRelease conversationRelease = this;
        boolean bl = false;
        int n = this.settings.getSpaceLine();
        int n2 = 0;
        while (n2 < n) {
            void json;
            int it = n2++;
            boolean bl2 = false;
            json.newLine();
        }
        return conversationRelease.fixed(rawMessage);
    }

    private final RawMessage fixed(RawMessage $this$fixed) {
        return $this$fixed.append("\n").runCommand("PLEASE!PASS!ME!d3486345-e35d-326a-b5c5-787de3814770!");
    }

    @NotNull
    public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
        Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
        RawMessage.sendTo$default((RawMessage)this.newJson(), (ProxyCommandSender)AdapterKt.adaptCommandSender((Object)UtilsForKetherKt.getBukkitPlayer(frame)), null, (int)2, null);
        QuestDevelopment.INSTANCE.releaseTransmit(UtilsForKetherKt.getBukkitPlayer(frame));
        CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
        return completableFuture;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationRelease$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Ljava/lang/Void;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"release"}, namespace="chemdah-conversation-player")
        @NotNull
        public final ScriptActionParser<Void> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

