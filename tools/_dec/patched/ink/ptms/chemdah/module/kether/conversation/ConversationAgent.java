/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether.conversation;

import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.module.kether.conversation.ConversationAgent;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0000\u0018\u0000 \t2\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\tB\u0005\u00a2\u0006\u0002\u0010\u0003J\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00052\n\u0010\u0006\u001a\u00060\u0007j\u0002`\bH\u0016\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationAgent;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "()V", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConversationAgent.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConversationAgent.kt\nink/ptms/chemdah/module/kether/conversation/ConversationAgent\n+ 2 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n*L\n1#1,30:1\n467#2,7:31\n*S KotlinDebug\n*F\n+ 1 ConversationAgent.kt\nink/ptms/chemdah/module/kether/conversation/ConversationAgent\n*L\n19#1:31,7\n*E\n"})
public final class ConversationAgent
extends ScriptAction<Void> {
    @NotNull
    public static final Companion Companion = new Companion(null);

    /*
     * WARNING - void declaration
     */
    @NotNull
    public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
        void $this$filterKeys$iv;
        Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
        Session session = UtilsForKetherKt.getSession(frame);
        session.getVariables().clear();
        Map map = KetherHelperKt.deepVars((QuestContext.Frame)frame);
        Map<String, Object> map2 = session.getVariables();
        boolean $i$f$filterKeys = false;
        LinkedHashMap result$iv = new LinkedHashMap();
        for (Map.Entry entry$iv : $this$filterKeys$iv.entrySet()) {
            String it = (String)entry$iv.getKey();
            boolean bl = false;
            if (!(!StringsKt.startsWith$default((String)it, (String)"~", (boolean)false, (int)2, null))) continue;
            result$iv.put(entry$iv.getKey(), entry$iv.getValue());
        }
        map2.putAll(result$iv);
        CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
        return completableFuture;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationAgent$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Ljava/lang/Void;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"agent"}, namespace="chemdah-conversation")
        @NotNull
        public final ScriptActionParser<Void> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

