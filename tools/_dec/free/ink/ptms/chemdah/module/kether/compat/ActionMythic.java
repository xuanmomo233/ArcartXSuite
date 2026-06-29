/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.collections.SetsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether.compat;

import ink.ptms.chemdah.module.kether.compat.ActionMythic;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.um.Skill;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.MapsKt;
import kotlin1822.collections.SetsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u0000 \u00032\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/compat/ActionMythic;", "", "()V", "Companion", "MythicMobsCast", "Chemdah"})
public final class ActionMythic {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/compat/ActionMythic$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Ljava/lang/Void;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"mythicmobs", "mm"}, shared=true)
        @NotNull
        public final ScriptActionParser<Void> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001a\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00020\r2\n\u0010\u000e\u001a\u00060\u000fj\u0002`\u0010H\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/module/kether/compat/ActionMythic$MythicMobsCast;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "mechanic", "Link/ptms/chemdah/um/Skill;", "trigger", "Link/ptms/chemdah/um/Skill$Trigger;", "(Link/ptms/chemdah/um/Skill;Link/ptms/chemdah/um/Skill$Trigger;)V", "getMechanic", "()Link/ptms/chemdah/um/Skill;", "getTrigger", "()Link/ptms/chemdah/um/Skill$Trigger;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class MythicMobsCast
    extends ScriptAction<Void> {
        @NotNull
        private final Skill mechanic;
        @NotNull
        private final Skill.Trigger trigger;

        public MythicMobsCast(@NotNull Skill mechanic, @NotNull Skill.Trigger trigger2) {
            Intrinsics.checkNotNullParameter((Object)mechanic, (String)"mechanic");
            Intrinsics.checkNotNullParameter((Object)trigger2, (String)"trigger");
            this.mechanic = mechanic;
            this.trigger = trigger2;
        }

        @NotNull
        public final Skill getMechanic() {
            return this.mechanic;
        }

        @NotNull
        public final Skill.Trigger getTrigger() {
            return this.trigger;
        }

        @NotNull
        public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            Player player2 = UtilsForKetherKt.getBukkitPlayer(frame);
            ExecutorKt.submit$default((boolean)false, (boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this, player2){
                final /* synthetic */ MythicMobsCast this$0;
                final /* synthetic */ Player $player;
                {
                    this.this$0 = $receiver;
                    this.$player = $player;
                    super(1);
                }

                public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                    Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                    Skill.execute$default(this.this$0.getMechanic(), this.this$0.getTrigger(), (Entity)this.$player, (Entity)this.$player, SetsKt.emptySet(), SetsKt.emptySet(), 0.0f, MapsKt.emptyMap(), null, 128, null);
                }
            }), (int)15, null);
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
            return completableFuture;
        }
    }
}

