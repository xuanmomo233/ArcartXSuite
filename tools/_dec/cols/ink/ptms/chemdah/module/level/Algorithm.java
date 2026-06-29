/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.level;

import ink.ptms.chemdah.module.level.Algorithm;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.common5.NashornCompilerKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.kether.KetherConcurrentKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptOptions;
import java.util.concurrent.CompletableFuture;
import javax.script.CompiledScript;
import javax.script.SimpleBindings;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\b&\u0018\u00002\u00020\u0001:\u0002\n\u000bB\u0005\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00040\b2\u0006\u0010\t\u001a\u00020\u0004H&R\u0012\u0010\u0003\u001a\u00020\u0004X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/module/level/Algorithm;", "", "()V", "maxLevel", "", "getMaxLevel", "()I", "getExp", "Ljava/util/concurrent/CompletableFuture;", "level", "JavaScript", "Kether", "Chemdah"})
public abstract class Algorithm {
    public abstract int getMaxLevel();

    @NotNull
    public abstract CompletableFuture<Integer> getExp(int var1);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00060\u00102\u0006\u0010\u0011\u001a\u00020\u0006H\u0016R\u0014\u0010\u0005\u001a\u00020\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0013\u0010\t\u001a\u0004\u0018\u00010\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/module/level/Algorithm$JavaScript;", "Link/ptms/chemdah/module/level/Algorithm;", "section", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "maxLevel", "", "getMaxLevel", "()I", "script", "Ljavax/script/CompiledScript;", "getScript", "()Ljavax/script/CompiledScript;", "getSection", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getExp", "Ljava/util/concurrent/CompletableFuture;", "level", "Chemdah"})
    public static final class JavaScript
    extends Algorithm {
        @NotNull
        private final ConfigurationSection section;
        @Nullable
        private final CompiledScript script;
        private final int maxLevel;

        public JavaScript(@NotNull ConfigurationSection section) {
            Intrinsics.checkNotNullParameter((Object)section, (String)"section");
            this.section = section;
            String string = this.section.getString("experience.math");
            this.script = string != null ? NashornCompilerKt.compileJS((String)string) : null;
            this.maxLevel = this.section.getInt("max");
        }

        @NotNull
        public final ConfigurationSection getSection() {
            return this.section;
        }

        @Nullable
        public final CompiledScript getScript() {
            return this.script;
        }

        @Override
        public int getMaxLevel() {
            return this.maxLevel;
        }

        @Override
        @NotNull
        public CompletableFuture<Integer> getExp(int level) {
            if (this.script == null) {
                CompletableFuture<Integer> completableFuture = CompletableFuture.completedFuture(0);
                Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(0)");
                return completableFuture;
            }
            CompletableFuture<Integer> completableFuture = CompletableFuture.completedFuture(Coerce.toInteger((Object)this.script.eval(new SimpleBindings(MapsKt.mapOf((Pair)TuplesKt.to((Object)"level", (Object)level))))));
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(Coerce.t\u2026pOf(\"level\" to level)))))");
            return completableFuture;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00060\u00102\u0006\u0010\u0011\u001a\u00020\u0006H\u0016R\u0014\u0010\u0005\u001a\u00020\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0013\u0010\t\u001a\u0004\u0018\u00010\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/module/level/Algorithm$Kether;", "Link/ptms/chemdah/module/level/Algorithm;", "section", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "maxLevel", "", "getMaxLevel", "()I", "script", "", "getScript", "()Ljava/lang/String;", "getSection", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getExp", "Ljava/util/concurrent/CompletableFuture;", "level", "Chemdah"})
    public static final class Kether
    extends Algorithm {
        @NotNull
        private final ConfigurationSection section;
        @Nullable
        private final String script;
        private final int maxLevel;

        public Kether(@NotNull ConfigurationSection section) {
            Intrinsics.checkNotNullParameter((Object)section, (String)"section");
            this.section = section;
            String string = this.section.getString("experience.math");
            this.script = string != null ? string.toString() : null;
            this.maxLevel = this.section.getInt("max");
        }

        @NotNull
        public final ConfigurationSection getSection() {
            return this.section;
        }

        @Nullable
        public final String getScript() {
            return this.script;
        }

        @Override
        public int getMaxLevel() {
            return this.maxLevel;
        }

        @Override
        @NotNull
        public CompletableFuture<Integer> getExp(int level) {
            if (this.script == null) {
                CompletableFuture<Integer> completableFuture = CompletableFuture.completedFuture(0);
                Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(0)");
                return completableFuture;
            }
            CompletableFuture<Integer> completableFuture = (CompletableFuture<Integer>)KetherHelperKt.runKether$default(null, (boolean)true, (Function0)((Function0)new Function0<CompletableFuture<Integer>>(this, level){
                final /* synthetic */ Kether this$0;
                final /* synthetic */ int $level;
                {
                    this.this$0 = $receiver;
                    this.$level = $level;
                    super(0);
                }

                @NotNull
                public final CompletableFuture<Integer> invoke() {
                    return KetherConcurrentKt.int((CompletableFuture)KetherShell.INSTANCE.eval(this.this$0.getScript(), ScriptOptions.Companion.new((Function1)new Function1<ScriptOptions.ScriptOptionsBuilder, Unit>(this.$level){
                        final /* synthetic */ int $level;
                        {
                            this.$level = $level;
                            super(1);
                        }

                        public final void invoke(@NotNull ScriptOptions.ScriptOptionsBuilder $this$new) {
                            Intrinsics.checkNotNullParameter((Object)$this$new, (String)"$this$new");
                            Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"level", (Object)this.$level)};
                            $this$new.vars(pairArray);
                        }
                    })), (Function1)getExp.2.INSTANCE);
                }
            }), (int)1, null);
            if (completableFuture == null) {
                CompletableFuture<Integer> completableFuture2 = CompletableFuture.completedFuture(0);
                completableFuture = completableFuture2;
                Intrinsics.checkNotNullExpressionValue(completableFuture2, (String)"completedFuture(0)");
            }
            return completableFuture;
        }
    }
}

