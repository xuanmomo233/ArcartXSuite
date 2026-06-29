/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="restart")
@Option(type=Option.Type.ANY)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0006\b\u0007\u0018\u0000 \u000e2\u00020\u0001:\u0001\u000eB\u0017\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R \u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\r\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonRestart;", "Link/ptms/chemdah/core/quest/addon/Addon;", "root", "", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Ljava/lang/Object;Link/ptms/chemdah/core/quest/QuestContainer;)V", "restart", "", "", "getRestart", "()Ljava/util/List;", "setRestart", "(Ljava/util/List;)V", "Companion", "Chemdah"})
public final class AddonRestart
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private List<String> restart;

    public AddonRestart(@Nullable Object root2, @NotNull QuestContainer questContainer) {
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(root2, questContainer);
        Object object = root2;
        if (object == null || (object = CollectionKt.asList((Object)object)) == null) {
            object = CollectionsKt.emptyList();
        }
        this.restart = object;
    }

    @NotNull
    public final List<String> getRestart() {
        return this.restart;
    }

    public final void setRestart(@NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.restart = list2;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004*\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonRestart$Companion;", "", "()V", "canRestart", "Ljava/util/concurrent/CompletableFuture;", "", "Link/ptms/chemdah/core/quest/QuestContainer;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final CompletableFuture<Boolean> canRestart(@NotNull QuestContainer $this$canRestart, @NotNull PlayerProfile profile) {
            CompletableFuture<Boolean> completableFuture;
            Intrinsics.checkNotNullParameter((Object)$this$canRestart, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            CompletableFuture<Boolean> future = completableFuture = new CompletableFuture<Boolean>();
            boolean bl = false;
            AddonRestart addonRestart = (AddonRestart)$this$canRestart.addon("restart");
            List<String> reset = addonRestart != null ? addonRestart.getRestart() : null;
            Collection collection = reset;
            if (collection == null || collection.isEmpty()) {
                future.complete(false);
            } else {
                try {
                    collection = KetherShell.INSTANCE;
                    ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)profile.getPlayer());
                    List<String> list2 = UtilsForKetherKt.getNamespaceQuest();
                    FuturesKt.applyWithError(KetherShell.eval$default((KetherShell)collection, reset, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, (Function1)((Function1)new Function1<ScriptContext, Unit>($this$canRestart){
                        final /* synthetic */ QuestContainer $this_canRestart;
                        {
                            this.$this_canRestart = $receiver;
                            super(1);
                        }

                        public final void invoke(@NotNull ScriptContext $this$eval) {
                            Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                            $this$eval.set("@QuestContainer", (Object)this.$this_canRestart);
                        }
                    }), (int)42, null), (Function1)new Function1<Object, Unit>(future){
                        final /* synthetic */ CompletableFuture<Boolean> $future;
                        {
                            this.$future = $future;
                            super(1);
                        }

                        public final void invoke(@Nullable Object it) {
                            this.$future.complete(Coerce.toBoolean((Object)it));
                        }
                    });
                }
                catch (Throwable e) {
                    Object[] objectArray = new Object[]{"path: " + $this$canRestart.getPath() + ", addon: restart, source: " + reset};
                    IOKt.warning((Object[])objectArray);
                    KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
                    future.complete(false);
                }
            }
            return completableFuture;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

