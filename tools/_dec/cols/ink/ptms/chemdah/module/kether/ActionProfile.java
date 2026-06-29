/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.module.kether.ActionProfile;
import ink.ptms.chemdah.module.level.Level;
import ink.ptms.chemdah.module.level.LevelOption;
import ink.ptms.chemdah.module.level.LevelSystem;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\t\u0018\u0000 \u00032\u00020\u0001:\u0007\u0003\u0004\u0005\u0006\u0007\b\tB\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/module/kether/ActionProfile;", "", "()V", "Companion", "LevelType", "ProfileDataGet", "ProfileDataKeys", "ProfileDataSet", "ProfileLevelGet", "ProfileLevelSet", "Chemdah"})
public final class ActionProfile {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\f\u0012\b\b\u0001\u0012\u0004\u0018\u00010\u00010\u0004H\u0007\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/ActionProfile$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"profile"}, shared=true)
        @NotNull
        public final ScriptActionParser<? extends Object> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0005\b\u0086\u0001\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/ActionProfile$LevelType;", "", "(Ljava/lang/String;I)V", "LEVEL", "EXP", "EXP_MAX", "Chemdah"})
    public static final class LevelType
    extends Enum<LevelType> {
        public static final /* enum */ LevelType LEVEL = new LevelType();
        public static final /* enum */ LevelType EXP = new LevelType();
        public static final /* enum */ LevelType EXP_MAX = new LevelType();
        private static final /* synthetic */ LevelType[] $VALUES;

        public static LevelType[] values() {
            return (LevelType[])$VALUES.clone();
        }

        public static LevelType valueOf(String value2) {
            return Enum.valueOf(LevelType.class, value2);
        }

        static {
            $VALUES = levelTypeArray = new LevelType[]{LevelType.LEVEL, LevelType.EXP, LevelType.EXP_MAX};
        }
    }

    /*
     * Illegal identifiers - consider using --renameillegalidents true
     */
    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u001f\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\f\b\u0002\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\u0002\u0010\u0006J\u001a\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00020\u000b2\n\u0010\f\u001a\u00060\rj\u0002`\u000eH\u0016R\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\b\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/kether/ActionProfile$ProfileDataGet;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "key", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "default", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getDefault", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getKey", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class ProfileDataGet
    extends ScriptAction<Object> {
        @NotNull
        private final ParsedAction<?> key;
        @NotNull
        private final ParsedAction<?> default;

        public ProfileDataGet(@NotNull ParsedAction<?> key, @NotNull ParsedAction<?> parsedAction) {
            Intrinsics.checkNotNullParameter(key, (String)"key");
            Intrinsics.checkNotNullParameter(parsedAction, (String)"default");
            this.key = key;
            this.default = parsedAction;
        }

        public /* synthetic */ ProfileDataGet(ParsedAction parsedAction, ParsedAction parsedAction2, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 2) != 0) {
                ParsedAction parsedAction3 = ParsedAction.noop();
                Intrinsics.checkNotNullExpressionValue((Object)parsedAction3, (String)"noop<Any>()");
                parsedAction2 = parsedAction3;
            }
            this(parsedAction, parsedAction2);
        }

        @NotNull
        public final ParsedAction<?> getKey() {
            return this.key;
        }

        @NotNull
        public final ParsedAction<?> getDefault() {
            return this.default;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletableFuture<Object> future = new CompletableFuture<Object>();
            frame.newFrame(this.key).run().thenApply(arg_0 -> ProfileDataGet.run$lambda$1(frame, this, future, arg_0));
            return future;
        }

        private static final Boolean run$lambda$1$lambda$0(CompletableFuture $future, QuestContext.Frame $frame, Object $it, Object def) {
            Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Object object = UtilsForKetherKt.getProfile($frame);
            if (object == null || (object = ((PlayerProfile)object).getPersistentDataContainer()) == null || (object = ((DataContainer)object).get($it.toString())) == null || (object = ((Data)object).getData()) == null) {
                object = def;
            }
            return $future.complete(object);
        }

        private static final CompletableFuture run$lambda$1(QuestContext.Frame $frame, ProfileDataGet this$0, CompletableFuture $future, Object it) {
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
            return $frame.newFrame(this$0.default).run().thenApply(arg_0 -> ProfileDataGet.run$lambda$1$lambda$0($future, $frame, it, arg_0));
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00030\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0004J \u0010\u0005\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00030\u00020\u00062\n\u0010\u0007\u001a\u00060\bj\u0002`\tH\u0016\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/module/kether/ActionProfile$ProfileDataKeys;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "", "()V", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class ProfileDataKeys
    extends ScriptAction<List<? extends String>> {
        @NotNull
        public CompletableFuture<List<String>> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            Object object = UtilsForKetherKt.getProfile(frame);
            if (object == null || (object = ((PlayerProfile)object).getPersistentDataContainer()) == null || (object = ((DataContainer)object).keys()) == null) {
                object = CollectionsKt.emptyList();
            }
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(object);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(frame.ge\u2026r?.keys() ?: emptyList())");
            return completableFuture;
        }
    }

    /*
     * Illegal identifiers - consider using --renameillegalidents true
     */
    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B3\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\f\b\u0002\u0010\b\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\u0002\u0010\tJ\u001a\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00020\u00112\n\u0010\u0012\u001a\u00060\u0013j\u0002`\u0014H\u0016R\u0015\u0010\b\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u000bR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u000b\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/module/kether/ActionProfile$ProfileDataSet;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "key", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "value", "symbol", "Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;", "default", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getDefault", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getKey", "getSymbol", "()Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;", "getValue", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class ProfileDataSet
    extends ScriptAction<Void> {
        @NotNull
        private final ParsedAction<?> key;
        @NotNull
        private final ParsedAction<?> value;
        @NotNull
        private final PlayerOperator.Method symbol;
        @NotNull
        private final ParsedAction<?> default;

        public ProfileDataSet(@NotNull ParsedAction<?> key, @NotNull ParsedAction<?> value2, @NotNull PlayerOperator.Method symbol, @NotNull ParsedAction<?> parsedAction) {
            Intrinsics.checkNotNullParameter(key, (String)"key");
            Intrinsics.checkNotNullParameter(value2, (String)"value");
            Intrinsics.checkNotNullParameter((Object)symbol, (String)"symbol");
            Intrinsics.checkNotNullParameter(parsedAction, (String)"default");
            this.key = key;
            this.value = value2;
            this.symbol = symbol;
            this.default = parsedAction;
        }

        public /* synthetic */ ProfileDataSet(ParsedAction parsedAction, ParsedAction parsedAction2, PlayerOperator.Method method, ParsedAction parsedAction3, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 8) != 0) {
                ParsedAction parsedAction4 = ParsedAction.noop();
                Intrinsics.checkNotNullExpressionValue((Object)parsedAction4, (String)"noop<Any>()");
                parsedAction3 = parsedAction4;
            }
            this(parsedAction, parsedAction2, method, parsedAction3);
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
        public final ParsedAction<?> getDefault() {
            return this.default;
        }

        @NotNull
        public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletionStage completionStage = frame.newFrame(this.key).run().thenAccept(arg_0 -> ProfileDataSet.run$lambda$2(frame, this, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"frame.newFrame(key).run<\u2026          }\n            }");
            return completionStage;
        }

        private static final void run$lambda$2$lambda$1$lambda$0(QuestContext.Frame $frame, Object $value, Object $key, ProfileDataSet this$0, Object def) {
            DataContainer persistentDataContainer;
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            PlayerProfile playerProfile = UtilsForKetherKt.getProfile($frame);
            DataContainer dataContainer = persistentDataContainer = playerProfile != null ? playerProfile.getPersistentDataContainer() : null;
            if (persistentDataContainer == null) {
                Object[] objectArray = new Object[]{"Player data has not been loaded yet. (" + UtilsForKetherKt.getBukkitPlayer($frame).getName() + ')'};
                IOKt.warning((Object[])objectArray);
                return;
            }
            if ($value == null) {
                persistentDataContainer.remove($key.toString());
            } else if (this$0.symbol == PlayerOperator.Method.INCREASE) {
                String string = $key.toString();
                Object object = persistentDataContainer.get($key.toString());
                if (object == null) {
                    object = def;
                }
                persistentDataContainer.set(string, UtilsForKetherKt.increaseAny(object, $value));
            } else {
                persistentDataContainer.set($key.toString(), $value);
            }
        }

        private static final void run$lambda$2$lambda$1(QuestContext.Frame $frame, ProfileDataSet this$0, Object $key, Object value2) {
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            $frame.newFrame(this$0.default).run().thenAccept(arg_0 -> ProfileDataSet.run$lambda$2$lambda$1$lambda$0($frame, value2, $key, this$0, arg_0));
        }

        private static final void run$lambda$2(QuestContext.Frame $frame, ProfileDataSet this$0, Object key) {
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            $frame.newFrame(this$0.value).run().thenAccept(arg_0 -> ProfileDataSet.run$lambda$2$lambda$1($frame, this$0, key, arg_0));
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0019\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001a\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00020\r2\n\u0010\u000e\u001a\u00060\u000fj\u0002`\u0010H\u0016R\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/module/kether/ActionProfile$ProfileLevelGet;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "key", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "type", "Link/ptms/chemdah/module/kether/ActionProfile$LevelType;", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/module/kether/ActionProfile$LevelType;)V", "getKey", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getType", "()Link/ptms/chemdah/module/kether/ActionProfile$LevelType;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class ProfileLevelGet
    extends ScriptAction<Integer> {
        @NotNull
        private final ParsedAction<?> key;
        @NotNull
        private final LevelType type;

        public ProfileLevelGet(@NotNull ParsedAction<?> key, @NotNull LevelType type) {
            Intrinsics.checkNotNullParameter(key, (String)"key");
            Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
            this.key = key;
            this.type = type;
        }

        @NotNull
        public final ParsedAction<?> getKey() {
            return this.key;
        }

        @NotNull
        public final LevelType getType() {
            return this.type;
        }

        @NotNull
        public CompletableFuture<Integer> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletableFuture<Integer> future = new CompletableFuture<Integer>();
            frame.newFrame(this.key).run().thenApply(arg_0 -> ProfileLevelGet.run$lambda$1(frame, future, this, arg_0));
            return future;
        }

        private static final void run$lambda$1$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }

        private static final Unit run$lambda$1(QuestContext.Frame $frame, CompletableFuture $future, ProfileLevelGet this$0, Object it) {
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            LevelOption option = LevelSystem.INSTANCE.getLevelOption(it.toString());
            if (option != null) {
                PlayerProfile profile = UtilsForKetherKt.getProfile($frame);
                if (profile == null) {
                    $future.complete(-1);
                    return Unit.INSTANCE;
                }
                switch (WhenMappings.$EnumSwitchMapping$0[this$0.type.ordinal()]) {
                    case 1: {
                        $future.complete(LevelSystem.INSTANCE.getLevel(profile, option).getLevel());
                        break;
                    }
                    case 2: {
                        $future.complete(LevelSystem.INSTANCE.getLevel(profile, option).getExperience());
                        break;
                    }
                    case 3: {
                        option.getAlgorithm().getExp(LevelSystem.INSTANCE.getLevel(profile, option).getLevel()).thenAccept(arg_0 -> ProfileLevelGet.run$lambda$1$lambda$0((Function1)new Function1<Integer, Unit>((CompletableFuture<Integer>)$future){
                            final /* synthetic */ CompletableFuture<Integer> $future;
                            {
                                this.$future = $future;
                                super(1);
                            }

                            public final void invoke(Integer exp) {
                                this.$future.complete(exp);
                            }
                        }, arg_0));
                    }
                }
            } else {
                $future.complete(-1);
            }
            return Unit.INSTANCE;
        }

        @Metadata(mv={1, 8, 0}, k=3, xi=48)
        public final class WhenMappings {
            public static final /* synthetic */ int[] $EnumSwitchMapping$0;

            static {
                int[] nArray = new int[LevelType.values().length];
                try {
                    nArray[LevelType.LEVEL.ordinal()] = 1;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[LevelType.EXP.ordinal()] = 2;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[LevelType.EXP_MAX.ordinal()] = 3;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                $EnumSwitchMapping$0 = nArray;
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B-\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\n\u0010\u0007\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nJ\u001a\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00020\u00132\n\u0010\u0014\u001a\u00060\u0015j\u0002`\u0016H\u0016R\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0015\u0010\u0007\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\f\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/module/kether/ActionProfile$ProfileLevelSet;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "key", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "type", "Link/ptms/chemdah/module/kether/ActionProfile$LevelType;", "value", "symbol", "Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/module/kether/ActionProfile$LevelType;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;)V", "getKey", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getSymbol", "()Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;", "getType", "()Link/ptms/chemdah/module/kether/ActionProfile$LevelType;", "getValue", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class ProfileLevelSet
    extends ScriptAction<Void> {
        @NotNull
        private final ParsedAction<?> key;
        @NotNull
        private final LevelType type;
        @NotNull
        private final ParsedAction<?> value;
        @NotNull
        private final PlayerOperator.Method symbol;

        public ProfileLevelSet(@NotNull ParsedAction<?> key, @NotNull LevelType type, @NotNull ParsedAction<?> value2, @NotNull PlayerOperator.Method symbol) {
            Intrinsics.checkNotNullParameter(key, (String)"key");
            Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
            Intrinsics.checkNotNullParameter(value2, (String)"value");
            Intrinsics.checkNotNullParameter((Object)symbol, (String)"symbol");
            this.key = key;
            this.type = type;
            this.value = value2;
            this.symbol = symbol;
        }

        @NotNull
        public final ParsedAction<?> getKey() {
            return this.key;
        }

        @NotNull
        public final LevelType getType() {
            return this.type;
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
            CompletionStage completionStage = frame.newFrame(this.key).run().thenAccept(arg_0 -> ProfileLevelSet.run$lambda$5(frame, this, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"frame.newFrame(key).run<\u2026          }\n            }");
            return completionStage;
        }

        private static final void run$lambda$5$lambda$4$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }

        private static final void run$lambda$5$lambda$4$lambda$1(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }

        private static final void run$lambda$5$lambda$4$lambda$2(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }

        private static final void run$lambda$5$lambda$4$lambda$3(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            $tmp0.invoke(p0);
        }

        private static final void run$lambda$5$lambda$4(Object $key, QuestContext.Frame $frame, ProfileLevelSet this$0, Object value2) {
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            LevelOption option = LevelSystem.INSTANCE.getLevelOption($key.toString());
            if (option != null) {
                PlayerProfile profile = UtilsForKetherKt.getProfile($frame);
                if (profile == null) {
                    Object[] objectArray = new Object[]{"Player data has not been loaded yet. (" + UtilsForKetherKt.getBukkitPlayer($frame).getName() + ')'};
                    IOKt.warning((Object[])objectArray);
                    return;
                }
                Level playerLevel = option.toLevel(LevelSystem.INSTANCE.getLevel(profile, option));
                if (this$0.symbol == PlayerOperator.Method.INCREASE) {
                    if (this$0.type == LevelType.LEVEL) {
                        playerLevel.addLevel(Coerce.toInteger((Object)value2)).thenAccept(arg_0 -> ProfileLevelSet.run$lambda$5$lambda$4$lambda$0((Function1)new Function1<Void, Unit>(profile, option, playerLevel){
                            final /* synthetic */ PlayerProfile $profile;
                            final /* synthetic */ LevelOption $option;
                            final /* synthetic */ Level $playerLevel;
                            {
                                this.$profile = $profile;
                                this.$option = $option;
                                this.$playerLevel = $playerLevel;
                                super(1);
                            }

                            public final void invoke(Void it) {
                                LevelSystem.INSTANCE.setLevel(this.$profile, this.$option, this.$playerLevel.toPlayerLevel());
                            }
                        }, arg_0));
                    } else {
                        playerLevel.addExperience(Coerce.toInteger((Object)value2)).thenAccept(arg_0 -> ProfileLevelSet.run$lambda$5$lambda$4$lambda$1((Function1)new Function1<Void, Unit>(profile, option, playerLevel){
                            final /* synthetic */ PlayerProfile $profile;
                            final /* synthetic */ LevelOption $option;
                            final /* synthetic */ Level $playerLevel;
                            {
                                this.$profile = $profile;
                                this.$option = $option;
                                this.$playerLevel = $playerLevel;
                                super(1);
                            }

                            public final void invoke(Void it) {
                                LevelSystem.INSTANCE.setLevel(this.$profile, this.$option, this.$playerLevel.toPlayerLevel());
                            }
                        }, arg_0));
                    }
                } else if (this$0.type == LevelType.LEVEL) {
                    playerLevel.setLevel(Coerce.toInteger((Object)value2)).thenAccept(arg_0 -> ProfileLevelSet.run$lambda$5$lambda$4$lambda$2((Function1)new Function1<Void, Unit>(profile, option, playerLevel){
                        final /* synthetic */ PlayerProfile $profile;
                        final /* synthetic */ LevelOption $option;
                        final /* synthetic */ Level $playerLevel;
                        {
                            this.$profile = $profile;
                            this.$option = $option;
                            this.$playerLevel = $playerLevel;
                            super(1);
                        }

                        public final void invoke(Void it) {
                            LevelSystem.INSTANCE.setLevel(this.$profile, this.$option, this.$playerLevel.toPlayerLevel());
                        }
                    }, arg_0));
                } else {
                    playerLevel.setExperience(Coerce.toInteger((Object)value2)).thenAccept(arg_0 -> ProfileLevelSet.run$lambda$5$lambda$4$lambda$3((Function1)new Function1<Void, Unit>(profile, option, playerLevel){
                        final /* synthetic */ PlayerProfile $profile;
                        final /* synthetic */ LevelOption $option;
                        final /* synthetic */ Level $playerLevel;
                        {
                            this.$profile = $profile;
                            this.$option = $option;
                            this.$playerLevel = $playerLevel;
                            super(1);
                        }

                        public final void invoke(Void it) {
                            LevelSystem.INSTANCE.setLevel(this.$profile, this.$option, this.$playerLevel.toPlayerLevel());
                        }
                    }, arg_0));
                }
            }
        }

        private static final void run$lambda$5(QuestContext.Frame $frame, ProfileLevelSet this$0, Object key) {
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            $frame.newFrame(this$0.value).run().thenAccept(arg_0 -> ProfileLevelSet.run$lambda$5$lambda$4(key, $frame, this$0, arg_0));
        }
    }
}

