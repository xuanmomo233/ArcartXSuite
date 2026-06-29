/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.tracker;

import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.addon.data.TrackBeacon;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackHandler;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J \u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0016J\u0018\u0010\u000b\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\f\u001a\u00020\rH\u0016J \u0010\u000b\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0007\u001a\u00020\bH\u0016\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/BeaconTracker;", "", "()V", "send", "", "player", "Lorg/bukkit/entity/Player;", "center", "Lorg/bukkit/Location;", "beacon", "Link/ptms/chemdah/core/quest/addon/data/TrackBeacon;", "sendBeaconTracker", "trackAddon", "Link/ptms/chemdah/core/quest/addon/AddonTrack;", "Chemdah"})
public class BeaconTracker {
    public void send(@NotNull Player player2, @NotNull Location center2, @NotNull TrackBeacon beacon) {
        block7: {
            block6: {
                Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
                Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
                Intrinsics.checkNotNullParameter((Object)beacon, (String)"beacon");
                if (!beacon.getEnable()) {
                    return;
                }
                if (!beacon.getPeriod().hasNext(player2.getName())) {
                    return;
                }
                if (center2.getWorld() == null) break block6;
                World world = center2.getWorld();
                Intrinsics.checkNotNull((Object)world);
                if (Intrinsics.areEqual((Object)world.getName(), (Object)player2.getWorld().getName())) break block7;
            }
            return;
        }
        if (center2.distance(player2.getLocation()) <= beacon.getDistance()) {
            return;
        }
        beacon.display(player2, center2);
    }

    public void sendBeaconTracker(@NotNull Player player2, @NotNull AddonTrack trackAddon) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)trackAddon, (String)"trackAddon");
        Location location = trackAddon.getCenter().getLocation(player2);
        if (location == null) {
            return;
        }
        Location center2 = location;
        this.send(player2, center2, trackAddon.getBeacon());
        QuestTrackHandler.INSTANCE.mark(player2, trackAddon.getQuestContainer().getPath() + ".beacon", trackAddon.getBeacon().getPeriod());
    }

    public void sendBeaconTracker(@NotNull Player player2, @NotNull AddonTrack trackAddon, @NotNull Location center2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)trackAddon, (String)"trackAddon");
        Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
        trackAddon.getBeacon().display(player2, center2);
    }
}

