/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Deprecated
 *  kotlin.Metadata
 *  kotlin.ReplaceWith
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.util.FuturesKt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Deprecated;
import kotlin.Metadata;
import kotlin.ReplaceWith;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0016\u0018\u00002\u00020\u0001B\u0007\b\u0016\u00a2\u0006\u0002\u0010\u0002B\u0013\u0012\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\u0002\u0010\u0006J\u001c\u0010\t\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u000b0\n2\u0006\u0010\f\u001a\u00020\rH\u0016J\u001c\u0010\u000e\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u000b0\n2\u0006\u0010\f\u001a\u00020\rH\u0007R\u0017\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/conversation/PlayerSide;", "", "()V", "reply", "", "Link/ptms/chemdah/core/conversation/PlayerReply;", "(Ljava/util/List;)V", "getReply", "()Ljava/util/List;", "checkReply", "Ljava/util/concurrent/CompletableFuture;", "", "session", "Link/ptms/chemdah/core/conversation/Session;", "checked", "Chemdah"})
public class PlayerSide {
    @NotNull
    private final List<PlayerReply> reply;

    public PlayerSide(@NotNull List<PlayerReply> reply) {
        Intrinsics.checkNotNullParameter(reply, (String)"reply");
        this.reply = reply;
    }

    @NotNull
    public final List<PlayerReply> getReply() {
        return this.reply;
    }

    public PlayerSide() {
        this(new ArrayList());
    }

    @NotNull
    public CompletableFuture<List<PlayerReply>> checkReply(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        CompletableFuture<List<PlayerReply>> future = new CompletableFuture<List<PlayerReply>>();
        ArrayList<PlayerReply> r = new ArrayList<PlayerReply>();
        PlayerSide.checkReply$process(this, session, future, r, 0);
        return future;
    }

    @Deprecated(message="Use checkReply", replaceWith=@ReplaceWith(expression="checkReply(session)", imports={}))
    @NotNull
    public final CompletableFuture<List<PlayerReply>> checked(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        return this.checkReply(session);
    }

    private static final void checkReply$process(PlayerSide this$0, Session $session, CompletableFuture<List<PlayerReply>> future, ArrayList<PlayerReply> r, int cur) {
        if (cur < this$0.reply.size()) {
            PlayerReply reply = this$0.reply.get(cur);
            FuturesKt.applyWithError(reply.check($session), (Function1)new Function1<Boolean, Unit>(r, reply, cur, this$0, $session, future){
                final /* synthetic */ ArrayList<PlayerReply> $r;
                final /* synthetic */ PlayerReply $reply;
                final /* synthetic */ int $cur;
                final /* synthetic */ PlayerSide this$0;
                final /* synthetic */ Session $session;
                final /* synthetic */ CompletableFuture<List<PlayerReply>> $future;
                {
                    this.$r = $r;
                    this.$reply = $reply;
                    this.$cur = $cur;
                    this.this$0 = $receiver;
                    this.$session = $session;
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(boolean it) {
                    if (it) {
                        this.$r.add(this.$reply);
                    }
                    PlayerSide.access$checkReply$process(this.this$0, this.$session, this.$future, this.$r, this.$cur + 1);
                }
            });
        } else {
            future.complete(r);
        }
    }

    public static final /* synthetic */ void access$checkReply$process(PlayerSide this$0, Session $session, CompletableFuture future, ArrayList r, int cur) {
        PlayerSide.checkReply$process(this$0, $session, future, r, cur);
    }
}

