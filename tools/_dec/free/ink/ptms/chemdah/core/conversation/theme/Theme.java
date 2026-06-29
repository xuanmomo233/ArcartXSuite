/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation.theme;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.theme.ThemeSettings;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000J\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\b\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b&\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\u00020\u0003B\u0005\u00a2\u0006\u0002\u0010\u0004J\b\u0010\u000b\u001a\u00020\fH\u0016J\r\u0010\r\u001a\u00028\u0000H&\u00a2\u0006\u0002\u0010\u0007J\u0016\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J\u0016\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J.\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u00122\f\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00170\u00162\b\b\u0002\u0010\u0018\u001a\u00020\fH&J\u0010\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J\u0016\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J\u000e\u0010\u001c\u001a\u00020\u001a2\u0006\u0010\u001d\u001a\u00020\u0017J\b\u0010\u001e\u001a\u00020\u001aH\u0016J&\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f*\u00020\u00122\u0012\u0010 \u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\"0\u00160!H\u0014R\u001c\u0010\u0005\u001a\u00028\u0000X\u0086.\u00a2\u0006\u0010\n\u0002\u0010\n\u001a\u0004\b\u0006\u0010\u0007\"\u0004\b\b\u0010\t\u00a8\u0006#"}, d2={"Link/ptms/chemdah/core/conversation/theme/Theme;", "T", "Link/ptms/chemdah/core/conversation/theme/ThemeSettings;", "", "()V", "settings", "getSettings", "()Link/ptms/chemdah/core/conversation/theme/ThemeSettings;", "setSettings", "(Link/ptms/chemdah/core/conversation/theme/ThemeSettings;)V", "Link/ptms/chemdah/core/conversation/theme/ThemeSettings;", "allowFarewell", "", "createConfig", "onBegin", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "session", "Link/ptms/chemdah/core/conversation/Session;", "onClose", "onDisplay", "message", "", "", "canReply", "onPostDisplay", "", "onReset", "register", "name", "reloadConfig", "createDisplay", "callback", "Ljava/util/function/Consumer;", "Link/ptms/chemdah/core/conversation/PlayerReply;", "Chemdah"})
public abstract class Theme<T extends ThemeSettings> {
    public T settings;

    @NotNull
    public final T getSettings() {
        T t = this.settings;
        if (t != null) {
            return t;
        }
        Intrinsics.throwUninitializedPropertyAccessException((String)"settings");
        return null;
    }

    public final void setSettings(@NotNull T t) {
        Intrinsics.checkNotNullParameter(t, (String)"<set-?>");
        this.settings = t;
    }

    public final void register(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        ((Map)ChemdahAPI.INSTANCE.getConversationTheme()).put(name, this);
    }

    @NotNull
    public abstract T createConfig();

    public void reloadConfig() {
        this.setSettings(this.createConfig());
    }

    public boolean allowFarewell() {
        return true;
    }

    @NotNull
    public CompletableFuture<Void> onReset(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
        return completableFuture;
    }

    @NotNull
    public CompletableFuture<Void> onBegin(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        if (this.settings == null) {
            this.setSettings(this.createConfig());
        }
        ((ThemeSettings)this.getSettings()).playSound(session);
        return Theme.onDisplay$default(this, session, session.getNpcSide(), false, 4, null);
    }

    @NotNull
    public CompletableFuture<Void> onClose(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
        return completableFuture;
    }

    @NotNull
    public abstract CompletableFuture<Void> onDisplay(@NotNull Session var1, @NotNull List<String> var2, boolean var3);

    public static /* synthetic */ CompletableFuture onDisplay$default(Theme theme, Session session, List list2, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: onDisplay");
        }
        if ((n & 4) != 0) {
            bl = true;
        }
        return theme.onDisplay(session, list2, bl);
    }

    public void onPostDisplay(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
    }

    @NotNull
    protected CompletableFuture<Void> createDisplay(@NotNull Session $this$createDisplay, @NotNull Consumer<List<PlayerReply>> callback) {
        Intrinsics.checkNotNullParameter((Object)$this$createDisplay, (String)"<this>");
        Intrinsics.checkNotNullParameter(callback, (String)"callback");
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        $this$createDisplay.getConversation().getPlayerSide().checkReply($this$createDisplay).thenAccept(arg_0 -> Theme.createDisplay$lambda$0((Function1)new Function1<List<? extends PlayerReply>, Unit>($this$createDisplay, callback, future){
            final /* synthetic */ Session $this_createDisplay;
            final /* synthetic */ Consumer<List<PlayerReply>> $callback;
            final /* synthetic */ CompletableFuture<Void> $future;
            {
                this.$this_createDisplay = $receiver;
                this.$callback = $callback;
                this.$future = $future;
                super(1);
            }

            /*
             * Unable to fully structure code
             */
            public final void invoke(List<? extends PlayerReply> replies) {
                block7: {
                    this.$this_createDisplay.getPlayerReplyForDisplay().clear();
                    v0 = this.$this_createDisplay.getPlayerReplyForDisplay();
                    Intrinsics.checkNotNullExpressionValue(replies, (String)"replies");
                    CollectionsKt.addAll((Collection)v0, (Iterable)replies);
                    this.$this_createDisplay.setPlayerReplyOnCursor((PlayerReply)CollectionsKt.firstOrNull(replies));
                    v1 = this.$this_createDisplay;
                    if (replies.isEmpty()) ** GOTO lbl-1000
                    var2_2 = replies;
                    var8_4 = v1;
                    $i$f$all = false;
                    if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                        v2 = true;
                    } else {
                        for (T element$iv : $this$all$iv) {
                            reply = (PlayerReply)element$iv;
                            $i$a$-all-Theme$createDisplay$1$1 = false;
                            if (reply.getAction().isEmpty()) continue;
                            v2 = false;
                            break block7;
                        }
                        v2 = true;
                    }
                }
                var9_10 = v2;
                v1 = var8_4;
                if (var9_10) lbl-1000:
                // 2 sources

                {
                    v3 = true;
                } else {
                    v3 = false;
                }
                v1.setFarewell(v3);
                try {
                    this.$callback.accept(replies);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
                this.$future.complete(null);
            }
        }, arg_0));
        return future;
    }

    private static final void createDisplay$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }
}

