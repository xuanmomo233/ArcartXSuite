/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.api;

import ink.ptms.chemdah.api.ChemdahTrackAPI;
import ink.ptms.chemdah.core.quest.addon.data.NavPoint;
import ink.ptms.chemdah.core.quest.addon.data.TrackBeacon;
import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import ink.ptms.chemdah.core.quest.addon.data.TrackLandmark;
import ink.ptms.chemdah.core.quest.addon.data.TrackNavigation;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackHandler;
import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologram;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000|\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010 \n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u00029:B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u0007J\u001e\u0010\u000f\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013JH\u0010\u000f\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0010\u001a\u00020\u00112\b\b\u0002\u0010\u0014\u001a\u00020\u00152\b\b\u0002\u0010\u0016\u001a\u00020\u00172\b\b\u0002\u0010\u0018\u001a\u00020\u00192\b\b\u0002\u0010\u001a\u001a\u00020\u00172\b\b\u0002\u0010\u001b\u001a\u00020\u001cJ\u001e\u0010\u001d\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u001e\u001a\u00020\u001fJ*\u0010 \u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0010\u001a\u00020\u00112\b\b\u0002\u0010\u0014\u001a\u00020\u00152\b\b\u0002\u0010\u001a\u001a\u00020!J*\u0010\"\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0010\u001a\u00020\u00112\b\b\u0002\u0010\u0014\u001a\u00020\u00152\b\b\u0002\u0010\u001a\u001a\u00020!J\u0018\u0010#\u001a\u0004\u0018\u00010\u00072\u0006\u0010\f\u001a\u00020\r2\u0006\u0010$\u001a\u00020\u0006J\u001a\u0010%\u001a\u00020\u001c2\u0006\u0010\f\u001a\u00020\r2\n\b\u0002\u0010$\u001a\u0004\u0018\u00010\u0006J\u0010\u0010&\u001a\u00020\u000b2\u0006\u0010'\u001a\u00020(H\u0007J\u000e\u0010)\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rJ\u0016\u0010)\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010*\u001a\u00020\u0006J\b\u0010+\u001a\u00020\u000bH\u0007J\u0016\u0010,\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u0007J\u001a\u0010-\u001a\u00020\u001c2\u0006\u0010\f\u001a\u00020\r2\n\b\u0002\u0010$\u001a\u0004\u0018\u00010\u0006J\u001e\u0010.\u001a\u00020/2\u0006\u00100\u001a\u00020\u00062\u0006\u00101\u001a\u00020\u00112\u0006\u00102\u001a\u00020\u0006J*\u00103\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u00072\u0012\u00104\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0004J,\u00105\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u00106\u001a\u00020\u00112\f\u00107\u001a\b\u0012\u0004\u0012\u00020\u0006082\u0006\u0010*\u001a\u00020\u0006R)\u0010\u0003\u001a\u001a\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u00040\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006;"}, d2={"Link/ptms/chemdah/api/ChemdahTrackAPI;", "", "()V", "sessions", "Ljava/util/concurrent/ConcurrentHashMap;", "Ljava/util/UUID;", "", "Link/ptms/chemdah/api/ChemdahTrackAPI$TrackingSession;", "getSessions", "()Ljava/util/concurrent/ConcurrentHashMap;", "cleanupBaffle", "", "player", "Lorg/bukkit/entity/Player;", "session", "displayBeacon", "target", "Lorg/bukkit/Location;", "beacon", "Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;", "particle", "Lorg/bukkit/Particle;", "size", "", "count", "", "distance", "fixed", "", "displayNavigation", "navigation", "Link/ptms/chemdah/core/quest/addon/data/TrackNavigation;", "displayNavigationArrows", "", "displayNavigationPoints", "getSession", "source", "isTracking", "onQuit", "e", "Lorg/bukkit/event/player/PlayerQuitEvent;", "removeLandmark", "landmarkId", "setupTick", "startTracking", "stopTracking", "targetAt", "Link/ptms/chemdah/api/ChemdahTrackAPI$TrackingTarget;", "id", "location", "name", "tickSession", "playerSessions", "updateLandmark", "pos", "content", "", "TrackingSession", "TrackingTarget", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nChemdahTrackAPI.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ChemdahTrackAPI.kt\nink/ptms/chemdah/api/ChemdahTrackAPI\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 MapsJVM.kt\nkotlin/collections/MapsKt__MapsJVMKt\n+ 4 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,399:1\n1855#2,2:400\n1855#2:405\n1855#2,2:406\n1856#2:408\n1855#2,2:409\n1855#2:411\n1855#2,2:412\n1856#2:414\n1855#2:415\n1855#2,2:416\n1856#2:418\n73#3,2:402\n1#4:404\n*S KotlinDebug\n*F\n+ 1 ChemdahTrackAPI.kt\nink/ptms/chemdah/api/ChemdahTrackAPI\n*L\n122#1:400,2\n140#1:405\n141#1:406,2\n140#1:408\n150#1:409,2\n210#1:411\n218#1:412,2\n210#1:414\n254#1:415\n255#1:416,2\n254#1:418\n127#1:402,2\n127#1:404\n*E\n"})
public final class ChemdahTrackAPI {
    @NotNull
    public static final ChemdahTrackAPI INSTANCE = new ChemdahTrackAPI();
    @NotNull
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, TrackingSession>> sessions = new ConcurrentHashMap();

    private ChemdahTrackAPI() {
    }

    @NotNull
    public final ConcurrentHashMap<UUID, ConcurrentHashMap<String, TrackingSession>> getSessions() {
        return sessions;
    }

    /*
     * WARNING - void declaration
     */
    public final void startTracking(@NotNull Player player2, @NotNull TrackingSession session) {
        void $this$getOrPut$iv;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        ConcurrentHashMap<String, TrackingSession> concurrentHashMap = sessions.get(player2.getUniqueId());
        if (concurrentHashMap != null && (concurrentHashMap = concurrentHashMap.remove(session.getSource())) != null) {
            ConcurrentHashMap<String, TrackingSession> old = concurrentHashMap;
            boolean bl = false;
            Iterable $this$forEach$iv = ((TrackingSession)((Object)old)).getTargets();
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                TrackingTarget target = (TrackingTarget)element$iv;
                boolean bl2 = false;
                Map<String, ChemdahHologram> map = QuestTrackHandler.INSTANCE.getLandmarkTracker().getLandmarkHologramMap().get(player2.getName());
                if (map == null || (map = map.remove(target.getId())) == null) continue;
                map.remove();
            }
            INSTANCE.cleanupBaffle(player2, (TrackingSession)((Object)old));
        }
        ConcurrentMap concurrentMap = sessions;
        UUID key$iv = player2.getUniqueId();
        boolean $i$f$getOrPut = false;
        Object object = $this$getOrPut$iv.get(key$iv);
        if (object == null) {
            boolean bl = false;
            ConcurrentHashMap default$iv = new ConcurrentHashMap();
            boolean bl3 = false;
            object = $this$getOrPut$iv.putIfAbsent(key$iv, default$iv);
            if (object == null) {
                object = default$iv;
            }
        }
        Intrinsics.checkNotNullExpressionValue(object, (String)"sessions.getOrPut(player\u2026) { ConcurrentHashMap() }");
        Map map = (Map)object;
        map.put(session.getSource(), session);
    }

    public final boolean stopTracking(@NotNull Player player2, @Nullable String source) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        if (source == null) {
            ConcurrentHashMap<String, TrackingSession> concurrentHashMap = sessions.remove(player2.getUniqueId());
            if (concurrentHashMap == null) {
                return false;
            }
            ConcurrentHashMap<String, TrackingSession> all = concurrentHashMap;
            Collection<TrackingSession> collection = all.values();
            Intrinsics.checkNotNullExpressionValue(collection, (String)"all.values");
            Iterable $this$forEach$iv = collection;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                TrackingSession session = (TrackingSession)element$iv;
                boolean bl = false;
                Iterable $this$forEach$iv2 = session.getTargets();
                boolean $i$f$forEach2 = false;
                for (Object element$iv2 : $this$forEach$iv2) {
                    TrackingTarget target = (TrackingTarget)element$iv2;
                    boolean bl2 = false;
                    Map<String, ChemdahHologram> map = QuestTrackHandler.INSTANCE.getLandmarkTracker().getLandmarkHologramMap().get(player2.getName());
                    if (map == null || (map = map.remove(target.getId())) == null) continue;
                    map.remove();
                }
                Intrinsics.checkNotNullExpressionValue((Object)session, (String)"session");
                INSTANCE.cleanupBaffle(player2, session);
            }
            return !((Map)all).isEmpty();
        }
        ConcurrentHashMap<String, TrackingSession> concurrentHashMap = sessions.get(player2.getUniqueId());
        if (concurrentHashMap == null) {
            return false;
        }
        ConcurrentHashMap<String, TrackingSession> playerSessions = concurrentHashMap;
        TrackingSession trackingSession = playerSessions.remove(source);
        if (trackingSession == null) {
            return false;
        }
        TrackingSession session = trackingSession;
        Iterable $this$forEach$iv = session.getTargets();
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            TrackingTarget target = (TrackingTarget)element$iv;
            boolean bl = false;
            Map<String, ChemdahHologram> map = QuestTrackHandler.INSTANCE.getLandmarkTracker().getLandmarkHologramMap().get(player2.getName());
            if (map == null || (map = map.remove(target.getId())) == null) continue;
            map.remove();
        }
        this.cleanupBaffle(player2, session);
        if (playerSessions.isEmpty()) {
            sessions.remove(player2.getUniqueId());
        }
        return true;
    }

    public static /* synthetic */ boolean stopTracking$default(ChemdahTrackAPI chemdahTrackAPI, Player player2, String string, int n, Object object) {
        if ((n & 2) != 0) {
            string = null;
        }
        return chemdahTrackAPI.stopTracking(player2, string);
    }

    public final boolean isTracking(@NotNull Player player2, @Nullable String source) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        ConcurrentHashMap<String, TrackingSession> concurrentHashMap = sessions.get(player2.getUniqueId());
        if (concurrentHashMap == null) {
            return false;
        }
        ConcurrentHashMap<String, TrackingSession> playerSessions = concurrentHashMap;
        return source == null ? !((Map)playerSessions).isEmpty() : playerSessions.containsKey(source);
    }

    public static /* synthetic */ boolean isTracking$default(ChemdahTrackAPI chemdahTrackAPI, Player player2, String string, int n, Object object) {
        if ((n & 2) != 0) {
            string = null;
        }
        return chemdahTrackAPI.isTracking(player2, string);
    }

    @Nullable
    public final TrackingSession getSession(@NotNull Player player2, @NotNull String source) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        ConcurrentHashMap<String, TrackingSession> concurrentHashMap = sessions.get(player2.getUniqueId());
        return concurrentHashMap != null ? concurrentHashMap.get(source) : null;
    }

    @Awake(value=LifeCycle.ENABLE)
    public final void setupTick() {
        ExecutorKt.submit$default((boolean)false, (boolean)true, (long)0L, (long)1L, (Function1)setupTick.1.INSTANCE, (int)5, null);
    }

    public final void tickSession(@NotNull Player player2, @NotNull TrackingSession session, @NotNull ConcurrentHashMap<String, TrackingSession> playerSessions) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        Intrinsics.checkNotNullParameter(playerSessions, (String)"playerSessions");
        Iterable $this$forEach$iv = session.getTargets();
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Location center2;
            TrackingTarget target = (TrackingTarget)element$iv;
            boolean bl = false;
            if (target.getCenter().getLocation(player2) == null) continue;
            World world = center2.getWorld();
            if (!Intrinsics.areEqual((Object)player2.getWorld().getName(), (Object)(world != null ? world.getName() : null))) continue;
            double distance = player2.getLocation().distance(center2);
            if (session.getCancelDistance() > 0.0 && distance <= session.getCancelDistance()) {
                playerSessions.remove(session.getSource());
                Iterable $this$forEach$iv2 = session.getTargets();
                boolean $i$f$forEach2 = false;
                for (Object element$iv2 : $this$forEach$iv2) {
                    TrackingTarget t = (TrackingTarget)element$iv2;
                    boolean bl2 = false;
                    Map<String, ChemdahHologram> map = QuestTrackHandler.INSTANCE.getLandmarkTracker().getLandmarkHologramMap().get(player2.getName());
                    if (map == null || (map = map.remove(t.getId())) == null) continue;
                    map.remove();
                }
                INSTANCE.cleanupBaffle(player2, session);
                Consumer<Player> consumer = session.getOnCancel();
                if (consumer != null) {
                    consumer.accept(player2);
                }
                return;
            }
            if (session.getBeacon() != null) {
                QuestTrackHandler.INSTANCE.getBeaconTracker().send(player2, center2, session.getBeacon());
            }
            if (session.getNavigation() != null) {
                QuestTrackHandler.INSTANCE.getNavigationTracker().send(player2, center2, session.getNavigation());
            }
            if (session.getLandmark() == null) continue;
            QuestTrackHandler.INSTANCE.getLandmarkTracker().update(player2, center2, session.getLandmark(), target.getId(), target.getName());
        }
    }

    public final void cleanupBaffle(@NotNull Player player2, @NotNull TrackingSession session) {
        block1: {
            Object object;
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            TrackBeacon trackBeacon = session.getBeacon();
            if (trackBeacon != null && (trackBeacon = trackBeacon.getPeriod()) != null) {
                trackBeacon.reset(player2.getName());
            }
            if ((object = session.getNavigation()) == null || (object = ((TrackNavigation)object).getNaviPoint()) == null || (object = ((NavPoint)object).getPeriod()) == null) break block1;
            object.reset(player2.getName());
        }
    }

    @SubscribeEvent
    public final void onQuit(@NotNull PlayerQuitEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        ConcurrentHashMap<String, TrackingSession> concurrentHashMap = sessions.remove(e.getPlayer().getUniqueId());
        if (concurrentHashMap == null) {
            return;
        }
        ConcurrentHashMap<String, TrackingSession> all = concurrentHashMap;
        Collection<TrackingSession> collection = all.values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"all.values");
        Iterable $this$forEach$iv = collection;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            TrackingSession session = (TrackingSession)element$iv;
            boolean bl = false;
            Iterable $this$forEach$iv2 = session.getTargets();
            boolean $i$f$forEach2 = false;
            for (Object element$iv2 : $this$forEach$iv2) {
                TrackingTarget target = (TrackingTarget)element$iv2;
                boolean bl2 = false;
                Map<String, ChemdahHologram> map = QuestTrackHandler.INSTANCE.getLandmarkTracker().getLandmarkHologramMap().get(e.getPlayer().getName());
                if (map == null || (map = map.remove(target.getId())) == null) continue;
                map.remove();
            }
            Player player2 = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
            Intrinsics.checkNotNullExpressionValue((Object)session, (String)"session");
            INSTANCE.cleanupBaffle(player2, session);
        }
    }

    public final void displayBeacon(@NotNull Player player2, @NotNull Location target, @NotNull TrackBeacon beacon) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        Intrinsics.checkNotNullParameter((Object)beacon, (String)"beacon");
        beacon.display(player2, target);
    }

    public final void displayBeacon(@NotNull Player player2, @NotNull Location target, @NotNull Particle particle, double size, int count2, double distance, boolean fixed) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        Intrinsics.checkNotNullParameter((Object)particle, (String)"particle");
        new TrackBeacon(particle, size, count2, distance, fixed, 20).display(player2, target);
    }

    public static /* synthetic */ void displayBeacon$default(ChemdahTrackAPI chemdahTrackAPI, Player player2, Location location, Particle particle, double d, int n, double d2, boolean bl, int n2, Object object) {
        if ((n2 & 4) != 0) {
            particle = Particle.HAPPY_VILLAGER;
        }
        if ((n2 & 8) != 0) {
            d = 0.5;
        }
        if ((n2 & 0x10) != 0) {
            n = 1;
        }
        if ((n2 & 0x20) != 0) {
            d2 = 32.0;
        }
        if ((n2 & 0x40) != 0) {
            bl = false;
        }
        chemdahTrackAPI.displayBeacon(player2, location, particle, d, n, d2, bl);
    }

    public final void displayNavigation(@NotNull Player player2, @NotNull Location target, @NotNull TrackNavigation navigation) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        Intrinsics.checkNotNullParameter((Object)navigation, (String)"navigation");
        navigation.display(player2, target);
    }

    public final void displayNavigationPoints(@NotNull Player player2, @NotNull Location target, @NotNull Particle particle, float distance) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        Intrinsics.checkNotNullParameter((Object)particle, (String)"particle");
        new TrackNavigation(new NavPoint.Normal(particle, 0.5, 0.2, 0.2, 1, 2L, 20), distance, false).display(player2, target);
    }

    public static /* synthetic */ void displayNavigationPoints$default(ChemdahTrackAPI chemdahTrackAPI, Player player2, Location location, Particle particle, float f, int n, Object object) {
        if ((n & 4) != 0) {
            particle = Particle.CRIT;
        }
        if ((n & 8) != 0) {
            f = 128.0f;
        }
        chemdahTrackAPI.displayNavigationPoints(player2, location, particle, f);
    }

    public final void displayNavigationArrows(@NotNull Player player2, @NotNull Location target, @NotNull Particle particle, float distance) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        Intrinsics.checkNotNullParameter((Object)particle, (String)"particle");
        new TrackNavigation(new NavPoint.Arrow(particle, 1.0, 3, 0.5, 45.0, 2L, 20), distance, false).display(player2, target);
    }

    public static /* synthetic */ void displayNavigationArrows$default(ChemdahTrackAPI chemdahTrackAPI, Player player2, Location location, Particle particle, float f, int n, Object object) {
        if ((n & 4) != 0) {
            particle = Particle.CRIT;
        }
        if ((n & 8) != 0) {
            f = 128.0f;
        }
        chemdahTrackAPI.displayNavigationArrows(player2, location, particle, f);
    }

    public final void updateLandmark(@NotNull Player player2, @NotNull Location pos, @NotNull List<String> content, @NotNull String landmarkId) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)pos, (String)"pos");
        Intrinsics.checkNotNullParameter(content, (String)"content");
        Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
        QuestTrackHandler.INSTANCE.getLandmarkTracker().updateLandmarkTracker(player2, pos, content, landmarkId);
    }

    public final void removeLandmark(@NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        QuestTrackHandler.INSTANCE.getLandmarkTracker().removeLandmarkTracker(player2);
    }

    public final void removeLandmark(@NotNull Player player2, @NotNull String landmarkId) {
        block0: {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
            Map<String, ChemdahHologram> map = QuestTrackHandler.INSTANCE.getLandmarkTracker().getLandmarkHologramMap().get(player2.getName());
            if (map == null || (map = map.remove(landmarkId)) == null) break block0;
            map.remove();
        }
    }

    @NotNull
    public final TrackingTarget targetAt(@NotNull String id2, @NotNull Location location, @NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return new TrackingTarget(id2, new TrackCenter(id2, location){
            final /* synthetic */ String $id;
            final /* synthetic */ Location $location;
            {
                this.$id = $id;
                this.$location = $location;
            }

            @NotNull
            public String identifier() {
                World world = this.$location.getWorld();
                return this.$id + '@' + (world != null ? world.getName() : null) + ':' + this.$location.getBlockX() + ',' + this.$location.getBlockY() + ',' + this.$location.getBlockZ();
            }

            @NotNull
            public Location getLocation(@NotNull Player player2) {
                Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                return this.$location;
            }
        }, name);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0010\n\u0002\u0010\u000b\n\u0002\b\u0002\b\u0016\u0018\u00002\u00020\u0001BQ\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\b\u0012\b\u0010\t\u001a\u0004\u0018\u00010\n\u0012\b\u0010\u000b\u001a\u0004\u0018\u00010\f\u0012\u0006\u0010\r\u001a\u00020\u000e\u0012\u000e\u0010\u000f\u001a\n\u0012\u0004\u0012\u00020\u0011\u0018\u00010\u0010\u00a2\u0006\u0002\u0010\u0012J\u0010\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020\u0011H\u0016R\u0013\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\r\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0013\u0010\u000b\u001a\u0004\u0018\u00010\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0013\u0010\t\u001a\u0004\u0018\u00010\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0019\u0010\u000f\u001a\n\u0012\u0004\u0012\u00020\u0011\u0018\u00010\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u001cR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u0017\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010 \u00a8\u0006$"}, d2={"Link/ptms/chemdah/api/ChemdahTrackAPI$TrackingSession;", "", "source", "", "targets", "", "Link/ptms/chemdah/api/ChemdahTrackAPI$TrackingTarget;", "beacon", "Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;", "navigation", "Link/ptms/chemdah/core/quest/addon/data/TrackNavigation;", "landmark", "Link/ptms/chemdah/core/quest/addon/data/TrackLandmark;", "cancelDistance", "", "onCancel", "Ljava/util/function/Consumer;", "Lorg/bukkit/entity/Player;", "(Ljava/lang/String;Ljava/util/List;Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;Link/ptms/chemdah/core/quest/addon/data/TrackNavigation;Link/ptms/chemdah/core/quest/addon/data/TrackLandmark;DLjava/util/function/Consumer;)V", "getBeacon", "()Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;", "getCancelDistance", "()D", "getLandmark", "()Link/ptms/chemdah/core/quest/addon/data/TrackLandmark;", "getNavigation", "()Link/ptms/chemdah/core/quest/addon/data/TrackNavigation;", "getOnCancel", "()Ljava/util/function/Consumer;", "getSource", "()Ljava/lang/String;", "getTargets", "()Ljava/util/List;", "tick", "", "player", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nChemdahTrackAPI.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ChemdahTrackAPI.kt\nink/ptms/chemdah/api/ChemdahTrackAPI$TrackingSession\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,399:1\n1855#2,2:400\n*S KotlinDebug\n*F\n+ 1 ChemdahTrackAPI.kt\nink/ptms/chemdah/api/ChemdahTrackAPI$TrackingSession\n*L\n85#1:400,2\n*E\n"})
    public static class TrackingSession {
        @NotNull
        private final String source;
        @NotNull
        private final List<TrackingTarget> targets;
        @Nullable
        private final TrackBeacon beacon;
        @Nullable
        private final TrackNavigation navigation;
        @Nullable
        private final TrackLandmark landmark;
        private final double cancelDistance;
        @Nullable
        private final Consumer<Player> onCancel;

        public TrackingSession(@NotNull String source, @NotNull List<TrackingTarget> targets, @Nullable TrackBeacon beacon, @Nullable TrackNavigation navigation, @Nullable TrackLandmark landmark, double cancelDistance, @Nullable Consumer<Player> onCancel) {
            Intrinsics.checkNotNullParameter((Object)source, (String)"source");
            Intrinsics.checkNotNullParameter(targets, (String)"targets");
            this.source = source;
            this.targets = targets;
            this.beacon = beacon;
            this.navigation = navigation;
            this.landmark = landmark;
            this.cancelDistance = cancelDistance;
            this.onCancel = onCancel;
        }

        @NotNull
        public final String getSource() {
            return this.source;
        }

        @NotNull
        public final List<TrackingTarget> getTargets() {
            return this.targets;
        }

        @Nullable
        public final TrackBeacon getBeacon() {
            return this.beacon;
        }

        @Nullable
        public final TrackNavigation getNavigation() {
            return this.navigation;
        }

        @Nullable
        public final TrackLandmark getLandmark() {
            return this.landmark;
        }

        public final double getCancelDistance() {
            return this.cancelDistance;
        }

        @Nullable
        public final Consumer<Player> getOnCancel() {
            return this.onCancel;
        }

        public boolean tick(@NotNull Player player2) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Iterable $this$forEach$iv = this.targets;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Location center2;
                TrackingTarget target = (TrackingTarget)element$iv;
                boolean bl = false;
                if (target.getCenter().getLocation(player2) == null) continue;
                World world = center2.getWorld();
                if (!Intrinsics.areEqual((Object)player2.getWorld().getName(), (Object)(world != null ? world.getName() : null))) continue;
                double distance = player2.getLocation().distance(center2);
                if (this.cancelDistance > 0.0 && distance <= this.cancelDistance) {
                    Consumer<Player> consumer = this.onCancel;
                    if (consumer != null) {
                        consumer.accept(player2);
                    }
                    return false;
                }
                if (this.beacon != null) {
                    QuestTrackHandler.INSTANCE.getBeaconTracker().send(player2, center2, this.beacon);
                }
                if (this.navigation != null) {
                    QuestTrackHandler.INSTANCE.getNavigationTracker().send(player2, center2, this.navigation);
                }
                if (this.landmark == null) continue;
                QuestTrackHandler.INSTANCE.getLandmarkTracker().update(player2, center2, this.landmark, target.getId(), target.getName());
            }
            return true;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J'\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0015H\u00d6\u0001J\t\u0010\u0016\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u000b\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/api/ChemdahTrackAPI$TrackingTarget;", "", "id", "", "center", "Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "name", "(Ljava/lang/String;Link/ptms/chemdah/core/quest/addon/data/TrackCenter;Ljava/lang/String;)V", "getCenter", "()Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "getId", "()Ljava/lang/String;", "getName", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "Chemdah"})
    public static final class TrackingTarget {
        @NotNull
        private final String id;
        @NotNull
        private final TrackCenter center;
        @NotNull
        private final String name;

        public TrackingTarget(@NotNull String id2, @NotNull TrackCenter center2, @NotNull String name) {
            Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
            Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            this.id = id2;
            this.center = center2;
            this.name = name;
        }

        @NotNull
        public final String getId() {
            return this.id;
        }

        @NotNull
        public final TrackCenter getCenter() {
            return this.center;
        }

        @NotNull
        public final String getName() {
            return this.name;
        }

        @NotNull
        public final String component1() {
            return this.id;
        }

        @NotNull
        public final TrackCenter component2() {
            return this.center;
        }

        @NotNull
        public final String component3() {
            return this.name;
        }

        @NotNull
        public final TrackingTarget copy(@NotNull String id2, @NotNull TrackCenter center2, @NotNull String name) {
            Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
            Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            return new TrackingTarget(id2, center2, name);
        }

        public static /* synthetic */ TrackingTarget copy$default(TrackingTarget trackingTarget, String string, TrackCenter trackCenter, String string2, int n, Object object) {
            if ((n & 1) != 0) {
                string = trackingTarget.id;
            }
            if ((n & 2) != 0) {
                trackCenter = trackingTarget.center;
            }
            if ((n & 4) != 0) {
                string2 = trackingTarget.name;
            }
            return trackingTarget.copy(string, trackCenter, string2);
        }

        @NotNull
        public String toString() {
            return "TrackingTarget(id=" + this.id + ", center=" + this.center + ", name=" + this.name + ')';
        }

        public int hashCode() {
            int result = this.id.hashCode();
            result = result * 31 + this.center.hashCode();
            result = result * 31 + this.name.hashCode();
            return result;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TrackingTarget)) {
                return false;
            }
            TrackingTarget trackingTarget = (TrackingTarget)other;
            if (!Intrinsics.areEqual((Object)this.id, (Object)trackingTarget.id)) {
                return false;
            }
            if (!Intrinsics.areEqual((Object)this.center, (Object)trackingTarget.center)) {
                return false;
            }
            return Intrinsics.areEqual((Object)this.name, (Object)trackingTarget.name);
        }
    }
}

