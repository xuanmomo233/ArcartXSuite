/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.kether.ParsedAction
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.module.kether.ActionParticle;
import ink.ptms.chemdah.taboolib.library.kether.ParsedAction;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.util.Effect;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u0000 \u00032\u00020\u0001:\u0002\u0003\u0004B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/ActionParticle;", "", "()V", "Companion", "ParticleNormal", "Chemdah"})
public final class ActionParticle {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0007\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/ActionParticle$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Ljava/lang/Void;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"particle"}, shared=true)
        @NotNull
        public final ScriptActionParser<Void> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B#\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0006\u0012\b\b\u0002\u0010\u0007\u001a\u00020\b\u00a2\u0006\u0002\u0010\tJ\u001a\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00020\u00112\n\u0010\u0012\u001a\u00060\u0013j\u0002`\u0014H\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/module/kether/ActionParticle$ParticleNormal;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "effect", "Link/ptms/chemdah/util/Effect;", "location", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "self", "", "(Link/ptms/chemdah/util/Effect;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Z)V", "getEffect", "()Link/ptms/chemdah/util/Effect;", "getLocation", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getSelf", "()Z", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class ParticleNormal
    extends ScriptAction<Void> {
        @NotNull
        private final Effect effect;
        @NotNull
        private final ParsedAction<?> location;
        private final boolean self;

        public ParticleNormal(@NotNull Effect effect, @NotNull ParsedAction<?> location, boolean self) {
            Intrinsics.checkNotNullParameter((Object)effect, (String)"effect");
            Intrinsics.checkNotNullParameter(location, (String)"location");
            this.effect = effect;
            this.location = location;
            this.self = self;
        }

        public /* synthetic */ ParticleNormal(Effect effect, ParsedAction parsedAction, boolean bl, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 4) != 0) {
                bl = false;
            }
            this(effect, parsedAction, bl);
        }

        @NotNull
        public final Effect getEffect() {
            return this.effect;
        }

        @NotNull
        public final ParsedAction<?> getLocation() {
            return this.location;
        }

        public final boolean getSelf() {
            return this.self;
        }

        @NotNull
        public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletionStage completionStage = frame.newFrame(this.location).run().thenAccept(arg_0 -> ParticleNormal.run$lambda$0((Function1)new Function1<Location, Unit>(this, frame){
                final /* synthetic */ ParticleNormal this$0;
                final /* synthetic */ QuestContext.Frame $frame;
                {
                    this.this$0 = $receiver;
                    this.$frame = $frame;
                    super(1);
                }

                public final void invoke(Location it) {
                    if (this.this$0.getSelf()) {
                        Effect effect = this.this$0.getEffect();
                        Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                        effect.run(it, UtilsForKetherKt.getBukkitPlayer(this.$frame));
                    } else {
                        Effect effect = this.this$0.getEffect();
                        Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                        effect.run(it);
                    }
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"override fun run(frame: \u2026}\n            }\n        }");
            return completionStage;
        }

        private static final void run$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }
    }
}

