/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.StringKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000P\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\b\r\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u001b\u0012\u0014\u0010\u0002\u001a\u0010\u0012\u0004\u0012\u00020\u0004\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0003\u00a2\u0006\u0002\u0010\u0005J\u0010\u0010$\u001a\u00020\u00042\u0006\u0010%\u001a\u00020&H\u0016J\u0016\u0010'\u001a\b\u0012\u0004\u0012\u00020\u00150(2\u0006\u0010%\u001a\u00020&H\u0016J\u0013\u0010)\u001a\u00020\u00152\b\u0010*\u001a\u0004\u0018\u00010\u0001H\u0096\u0002J\b\u0010+\u001a\u00020,H\u0016J\u0010\u0010-\u001a\u00020\u00152\u0006\u0010.\u001a\u00020/H\u0016J\u0016\u00100\u001a\b\u0012\u0004\u0012\u0002010(2\u0006\u0010%\u001a\u00020&H\u0016R\u0017\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00040\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0017\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00040\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tR\u001c\u0010\f\u001a\u0004\u0018\u00010\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u001c\u0010\u0011\u001a\u0004\u0018\u00010\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u000e\"\u0004\b\u0013\u0010\u0010R\u001a\u0010\u0014\u001a\u00020\u0015X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u0016\"\u0004\b\u0017\u0010\u0018R\u001c\u0010\u0019\u001a\u0004\u0018\u00010\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0019\u0010\u000e\"\u0004\b\u001a\u0010\u0010R\u0011\u0010\u001b\u001a\u00020\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u001f\u0010\u0002\u001a\u0010\u0012\u0004\u0012\u00020\u0004\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010 R\u001a\u0010!\u001a\u00020\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\"\u0010\u000e\"\u0004\b#\u0010\u0010\u00a8\u00062"}, d2={"Link/ptms/chemdah/core/conversation/PlayerReply;", "", "root", "", "", "(Ljava/util/Map;)V", "action", "", "getAction", "()Ljava/util/List;", "actionAsync", "getActionAsync", "condition", "getCondition", "()Ljava/lang/String;", "setCondition", "(Ljava/lang/String;)V", "format", "getFormat", "setFormat", "isSwapLine", "", "()Z", "setSwapLine", "(Z)V", "isUniqueId", "setUniqueId", "rid", "Ljava/util/UUID;", "getRid", "()Ljava/util/UUID;", "getRoot", "()Ljava/util/Map;", "text", "getText", "setText", "build", "session", "Link/ptms/chemdah/core/conversation/Session;", "check", "Ljava/util/concurrent/CompletableFuture;", "equals", "other", "hashCode", "", "isPlayerSelected", "player", "Lorg/bukkit/entity/Player;", "select", "Ljava/lang/Void;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nPlayerReply.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PlayerReply.kt\nink/ptms/chemdah/core/conversation/PlayerReply\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 3 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,151:1\n1#2:152\n29#3:153\n*S KotlinDebug\n*F\n+ 1 PlayerReply.kt\nink/ptms/chemdah/core/conversation/PlayerReply\n*L\n56#1:153\n*E\n"})
public class PlayerReply {
    @NotNull
    private final Map<String, Object> root;
    @NotNull
    private final UUID rid;
    @Nullable
    private String condition;
    @NotNull
    private String text;
    @Nullable
    private String format;
    @NotNull
    private final List<String> action;
    @NotNull
    private final List<String> actionAsync;
    @Nullable
    private String isUniqueId;
    private boolean isSwapLine;

    /*
     * WARNING - void declaration
     */
    public PlayerReply(@NotNull Map<String, Object> root2) {
        List list2;
        List list3;
        Object object;
        Intrinsics.checkNotNullParameter(root2, (String)"root");
        this.root = root2;
        UUID uUID = UUID.randomUUID();
        Intrinsics.checkNotNullExpressionValue((Object)uUID, (String)"randomUUID()");
        this.rid = uUID;
        PlayerReply playerReply = this;
        Object object2 = this.root.get("if");
        if (object2 != null && (object2 = object2.toString()) != null) {
            void it;
            Object object3;
            Object object4 = object3 = object2;
            PlayerReply playerReply2 = playerReply;
            boolean bl = false;
            boolean bl2 = !StringsKt.isBlank((CharSequence)((CharSequence)it));
            playerReply = playerReply2;
            object = bl2 ? object3 : null;
        } else {
            object = null;
        }
        playerReply.condition = object;
        this.text = String.valueOf(this.root.get("reply"));
        Object object5 = this.root.get("format");
        if (object5 == null || (object5 = object5.toString()) == null) {
            Object object6 = this.root.get("type");
            object5 = this.format = object6 != null ? object6.toString() : null;
        }
        if ((list3 = this.root.get("then")) == null || (list3 = CollectionKt.asList((Object)list3)) == null || (list3 = StringKt.flatLines(list3)) == null || (list3 = CollectionsKt.toMutableList((Collection)list3)) == null) {
            list3 = this.action = (List)new ArrayList();
        }
        if ((list2 = this.root.get("then-async")) == null || (list2 = CollectionKt.asList((Object)list2)) == null || (list2 = StringKt.flatLines(list2)) == null || (list2 = CollectionsKt.toMutableList((Collection)list2)) == null) {
            list2 = new ArrayList();
        }
        this.actionAsync = list2;
        Object object7 = this.root.get("unique");
        this.isUniqueId = object7 != null ? object7.toString() : null;
        Object $this$cbool$iv = this.root.get("swap");
        boolean $i$f$getCbool = false;
        this.isSwapLine = Coerce.toBoolean((Object)$this$cbool$iv);
    }

    @NotNull
    public final Map<String, Object> getRoot() {
        return this.root;
    }

    @NotNull
    public final UUID getRid() {
        return this.rid;
    }

    @Nullable
    public final String getCondition() {
        return this.condition;
    }

    public final void setCondition(@Nullable String string) {
        this.condition = string;
    }

    @NotNull
    public final String getText() {
        return this.text;
    }

    public final void setText(@NotNull String string) {
        Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
        this.text = string;
    }

    @Nullable
    public final String getFormat() {
        return this.format;
    }

    public final void setFormat(@Nullable String string) {
        this.format = string;
    }

    @NotNull
    public final List<String> getAction() {
        return this.action;
    }

    @NotNull
    public final List<String> getActionAsync() {
        return this.actionAsync;
    }

    @Nullable
    public final String isUniqueId() {
        return this.isUniqueId;
    }

    public final void setUniqueId(@Nullable String string) {
        this.isUniqueId = string;
    }

    public final boolean isSwapLine() {
        return this.isSwapLine;
    }

    public final void setSwapLine(boolean bl) {
        this.isSwapLine = bl;
    }

    public boolean isPlayerSelected(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        if (this.isUniqueId != null && ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player)) {
            return ChemdahAPI.INSTANCE.getChemdahProfile(player).getPersistentDataContainer().containsKey("conversation.unique." + this.isUniqueId);
        }
        return false;
    }

    @NotNull
    public String build(@NotNull Session session) {
        String string;
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        try {
            string = StringsKt.replace$default((String)UtilKt.colored((String)KetherFunction.parse$default((KetherFunction)KetherFunction.INSTANCE, (String)this.text, (boolean)false, UtilsForKetherKt.getNamespaceConversationPlayer(), null, null, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(session){
                final /* synthetic */ Session $session;
                {
                    this.$session = $session;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$parse) {
                    Intrinsics.checkNotNullParameter((Object)$this$parse, (String)"$this$parse");
                    KetherHelperKt.extend((ScriptContext)$this$parse, this.$session.getVariables());
                }
            }), (int)58, null)), (String)"\\n", (String)"\n", (boolean)false, (int)4, null);
        }
        catch (Throwable e) {
            KetherHelperKt.printKetherErrorMessage$default((Throwable)e, (boolean)false, (int)1, null);
            string = e.getLocalizedMessage();
        }
        String text2 = string;
        Intrinsics.checkNotNullExpressionValue((Object)text2, (String)"text");
        return text2;
    }

    @NotNull
    public CompletableFuture<Boolean> check(@NotNull Session session) {
        CompletableFuture<Boolean> completableFuture;
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        if (session.isSelected()) {
            CompletableFuture<Boolean> completableFuture2 = CompletableFuture.completedFuture(false);
            completableFuture = completableFuture2;
            Intrinsics.checkNotNullExpressionValue(completableFuture2, (String)"completedFuture(false)");
        } else if (this.condition == null) {
            CompletableFuture<Boolean> completableFuture3 = CompletableFuture.completedFuture(true);
            completableFuture = completableFuture3;
            Intrinsics.checkNotNullExpressionValue(completableFuture3, (String)"completedFuture(true)");
        } else {
            CompletionStage<Boolean> completionStage;
            try {
                String string = this.condition;
                Intrinsics.checkNotNull((Object)string);
                completionStage = KetherShell.eval$default((KetherShell)KetherShell.INSTANCE, (String)string, (boolean)false, UtilsForKetherKt.getNamespaceConversationPlayer(), null, null, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(session){
                    final /* synthetic */ Session $session;
                    {
                        this.$session = $session;
                        super(1);
                    }

                    public final void invoke(@NotNull ScriptContext $this$eval) {
                        Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                        KetherHelperKt.extend((ScriptContext)$this$eval, this.$session.getVariables());
                    }
                }), (int)58, null).thenApply(PlayerReply::check$lambda$1);
            }
            catch (Throwable e) {
                KetherHelperKt.printKetherErrorMessage$default((Throwable)e, (boolean)false, (int)1, null);
                completionStage = CompletableFuture.completedFuture(false);
            }
            CompletableFuture<Boolean> completableFuture4 = completionStage;
            completableFuture = completableFuture4;
            Intrinsics.checkNotNullExpressionValue(completableFuture4, (String)"session: Session): Compl\u2026          }\n            }");
        }
        return completableFuture;
    }

    @NotNull
    public CompletableFuture<Void> select(@NotNull Session session) {
        CompletionStage<Object> completionStage;
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        if (session.isSelected()) {
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
            return completableFuture;
        }
        if (!new ConversationEvents.SelectReply(session.getPlayer(), session, this).call()) {
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
            return completableFuture;
        }
        if (this.isUniqueId != null && ChemdahAPI.INSTANCE.isChemdahProfileLoaded(session.getPlayer())) {
            ChemdahAPI.INSTANCE.getChemdahProfile(session.getPlayer()).getPersistentDataContainer().set("conversation.unique." + this.isUniqueId, true);
        }
        session.setSelected(true);
        try {
            KetherShell.eval$default((KetherShell)KetherShell.INSTANCE, this.actionAsync, (boolean)false, UtilsForKetherKt.getNamespaceConversationPlayer(), null, null, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(session){
                final /* synthetic */ Session $session;
                {
                    this.$session = $session;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$eval) {
                    Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                    KetherHelperKt.extend((ScriptContext)$this$eval, this.$session.getVariables());
                }
            }), (int)58, null);
            completionStage = KetherShell.eval$default((KetherShell)KetherShell.INSTANCE, this.action, (boolean)false, UtilsForKetherKt.getNamespaceConversationPlayer(), null, null, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(session){
                final /* synthetic */ Session $session;
                {
                    this.$session = $session;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$eval) {
                    Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                    KetherHelperKt.extend((ScriptContext)$this$eval, this.$session.getVariables());
                }
            }), (int)58, null).thenAccept(arg_0 -> PlayerReply.select$lambda$2(session, arg_0));
            Intrinsics.checkNotNullExpressionValue(completionStage, (String)"session: Session): Compl\u2026}\n            }\n        }");
        }
        catch (Throwable e) {
            KetherHelperKt.printKetherErrorMessage$default((Throwable)e, (boolean)false, (int)1, null);
            Session.close$default(session, false, 1, null);
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"{\n            e.printKet\u2026tedFuture(null)\n        }");
            completionStage = completableFuture;
        }
        return completionStage;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerReply)) {
            return false;
        }
        return Intrinsics.areEqual((Object)this.rid, (Object)((PlayerReply)other).rid);
    }

    public int hashCode() {
        return this.rid.hashCode();
    }

    private static final Boolean check$lambda$1(Object it) {
        return Coerce.toBoolean((Object)it);
    }

    private static final void select$lambda$2(Session $session, Object it) {
        Intrinsics.checkNotNullParameter((Object)$session, (String)"$session");
        if ($session.isTransitioning()) {
            $session.setTransitioning(false);
        } else {
            new ConversationEvents.ReplyClosed($session).call();
            Session.close$default($session, false, 1, null);
        }
    }
}

