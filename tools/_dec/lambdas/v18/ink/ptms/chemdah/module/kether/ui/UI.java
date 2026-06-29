/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.library.kether.ArgType
 *  ink.ptms.chemdah.taboolib.library.kether.ArgTypes
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.library.kether.QuestReader
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.IntRange
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether.ui;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.meta.MetaType;
import ink.ptms.chemdah.module.kether.ui.UI;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.kether.ArgType;
import ink.ptms.chemdah.taboolib.library.kether.ArgTypes;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.library.kether.QuestReader;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.IntRange;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0005\u0018\u0000 \u00032\u00020\u0001:\u0003\u0003\u0004\u0005B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/ui/UI;", "", "()V", "Companion", "UIBar", "UIPercent", "Chemdah"})
public final class UI {
    @NotNull
    public static final Companion Companion = new Companion(null);
    private static final ArgType<List<String>> tokenType = ArgTypes.listOf(UI::tokenType$lambda$0);

    private static final String tokenType$lambda$0(QuestReader reader) {
        return reader.nextToken();
    }

    public static final /* synthetic */ ArgType access$getTokenType$cp() {
        return tokenType;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010!\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00060\nH\u0007Rj\u0010\u0003\u001a^\u0012(\u0012&\u0012\f\u0012\n \u0007*\u0004\u0018\u00010\u00060\u0006 \u0007*\u0012\u0012\f\u0012\n \u0007*\u0004\u0018\u00010\u00060\u0006\u0018\u00010\b0\u0005 \u0007*.\u0012(\u0012&\u0012\f\u0012\n \u0007*\u0004\u0018\u00010\u00060\u0006 \u0007*\u0012\u0012\f\u0012\n \u0007*\u0004\u0018\u00010\u00060\u0006\u0018\u00010\b0\u0005\u0018\u00010\u00040\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/module/kether/ui/UI$Companion;", "", "()V", "tokenType", "Link/ptms/chemdah/taboolib/library/kether/ArgType;", "", "", "kotlin1822.jvm.PlatformType", "", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"ui"}, namespace="chemdah-quest-ui")
        @NotNull
        public final ScriptActionParser<String> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B)\u0012\u0006\u0010\u0003\u001a\u00020\u0002\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005\u0012\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005\u00a2\u0006\u0002\u0010\u0007J\u001a\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00020\u000e2\n\u0010\u000f\u001a\u00060\u0010j\u0002`\u0011H\u0016R\u0017\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0017\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0003\u001a\u00020\u0002\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/module/kether/ui/UI$UIBar;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "plan", "include", "", "exclude", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "getExclude", "()Ljava/util/List;", "getInclude", "getPlan", "()Ljava/lang/String;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nUI.kt\nKotlin\n*S Kotlin\n*F\n+ 1 UI.kt\nink/ptms/chemdah/module/kether/ui/UI$UIBar\n+ 2 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 4 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,90:1\n515#2:91\n500#2,2:92\n502#2,4:100\n1747#3,3:94\n2624#3,3:97\n1774#3,4:104\n11#4:108\n*S KotlinDebug\n*F\n+ 1 UI.kt\nink/ptms/chemdah/module/kether/ui/UI$UIBar\n*L\n28#1:91\n28#1:92,2\n28#1:100,4\n28#1:94,3\n28#1:97,3\n29#1:104,4\n29#1:108\n*E\n"})
    public static final class UIBar
    extends ScriptAction<String> {
        @NotNull
        private final String plan;
        @NotNull
        private final List<String> include;
        @NotNull
        private final List<String> exclude;

        public UIBar(@NotNull String plan, @NotNull List<String> include, @NotNull List<String> exclude) {
            Intrinsics.checkNotNullParameter((Object)plan, (String)"plan");
            Intrinsics.checkNotNullParameter(include, (String)"include");
            Intrinsics.checkNotNullParameter(exclude, (String)"exclude");
            this.plan = plan;
            this.include = include;
            this.exclude = exclude;
        }

        @NotNull
        public final String getPlan() {
            return this.plan;
        }

        @NotNull
        public final List<String> getInclude() {
            return this.include;
        }

        @NotNull
        public final List<String> getExclude() {
            return this.exclude;
        }

        /*
         * Unable to fully structure code
         */
        @NotNull
        public CompletableFuture<String> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            profile = UtilsForKetherKt.getProfile(frame);
            $this$filter$iv = ChemdahAPI.INSTANCE.getQuestTemplate();
            $i$f$filter = false;
            var6_6 = $this$filter$iv;
            destination$iv$iv = new LinkedHashMap<K, V>();
            $i$f$filterTo = false;
            var9_12 = $this$filterTo$iv$iv.entrySet().iterator();
            while (var9_12.hasNext()) {
                block13: {
                    block12: {
                        var11_15 = element$iv$iv = var9_12.next();
                        $i$a$-filter-UI$UIBar$run$quests$1 = false;
                        v = (Template)var11_15.getValue();
                        $this$any$iv = MetaType.Companion.type(v);
                        $i$f$any = false;
                        if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                            v0 = false;
                        } else {
                            for (T element$iv : $this$any$iv) {
                                it = (String)element$iv;
                                $i$a$-any-UI$UIBar$run$quests$1$1 = false;
                                if (!this.include.contains(it)) continue;
                                v0 = true;
                                break block12;
                            }
                            v0 = false;
                        }
                    }
                    if (!v0) ** GOTO lbl-1000
                    $this$none$iv = MetaType.Companion.type(v);
                    $i$f$none = false;
                    if ($this$none$iv instanceof Collection && ((Collection)$this$none$iv).isEmpty()) {
                        v1 = true;
                    } else {
                        for (T element$iv : $this$none$iv) {
                            it = (String)element$iv;
                            $i$a$-none-UI$UIBar$run$quests$1$2 = false;
                            if (!this.exclude.contains(it)) continue;
                            v1 = false;
                            break block13;
                        }
                        v1 = true;
                    }
                }
                if (v1) {
                    v2 = true;
                } else lbl-1000:
                // 2 sources

                {
                    v2 = false;
                }
                if (!v2) continue;
                destination$iv$iv.put(element$iv$iv.getKey(), element$iv$iv.getValue());
            }
            quests = CollectionsKt.toList((Iterable)destination$iv$iv.values());
            $this$count$iv = quests;
            $i$f$count = false;
            if ($this$count$iv instanceof Collection && ((Collection)$this$count$iv).isEmpty()) {
                v3 = 0;
            } else {
                count$iv = 0;
                for (Map.Entry<K, V> element$iv : $this$count$iv) {
                    it = (Template)element$iv;
                    $i$a$-count-UI$UIBar$run$percent$1 = false;
                    v4 = profile;
                    v5 = v4 != null ? v4.isQuestCompleted(it) : false;
                    if (!v5 || ++count$iv >= 0) continue;
                    CollectionsKt.throwCountOverflow();
                }
                v3 = count$iv;
            }
            $this$cdouble$iv = quests.size();
            $i$f$getCdouble = false;
            percent = (double)v3 / Coerce.toDouble((Object)$this$cdouble$iv);
            ui = UtilsForKetherKt.UI(frame);
            path = "item.info.bar";
            v6 = ui.getConfig().getString(path + '.' + this.plan + ".empty", "&8|");
            Intrinsics.checkNotNull((Object)v6);
            empty = UtilKt.colored((String)v6);
            v7 = ui.getConfig().getString(path + '.' + this.plan + ".fill", "&a|");
            Intrinsics.checkNotNull((Object)v7);
            fill = UtilKt.colored((String)v7);
            size = ui.getConfig().getInt(path + '.' + this.plan + ".size", 35);
            v8 = CompletableFuture.completedFuture(CollectionsKt.joinToString$default((Iterable)((Iterable)new IntRange(1, size)), (CharSequence)"", null, null, (int)0, null, (Function1)((Function1)new Function1<Integer, CharSequence>(percent, empty, size, fill){
                final /* synthetic */ double $percent;
                final /* synthetic */ String $empty;
                final /* synthetic */ int $size;
                final /* synthetic */ String $fill;
                {
                    this.$percent = $percent;
                    this.$empty = $empty;
                    this.$size = $size;
                    this.$fill = $fill;
                    super(1);
                }

                @NotNull
                public final CharSequence invoke(int it) {
                    return Double.isNaN(this.$percent) || this.$percent == 0.0 ? (CharSequence)this.$empty : (this.$percent >= (double)it / (double)this.$size ? (CharSequence)this.$fill : (CharSequence)this.$empty);
                }
            }), (int)30, null));
            Intrinsics.checkNotNullExpressionValue(v8, (String)"percent = quests.count {\u2026else empty\n            })");
            return v8;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B!\u0012\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00020\u0004\u0012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00020\u0004\u00a2\u0006\u0002\u0010\u0006J\u001a\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00020\u000b2\n\u0010\f\u001a\u00060\rj\u0002`\u000eH\u0016R\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0017\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\b\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/kether/ui/UI$UIPercent;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "include", "", "exclude", "(Ljava/util/List;Ljava/util/List;)V", "getExclude", "()Ljava/util/List;", "getInclude", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nUI.kt\nKotlin\n*S Kotlin\n*F\n+ 1 UI.kt\nink/ptms/chemdah/module/kether/ui/UI$UIPercent\n+ 2 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,90:1\n515#2:91\n500#2,2:92\n502#2,4:100\n1747#3,3:94\n2624#3,3:97\n1774#3,4:104\n*S KotlinDebug\n*F\n+ 1 UI.kt\nink/ptms/chemdah/module/kether/ui/UI$UIPercent\n*L\n45#1:91\n45#1:92,2\n45#1:100,4\n45#1:94,3\n45#1:97,3\n46#1:104,4\n*E\n"})
    public static final class UIPercent
    extends ScriptAction<String> {
        @NotNull
        private final List<String> include;
        @NotNull
        private final List<String> exclude;

        public UIPercent(@NotNull List<String> include, @NotNull List<String> exclude) {
            Intrinsics.checkNotNullParameter(include, (String)"include");
            Intrinsics.checkNotNullParameter(exclude, (String)"exclude");
            this.include = include;
            this.exclude = exclude;
        }

        @NotNull
        public final List<String> getInclude() {
            return this.include;
        }

        @NotNull
        public final List<String> getExclude() {
            return this.exclude;
        }

        /*
         * Unable to fully structure code
         */
        @NotNull
        public CompletableFuture<String> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            profile = UtilsForKetherKt.getProfile(frame);
            $this$filter$iv = ChemdahAPI.INSTANCE.getQuestTemplate();
            $i$f$filter = false;
            var6_6 = $this$filter$iv;
            destination$iv$iv = new LinkedHashMap<K, V>();
            $i$f$filterTo = false;
            var9_10 = $this$filterTo$iv$iv.entrySet().iterator();
            while (var9_10.hasNext()) {
                block15: {
                    block14: {
                        var11_12 = element$iv$iv = var9_10.next();
                        $i$a$-filter-UI$UIPercent$run$quests$1 = false;
                        v = (Template)var11_12.getValue();
                        $this$any$iv = MetaType.Companion.type(v);
                        $i$f$any = false;
                        if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                            v0 = false;
                        } else {
                            for (T element$iv : $this$any$iv) {
                                it = (String)element$iv;
                                $i$a$-any-UI$UIPercent$run$quests$1$1 = false;
                                if (!this.include.contains(it)) continue;
                                v0 = true;
                                break block14;
                            }
                            v0 = false;
                        }
                    }
                    if (!v0) ** GOTO lbl-1000
                    $this$none$iv = MetaType.Companion.type(v);
                    $i$f$none = false;
                    if ($this$none$iv instanceof Collection && ((Collection)$this$none$iv).isEmpty()) {
                        v1 = true;
                    } else {
                        for (T element$iv : $this$none$iv) {
                            it = (String)element$iv;
                            $i$a$-none-UI$UIPercent$run$quests$1$2 = false;
                            if (!this.exclude.contains(it)) continue;
                            v1 = false;
                            break block15;
                        }
                        v1 = true;
                    }
                }
                if (v1) {
                    v2 = true;
                } else lbl-1000:
                // 2 sources

                {
                    v2 = false;
                }
                if (!v2) continue;
                destination$iv$iv.put(element$iv$iv.getKey(), element$iv$iv.getValue());
            }
            quests = CollectionsKt.toList((Iterable)destination$iv$iv.values());
            $this$count$iv = quests;
            $i$f$count = false;
            if ($this$count$iv instanceof Collection && ((Collection)$this$count$iv).isEmpty()) {
                v3 = 0;
            } else {
                count$iv = 0;
                for (Map.Entry<K, V> element$iv : $this$count$iv) {
                    it = (Template)element$iv;
                    $i$a$-count-UI$UIPercent$run$percent$1 = false;
                    v4 = profile;
                    v5 = v4 != null ? v4.isQuestCompleted(it) : false;
                    if (!v5 || ++count$iv >= 0) continue;
                    CollectionsKt.throwCountOverflow();
                }
                v3 = count$iv;
            }
            percent = (double)v3 / (double)quests.size();
            if (Double.isNaN(percent)) {
                v6 = CompletableFuture.completedFuture("0");
                v7 = v6;
                Intrinsics.checkNotNullExpressionValue(v6, (String)"{\n                Comple\u2026Future(\"0\")\n            }");
            } else {
                v8 = CompletableFuture.completedFuture(String.valueOf(Coerce.format((double)(percent * (double)100))));
                v7 = v8;
                Intrinsics.checkNotNullExpressionValue(v8, (String)"{\n                Comple\u2026toString())\n            }");
            }
            return v7;
        }
    }
}

