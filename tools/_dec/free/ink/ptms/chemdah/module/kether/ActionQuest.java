/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.library.kether.QuestReader
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.functions.Function3
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.module.kether.ActionQuest;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.library.kether.QuestReader;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.functions.Function3;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\u0018\u0000 \u00032\u00020\u0001:\u0001\u0003B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0004"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest;", "", "()V", "Companion", "Chemdah"})
public final class ActionQuest {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000R\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\f\u0012\b\b\u0001\u0012\u0004\u0018\u00010\u00010\u0004H\u0007J\u0010\u0010\u0005\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0004H\u0007J3\u0010\u0006\u001a\u0004\u0018\u0001H\u0007\"\u0004\b\u0000\u0010\u0007*\u00020\b2\u0017\u0010\t\u001a\u0013\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u0002H\u00070\n\u00a2\u0006\u0002\b\u000bH\u0082\b\u00a2\u0006\u0002\u0010\fJ4\u0010\r\u001a\u0004\u0018\u0001H\u0007\"\u0004\b\u0000\u0010\u0007*\u00060\u000ej\u0002`\u000f2\u0014\u0010\t\u001a\u0010\u0012\u0004\u0012\u00020\u0010\u0012\u0006\u0012\u0004\u0018\u0001H\u00070\nH\u0082\b\u00a2\u0006\u0002\u0010\u0011J:\u0010\u0012\u001a\u0004\u0018\u0001H\u0007\"\u0004\b\u0000\u0010\u0007*\u00060\u000ej\u0002`\u000f2\u001a\u0010\t\u001a\u0016\u0012\u0004\u0012\u00020\u0010\u0012\u0004\u0012\u00020\u0014\u0012\u0006\u0012\u0004\u0018\u0001H\u00070\u0013H\u0082\b\u00a2\u0006\u0002\u0010\u0015JH\u0010\u0016\u001a\u0004\u0018\u0001H\u0007\"\u0004\b\u0000\u0010\u0007*\u00060\u000ej\u0002`\u000f2\u0006\u0010\u0017\u001a\u00020\u00182 \u0010\t\u001a\u001c\u0012\u0004\u0012\u00020\u0010\u0012\u0004\u0012\u00020\u0014\u0012\u0004\u0012\u00020\u001a\u0012\u0006\u0012\u0004\u0018\u0001H\u00070\u0019H\u0082\b\u00a2\u0006\u0002\u0010\u001b\u00a8\u0006\u001c"}, d2={"Link/ptms/chemdah/module/kether/ActionQuest$Companion;", "", "()V", "parserQuest", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "parserQuests", "tryOptional", "T", "Link/ptms/chemdah/taboolib/library/kether/QuestReader;", "block", "Lkotlin1822/Function1;", "Lkotlin1822/ExtensionFunctionType;", "(Link/ptms/chemdah/taboolib/library/kether/QuestReader;Lkotlin1822/jvm/functions/Function1;)Ljava/lang/Object;", "withProfile", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;Lkotlin1822/jvm/functions/Function1;)Ljava/lang/Object;", "withQuest", "Lkotlin1822/Function2;", "Link/ptms/chemdah/core/quest/Quest;", "(Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;Lkotlin1822/jvm/functions/Function2;)Ljava/lang/Object;", "withTask", "taskName", "", "Lkotlin1822/Function3;", "Link/ptms/chemdah/core/quest/Task;", "(Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;Ljava/lang/String;Lkotlin1822/jvm/functions/Function3;)Ljava/lang/Object;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nActionQuest.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ActionQuest.kt\nink/ptms/chemdah/module/kether/ActionQuest$Companion\n*L\n1#1,329:1\n39#1,6:330\n48#1:336\n39#1,12:337\n*S KotlinDebug\n*F\n+ 1 ActionQuest.kt\nink/ptms/chemdah/module/kether/ActionQuest$Companion\n*L\n48#1:330,6\n55#1:336\n55#1:337,12\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        private final <T> T withProfile(QuestContext.Frame $this$withProfile, Function1<? super PlayerProfile, ? extends T> block) {
            boolean $i$f$withProfile = false;
            PlayerProfile profile = UtilsForKetherKt.getProfile($this$withProfile);
            if (profile == null) {
                Object[] objectArray = new Object[]{"Player data has not been loaded yet. (" + UtilsForKetherKt.getBukkitPlayer($this$withProfile).getName() + ')'};
                IOKt.warning((Object[])objectArray);
                return null;
            }
            return (T)block.invoke((Object)profile);
        }

        private final <T> T withQuest(QuestContext.Frame $this$withQuest, Function2<? super PlayerProfile, ? super Quest, ? extends T> block) {
            Object object;
            boolean $i$f$withQuest = false;
            Companion companion = this;
            QuestContext.Frame $this$withProfile$iv = $this$withQuest;
            boolean $i$f$withProfile = false;
            PlayerProfile profile$iv = UtilsForKetherKt.getProfile($this$withProfile$iv);
            if (profile$iv == null) {
                Object[] objectArray = new Object[]{"Player data has not been loaded yet. (" + UtilsForKetherKt.getBukkitPlayer($this$withProfile$iv).getName() + ')'};
                IOKt.warning((Object[])objectArray);
                object = null;
            } else {
                PlayerProfile profile = profile$iv;
                boolean bl = false;
                Quest quest2 = PlayerProfile.getQuestById$default(profile, UtilsForKetherKt.getQuestSelected($this$withQuest), false, 2, null);
                if (quest2 == null) {
                    object = null;
                } else {
                    Quest quest3 = quest2;
                    object = block.invoke((Object)profile, (Object)quest3);
                }
            }
            return (T)object;
        }

        /*
         * WARNING - void declaration
         */
        private final <T> T withTask(QuestContext.Frame $this$withTask, String taskName, Function3<? super PlayerProfile, ? super Quest, ? super Task, ? extends T> block) {
            Object object;
            void this_$iv;
            boolean $i$f$withTask = false;
            Companion companion = this;
            QuestContext.Frame $this$withQuest$iv = $this$withTask;
            boolean $i$f$withQuest = false;
            void var8_8 = this_$iv;
            QuestContext.Frame $this$withProfile$iv$iv = $this$withQuest$iv;
            boolean $i$f$withProfile = false;
            PlayerProfile profile$iv$iv = UtilsForKetherKt.getProfile($this$withProfile$iv$iv);
            if (profile$iv$iv == null) {
                Object[] objectArray = new Object[]{"Player data has not been loaded yet. (" + UtilsForKetherKt.getBukkitPlayer($this$withProfile$iv$iv).getName() + ')'};
                IOKt.warning((Object[])objectArray);
                object = null;
            } else {
                PlayerProfile profile$iv = profile$iv$iv;
                boolean bl = false;
                Quest quest2 = PlayerProfile.getQuestById$default(profile$iv, UtilsForKetherKt.getQuestSelected($this$withQuest$iv), false, 2, null);
                if (quest2 == null) {
                    object = null;
                } else {
                    void quest3;
                    Quest quest$iv;
                    Quest quest4 = quest$iv = quest2;
                    PlayerProfile profile = profile$iv;
                    boolean bl2 = false;
                    Task task = quest3.getTask(taskName);
                    if (task == null) {
                        object = null;
                    } else {
                        Task task2 = task;
                        object = block.invoke((Object)profile, (Object)quest3, (Object)task2);
                    }
                }
            }
            return (T)object;
        }

        private final <T> T tryOptional(QuestReader $this$tryOptional, Function1<? super QuestReader, ? extends T> block) {
            Object object;
            boolean $i$f$tryOptional = false;
            try {
                $this$tryOptional.mark();
                object = block.invoke((Object)$this$tryOptional);
            }
            catch (Throwable _) {
                $this$tryOptional.reset();
                object = null;
            }
            return (T)object;
        }

        @KetherParser(value={"quests"}, namespace="chemdah", shared=true)
        @NotNull
        public final ScriptActionParser<Object> parserQuests() {
            return KetherHelperKt.scriptParser((Function1)parserQuests.1.INSTANCE);
        }

        @KetherParser(value={"quest"}, shared=true)
        @NotNull
        public final ScriptActionParser<? extends Object> parserQuest() {
            return KetherHelperKt.scriptParser((Function1)parserQuest.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

