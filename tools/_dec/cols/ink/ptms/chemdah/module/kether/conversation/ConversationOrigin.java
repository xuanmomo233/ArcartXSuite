/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.kether.conversation;

import ink.ptms.chemdah.module.kether.conversation.ConversationOrigin;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
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
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0000\u0018\u0000 \t2\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\tB\u0005\u00a2\u0006\u0002\u0010\u0003J\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00052\n\u0010\u0006\u001a\u00060\u0007j\u0002`\bH\u0016\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationOrigin;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Lorg/bukkit/Location;", "()V", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Companion", "Chemdah"})
public final class ConversationOrigin
extends ScriptAction<Location> {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @NotNull
    public CompletableFuture<Location> run(@NotNull QuestContext.Frame frame) {
        Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
        CompletableFuture<Location> completableFuture = CompletableFuture.completedFuture(UtilsForKetherKt.getSession(frame).getOrigin());
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(frame.getSession().origin)");
        return completableFuture;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationOrigin$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Lorg/bukkit/Location;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"origin"}, namespace="chemdah-conversation")
        @NotNull
        public final ScriptActionParser<Location> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

