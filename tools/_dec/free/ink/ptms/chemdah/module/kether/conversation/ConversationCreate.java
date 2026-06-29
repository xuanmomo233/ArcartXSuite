/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether.conversation;

import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.module.kether.conversation.ConversationCreate;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bH\u0003J\u0010\u0010\f\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\rH\u0003J\u0010\u0010\u000e\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u000fH\u0007R\u001e\u0010\u0003\u001a\u0012\u0012\u0004\u0012\u00020\u0005\u0012\b\u0012\u00060\u0006j\u0002`\u00070\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/module/kether/conversation/ConversationCreate;", "", "()V", "closeCallback", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Ljava/lang/Runnable;", "Lkotlin1822x/coroutines173/Runnable;", "onClose", "", "e", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Closed;", "onQuit", "Lorg/bukkit/event/player/PlayerQuitEvent;", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Chemdah"})
public final class ConversationCreate {
    @NotNull
    public static final ConversationCreate INSTANCE = new ConversationCreate();
    @NotNull
    private static final ConcurrentHashMap<String, Runnable> closeCallback = new ConcurrentHashMap();

    private ConversationCreate() {
    }

    @KetherParser(value={"conversation"}, namespace="chemdah", shared=true)
    @NotNull
    public final ScriptActionParser<Object> parser() {
        return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
    }

    @SubscribeEvent
    private final void onClose(ConversationEvents.Closed e) {
        block0: {
            Runnable runnable = closeCallback.remove(e.getSession().getPlayer().getName());
            if (runnable == null) break block0;
            runnable.run();
        }
    }

    @SubscribeEvent
    private final void onQuit(PlayerQuitEvent e) {
        closeCallback.remove(e.getPlayer().getName());
    }

    public static final /* synthetic */ ConcurrentHashMap access$getCloseCallback$p() {
        return closeCallback;
    }
}

