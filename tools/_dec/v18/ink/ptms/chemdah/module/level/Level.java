/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.Ref$IntRef
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.level;

import ink.ptms.chemdah.module.level.Algorithm;
import ink.ptms.chemdah.module.level.PlayerLevel;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.Ref;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007J\u0014\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u0005J\u0014\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u0005J\u0014\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u0005J\u0014\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u0005J\u0006\u0010\u0015\u001a\u00020\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u001e\u0010\u0006\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u0005@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u001e\u0010\u0004\u001a\u00020\u00052\u0006\u0010\n\u001a\u00020\u0005@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\f\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/module/level/Level;", "", "algorithm", "Link/ptms/chemdah/module/level/Algorithm;", "level", "", "experience", "(Link/ptms/chemdah/module/level/Algorithm;II)V", "getAlgorithm", "()Link/ptms/chemdah/module/level/Algorithm;", "<set-?>", "getExperience", "()I", "getLevel", "addExperience", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "value", "addLevel", "setExperience", "setLevel", "toPlayerLevel", "Link/ptms/chemdah/module/level/PlayerLevel;", "Chemdah"})
public final class Level {
    @NotNull
    private final Algorithm algorithm;
    private int experience;
    private int level;

    public Level(@NotNull Algorithm algorithm, int level, int experience) {
        Intrinsics.checkNotNullParameter((Object)algorithm, (String)"algorithm");
        this.algorithm = algorithm;
        this.experience = experience;
        this.level = level;
    }

    @NotNull
    public final Algorithm getAlgorithm() {
        return this.algorithm;
    }

    public final int getExperience() {
        return this.experience;
    }

    public final int getLevel() {
        return this.level;
    }

    @NotNull
    public final PlayerLevel toPlayerLevel() {
        return new PlayerLevel(this.level, this.experience);
    }

    @NotNull
    public final CompletableFuture<Void> setLevel(int value2) {
        this.level = value2;
        return this.addExperience(0);
    }

    @NotNull
    public final CompletableFuture<Void> addLevel(int value2) {
        this.level += value2;
        return this.addExperience(0);
    }

    @NotNull
    public final CompletableFuture<Void> setExperience(int value2) {
        this.experience = value2;
        return this.addExperience(0);
    }

    @NotNull
    public final CompletableFuture<Void> addExperience(int value2) {
        if (this.level >= this.algorithm.getMaxLevel()) {
            this.level = this.algorithm.getMaxLevel();
            this.algorithm.getExp(this.level).thenAccept(arg_0 -> Level.addExperience$lambda$0((Function1)new Function1<Integer, Unit>(this){
                final /* synthetic */ Level this$0;
                {
                    this.this$0 = $receiver;
                    super(1);
                }

                public final void invoke(Integer it) {
                    Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                    Level.access$setExperience$p(this.this$0, it);
                }
            }, arg_0));
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
            return completableFuture;
        }
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        Ref.IntRef lvl = new Ref.IntRef();
        lvl.element = this.level;
        Ref.IntRef exp = new Ref.IntRef();
        exp.element = this.experience + value2;
        Ref.IntRef expNextLevel = new Ref.IntRef();
        Level.addExperience$process(this, lvl, expNextLevel, exp, future);
        return future;
    }

    private static final void addExperience$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void addExperience$getNextLevel$lambda$1(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final CompletableFuture<Void> addExperience$getNextLevel(Level this$0, Ref.IntRef lvl, Ref.IntRef expNextLevel) {
        return this$0.algorithm.getExp(lvl.element).thenAccept(arg_0 -> Level.addExperience$getNextLevel$lambda$1((Function1)new Function1<Integer, Unit>(expNextLevel){
            final /* synthetic */ Ref.IntRef $expNextLevel;
            {
                this.$expNextLevel = $expNextLevel;
                super(1);
            }

            public final void invoke(Integer it) {
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                this.$expNextLevel.element = it <= 0 ? Integer.MAX_VALUE : it;
            }
        }, arg_0));
    }

    private static final void addExperience$finish(Ref.IntRef lvl, Level this$0, Ref.IntRef expNextLevel, Ref.IntRef exp, CompletableFuture<Void> future) {
        if (lvl.element >= this$0.algorithm.getMaxLevel()) {
            this$0.level = this$0.algorithm.getMaxLevel();
            this$0.experience = expNextLevel.element;
        } else {
            this$0.level = lvl.element;
            this$0.experience = exp.element;
        }
        future.complete(null);
    }

    private static final void addExperience$process$lambda$2(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void addExperience$process(Level this$0, Ref.IntRef lvl, Ref.IntRef expNextLevel, Ref.IntRef exp, CompletableFuture<Void> future) {
        Level.addExperience$getNextLevel(this$0, lvl, expNextLevel).thenAccept(arg_0 -> Level.addExperience$process$lambda$2((Function1)new Function1<Void, Unit>(exp, expNextLevel, lvl, this$0, future){
            final /* synthetic */ Ref.IntRef $exp;
            final /* synthetic */ Ref.IntRef $expNextLevel;
            final /* synthetic */ Ref.IntRef $lvl;
            final /* synthetic */ Level this$0;
            final /* synthetic */ CompletableFuture<Void> $future;
            {
                this.$exp = $exp;
                this.$expNextLevel = $expNextLevel;
                this.$lvl = $lvl;
                this.this$0 = $receiver;
                this.$future = $future;
                super(1);
            }

            public final void invoke(Void it) {
                if (this.$exp.element >= this.$expNextLevel.element) {
                    ++this.$lvl.element;
                    this.$exp.element -= this.$expNextLevel.element;
                    Level.access$addExperience$process(this.this$0, this.$lvl, this.$expNextLevel, this.$exp, this.$future);
                } else {
                    Level.access$addExperience$finish(this.$lvl, this.this$0, this.$expNextLevel, this.$exp, this.$future);
                }
            }
        }, arg_0));
    }

    public static final /* synthetic */ void access$setExperience$p(Level $this, int n) {
        $this.experience = n;
    }

    public static final /* synthetic */ void access$addExperience$process(Level this$0, Ref.IntRef lvl, Ref.IntRef expNextLevel, Ref.IntRef exp, CompletableFuture future) {
        Level.addExperience$process(this$0, lvl, expNextLevel, exp, future);
    }

    public static final /* synthetic */ void access$addExperience$finish(Ref.IntRef lvl, Level this$0, Ref.IntRef expNextLevel, Ref.IntRef exp, CompletableFuture future) {
        Level.addExperience$finish(lvl, this$0, expNextLevel, exp, future);
    }
}

