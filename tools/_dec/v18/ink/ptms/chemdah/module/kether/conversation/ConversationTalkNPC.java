/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether.conversation;

import ink.ptms.chemdah.module.kether.conversation.ConversationTalkNPC;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0000\u0018\u0000 \r2\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\rB\r\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\u0002\u0010\u0005J\u001a\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00020\t2\n\u0010\n\u001a\u00060\u000bj\u0002`\fH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationTalkNPC;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "token", "", "(Ljava/lang/String;)V", "getToken", "()Ljava/lang/String;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Companion", "Chemdah"})
public final class ConversationTalkNPC
extends ScriptAction<Void> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final String token;

    public ConversationTalkNPC(@NotNull String token) {
        Intrinsics.checkNotNullParameter((Object)token, (String)"token");
        this.token = token;
    }

    @NotNull
    public final String getToken() {
        return this.token;
    }

    @NotNull
    public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
        Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
        try {
            UtilsForKetherKt.getSession(frame).getNpcSide().add(UtilKt.colored((String)KetherFunction.parse$default((KetherFunction)KetherFunction.INSTANCE, (String)this.token, (boolean)false, UtilsForKetherKt.getNamespaceConversationNPC(), null, null, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(frame){
                final /* synthetic */ QuestContext.Frame $frame;
                {
                    this.$frame = $frame;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$parse) {
                    Intrinsics.checkNotNullParameter((Object)$this$parse, (String)"$this$parse");
                    KetherHelperKt.extend((ScriptContext)$this$parse, (Map)UtilsForKetherKt.vars(this.$frame));
                }
            }), (int)58, null)));
        }
        catch (Throwable e) {
            KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
        }
        CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
        return completableFuture;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationTalkNPC$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Ljava/lang/Void;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"talk"}, namespace="chemdah-conversation-npc")
        @NotNull
        public final ScriptActionParser<Void> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

