/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.kether.conversation;

import ink.ptms.adyeshach.api.AdyeshachAPI;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.core.conversation.theme.ThemeSettings;
import ink.ptms.chemdah.module.kether.conversation.ConversationTalkPlayer;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0000\u0018\u0000 \u000e2\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u000eB\r\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\u0002\u0010\u0005J\u001a\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00020\t2\n\u0010\n\u001a\u00060\u000bj\u0002`\fH\u0016J\b\u0010\r\u001a\u00020\u0004H\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationTalkPlayer;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "token", "", "(Ljava/lang/String;)V", "getToken", "()Ljava/lang/String;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "toString", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConversationTalkPlayer.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConversationTalkPlayer.kt\nink/ptms/chemdah/module/kether/conversation/ConversationTalkPlayer\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,55:1\n1864#2,3:56\n*S KotlinDebug\n*F\n+ 1 ConversationTalkPlayer.kt\nink/ptms/chemdah/module/kether/conversation/ConversationTalkPlayer\n*L\n33#1:56,3\n*E\n"})
public final class ConversationTalkPlayer
extends ScriptAction<Void> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final String token;

    public ConversationTalkPlayer(@NotNull String token) {
        Intrinsics.checkNotNullParameter((Object)token, (String)"token");
        this.token = token;
    }

    @NotNull
    public final String getToken() {
        return this.token;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
        Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
        Session session = UtilsForKetherKt.getSession(frame);
        try {
            String $this$run_u24lambda_u242 = KetherFunction.parse$default((KetherFunction)KetherFunction.INSTANCE, (String)this.token, (boolean)false, UtilsForKetherKt.getNamespaceConversationPlayer(), null, null, null, (Function1)((Function1)new Function1<ScriptContext, Unit>(frame){
                final /* synthetic */ QuestContext.Frame $frame;
                {
                    this.$frame = $frame;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$parse) {
                    Intrinsics.checkNotNullParameter((Object)$this$parse, (String)"$this$parse");
                    KetherHelperKt.extend((ScriptContext)$this$parse, (Map)UtilsForKetherKt.vars(this.$frame));
                }
            }), (int)58, null);
            boolean bl = false;
            String[] stringArray = new String[]{"\\n"};
            List messages = UtilKt.colored((List)StringsKt.split$default((CharSequence)$this$run_u24lambda_u242, (String[])stringArray, (boolean)false, (int)0, (int)6, null));
            Theme<?> theme = session.getConversation().getOption().getThemeInstance();
            if (theme.allowFarewell()) {
                session.getNpcSide().clear();
                session.getNpcSide().addAll(messages);
                session.setFarewell(true);
                return theme.onDisplay(session, messages, false);
            }
            ((ThemeSettings)theme.getSettings()).playSound(session);
            Iterable $this$forEachIndexed$iv = messages;
            boolean $i$f$forEachIndexed = false;
            int index$iv = 0;
            for (Object item$iv : $this$forEachIndexed$iv) {
                void s;
                int n;
                if ((n = index$iv++) < 0) {
                    CollectionsKt.throwIndexOverflow();
                }
                String string = (String)item$iv;
                int index = n;
                boolean bl2 = false;
                Player player = session.getPlayer();
                Location location = session.getOrigin().clone().add(0.0, 0.25 + (double)index * 0.3, 0.0);
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"session.origin.clone().a\u2026.25 + (index * 0.3), 0.0)");
                String[] stringArray2 = new String[]{"\u00a77" + (String)s};
                AdyeshachAPI.INSTANCE.createHolographic(player, location, 40L, ConversationTalkPlayer::run$lambda$2$lambda$1$lambda$0, stringArray2);
            }
        }
        catch (Throwable e) {
            KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
        }
        CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
        return completableFuture;
    }

    @NotNull
    public String toString() {
        return "ConversationTalkPlayer(token='" + this.token + "')";
    }

    private static final String run$lambda$2$lambda$1$lambda$0(String it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationTalkPlayer$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Ljava/lang/Void;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"talk"}, namespace="chemdah-conversation-player")
        @NotNull
        public final ScriptActionParser<Void> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

