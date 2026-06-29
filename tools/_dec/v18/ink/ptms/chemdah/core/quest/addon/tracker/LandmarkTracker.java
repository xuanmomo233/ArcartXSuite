/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.Adyeshach
 *  ink.ptms.adyeshach.core.AdyeshachHologram
 *  ink.ptms.adyeshach.core.AdyeshachHologramHandler
 *  ink.ptms.adyeshach.core.util.UtilsKt
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

import ink.ptms.adyeshach.core.Adyeshach;
import ink.ptms.adyeshach.core.AdyeshachHologram;
import ink.ptms.adyeshach.core.AdyeshachHologramHandler;
import ink.ptms.adyeshach.core.util.UtilsKt;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonDepend;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.addon.data.TrackLandmark;
import ink.ptms.chemdah.core.quest.addon.tracker.LandmarkTracker;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackHandler;
import ink.ptms.chemdah.core.quest.meta.MetaName;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt;
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

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J2\u0010\t\u001a\u0004\u0018\u00010\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00052\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\nH\u0016J\u0010\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u000b\u001a\u00020\fH\u0016J\b\u0010\u0015\u001a\u00020\u0014H\u0016J\u0010\u0010\u0016\u001a\u00020\u00142\u0006\u0010\u000b\u001a\u00020\fH\u0016J \u0010\u0016\u001a\u00020\u00142\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0005H\u0016J.\u0010\u0016\u001a\u00020\u00142\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0017\u001a\u00020\n2\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u00050\u00192\u0006\u0010\u000f\u001a\u00020\u0005H\u0016R)\u0010\u0003\u001a\u001a\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u00040\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\u001a"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/LandmarkTracker;", "", "()V", "landmarkHologramMap", "", "", "Link/ptms/adyeshach/core/AdyeshachHologram;", "getLandmarkHologramMap", "()Ljava/util/Map;", "calcLandmarkDisplayPosition", "Lorg/bukkit/Location;", "player", "Lorg/bukkit/entity/Player;", "trackAddon", "Link/ptms/chemdah/core/quest/addon/AddonTrack;", "landmarkId", "distance", "", "trackCenter", "removeLandmarkTracker", "", "setup", "updateLandmarkTracker", "pos", "content", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nLandmarkTracker.kt\nKotlin\n*S Kotlin\n*F\n+ 1 LandmarkTracker.kt\nink/ptms/chemdah/core/quest/addon/tracker/LandmarkTracker\n+ 2 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 3 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 4 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,155:1\n515#2:156\n500#2,6:157\n361#2,7:169\n215#3,2:163\n215#3,2:176\n1549#4:165\n1620#4,3:166\n*S KotlinDebug\n*F\n+ 1 LandmarkTracker.kt\nink/ptms/chemdah/core/quest/addon/tracker/LandmarkTracker\n*L\n66#1:156\n66#1:157,6\n137#1:169,7\n66#1:163,2\n153#1:176,2\n97#1:165\n97#1:166,3\n*E\n"})
public class LandmarkTracker {
    @NotNull
    private final Map<String, Map<String, AdyeshachHologram>> landmarkHologramMap = new ConcurrentHashMap();

    @NotNull
    public final Map<String, Map<String, AdyeshachHologram>> getLandmarkHologramMap() {
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
    public void updateLandmarkTracker(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        if (ChemdahAPI.INSTANCE.getNonChemdahProfileLoaded(player)) {
            return;
        }
        Template template = AddonTrack.Companion.getTrackQuest(ChemdahAPI.INSTANCE.getChemdahProfile(player));
        if (template == null) {
            return;
        }
        Template trackQuest = template;
        if (PlayerProfile.getQuestById$default(ChemdahAPI.INSTANCE.getChemdahProfile(player), trackQuest.getId(), false, 2, null) == null) {
            AddonTrack addonTrack = AddonTrack.Companion.track(trackQuest);
            if (addonTrack == null) {
                return;
            }
            this.updateLandmarkTracker(player, addonTrack, trackQuest.getPath());
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
                if (!task.isCompleted(ChemdahAPI.INSTANCE.getChemdahProfile(player)) && AddonDepend.Companion.isQuestDependCompleted(task, player)) {
                    AddonTrack addonTrack = AddonTrack.Companion.track(task);
                    Intrinsics.checkNotNull((Object)addonTrack);
                    this.updateLandmarkTracker(player, addonTrack, task.getPath());
                    continue;
                }
                AdyeshachHologram adyeshachHologram = this.landmarkHologramMap.get(player.getName());
                if (adyeshachHologram == null || (adyeshachHologram = adyeshachHologram.remove(task.getPath())) == null) continue;
                adyeshachHologram.remove();
            }
        }
    }

    /*
     * WARNING - void declaration
     */
    public void updateLandmarkTracker(@NotNull Player player, @NotNull AddonTrack trackAddon, @NotNull String landmarkId) {
        block9: {
            AdyeshachHologram adyeshachHologram;
            block6: {
                void $this$mapTo$iv$iv;
                Object object;
                Location pos;
                double distance;
                TrackLandmark landmark;
                block8: {
                    block7: {
                        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                        Intrinsics.checkNotNullParameter((Object)trackAddon, (String)"trackAddon");
                        Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
                        Location location = trackAddon.getCenter().getLocation(player);
                        if (location == null) {
                            return;
                        }
                        Location trackCenter = location;
                        landmark = trackAddon.getLandmark();
                        if (!landmark.getEnable() || !(!((Collection)landmark.getContent()).isEmpty())) break block6;
                        World world = trackCenter.getWorld();
                        if (!Intrinsics.areEqual((Object)(world != null ? world.getName() : null), (Object)player.getWorld().getName()) || ChemdahAPI.INSTANCE.getConversationSession(player) != null) break block6;
                        Location location2 = player.getLocation();
                        Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"player.location");
                        distance = UtilsKt.safeDistance((Location)trackCenter, (Location)location2);
                        Location location3 = this.calcLandmarkDisplayPosition(player, trackAddon, landmarkId, distance, trackCenter);
                        if (location3 == null) {
                            return;
                        }
                        pos = location3;
                        object = trackAddon.getName();
                        if (object == null) break block7;
                        String string = player.getLocale();
                        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"player.locale");
                        if ((object = object.get(string)) != null) break block8;
                    }
                    object = MetaName.Companion.displayName$default(MetaName.Companion, trackAddon.getQuestContainer(), false, 1, null);
                }
                Object name = object;
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
                    Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)name), TuplesKt.to((Object)"distance", (Object)CoerceExtensionsKt.format$default((double)distance, (int)0, null, (int)3, null))};
                    collection.add(StringKt.replace((String)it, pairArray));
                }
                List content = (List)destination$iv$iv;
                this.updateLandmarkTracker(player, pos, content, landmarkId);
                break block9;
            }
            if ((adyeshachHologram = this.landmarkHologramMap.get(player.getName())) == null || (adyeshachHologram = adyeshachHologram.remove(landmarkId)) == null) break block9;
            adyeshachHologram.remove();
        }
    }

    @Nullable
    public Location calcLandmarkDisplayPosition(@NotNull Player player, @NotNull AddonTrack trackAddon, @NotNull String landmarkId, double distance, @NotNull Location trackCenter) {
        Location location;
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)trackAddon, (String)"trackAddon");
        Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
        Intrinsics.checkNotNullParameter((Object)trackCenter, (String)"trackCenter");
        TrackLandmark landmark = trackAddon.getLandmark();
        Vector vector = trackCenter.toVector().subtract(player.getLocation().toVector()).normalize();
        Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"trackCenter.toVector().s\u2026n.toVector()).normalize()");
        Vector direction = vector;
        if (distance < landmark.getDistance()) {
            if (landmark.isHideNear()) {
                AdyeshachHologram adyeshachHologram = this.landmarkHologramMap.get(player.getName());
                if (adyeshachHologram != null && (adyeshachHologram = adyeshachHologram.remove(landmarkId)) != null) {
                    adyeshachHologram.remove();
                }
                return null;
            }
            location = trackCenter;
        } else {
            Location location2 = player.getLocation().add(direction.multiply(landmark.getDistance()));
            location = location2;
            Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"{\n            player.loc\u2026mark.distance))\n        }");
        }
        Location pos = location;
        pos.setY(RangesKt.coerceAtLeast((double)pos.getY(), (double)(player.getLocation().getY() + 0.5)));
        return pos;
    }

    /*
     * WARNING - void declaration
     */
    public void updateLandmarkTracker(@NotNull Player player, @NotNull Location pos, @NotNull List<String> content, @NotNull String landmarkId) {
        block3: {
            Map hologramMap;
            block2: {
                Object object;
                void $this$getOrPut$iv;
                Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                Intrinsics.checkNotNullParameter((Object)pos, (String)"pos");
                Intrinsics.checkNotNullParameter(content, (String)"content");
                Intrinsics.checkNotNullParameter((Object)landmarkId, (String)"landmarkId");
                Map<String, Map<String, Object>> map = this.landmarkHologramMap;
                String string = player.getName();
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
                AdyeshachHologram holo = (AdyeshachHologram)map;
                boolean bl = false;
                holo.teleport(pos);
                holo.update(content);
                break block3;
            }
            AdyeshachHologram adyeshachHologram = hologramMap.put(landmarkId, AdyeshachHologramHandler.createHologram$default((AdyeshachHologramHandler)Adyeshach.INSTANCE.api().getHologramHandler(), (Player)player, (Location)pos, content, (boolean)false, (int)8, null));
            if (adyeshachHologram == null) break block3;
            adyeshachHologram.remove();
        }
    }

    public void removeLandmarkTracker(@NotNull Player player) {
        block1: {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            Map<String, AdyeshachHologram> map = this.landmarkHologramMap.remove(player.getName());
            if (map == null) break block1;
            Map<String, AdyeshachHologram> $this$forEach$iv = map;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<String, AdyeshachHologram>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AdyeshachHologram> element$iv;
                Map.Entry<String, AdyeshachHologram> it = element$iv = iterator.next();
                boolean bl = false;
                it.getValue().remove();
            }
        }
    }
}

