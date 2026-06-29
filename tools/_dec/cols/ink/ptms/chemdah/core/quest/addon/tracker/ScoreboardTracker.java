/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.tracker;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonDepend;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.addon.data.TrackScoreboard;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.nms.NMSScoreboardKt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u001a\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\u0007\u001a\u0004\u0018\u00010\bH\u0016J\u0010\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u001e\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\f0\u000bH\u0016\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/ScoreboardTracker;", "", "()V", "removeScoreboardTracker", "", "player", "Lorg/bukkit/entity/Player;", "quest", "Link/ptms/chemdah/core/quest/Template;", "updateScoreboardTracker", "content", "", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nScoreboardTracker.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ScoreboardTracker.kt\nink/ptms/chemdah/core/quest/addon/tracker/ScoreboardTracker\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 4 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 5 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,104:1\n1360#2:105\n1446#2,5:106\n1360#2:111\n1446#2,2:112\n1448#2,3:120\n1559#2:124\n1590#2,4:125\n76#3:114\n96#3,5:115\n187#3,3:131\n1#4:123\n37#5,2:129\n*S KotlinDebug\n*F\n+ 1 ScoreboardTracker.kt\nink/ptms/chemdah/core/quest/addon/tracker/ScoreboardTracker\n*L\n35#1:105\n35#1:106,5\n50#1:111\n50#1:112,2\n50#1:120,3\n88#1:124\n88#1:125,4\n54#1:114\n54#1:115,5\n100#1:131,3\n88#1:129,2\n*E\n"})
public class ScoreboardTracker {
    /*
     * WARNING - void declaration
     */
    public void updateScoreboardTracker(@NotNull Player player) {
        QuestEvents.ScoreboardTrack scoreboardTrack;
        List list2;
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        if (ChemdahAPI.INSTANCE.getNonChemdahProfileLoaded(player)) {
            return;
        }
        Template template = AddonTrack.Companion.getTrackQuest(ChemdahAPI.INSTANCE.getChemdahProfile(player));
        if (template == null) {
            return;
        }
        Template quest2 = template;
        AddonTrack questTrack = AddonTrack.Companion.track(quest2);
        if (PlayerProfile.getQuestById$default(ChemdahAPI.INSTANCE.getChemdahProfile(player), quest2.getId(), false, 2, null) == null) {
            Object object = questTrack;
            boolean bl = object != null && (object = ((AddonTrack)object).getScoreboard()) != null ? ((TrackScoreboard)object).getEnable() : false;
            if (bl) {
                void $this$flatMapTo$iv$iv;
                Iterable $this$flatMap$iv = questTrack.getScoreboard().getContent();
                boolean $i$f$flatMap = false;
                Iterable iterable = $this$flatMap$iv;
                Collection destination$iv$iv = new ArrayList();
                boolean $i$f$flatMapTo = false;
                for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
                    TrackScoreboard.Line line = (TrackScoreboard.Line)element$iv$iv;
                    boolean bl2 = false;
                    Iterable list$iv$iv = line.isQuestLine() ? questTrack.formatDescription(player, quest2, line) : line.getContent();
                    CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
                }
                list2 = (List)destination$iv$iv;
            } else {
                list2 = CollectionsKt.emptyList();
            }
        } else {
            Object object = questTrack;
            if (object != null && (object = ((AddonTrack)object).getScoreboard()) != null && (object = ((TrackScoreboard)object).getContent()) != null) {
                void $this$flatMapTo$iv$iv;
                Iterable $this$flatMap$iv = (Iterable)object;
                boolean $i$f$flatMap = false;
                Iterable iterable = $this$flatMap$iv;
                Collection destination$iv$iv = new ArrayList();
                boolean $i$f$flatMapTo = false;
                for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
                    List list3;
                    TrackScoreboard.Line line = (TrackScoreboard.Line)element$iv$iv;
                    boolean bl = false;
                    if (line.isQuestLine()) {
                        void $this$flatMapTo$iv$iv2;
                        Map $this$flatMap$iv2 = quest2.getTaskMap();
                        boolean $i$f$flatMap2 = false;
                        Map map = $this$flatMap$iv2;
                        Collection destination$iv$iv2 = new ArrayList();
                        boolean $i$f$flatMapTo2 = false;
                        Iterator iterator = $this$flatMapTo$iv$iv2.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry element$iv$iv2;
                            Map.Entry entry = element$iv$iv2 = iterator.next();
                            boolean bl3 = false;
                            Task task = (Task)entry.getValue();
                            AddonTrack taskTrack = AddonTrack.Companion.track(task);
                            Object object2 = taskTrack;
                            Iterable list$iv$iv = (object2 != null && (object2 = ((AddonTrack)object2).getScoreboard()) != null ? ((TrackScoreboard)object2).getEnable() : false) && !task.isCompleted(ChemdahAPI.INSTANCE.getChemdahProfile(player)) && AddonDepend.Companion.isQuestDependCompleted(task, player) ? taskTrack.formatDescription(player, task, line) : CollectionsKt.emptyList();
                            CollectionsKt.addAll((Collection)destination$iv$iv2, (Iterable)list$iv$iv);
                        }
                        list3 = (List)destination$iv$iv2;
                    } else {
                        list3 = line.getContent();
                    }
                    Iterable list$iv$iv = list3;
                    CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
                }
                list2 = (List)destination$iv$iv;
            } else {
                list2 = CollectionsKt.emptyList();
            }
        }
        List content = list2;
        QuestEvents.ScoreboardTrack it = scoreboardTrack = new QuestEvents.ScoreboardTrack(CollectionsKt.toMutableList((Collection)content), ChemdahAPI.INSTANCE.getChemdahProfile(player));
        boolean bl = false;
        it.call();
        QuestEvents.ScoreboardTrack event = scoreboardTrack;
        if (event.getContent().size() > 2) {
            this.updateScoreboardTracker(player, event.getContent());
        } else {
            this.removeScoreboardTracker(player, quest2);
        }
    }

    /*
     * WARNING - void declaration
     */
    public void updateScoreboardTracker(@NotNull Player player, @NotNull List<String> content) {
        void $this$toTypedArray$iv;
        void $this$mapIndexedTo$iv$iv;
        Collection $this$mapIndexed$iv;
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter(content, (String)"content");
        Iterable iterable = UtilKt.colored(content);
        Player player2 = player;
        boolean $i$f$mapIndexed = false;
        void var6_6 = $this$mapIndexed$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$mapIndexed$iv, (int)10));
        boolean $i$f$mapIndexedTo = false;
        int index$iv$iv = 0;
        for (Object item$iv$iv : $this$mapIndexedTo$iv$iv) {
            void s;
            void index;
            int n;
            if ((n = index$iv$iv++) < 0) {
                CollectionsKt.throwIndexOverflow();
            }
            String string = (String)item$iv$iv;
            int n2 = n;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add("" + '\u00a7' + AddonTrack.Companion.getUniqueChars$Chemdah().get((int)index).charValue() + (String)s + ' ');
        }
        $this$mapIndexed$iv = (List)destination$iv$iv;
        boolean $i$f$toTypedArray = false;
        void thisCollection$iv = $this$toTypedArray$iv;
        String[] stringArray = thisCollection$iv.toArray(new String[0]);
        NMSScoreboardKt.sendScoreboard((Player)player2, (String[])Arrays.copyOf(stringArray, stringArray.length));
    }

    public void removeScoreboardTracker(@NotNull Player player, @Nullable Template quest2) {
        block10: {
            block9: {
                boolean bl;
                block8: {
                    Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                    if (ChemdahAPI.INSTANCE.getNonChemdahProfileLoaded(player) || quest2 == null) {
                        return;
                    }
                    Object object = AddonTrack.Companion.track(quest2);
                    if (object != null && (object = ((AddonTrack)object).getScoreboard()) != null ? ((TrackScoreboard)object).getEnable() : false) break block9;
                    Map $this$any$iv = quest2.getTaskMap();
                    boolean $i$f$any = false;
                    if ($this$any$iv.isEmpty()) {
                        bl = false;
                    } else {
                        Iterator iterator = $this$any$iv.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry element$iv;
                            Map.Entry it = element$iv = iterator.next();
                            boolean bl2 = false;
                            Object object2 = AddonTrack.Companion.track((QuestContainer)it.getValue());
                            boolean bl3 = object2 != null && (object2 = ((AddonTrack)object2).getScoreboard()) != null ? ((TrackScoreboard)object2).getEnable() : false;
                            if (!bl3) continue;
                            bl = true;
                            break block8;
                        }
                        bl = false;
                    }
                }
                if (!bl) break block10;
            }
            NMSScoreboardKt.sendScoreboard((Player)player, (String[])new String[0]);
        }
    }
}

