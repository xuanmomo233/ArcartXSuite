/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.function.ThrottleFunction$Simple
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.tracker;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.ChemdahTrackAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonDepend;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.addon.data.NavPoint;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackHandler;
import ink.ptms.chemdah.taboolib.common.function.ThrottleFunction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u0000 \u00072\u00020\u0001:\u0001\u0007B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/QuestTrackingSession;", "Link/ptms/chemdah/api/ChemdahTrackAPI$TrackingSession;", "()V", "tick", "", "player", "Lorg/bukkit/entity/Player;", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestTrackingSession.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestTrackingSession.kt\nink/ptms/chemdah/core/quest/addon/tracker/QuestTrackingSession\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,80:1\n2624#2,3:81\n215#3,2:84\n*S KotlinDebug\n*F\n+ 1 QuestTrackingSession.kt\nink/ptms/chemdah/core/quest/addon/tracker/QuestTrackingSession\n*L\n44#1:81,3\n59#1:84,2\n*E\n"})
public final class QuestTrackingSession
extends ChemdahTrackAPI.TrackingSession {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    public static final String SOURCE = "quest";

    public QuestTrackingSession() {
        super(SOURCE, new ArrayList(), null, null, null, -1.0, null);
    }

    @Override
    public boolean tick(@NotNull Player player2) {
        boolean bl;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        if (!ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player2)) {
            return true;
        }
        PlayerProfile chemdahProfile = ChemdahAPI.INSTANCE.getChemdahProfile(player2);
        Template template = AddonTrack.Companion.getTrackQuest(chemdahProfile);
        if (template == null) {
            return true;
        }
        Template trackQuest = template;
        ThrottleFunction.Simple.invoke$default(QuestTrackHandler.INSTANCE.getRefreshAcceptedQuests(), (Object)player2, (long)0L, (int)2, null);
        ThrottleFunction.Simple.invoke$default(QuestTrackHandler.INSTANCE.getUpdateScoreboardTracker(), (Object)player2, (long)0L, (int)2, null);
        List<Quest> list2 = QuestTrackHandler.INSTANCE.getAcceptedQuestsMap().get(player2.getName());
        if (list2 != null) {
            boolean bl2;
            block13: {
                Iterable $this$none$iv = list2;
                boolean $i$f$none = false;
                if ($this$none$iv instanceof Collection && ((Collection)$this$none$iv).isEmpty()) {
                    bl2 = true;
                } else {
                    for (Object element$iv : $this$none$iv) {
                        Quest it = (Quest)element$iv;
                        boolean bl3 = false;
                        if (!Intrinsics.areEqual((Object)it.getId(), (Object)trackQuest.getId())) continue;
                        bl2 = false;
                        break block13;
                    }
                    bl2 = true;
                }
            }
            bl = bl2;
        } else {
            bl = false;
        }
        if (bl) {
            AddonTrack addonTrack = AddonTrack.Companion.track(trackQuest);
            if (addonTrack == null) {
                return true;
            }
            AddonTrack track2 = addonTrack;
            Location location = track2.getCenter().getLocation(player2);
            if (location == null) {
                return true;
            }
            Location center2 = location;
            QuestTrackHandler.INSTANCE.getBeaconTracker().send(player2, center2, track2.getBeacon());
            QuestTrackHandler.INSTANCE.mark(player2, track2.getQuestContainer().getPath() + ".beacon", track2.getBeacon().getPeriod());
            QuestTrackHandler.INSTANCE.getNavigationTracker().send(player2, center2, track2.getNavigation());
            NavPoint naviPoint = track2.getNavigation().getNaviPoint();
            if (naviPoint != null) {
                QuestTrackHandler.INSTANCE.mark(player2, track2.getQuestContainer().getPath() + ".navigation", naviPoint.getPeriod());
            }
        } else {
            Map $this$forEach$iv = trackQuest.getTaskMap();
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Location center3;
                AddonTrack track3;
                Map.Entry element$iv;
                Map.Entry entry = element$iv = iterator.next();
                boolean bl4 = false;
                Task task = (Task)entry.getValue();
                if (AddonTrack.Companion.track(task) == null || task.isCompleted(chemdahProfile) || !AddonDepend.Companion.isQuestDependCompleted(task, player2) || track3.getCenter().getLocation(player2) == null) continue;
                QuestTrackHandler.INSTANCE.getBeaconTracker().send(player2, center3, track3.getBeacon());
                QuestTrackHandler.INSTANCE.mark(player2, track3.getQuestContainer().getPath() + ".beacon", track3.getBeacon().getPeriod());
                QuestTrackHandler.INSTANCE.getNavigationTracker().send(player2, center3, track3.getNavigation());
                NavPoint naviPoint = track3.getNavigation().getNaviPoint();
                if (naviPoint == null) continue;
                QuestTrackHandler.INSTANCE.mark(player2, track3.getQuestContainer().getPath() + ".navigation", naviPoint.getPeriod());
            }
        }
        return true;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/QuestTrackingSession$Companion;", "", "()V", "SOURCE", "", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

