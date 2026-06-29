/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.kether.conversation;

import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.module.kether.conversation.ConversationGoto;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationGoto;", "", "()V", "parseConversationGoto", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
public final class ConversationGoto {
    @NotNull
    public static final ConversationGoto INSTANCE = new ConversationGoto();

    private ConversationGoto() {
    }

    @KetherParser(value={"goto"}, namespace="chemdah-conversation")
    @NotNull
    public final ScriptActionParser<Session> parseConversationGoto() {
        return KetherHelperKt.combinationParser((Function2)parseConversationGoto.1.INSTANCE);
    }
}

