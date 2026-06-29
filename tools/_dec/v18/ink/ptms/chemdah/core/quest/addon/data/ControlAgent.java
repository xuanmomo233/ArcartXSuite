/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.data.Control;
import ink.ptms.chemdah.core.quest.addon.data.ControlAgent;
import ink.ptms.chemdah.core.quest.addon.data.ControlResult;
import ink.ptms.chemdah.core.quest.addon.data.ControlTrigger;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0013\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\u0002\u0010\u0005J\u001e\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\r2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J\u0018\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0016R\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0016\u0010\b\u001a\u0004\u0018\u00010\t8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/addon/data/ControlAgent;", "Link/ptms/chemdah/core/quest/addon/data/Control;", "agent", "", "", "(Ljava/util/List;)V", "getAgent", "()Ljava/util/List;", "trigger", "Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "getTrigger", "()Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "check", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/quest/addon/data/ControlResult;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "template", "Link/ptms/chemdah/core/quest/Template;", "signature", "", "Chemdah"})
public class ControlAgent
extends Control {
    @NotNull
    private final List<String> agent;

    public ControlAgent(@NotNull List<String> agent2) {
        Intrinsics.checkNotNullParameter(agent2, (String)"agent");
        this.agent = agent2;
    }

    @NotNull
    public final List<String> getAgent() {
        return this.agent;
    }

    @Override
    @Nullable
    public ControlTrigger getTrigger() {
        return null;
    }

    @Override
    @NotNull
    public CompletableFuture<ControlResult> check(@NotNull PlayerProfile profile, @NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        CompletableFuture<ControlResult> completableFuture = (CompletableFuture<ControlResult>)KetherHelperKt.runKether$default(null, (boolean)true, (Function0)((Function0)new Function0<CompletableFuture<ControlResult>>(this, profile, template){
            final /* synthetic */ ControlAgent this$0;
            final /* synthetic */ PlayerProfile $profile;
            final /* synthetic */ Template $template;
            {
                this.this$0 = $receiver;
                this.$profile = $profile;
                this.$template = $template;
                super(0);
            }

            public final CompletableFuture<ControlResult> invoke() {
                KetherShell ketherShell = KetherShell.INSTANCE;
                List list2 = CollectionKt.asList(this.this$0.getAgent());
                ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)this.$profile.getPlayer());
                List<String> list3 = UtilsForKetherKt.getNamespaceQuest();
                return KetherShell.eval$default((KetherShell)ketherShell, (List)list2, (boolean)false, list3, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, (Function1)((Function1)new Function1<ScriptContext, Unit>(this.$template){
                    final /* synthetic */ Template $template;
                    {
                        this.$template = $template;
                        super(1);
                    }

                    public final void invoke(@NotNull ScriptContext $this$eval) {
                        Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                        $this$eval.set("@QuestContainer", (Object)this.$template);
                    }
                }), (int)42, null).thenApply(arg_0 -> check.1.invoke$lambda$0(this.this$0, arg_0));
            }

            private static final ControlResult invoke$lambda$0(ControlAgent this$0, Object it) {
                Intrinsics.checkNotNullParameter((Object)this$0, (String)"this$0");
                return this$0.toResult(Coerce.toBoolean((Object)it), "agent");
            }
        }), (int)1, null);
        if (completableFuture == null) {
            CompletableFuture<ControlResult> completableFuture2 = CompletableFuture.completedFuture(new ControlResult(false, "agent"));
            completableFuture = completableFuture2;
            Intrinsics.checkNotNullExpressionValue(completableFuture2, (String)"completedFuture(ControlResult(false, \"agent\"))");
        }
        return completableFuture;
    }

    @Override
    public void signature(@NotNull PlayerProfile profile, @NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
    }
}

