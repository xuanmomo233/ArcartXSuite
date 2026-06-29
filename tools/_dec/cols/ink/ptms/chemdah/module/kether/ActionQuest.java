/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.addon.AddonStats;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.module.kether.ActionQuest;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.kether.ParsedAction;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.PlayerOperator;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kotlin.Metadata;
import kotlin1822.NoWhenBranchMatchedException;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\t\u0018\u0000 \u00032\u00020\u0001:\u0007\u0003\u0004\u0005\u0006\u0007\b\tB\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest;", "", "()V", "Companion", "QuestDataGet", "QuestDataKeys", "QuestDataSet", "QuestProgress", "QuestStats", "Quests", "Chemdah"})
public final class ActionQuest {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0014\u0010\u0003\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00060\u00050\u0004H\u0007J\u0012\u0010\u0007\u001a\f\u0012\b\b\u0001\u0012\u0004\u0018\u00010\u00010\u0004H\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$Companion;", "", "()V", "parser0", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "", "", "parser1", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"quests"}, namespace="chemdah", shared=true)
        @NotNull
        public final ScriptActionParser<List<String>> parser0() {
            return KetherHelperKt.scriptParser((Function1)parser0.1.INSTANCE);
        }

        @KetherParser(value={"quest"}, shared=true)
        @NotNull
        public final ScriptActionParser<? extends Object> parser1() {
            return KetherHelperKt.scriptParser((Function1)parser1.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001B\u0011\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\u0002\u0010\u0005J\u001c\u0010\b\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\t2\n\u0010\n\u001a\u00060\u000bj\u0002`\fH\u0016R\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$QuestDataGet;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "key", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getKey", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class QuestDataGet
    extends ScriptAction<Object> {
        @NotNull
        private final ParsedAction<?> key;

        public QuestDataGet(@NotNull ParsedAction<?> key) {
            Intrinsics.checkNotNullParameter(key, (String)"key");
            this.key = key;
        }

        @NotNull
        public final ParsedAction<?> getKey() {
            return this.key;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletionStage completionStage = frame.newFrame(this.key).run().thenApply(arg_0 -> QuestDataGet.run$lambda$0(frame, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"frame.newFrame(key).run<\u2026ng())?.data\n            }");
            return completionStage;
        }

        private static final Object run$lambda$0(QuestContext.Frame $frame, Object it) {
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Object object = UtilsForKetherKt.getProfile($frame);
            return object != null && (object = PlayerProfile.getQuestById$default((PlayerProfile)object, UtilsForKetherKt.getQuestSelected($frame), false, 2, null)) != null && (object = ((Quest)object).getPersistentDataContainer()) != null && (object = ((DataContainer)object).get(it.toString())) != null ? ((Data)object).getData() : null;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0003J\u001c\u0010\u0004\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u00052\n\u0010\u0006\u001a\u00060\u0007j\u0002`\bH\u0016\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$QuestDataKeys;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "()V", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class QuestDataKeys
    extends ScriptAction<Object> {
        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            Object object = UtilsForKetherKt.getProfile(frame);
            if (object == null || (object = PlayerProfile.getQuestById$default((PlayerProfile)object, UtilsForKetherKt.getQuestSelected(frame), false, 2, null)) == null || (object = ((Quest)object).getPersistentDataContainer()) == null || (object = ((DataContainer)object).keys()) == null) {
                object = CollectionsKt.emptyList();
            }
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(object);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(\n       \u2026t<String>()\n            )");
            return completableFuture;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B%\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u001a\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00020\u000f2\n\u0010\u0010\u001a\u00060\u0011j\u0002`\u0012H\u0016R\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\n\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$QuestDataSet;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "key", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "value", "symbol", "Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;)V", "getKey", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getSymbol", "()Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;", "getValue", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class QuestDataSet
    extends ScriptAction<Void> {
        @NotNull
        private final ParsedAction<?> key;
        @NotNull
        private final ParsedAction<?> value;
        @NotNull
        private final PlayerOperator.Method symbol;

        public QuestDataSet(@NotNull ParsedAction<?> key, @NotNull ParsedAction<?> value2, @NotNull PlayerOperator.Method symbol) {
            Intrinsics.checkNotNullParameter(key, (String)"key");
            Intrinsics.checkNotNullParameter(value2, (String)"value");
            Intrinsics.checkNotNullParameter((Object)symbol, (String)"symbol");
            this.key = key;
            this.value = value2;
            this.symbol = symbol;
        }

        @NotNull
        public final ParsedAction<?> getKey() {
            return this.key;
        }

        @NotNull
        public final ParsedAction<?> getValue() {
            return this.value;
        }

        @NotNull
        public final PlayerOperator.Method getSymbol() {
            return this.symbol;
        }

        @NotNull
        public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletionStage completionStage = frame.newFrame(this.key).run().thenAccept(arg_0 -> QuestDataSet.run$lambda$1(frame, this, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"frame.newFrame(key).run<\u2026          }\n            }");
            return completionStage;
        }

        private static final void run$lambda$1$lambda$0(QuestContext.Frame $frame, Object $key, QuestDataSet this$0, Object value2) {
            DataContainer persistentDataContainer;
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            PlayerProfile profile = UtilsForKetherKt.getProfile($frame);
            if (profile == null) {
                Object[] objectArray = new Object[]{"Player data has not been loaded yet. (" + UtilsForKetherKt.getBukkitPlayer($frame).getName() + ')'};
                IOKt.warning((Object[])objectArray);
                return;
            }
            Quest quest2 = PlayerProfile.getQuestById$default(profile, UtilsForKetherKt.getQuestSelected($frame), false, 2, null);
            DataContainer dataContainer = persistentDataContainer = quest2 != null ? quest2.getPersistentDataContainer() : null;
            if (persistentDataContainer != null) {
                if (value2 == null) {
                    persistentDataContainer.remove($key.toString());
                } else if (this$0.symbol == PlayerOperator.Method.INCREASE) {
                    persistentDataContainer.set($key.toString(), UtilsForKetherKt.increaseAny(persistentDataContainer.get($key.toString()), value2));
                } else {
                    persistentDataContainer.set($key.toString(), value2);
                }
            }
        }

        private static final void run$lambda$1(QuestContext.Frame $frame, QuestDataSet this$0, Object key) {
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            $frame.newFrame(this$0.value).run().thenAccept(arg_0 -> QuestDataSet.run$lambda$1$lambda$0($frame, key, this$0, arg_0));
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u0011B\u001b\u0012\f\u0010\u0003\u001a\b\u0012\u0002\b\u0003\u0018\u00010\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001a\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00020\r2\n\u0010\u000e\u001a\u00060\u000fj\u0002`\u0010H\u0016R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0017\u0010\u0003\u001a\b\u0012\u0002\b\u0003\u0018\u00010\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$QuestProgress;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "task", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "action", "Link/ptms/chemdah/module/kether/ActionQuest$QuestProgress$Action;", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/module/kether/ActionQuest$QuestProgress$Action;)V", "getAction", "()Link/ptms/chemdah/module/kether/ActionQuest$QuestProgress$Action;", "getTask", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Action", "Chemdah"})
    public static final class QuestProgress
    extends ScriptAction<Object> {
        @Nullable
        private final ParsedAction<?> task;
        @NotNull
        private final Action action;

        public QuestProgress(@Nullable ParsedAction<?> task, @NotNull Action action) {
            Intrinsics.checkNotNullParameter((Object)((Object)action), (String)"action");
            this.task = task;
            this.action = action;
        }

        @Nullable
        public final ParsedAction<?> getTask() {
            return this.task;
        }

        @NotNull
        public final Action getAction() {
            return this.action;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletableFuture<Object> future = new CompletableFuture<Object>();
            PlayerProfile profile = UtilsForKetherKt.getProfile(frame);
            if (profile == null) {
                future.complete("NULL");
                return future;
            }
            Quest quest2 = PlayerProfile.getQuestById$default(profile, UtilsForKetherKt.getQuestSelected(frame), false, 2, null);
            if (quest2 == null) {
                future.complete("NULL");
                return future;
            }
            if (this.task == null) {
                AddonStats.Companion.getProgress(quest2.getTemplate(), profile).thenAccept(arg_0 -> QuestProgress.run$lambda$0((Function1)new Function1<Progress, Unit>(future, this){
                    final /* synthetic */ CompletableFuture<Object> $future;
                    final /* synthetic */ QuestProgress this$0;
                    {
                        this.$future = $future;
                        this.this$0 = $receiver;
                        super(1);
                    }

                    public final void invoke(Progress progress) {
                        Object object;
                        switch (run.WhenMappings.$EnumSwitchMapping$0[this.this$0.getAction().ordinal()]) {
                            case 1: {
                                object = progress.getValue();
                                break;
                            }
                            case 2: {
                                object = progress.getTarget();
                                break;
                            }
                            case 3: {
                                object = progress.getPercent();
                                break;
                            }
                            case 4: {
                                object = Coerce.format((double)(progress.getPercent() * (double)100));
                                break;
                            }
                            default: {
                                throw new NoWhenBranchMatchedException();
                            }
                        }
                        this.$future.complete(object);
                    }
                }, arg_0));
            } else {
                frame.newFrame(this.task).run().thenAccept(arg_0 -> QuestProgress.run$lambda$2(quest2, future, profile, this, arg_0));
            }
            return future;
        }

        private static final void run$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }

        private static final void run$lambda$2$lambda$1(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }

        private static final void run$lambda$2(Quest $quest, CompletableFuture $future, PlayerProfile $profile, QuestProgress this$0, Object task) {
            Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            Task t = $quest.getTask(task.toString());
            if (t == null) {
                $future.complete("NULL");
                return;
            }
            AddonStats.Companion.getProgress(t, $profile).thenAccept(arg_0 -> QuestProgress.run$lambda$2$lambda$1((Function1)new Function1<Progress, Unit>((CompletableFuture<Object>)$future, this$0){
                final /* synthetic */ CompletableFuture<Object> $future;
                final /* synthetic */ QuestProgress this$0;
                {
                    this.$future = $future;
                    this.this$0 = $receiver;
                    super(1);
                }

                public final void invoke(Progress progress) {
                    Object object;
                    switch (run.2.WhenMappings.$EnumSwitchMapping$0[this.this$0.getAction().ordinal()]) {
                        case 1: {
                            object = progress.getValue();
                            break;
                        }
                        case 2: {
                            object = progress.getTarget();
                            break;
                        }
                        case 3: {
                            object = progress.getPercent();
                            break;
                        }
                        case 4: {
                            object = Coerce.format((double)(progress.getPercent() * (double)100));
                            break;
                        }
                        default: {
                            throw new NoWhenBranchMatchedException();
                        }
                    }
                    this.$future.complete(object);
                }
            }, arg_0));
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0006\b\u0086\u0001\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$QuestProgress$Action;", "", "(Ljava/lang/String;I)V", "VALUE", "TARGET", "PERCENT", "PERCENT_100", "Chemdah"})
        public static final class Action
        extends Enum<Action> {
            public static final /* enum */ Action VALUE = new Action();
            public static final /* enum */ Action TARGET = new Action();
            public static final /* enum */ Action PERCENT = new Action();
            public static final /* enum */ Action PERCENT_100 = new Action();
            private static final /* synthetic */ Action[] $VALUES;

            public static Action[] values() {
                return (Action[])$VALUES.clone();
            }

            public static Action valueOf(String value2) {
                return Enum.valueOf(Action.class, value2);
            }

            static {
                $VALUES = actionArray = new Action[]{Action.VALUE, Action.TARGET, Action.PERCENT, Action.PERCENT_100};
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u0011B\u001b\u0012\f\u0010\u0003\u001a\b\u0012\u0002\b\u0003\u0018\u00010\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001a\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00020\r2\n\u0010\u000e\u001a\u00060\u000fj\u0002`\u0010H\u0016R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0017\u0010\u0003\u001a\b\u0012\u0002\b\u0003\u0018\u00010\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$QuestStats;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "task", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "action", "Link/ptms/chemdah/module/kether/ActionQuest$QuestStats$Action;", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/module/kether/ActionQuest$QuestStats$Action;)V", "getAction", "()Link/ptms/chemdah/module/kether/ActionQuest$QuestStats$Action;", "getTask", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Action", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nActionQuest.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ActionQuest.kt\nink/ptms/chemdah/module/kether/ActionQuest$QuestStats\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,430:1\n1855#2,2:431\n*S KotlinDebug\n*F\n+ 1 ActionQuest.kt\nink/ptms/chemdah/module/kether/ActionQuest$QuestStats\n*L\n116#1:431,2\n*E\n"})
    public static final class QuestStats
    extends ScriptAction<Void> {
        @Nullable
        private final ParsedAction<?> task;
        @NotNull
        private final Action action;

        public QuestStats(@Nullable ParsedAction<?> task, @NotNull Action action) {
            Intrinsics.checkNotNullParameter((Object)((Object)action), (String)"action");
            this.task = task;
            this.action = action;
        }

        @Nullable
        public final ParsedAction<?> getTask() {
            return this.task;
        }

        @NotNull
        public final Action getAction() {
            return this.action;
        }

        @NotNull
        public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            PlayerProfile profile = UtilsForKetherKt.getProfile(frame);
            if (profile == null) {
                Object[] objectArray = new Object[]{"Player data has not been loaded yet. (" + UtilsForKetherKt.getBukkitPlayer(frame).getName() + ')'};
                IOKt.warning((Object[])objectArray);
                CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
                Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
                return completableFuture;
            }
            if (this.task == null) {
                Quest quest2 = PlayerProfile.getQuestById$default(profile, UtilsForKetherKt.getQuestSelected(frame), false, 2, null);
                if (quest2 != null) {
                    Quest $this$run_u24lambda_u240 = quest2;
                    boolean bl = false;
                    switch (WhenMappings.$EnumSwitchMapping$0[this.action.ordinal()]) {
                        case 1: {
                            AddonStats.Companion.hiddenStats($this$run_u24lambda_u240, profile);
                            break;
                        }
                        case 2: {
                            AddonStats.Companion.refreshStats($this$run_u24lambda_u240.getTemplate(), profile);
                            AddonStats.Companion.refreshStatusAlwaysType($this$run_u24lambda_u240, profile);
                        }
                    }
                }
            } else {
                frame.newFrame(this.task).run().thenAccept(arg_0 -> QuestStats.run$lambda$4(profile, frame, this, arg_0));
            }
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
            return completableFuture;
        }

        private static final void run$lambda$4(PlayerProfile $profile, QuestContext.Frame $frame, QuestStats this$0, Object task) {
            block13: {
                Quest $this$run_u24lambda_u244_u24lambda_u243;
                block14: {
                    Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
                    Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
                    Quest quest2 = PlayerProfile.getQuestById$default($profile, UtilsForKetherKt.getQuestSelected($frame), false, 2, null);
                    if (quest2 == null) break block13;
                    $this$run_u24lambda_u244_u24lambda_u243 = quest2;
                    boolean bl = false;
                    if (!Intrinsics.areEqual((Object)task.toString(), (Object)"*")) break block14;
                    Iterable $this$forEach$iv = $this$run_u24lambda_u244_u24lambda_u243.getTasks();
                    boolean $i$f$forEach = false;
                    for (Object element$iv : $this$forEach$iv) {
                        Task it = (Task)element$iv;
                        boolean bl2 = false;
                        switch (WhenMappings.$EnumSwitchMapping$0[this$0.action.ordinal()]) {
                            case 1: {
                                AddonStats.Companion.hiddenStats(it, $profile);
                                break;
                            }
                            case 2: {
                                AddonStats.Companion.refreshStats(it, $profile);
                            }
                        }
                    }
                    switch (WhenMappings.$EnumSwitchMapping$0[this$0.action.ordinal()]) {
                        case 1: {
                            AddonStats.Companion.hiddenStats($this$run_u24lambda_u244_u24lambda_u243, $profile);
                            break;
                        }
                        case 2: {
                            AddonStats.Companion.refreshStats($this$run_u24lambda_u244_u24lambda_u243.getTemplate(), $profile);
                        }
                    }
                    break block13;
                }
                Task task2 = $this$run_u24lambda_u244_u24lambda_u243.getTask(task.toString());
                if (task2 == null) break block13;
                Task $this$run_u24lambda_u244_u24lambda_u243_u24lambda_u242 = task2;
                boolean bl = false;
                switch (WhenMappings.$EnumSwitchMapping$0[this$0.action.ordinal()]) {
                    case 1: {
                        AddonStats.Companion.hiddenStats($this$run_u24lambda_u244_u24lambda_u243_u24lambda_u242, $profile);
                        break;
                    }
                    case 2: {
                        AddonStats.Companion.refreshStats($this$run_u24lambda_u244_u24lambda_u243_u24lambda_u242, $profile);
                    }
                }
            }
        }

        @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0004\b\u0086\u0001\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$QuestStats$Action;", "", "(Ljava/lang/String;I)V", "HIDDEN", "REFRESH", "Chemdah"})
        public static final class Action
        extends Enum<Action> {
            public static final /* enum */ Action HIDDEN = new Action();
            public static final /* enum */ Action REFRESH = new Action();
            private static final /* synthetic */ Action[] $VALUES;

            public static Action[] values() {
                return (Action[])$VALUES.clone();
            }

            public static Action valueOf(String value2) {
                return Enum.valueOf(Action.class, value2);
            }

            static {
                $VALUES = actionArray = new Action[]{Action.HIDDEN, Action.REFRESH};
            }
        }

        @Metadata(mv={1, 8, 0}, k=3, xi=48)
        public final class WhenMappings {
            public static final /* synthetic */ int[] $EnumSwitchMapping$0;

            static {
                int[] nArray = new int[Action.values().length];
                try {
                    nArray[Action.HIDDEN.ordinal()] = 1;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[Action.REFRESH.ordinal()] = 2;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                $EnumSwitchMapping$0 = nArray;
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00030\u00020\u0001B\r\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J \u0010\t\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00030\u00020\n2\n\u0010\u000b\u001a\u00060\fj\u0002`\rH\u0016R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$Quests;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "", "self", "", "(Z)V", "getSelf", "()Z", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nActionQuest.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ActionQuest.kt\nink/ptms/chemdah/module/kether/ActionQuest$Quests\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,430:1\n1549#2:431\n1620#2,3:432\n*S KotlinDebug\n*F\n+ 1 ActionQuest.kt\nink/ptms/chemdah/module/kether/ActionQuest$Quests\n*L\n33#1:431\n33#1:432,3\n*E\n"})
    public static final class Quests
    extends ScriptAction<List<? extends String>> {
        private final boolean self;

        public Quests(boolean self) {
            this.self = self;
        }

        public final boolean getSelf() {
            return this.self;
        }

        /*
         * WARNING - void declaration
         */
        @NotNull
        public CompletableFuture<List<String>> run(@NotNull QuestContext.Frame frame) {
            List list2;
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            Object object = UtilsForKetherKt.getProfile(frame);
            if (object != null && (object = ((PlayerProfile)object).getQuests(!this.self)) != null) {
                void $this$mapTo$iv$iv;
                Iterable $this$map$iv = (Iterable)object;
                boolean $i$f$map = false;
                Iterable iterable = $this$map$iv;
                Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (Object item$iv$iv : $this$mapTo$iv$iv) {
                    void it;
                    Quest quest2 = (Quest)item$iv$iv;
                    Collection collection = destination$iv$iv;
                    boolean bl = false;
                    collection.add(it.getId());
                }
                list2 = (List)destination$iv$iv;
            } else {
                list2 = CollectionsKt.emptyList();
            }
            CompletableFuture<List<String>> completableFuture = CompletableFuture.completedFuture(list2);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(frame.ge\u2026{ it.id } ?: emptyList())");
            return completableFuture;
        }
    }
}

