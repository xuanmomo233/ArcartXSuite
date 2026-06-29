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
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether.quest;

import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.module.kether.quest.QuestThis;
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
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u0000 \r2\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\rB\r\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\u0002\u0010\u0005J\u001a\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00020\t2\n\u0010\n\u001a\u00060\u000bj\u0002`\fH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/module/kether/quest/QuestThis;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "task", "", "(Z)V", "getTask", "()Z", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Companion", "Chemdah"})
public final class QuestThis
extends ScriptAction<String> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    private final boolean task;

    public QuestThis(boolean task) {
        this.task = task;
    }

    public final boolean getTask() {
        return this.task;
    }

    @NotNull
    public CompletableFuture<String> run(@NotNull QuestContext.Frame frame) {
        Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
        QuestContainer container = UtilsForKetherKt.getQuestContainer(frame);
        String id2 = container instanceof Task ? (this.task ? container.getId() : ((Task)container).getTemplate().getId()) : container.getId();
        CompletableFuture<String> completableFuture = CompletableFuture.completedFuture(id2);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(id)");
        return completableFuture;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/quest/QuestThis$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"this"}, namespace="chemdah-quest", shared=true)
        @NotNull
        public final ScriptActionParser<String> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

