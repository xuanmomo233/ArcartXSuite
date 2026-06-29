/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.conversation.theme;

import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.core.conversation.theme.ThemeSettings;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\u0018\u0000 \u00102\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u0010B\u0005\u00a2\u0006\u0002\u0010\u0003J\b\u0010\u0004\u001a\u00020\u0002H\u0016J\u0016\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J,\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\b\u001a\u00020\t2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u000e\u001a\u00020\u000fH\u0016\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeDemo;", "Link/ptms/chemdah/core/conversation/theme/Theme;", "Link/ptms/chemdah/core/conversation/theme/ThemeSettings;", "()V", "createConfig", "onClose", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "session", "Link/ptms/chemdah/core/conversation/Session;", "onDisplay", "message", "", "", "canReply", "", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nThemeDemo.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ThemeDemo.kt\nink/ptms/chemdah/core/conversation/theme/ThemeDemo\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,47:1\n1864#2,3:48\n*S KotlinDebug\n*F\n+ 1 ThemeDemo.kt\nink/ptms/chemdah/core/conversation/theme/ThemeDemo\n*L\n22#1:48,3\n*E\n"})
public final class ThemeDemo
extends Theme<ThemeSettings> {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Override
    @NotNull
    public ThemeSettings createConfig() {
        Configuration configuration = Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null);
        return new ThemeSettings(configuration){};
    }

    @Override
    @NotNull
    public CompletableFuture<Void> onDisplay(@NotNull Session session, @NotNull List<String> message2, boolean canReply) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        Intrinsics.checkNotNullParameter(message2, (String)"message");
        Player player = session.getPlayer();
        player.sendMessage(session.getTitle() + " \u8bf4: " + session.getNpcSide());
        this.createDisplay(session, arg_0 -> ThemeDemo.onDisplay$lambda$1(player, session, arg_0));
        CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
        return completableFuture;
    }

    @Override
    @NotNull
    public CompletableFuture<Void> onClose(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        return super.onClose(session);
    }

    /*
     * WARNING - void declaration
     */
    private static final void onDisplay$lambda$1(Player $player, Session $session, List it) {
        Intrinsics.checkNotNullParameter((Object)$player, (String)"$player");
        Intrinsics.checkNotNullParameter((Object)$session, (String)"$session");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        $player.sendMessage("\u4f60\u8bf4:");
        Iterable $this$forEachIndexed$iv = $session.getPlayerReplyForDisplay();
        boolean $i$f$forEachIndexed = false;
        int index$iv = 0;
        for (Object item$iv : $this$forEachIndexed$iv) {
            void reply;
            int n;
            if ((n = index$iv++) < 0) {
                CollectionsKt.throwIndexOverflow();
            }
            PlayerReply playerReply = (PlayerReply)item$iv;
            int index = n;
            boolean bl = false;
            $player.sendMessage("" + ' ' + index + ": " + reply.getText());
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0003\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeDemo$Companion;", "", "()V", "onEnable", "", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @Awake(value=LifeCycle.ENABLE)
        private final void onEnable() {
            new ThemeDemo().register("demo");
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

