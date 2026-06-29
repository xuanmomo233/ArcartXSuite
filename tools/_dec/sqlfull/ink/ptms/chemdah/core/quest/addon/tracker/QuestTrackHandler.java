/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.tracker;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.api.event.collect.ObjectiveEvents;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonDepend;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.addon.tracker.BeaconTracker;
import ink.ptms.chemdah.core.quest.addon.tracker.LandmarkTracker;
import ink.ptms.chemdah.core.quest.addon.tracker.NavigationTracker;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackHandler;
import ink.ptms.chemdah.core.quest.addon.tracker.ScoreboardTracker;
import ink.ptms.chemdah.core.quest.meta.MetaName;
import ink.ptms.chemdah.module.party.PartySystem;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.function.ThrottleFunction;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.Schedule;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common5.Baffle;
import ink.ptms.chemdah.taboolib.module.chat.ComponentText;
import ink.ptms.chemdah.taboolib.module.chat.Components;
import ink.ptms.chemdah.taboolib.module.configuration.ConfigNode;
import ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt;
import ink.ptms.chemdah.util.StringKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u00a0\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0010%\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001e\u00104\u001a\u0002052\u0006\u00106\u001a\u00020,2\u0006\u00107\u001a\u00020\u00052\u0006\u00108\u001a\u00020#J\u0010\u00109\u001a\u0002052\u0006\u0010:\u001a\u00020;H\u0003J\u0010\u0010<\u001a\u0002052\u0006\u0010:\u001a\u00020=H\u0003J\u0010\u0010>\u001a\u0002052\u0006\u0010:\u001a\u00020?H\u0003J\u0010\u0010@\u001a\u0002052\u0006\u0010:\u001a\u00020AH\u0003J\u0010\u0010B\u001a\u0002052\u0006\u0010:\u001a\u00020CH\u0003J\u0010\u0010D\u001a\u0002052\u0006\u0010:\u001a\u00020EH\u0003J\u0010\u0010F\u001a\u0002052\u0006\u0010:\u001a\u00020GH\u0003J\b\u0010H\u001a\u000205H\u0003J\u0010\u0010I\u001a\u0002052\u0006\u0010:\u001a\u00020JH\u0003J\b\u0010K\u001a\u000205H\u0007R#\u0010\u0003\u001a\u0014\u0012\u0004\u0012\u00020\u0005\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00070\u00060\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u001a\u0010\n\u001a\u00020\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u001e\u0010\u0010\u001a\u00020\u00118\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u001a\u0010\u0015\u001a\u00020\u0016X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\u0018\"\u0004\b\u0019\u0010\u001aR\u001e\u0010\u001b\u001a\u00020\u001c8\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001d\u0010\u001e\"\u0004\b\u001f\u0010 R&\u0010!\u001a\u001a\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020#0\"0\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010$\u001a\u00020%X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b&\u0010'\"\u0004\b(\u0010)R\u0014\u0010*\u001a\b\u0012\u0004\u0012\u00020,0+X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010-\u001a\u00020.X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b/\u00100\"\u0004\b1\u00102R\u0014\u00103\u001a\b\u0012\u0004\u0012\u00020,0+X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006L"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/QuestTrackHandler;", "", "()V", "acceptedQuestsMap", "Ljava/util/concurrent/ConcurrentHashMap;", "", "", "Link/ptms/chemdah/core/quest/Quest;", "getAcceptedQuestsMap", "()Ljava/util/concurrent/ConcurrentHashMap;", "beaconTracker", "Link/ptms/chemdah/core/quest/addon/tracker/BeaconTracker;", "getBeaconTracker", "()Link/ptms/chemdah/core/quest/addon/tracker/BeaconTracker;", "setBeaconTracker", "(Link/ptms/chemdah/core/quest/addon/tracker/BeaconTracker;)V", "isLandmarkUpdateOnMove", "", "()Z", "setLandmarkUpdateOnMove", "(Z)V", "landmarkTracker", "Link/ptms/chemdah/core/quest/addon/tracker/LandmarkTracker;", "getLandmarkTracker", "()Link/ptms/chemdah/core/quest/addon/tracker/LandmarkTracker;", "setLandmarkTracker", "(Link/ptms/chemdah/core/quest/addon/tracker/LandmarkTracker;)V", "landmarkUpdatePeriod", "", "getLandmarkUpdatePeriod", "()J", "setLandmarkUpdatePeriod", "(J)V", "markedBaffleMap", "", "Link/ptms/chemdah/taboolib/common5/Baffle;", "navigationTracker", "Link/ptms/chemdah/core/quest/addon/tracker/NavigationTracker;", "getNavigationTracker", "()Link/ptms/chemdah/core/quest/addon/tracker/NavigationTracker;", "setNavigationTracker", "(Link/ptms/chemdah/core/quest/addon/tracker/NavigationTracker;)V", "refreshAcceptedQuests", "Link/ptms/chemdah/taboolib/common/function/ThrottleFunction$Simple;", "Lorg/bukkit/entity/Player;", "scoreboardTracker", "Link/ptms/chemdah/core/quest/addon/tracker/ScoreboardTracker;", "getScoreboardTracker", "()Link/ptms/chemdah/core/quest/addon/tracker/ScoreboardTracker;", "setScoreboardTracker", "(Link/ptms/chemdah/core/quest/addon/tracker/ScoreboardTracker;)V", "updateScoreboardTracker", "mark", "", "player", "node", "baffle", "onComplete", "e", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Complete$Post;", "onContinue", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Continue$Post;", "onMove", "Lorg/bukkit/event/player/PlayerMoveEvent;", "onQuit", "Lorg/bukkit/event/player/PlayerQuitEvent;", "onRegistered", "Link/ptms/chemdah/api/event/collect/QuestEvents$Registered;", "onSelect", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Selected;", "onTalk", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Begin;", "onTick", "onTrack", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Track;", "setup", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestTrackHandler.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestTrackHandler.kt\nink/ptms/chemdah/core/quest/addon/tracker/QuestTrackHandler\n+ 2 MapsJVM.kt\nkotlin/collections/MapsKt__MapsJVMKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 4 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 5 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 6 Throttle.kt\ntaboolib/common/function/ThrottleKt\n*L\n1#1,286:1\n73#2,2:287\n1#3:289\n766#4:290\n857#4,2:291\n1855#4:293\n2624#4,3:294\n1856#4:299\n1855#4,2:300\n1855#4,2:302\n215#5,2:297\n215#5,2:304\n216#6:306\n216#6:307\n*S KotlinDebug\n*F\n+ 1 QuestTrackHandler.kt\nink/ptms/chemdah/core/quest/addon/tracker/QuestTrackHandler\n*L\n95#1:287,2\n95#1:289\n104#1:290\n104#1:291,2\n104#1:293\n115#1:294,3\n104#1:299\n187#1:300,2\n202#1:302,2\n124#1:297,2\n283#1:304,2\n68#1:306\n76#1:307\n*E\n"})
public final class QuestTrackHandler {
    @NotNull
    public static final QuestTrackHandler INSTANCE;
    @NotNull
    private static BeaconTracker beaconTracker;
    @NotNull
    private static NavigationTracker navigationTracker;
    @NotNull
    private static LandmarkTracker landmarkTracker;
    @NotNull
    private static ScoreboardTracker scoreboardTracker;
    @ConfigNode(value="default-quest.landmark-update-period")
    private static long landmarkUpdatePeriod;
    @ConfigNode(value="default-quest.landmark-update-on-move")
    private static boolean isLandmarkUpdateOnMove;
    @NotNull
    private static final ConcurrentHashMap<String, List<Quest>> acceptedQuestsMap;
    @NotNull
    private static final ConcurrentHashMap<String, Map<String, Baffle>> markedBaffleMap;
    @NotNull
    private static final ThrottleFunction.Simple<Player> refreshAcceptedQuests;
    @NotNull
    private static final ThrottleFunction.Simple<Player> updateScoreboardTracker;

    private QuestTrackHandler() {
    }

    @NotNull
    public final BeaconTracker getBeaconTracker() {
        return beaconTracker;
    }

    public final void setBeaconTracker(@NotNull BeaconTracker beaconTracker) {
        Intrinsics.checkNotNullParameter((Object)beaconTracker, (String)"<set-?>");
        QuestTrackHandler.beaconTracker = beaconTracker;
    }

    @NotNull
    public final NavigationTracker getNavigationTracker() {
        return navigationTracker;
    }

    public final void setNavigationTracker(@NotNull NavigationTracker navigationTracker) {
        Intrinsics.checkNotNullParameter((Object)navigationTracker, (String)"<set-?>");
        QuestTrackHandler.navigationTracker = navigationTracker;
    }

    @NotNull
    public final LandmarkTracker getLandmarkTracker() {
        return landmarkTracker;
    }

    public final void setLandmarkTracker(@NotNull LandmarkTracker landmarkTracker) {
        Intrinsics.checkNotNullParameter((Object)landmarkTracker, (String)"<set-?>");
        QuestTrackHandler.landmarkTracker = landmarkTracker;
    }

    @NotNull
    public final ScoreboardTracker getScoreboardTracker() {
        return scoreboardTracker;
    }

    public final void setScoreboardTracker(@NotNull ScoreboardTracker scoreboardTracker) {
        Intrinsics.checkNotNullParameter((Object)scoreboardTracker, (String)"<set-?>");
        QuestTrackHandler.scoreboardTracker = scoreboardTracker;
    }

    public final long getLandmarkUpdatePeriod() {
        return landmarkUpdatePeriod;
    }

    public final void setLandmarkUpdatePeriod(long l) {
        landmarkUpdatePeriod = l;
    }

    public final boolean isLandmarkUpdateOnMove() {
        return isLandmarkUpdateOnMove;
    }

    public final void setLandmarkUpdateOnMove(boolean bl) {
        isLandmarkUpdateOnMove = bl;
    }

    @NotNull
    public final ConcurrentHashMap<String, List<Quest>> getAcceptedQuestsMap() {
        return acceptedQuestsMap;
    }

    @Awake(value=LifeCycle.ENABLE)
    public final void setup() {
        landmarkTracker.setup();
    }

    /*
     * WARNING - void declaration
     */
    public final void mark(@NotNull Player player, @NotNull String node, @NotNull Baffle baffle) {
        void $this$getOrPut$iv;
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        Intrinsics.checkNotNullParameter((Object)baffle, (String)"baffle");
        ConcurrentMap concurrentMap = markedBaffleMap;
        String key$iv = player.getName();
        boolean $i$f$getOrPut = false;
        Object object = $this$getOrPut$iv.get(key$iv);
        if (object == null) {
            boolean bl = false;
            Map default$iv = new ConcurrentHashMap();
            boolean bl2 = false;
            object = $this$getOrPut$iv.putIfAbsent(key$iv, default$iv);
            if (object == null) {
                object = default$iv;
            }
        }
        Intrinsics.checkNotNullExpressionValue(object, (String)"markedBaffleMap.getOrPut\u2026) { ConcurrentHashMap() }");
        ((Map)object).put(node, baffle);
    }

    /*
     * WARNING - void declaration
     */
    @Schedule(async=true, period=1L)
    private final void onTick() {
        void $this$filterTo$iv$iv;
        Collection collection = Bukkit.getOnlinePlayers();
        Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getOnlinePlayers()");
        Iterable $this$filter$iv = collection;
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            Player it = (Player)element$iv$iv;
            boolean bl2 = false;
            Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
            if (!ChemdahAPI.INSTANCE.isChemdahProfileLoaded(it)) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        Iterable $this$forEach$iv = (List)destination$iv$iv;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            boolean bl;
            Template trackQuest;
            List<Quest> bl2;
            Player player = (Player)element$iv;
            boolean bl3 = false;
            Intrinsics.checkNotNullExpressionValue((Object)player, (String)"player");
            PlayerProfile chemdahProfile = ChemdahAPI.INSTANCE.getChemdahProfile(player);
            if (AddonTrack.Companion.getTrackQuest(chemdahProfile) == null) continue;
            ThrottleFunction.Simple.invoke$default(refreshAcceptedQuests, (Object)player, (long)0L, (int)2, null);
            ThrottleFunction.Simple.invoke$default(updateScoreboardTracker, (Object)player, (long)0L, (int)2, null);
            List<Quest> list2 = bl2 = acceptedQuestsMap.get(player.getName());
            if (list2 != null) {
                boolean bl4;
                block9: {
                    Intrinsics.checkNotNullExpressionValue(list2, (String)"acceptedQuestsMap[player.name]");
                    Iterable $this$none$iv = bl2;
                    boolean $i$f$none = false;
                    if ($this$none$iv instanceof Collection && ((Collection)$this$none$iv).isEmpty()) {
                        bl4 = true;
                    } else {
                        for (Object element$iv2 : $this$none$iv) {
                            Quest it = (Quest)element$iv2;
                            boolean bl5 = false;
                            if (!Intrinsics.areEqual((Object)it.getId(), (Object)trackQuest.getId())) continue;
                            bl4 = false;
                            break block9;
                        }
                        bl4 = true;
                    }
                }
                bl = bl4;
            } else {
                bl = false;
            }
            if (bl) {
                AddonTrack track2;
                if (AddonTrack.Companion.track(trackQuest) == null) continue;
                beaconTracker.sendBeaconTracker(player, track2);
                navigationTracker.sendNavigationTracker(player, track2);
                continue;
            }
            Map $this$forEach$iv2 = trackQuest.getTaskMap();
            boolean $i$f$forEach2 = false;
            Iterator iterator = $this$forEach$iv2.entrySet().iterator();
            while (iterator.hasNext()) {
                AddonTrack track3;
                Map.Entry element$iv3;
                Map.Entry entry = element$iv3 = iterator.next();
                boolean bl6 = false;
                Task task = (Task)entry.getValue();
                if (AddonTrack.Companion.track(task) == null || task.isCompleted(chemdahProfile) || !AddonDepend.Companion.isQuestDependCompleted(task, player)) continue;
                beaconTracker.sendBeaconTracker(player, track3);
                navigationTracker.sendNavigationTracker(player, track3);
            }
        }
    }

    @SubscribeEvent
    private final void onTalk(ConversationEvents.Begin e) {
        landmarkTracker.removeLandmarkTracker(e.getSession().getPlayer());
    }

    @SubscribeEvent
    private final void onMove(PlayerMoveEvent e) {
        if (isLandmarkUpdateOnMove && BukkitEventKt.isMovement((PlayerMoveEvent)e)) {
            Player player = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
            if (ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player)) {
                Player player2 = e.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
                if (AddonTrack.Companion.getTrackQuest(ChemdahAPI.INSTANCE.getChemdahProfile(player2)) != null) {
                    Player player3 = e.getPlayer();
                    Intrinsics.checkNotNullExpressionValue((Object)player3, (String)"e.player");
                    landmarkTracker.updateLandmarkTracker(player3);
                }
            }
        }
    }

    @SubscribeEvent
    private final void onRegistered(QuestEvents.Registered e) {
        if (Intrinsics.areEqual((Object)AddonTrack.Companion.getTrackQuest(e.getPlayerProfile()), (Object)e.getQuest().getTemplate())) {
            landmarkTracker.updateLandmarkTracker(e.getPlayerProfile().getPlayer());
            scoreboardTracker.updateScoreboardTracker(e.getPlayerProfile().getPlayer());
        }
    }

    @SubscribeEvent
    private final void onContinue(ObjectiveEvents.Continue.Post e) {
        Iterable $this$forEach$iv = PartySystem.INSTANCE.getMembers(e.getQuest(), true);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player it = (Player)element$iv;
            boolean bl = false;
            if (!Intrinsics.areEqual((Object)AddonTrack.Companion.getTrackQuest(ChemdahAPI.INSTANCE.getChemdahProfile(it)), (Object)e.getTask().getTemplate())) continue;
            scoreboardTracker.updateScoreboardTracker(e.getPlayerProfile().getPlayer());
        }
    }

    @SubscribeEvent
    private final void onComplete(ObjectiveEvents.Complete.Post e) {
        Iterable $this$forEach$iv = PartySystem.INSTANCE.getMembers(e.getQuest(), true);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player it = (Player)element$iv;
            boolean bl = false;
            if (!Intrinsics.areEqual((Object)AddonTrack.Companion.getTrackQuest(ChemdahAPI.INSTANCE.getChemdahProfile(it)), (Object)e.getTask().getTemplate())) continue;
            landmarkTracker.updateLandmarkTracker(e.getPlayerProfile().getPlayer());
            scoreboardTracker.updateScoreboardTracker(e.getPlayerProfile().getPlayer());
        }
    }

    @SubscribeEvent
    private final void onSelect(PlayerEvents.Selected e) {
        ExecutorKt.submit$default((boolean)false, (boolean)false, (long)40L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(e){
            final /* synthetic */ PlayerEvents.Selected $e;
            {
                this.$e = $e;
                super(1);
            }

            public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                Template trackQuest = AddonTrack.Companion.getTrackQuest(this.$e.getPlayerProfile());
                if (trackQuest != null) {
                    QuestTrackHandler.INSTANCE.getLandmarkTracker().removeLandmarkTracker(this.$e.getPlayer());
                    QuestTrackHandler.INSTANCE.getScoreboardTracker().removeScoreboardTracker(this.$e.getPlayer(), trackQuest);
                    QuestTrackHandler.INSTANCE.getLandmarkTracker().updateLandmarkTracker(this.$e.getPlayer());
                    QuestTrackHandler.INSTANCE.getScoreboardTracker().updateScoreboardTracker(this.$e.getPlayer());
                }
            }
        }), (int)11, null);
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    private final void onTrack(PlayerEvents.Track e) {
        Template trackingQuest = e.getTrackingQuest();
        landmarkTracker.removeLandmarkTracker(e.getPlayer());
        scoreboardTracker.removeScoreboardTracker(e.getPlayer(), trackingQuest);
        if (e.getCancel()) {
            if (trackingQuest != null) {
                Object[] objectArray = new Object[]{trackingQuest.getId()};
                BukkitLangKt.sendLang((CommandSender)((CommandSender)e.getPlayer()), (String)"track-cancel", (Object[])objectArray);
            }
        } else if (trackingQuest != null) {
            ExecutorKt.submit$default((boolean)false, (boolean)false, (long)1L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(e, trackingQuest){
                final /* synthetic */ PlayerEvents.Track $e;
                final /* synthetic */ Template $trackingQuest;
                {
                    this.$e = $e;
                    this.$trackingQuest = $trackingQuest;
                    super(1);
                }

                /*
                 * WARNING - void declaration
                 */
                public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                    void $this$forEach$iv;
                    Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                    QuestTrackHandler.INSTANCE.getLandmarkTracker().updateLandmarkTracker(this.$e.getPlayer());
                    QuestTrackHandler.INSTANCE.getScoreboardTracker().updateScoreboardTracker(this.$e.getPlayer());
                    AddonTrack addonTrack = AddonTrack.Companion.track(this.$trackingQuest);
                    if (addonTrack == null || (addonTrack = addonTrack.getMessage()) == null) {
                        addonTrack = AddonTrack.Companion.getDefaultMessage();
                    }
                    AddonTrack trackMessage = addonTrack;
                    String string = this.$e.getPlayer().getLocale();
                    Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.player.locale");
                    Iterable iterable = trackMessage.get(string);
                    Template template = this.$trackingQuest;
                    PlayerEvents.Track track2 = this.$e;
                    boolean $i$f$forEach = false;
                    for (T element$iv : $this$forEach$iv) {
                        Object object;
                        String message2;
                        block6: {
                            block5: {
                                message2 = (String)element$iv;
                                boolean bl = false;
                                object = AddonTrack.Companion.track(template);
                                if (object == null || (object = ((AddonTrack)object).getName()) == null) break block5;
                                String string2 = track2.getPlayer().getLocale();
                                Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"e.player.locale");
                                if ((object = object.get(string2)) != null) break block6;
                            }
                            object = MetaName.Companion.displayName$default(MetaName.Companion, template, false, 1, null);
                        }
                        Object displayName = object;
                        Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)displayName)};
                        ComponentText componentText = Components.INSTANCE.empty().append(StringKt.replace(message2, pairArray));
                        pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)displayName)};
                        componentText.hoverText(StringKt.replace(message2, pairArray)).clickRunCommand("/ChemdahTrackCancel").sendTo(AdapterKt.adaptCommandSender((Object)track2.getPlayer()));
                    }
                }
            }), (int)11, null);
        }
    }

    @SubscribeEvent
    private final void onQuit(PlayerQuitEvent e) {
        block1: {
            acceptedQuestsMap.remove(e.getPlayer().getName());
            Player player = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
            refreshAcceptedQuests.removeKey((Object)player);
            Player player2 = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
            updateScoreboardTracker.removeKey((Object)player2);
            Player player3 = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player3, (String)"e.player");
            landmarkTracker.removeLandmarkTracker(player3);
            Map<String, Baffle> map = markedBaffleMap.remove(e.getPlayer().getName());
            if (map == null) break block1;
            Map<String, Baffle> $this$forEach$iv = map;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<String, Baffle>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Baffle> element$iv;
                Map.Entry<String, Baffle> it = element$iv = iterator.next();
                boolean bl = false;
                it.getValue().reset(e.getPlayer().getName());
            }
        }
    }

    static {
        long delay$iv;
        INSTANCE = new QuestTrackHandler();
        beaconTracker = new BeaconTracker();
        navigationTracker = new NavigationTracker();
        landmarkTracker = new LandmarkTracker();
        scoreboardTracker = new ScoreboardTracker();
        landmarkUpdatePeriod = 5L;
        isLandmarkUpdateOnMove = true;
        acceptedQuestsMap = new ConcurrentHashMap();
        markedBaffleMap = new ConcurrentHashMap();
        long l = 1000L;
        Function1 action$iv = refreshAcceptedQuests.1.INSTANCE;
        boolean $i$f$throttle = false;
        refreshAcceptedQuests = new ThrottleFunction.Simple(Player.class, delay$iv, action$iv);
        delay$iv = 5000L;
        action$iv = updateScoreboardTracker.1.INSTANCE;
        $i$f$throttle = false;
        updateScoreboardTracker = new ThrottleFunction.Simple(Player.class, delay$iv, action$iv);
    }
}

