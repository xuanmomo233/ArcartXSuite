/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.tracker;

import ink.ptms.adyeshach.core.util.UtilsKt;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.core.quest.addon.data.NavPoint;
import ink.ptms.chemdah.core.quest.addon.data.TrackNavigation;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackHandler;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016J \u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0016\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/NavigationTracker;", "", "()V", "sendNavigationTracker", "", "player", "Lorg/bukkit/entity/Player;", "trackAddon", "Link/ptms/chemdah/core/quest/addon/AddonTrack;", "center", "Lorg/bukkit/Location;", "Chemdah"})
public class NavigationTracker {
    public void sendNavigationTracker(@NotNull Player player, @NotNull AddonTrack trackAddon) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)trackAddon, (String)"trackAddon");
        TrackNavigation nav = trackAddon.getNavigation();
        if (nav.getNaviPoint() != null) {
            NavPoint navPoint = nav.getNaviPoint();
            Intrinsics.checkNotNull((Object)navPoint);
            if (navPoint.getPeriod().hasNext(player.getName())) {
                Location location = trackAddon.getCenter().getLocation(player);
                if (location == null) {
                    return;
                }
                Location center2 = location;
                if (nav.getEnable()) {
                    World world = center2.getWorld();
                    if (Intrinsics.areEqual((Object)(world != null ? world.getName() : null), (Object)player.getWorld().getName())) {
                        Location location2 = player.getLocation();
                        Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"player.location");
                        if (UtilsKt.safeDistance((Location)center2, (Location)location2) < (double)nav.getDistance()) {
                            this.sendNavigationTracker(player, trackAddon, center2);
                            String string = trackAddon.getQuestContainer().getPath() + ".navigation";
                            NavPoint navPoint2 = nav.getNaviPoint();
                            Intrinsics.checkNotNull((Object)navPoint2);
                            QuestTrackHandler.INSTANCE.mark(player, string, navPoint2.getPeriod());
                        }
                    }
                }
            }
        }
    }

    public void sendNavigationTracker(@NotNull Player player, @NotNull AddonTrack trackAddon, @NotNull Location center2) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)trackAddon, (String)"trackAddon");
        Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
        trackAddon.getNavigation().display(player, center2);
    }
}

