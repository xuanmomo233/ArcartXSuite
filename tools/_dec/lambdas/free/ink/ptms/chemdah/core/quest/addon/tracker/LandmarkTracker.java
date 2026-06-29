/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.RangesKt
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon.tracker;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonDepend;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.addon.data.TrackLandmark;
import ink.ptms.chemdah.core.quest.addon.tracker.LandmarkTracker;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackHandler;
import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologram;
import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologramFactory;
import ink.ptms.chemdah.core.quest.meta.MetaName;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt;
import ink.ptms.chemdah.util.LocationKt;
import ink.ptms.chemdah.util.StringKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.RangesKt;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000P\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0010 \n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J2\u0010\t\u001a\u0004\u0018\u00010\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\n2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00052\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J2\u0010\u0013\u001a\u0004\u0018\u00010\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0010\u001a\u00020\u00052\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0016\u001a\u00020\nH\u0016J\u0010\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u000b\u001a\u00020\fH\u0016J\b\u0010\u0019\u001a\u00020\u0018H\u0016J0\u0010\u001a\u001a\u00020\u00182\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\n2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00052\u0006\u0010\u001b\u001a\u00020\u0005H\u0016J\u0010\u0010\u001c\u001a\u00020\u00182\u0006\u0010\u000b\u001a\u00020\fH\u0016J \u0010\u001c\u001a\u00020\u00182\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0010\u001a\u00020\u0005H\u0016J.\u0010\u001c\u001a\u00020\u00182\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u001d\u001a\u00020\n2\f\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00050\u001f2\u0006\u0010\u0010\u001a\u00020\u0005H\u0016R)\u0010\u0003\u001a\u001a\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u00040\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006 "}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/LandmarkTracker;", "", "()V", "landmarkHologramMap", "", "", "Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologram;", "getLandmarkHologramMap", "()Ljava/util/Map;", "calcDisplayPosition", "Lorg/bukkit/Location;", "player", "Lorg/bukkit/entity/Player;", "center", "landmark", "Link/ptms/chemdah/core/quest/addon/data/TrackLandmark;", "landmarkId", "distance", "", "calcLandmarkDisplayPosition", "trackAddon", "Link/ptms/chemdah/core/quest/addon/AddonTrack;", "trackCenter", "removeLandmarkTracker", "", "setup", "update", "name", "updateLandmarkTracker", "pos", "content", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nLandmarkTracker.kt\nKotlin\n*S Kotlin\n*F\n+ 1 LandmarkTracker.kt\nink/ptms/chemdah/core/quest/addon/tracker/LandmarkTracker\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 4 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,183:1\n1549#2:184\n1620#2,3:185\n515#3:188\n500#3,6:189\n361#3,7:197\n215#4,2:195\n215#4,2:204\n*S KotlinDebug\n*F\n+ 1 LandmarkTracker.kt\nink/ptms/chemdah/core/quest/addon/tracker/LandmarkTracker\n*L\n72#1:184\n72#1:185,3\n120#1:188\n120#1:189,6\n164#1:197,7\n120#1:195,2\n180#1:204,2\n*E\n"})
public class LandmarkTracker {
    @NotNull
    private final Map<String, Map<String, ChemdahHologram>> landmarkHologramMap = new ConcurrentHashMap();

    @NotNull
    public final Map<String, Map<String, ChemdahHologram>> getLandmarkHologramMap() {
        return this.landmarkHologramMap;
    }

    public void setup() {
        if (QuestTrackHandler.INSTANCE.getLandmarkUpdatePeriod() > 0L) {
            ExecutorKt.submitAsync$default((boolean)false, (long)0L, (long)QuestTrackHandler.INSTANCE.getLandmarkUpdatePeriod(), (Function1)setup.1.INSTANCE, (int)3, null);
        }
    }

    /*
     * WARNING - void declaration
     */
    public void update(@NotNull Player player2, @NotNull Location center2, @NotNull TrackLandmark landmark, @NotNull String landmarkId, @NotNull String name) {
        void $this$mapTo$iv$iv;
        block9: {
            Object object;
            block8: {
                Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
                Intrinsics.checkNotNullParameter((Object)landmark, (String)"landmark");
                Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
                Intrinsics.checkNotNullParameter((Object)name, (String)"name");
                if (!landmark.getEnable() || landmark.getContent().isEmpty()) {
                    Map<String, ChemdahHologram> map = this.landmarkHologramMap.get(player2.getName());
                    if (map != null && (map = map.remove(landmarkId)) != null) {
                        map.remove();
                    }
                    return;
                }
                if (center2.getWorld() == null) break block8;
                World world = center2.getWorld();
                Intrinsics.checkNotNull((Object)world);
                if (Intrinsics.areEqual((Object)world.getName(), (Object)player2.getWorld().getName())) break block9;
            }
            if ((object = this.landmarkHologramMap.get(player2.getName())) != null && (object = object.remove(landmarkId)) != null) {
                object.remove();
            }
            return;
        }
        Location location = player2.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"player.location");
        double distance = LocationKt.safeDistance(center2, location);
        Location location2 = this.calcDisplayPosition(player2, center2, landmark, landmarkId, distance);
        if (location2 == null) {
            return;
        }
        Location pos = location2;
        Iterable $this$map$iv = landmark.getContent();
        boolean $i$f$map = false;
        Iterable iterable = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            String string = (String)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)name), TuplesKt.to((Object)"distance", (Object)CoerceExtensionsKt.format$default((double)distance, (int)0, (int)0, (int)3, null))};
            collection.add(StringKt.replace((String)it, pairArray));
        }
        List content = (List)destination$iv$iv;
        this.updateLandmarkTracker(player2, pos, content, landmarkId);
    }

    @Nullable
    public Location calcDisplayPosition(@NotNull Player player2, @NotNull Location center2, @NotNull TrackLandmark landmark, @NotNull String landmarkId, double distance) {
        Location location;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
        Intrinsics.checkNotNullParameter((Object)landmark, (String)"landmark");
        Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
        Vector vector = center2.toVector().subtract(player2.getLocation().toVector()).normalize();
        Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"center.toVector().subtra\u2026n.toVector()).normalize()");
        Vector direction = vector;
        if (distance < landmark.getDistance()) {
            if (landmark.isHideNear()) {
                Map<String, ChemdahHologram> map = this.landmarkHologramMap.get(player2.getName());
                if (map != null && (map = map.remove(landmarkId)) != null) {
                    map.remove();
                }
                return null;
            }
            location = center2;
        } else {
            Location location2 = player2.getLocation().add(direction.multiply(landmark.getDistance()));
            location = location2;
            Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"{\n            player.loc\u2026mark.distance))\n        }");
        }
        Location pos = location;
        pos.setY(RangesKt.coerceAtLeast((double)pos.getY(), (double)(player2.getLocation().getY() + 0.5)));
        return pos;
    }

    /*
     * WARNING - void declaration
     */
    public void updateLandmarkTracker(@NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        if (ChemdahAPI.INSTANCE.getNonChemdahProfileLoaded(player2)) {
            return;
        }
        Template template = AddonTrack.Companion.getTrackQuest(ChemdahAPI.INSTANCE.getChemdahProfile(player2));
        if (template == null) {
            return;
        }
        Template trackQuest = template;
        if (PlayerProfile.getQuestById$default(ChemdahAPI.INSTANCE.getChemdahProfile(player2), trackQuest.getId(), false, 2, null) == null) {
            AddonTrack addonTrack = AddonTrack.Companion.track(trackQuest);
            if (addonTrack == null) {
                return;
            }
            this.updateLandmarkTracker(player2, addonTrack, trackQuest.getPath());
        } else {
            void $this$filterTo$iv$iv;
            Map $this$filter$iv = trackQuest.getTaskMap();
            boolean $i$f$filter = false;
            Object object = $this$filter$iv;
            Map destination$iv$iv = new LinkedHashMap();
            boolean $i$f$filterTo = false;
            Iterator iterator = $this$filterTo$iv$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry element$iv$iv;
                Map.Entry entry = element$iv$iv = iterator.next();
                boolean bl = false;
                Task task = (Task)entry.getValue();
                if (!(AddonTrack.Companion.track(task) != null)) continue;
                destination$iv$iv.put(element$iv$iv.getKey(), element$iv$iv.getValue());
            }
            Map $this$forEach$iv = destination$iv$iv;
            boolean $i$f$forEach = false;
            object = $this$forEach$iv.entrySet().iterator();
            while (object.hasNext()) {
                Map.Entry element$iv;
                Map.Entry entry = element$iv = (Map.Entry)object.next();
                boolean bl = false;
                Task task = (Task)entry.getValue();
                if (!task.isCompleted(ChemdahAPI.INSTANCE.getChemdahProfile(player2)) && AddonDepend.Companion.isQuestDependCompleted(task, player2)) {
                    AddonTrack addonTrack = AddonTrack.Companion.track(task);
                    Intrinsics.checkNotNull((Object)addonTrack);
                    this.updateLandmarkTracker(player2, addonTrack, task.getPath());
                    continue;
                }
                Map<String, ChemdahHologram> map = this.landmarkHologramMap.get(player2.getName());
                if (map == null || (map = map.remove(task.getPath())) == null) continue;
                map.remove();
            }
        }
    }

    public void updateLandmarkTracker(@NotNull Player player2, @NotNull AddonTrack trackAddon, @NotNull String landmarkId) {
        Object object;
        Location trackCenter;
        block7: {
            block6: {
                Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                Intrinsics.checkNotNullParameter((Object)trackAddon, (String)"trackAddon");
                Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
                if (ChemdahAPI.INSTANCE.getConversationSession(player2) != null) {
                    Map<String, ChemdahHologram> map = this.landmarkHologramMap.get(player2.getName());
                    if (map != null && (map = map.remove(landmarkId)) != null) {
                        map.remove();
                    }
                    return;
                }
                Location location = trackAddon.getCenter().getLocation(player2);
                if (location == null) {
                    return;
                }
                trackCenter = location;
                object = trackAddon.getName();
                if (object == null) break block6;
                String string = player2.getLocale();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"player.locale");
                if ((object = object.get(string)) != null) break block7;
            }
            object = MetaName.Companion.displayName$default(MetaName.Companion, trackAddon.getQuestContainer(), false, ChemdahAPI.INSTANCE.getChemdahProfile(player2), 1, null);
        }
        Object name = object;
        this.update(player2, trackCenter, trackAddon.getLandmark(), landmarkId, (String)name);
    }

    @Nullable
    public Location calcLandmarkDisplayPosition(@NotNull Player player2, @NotNull AddonTrack trackAddon, @NotNull String landmarkId, double distance, @NotNull Location trackCenter) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)trackAddon, (String)"trackAddon");
        Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
        Intrinsics.checkNotNullParameter((Object)trackCenter, (String)"trackCenter");
        return this.calcDisplayPosition(player2, trackCenter, trackAddon.getLandmark(), landmarkId, distance);
    }

    /*
     * WARNING - void declaration
     */
    public void updateLandmarkTracker(@NotNull Player player2, @NotNull Location pos, @NotNull List<String> content, @NotNull String landmarkId) {
        block3: {
            Map hologramMap;
            block2: {
                Object object;
                void $this$getOrPut$iv;
                Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                Intrinsics.checkNotNullParameter((Object)pos, (String)"pos");
                Intrinsics.checkNotNullParameter(content, (String)"content");
                Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
                Map<String, Map<String, ChemdahHologram>> map = this.landmarkHologramMap;
                String string = player2.getName();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"player.name");
                String key$iv = string;
                boolean $i$f$getOrPut = false;
                Object value$iv = $this$getOrPut$iv.get(key$iv);
                if (value$iv == null) {
                    boolean bl = false;
                    Map answer$iv = new ConcurrentHashMap();
                    $this$getOrPut$iv.put(key$iv, answer$iv);
                    object = answer$iv;
                } else {
                    object = value$iv;
                }
                if (!(hologramMap = (Map)object).containsKey(landmarkId)) break block2;
                Object v = hologramMap.get(landmarkId);
                Intrinsics.checkNotNull(v);
                map = v;
                ChemdahHologram holo = (ChemdahHologram)((Object)map);
                boolean bl = false;
                holo.teleport(pos);
                holo.update(content);
                break block3;
            }
            ChemdahHologram chemdahHologram = hologramMap.put(landmarkId, ChemdahHologramFactory.INSTANCE.getHandler().createHologram(player2, pos, content));
            if (chemdahHologram == null) break block3;
            chemdahHologram.remove();
        }
    }

    public void removeLandmarkTracker(@NotNull Player player2) {
        block1: {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Map<String, ChemdahHologram> map = this.landmarkHologramMap.remove(player2.getName());
            if (map == null) break block1;
            Map<String, ChemdahHologram> $this$forEach$iv = map;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<String, ChemdahHologram>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, ChemdahHologram> element$iv;
                Map.Entry<String, ChemdahHologram> it = element$iv = iterator.next();
                boolean bl = false;
                it.getValue().remove();
            }
        }
    }
}

