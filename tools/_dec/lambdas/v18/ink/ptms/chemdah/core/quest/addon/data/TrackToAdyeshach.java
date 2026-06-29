/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.cache.Cache
 *  com.google.common.cache.CacheBuilder
 *  ink.ptms.adyeshach.api.AdyeshachAPI
 *  ink.ptms.adyeshach.common.entity.EntityInstance
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon.data;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import ink.ptms.adyeshach.api.AdyeshachAPI;
import ink.ptms.adyeshach.common.entity.EntityInstance;
import ink.ptms.chemdah.core.quest.addon.data.NullLocation;
import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import java.util.concurrent.TimeUnit;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0012\u0010\f\u001a\u0004\u0018\u00010\u00072\u0006\u0010\r\u001a\u00020\u000eH\u0016J\b\u0010\u000f\u001a\u00020\u0003H\u0016R\u001d\u0010\u0005\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/addon/data/TrackToAdyeshach;", "Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "id", "", "(Ljava/lang/String;)V", "cache", "Lcom/google/common/cache/Cache;", "Lorg/bukkit/Location;", "getCache", "()Lcom/google/common/cache/Cache;", "getId", "()Ljava/lang/String;", "getLocation", "player", "Lorg/bukkit/entity/Player;", "identifier", "Chemdah"})
public final class TrackToAdyeshach
implements TrackCenter {
    @NotNull
    private final String id;
    @NotNull
    private final Cache<String, Location> cache;

    public TrackToAdyeshach(@NotNull String id2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        this.id = id2;
        Cache cache = CacheBuilder.newBuilder().expireAfterWrite(250L, TimeUnit.MILLISECONDS).build();
        Intrinsics.checkNotNullExpressionValue((Object)cache, (String)"newBuilder().expireAfter\u2026nit.MILLISECONDS).build()");
        this.cache = cache;
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    @NotNull
    public final Cache<String, Location> getCache() {
        return this.cache;
    }

    @Override
    @NotNull
    public String identifier() {
        return this.id;
    }

    @Override
    @Nullable
    public Location getLocation(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Location loc = (Location)this.cache.get((Object)player.getName(), () -> TrackToAdyeshach.getLocation$lambda$0(this, player));
        return loc instanceof NullLocation ? null : loc;
    }

    private static final Location getLocation$lambda$0(TrackToAdyeshach this$0, Player $player) {
        Intrinsics.checkNotNullParameter((Object)this$0, (String)"this$0");
        Intrinsics.checkNotNullParameter((Object)$player, (String)"$player");
        EntityInstance entityInstance = AdyeshachAPI.INSTANCE.getEntityFromId(this$0.id, $player);
        if (entityInstance == null || (entityInstance = entityInstance.getLocation()) == null || (entityInstance = entityInstance.add(0.0, 1.0, 0.0)) == null) {
            entityInstance = NullLocation.INSTANCE;
        }
        return entityInstance;
    }
}

