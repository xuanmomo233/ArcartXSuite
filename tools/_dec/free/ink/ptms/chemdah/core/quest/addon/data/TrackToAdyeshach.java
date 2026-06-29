/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.cache.Cache
 *  com.google.common.cache.CacheBuilder
 *  ink.ptms.adyeshach.core.Adyeshach
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.optionals.OptionalsKt
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon.data;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import ink.ptms.adyeshach.core.Adyeshach;
import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.optionals.OptionalsKt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\r\u001a\u0004\u0018\u00010\b2\u0006\u0010\u000e\u001a\u00020\u000fJ\u0012\u0010\u0010\u001a\u0004\u0018\u00010\u00112\u0006\u0010\u000e\u001a\u00020\u000fH\u0016J\b\u0010\u0012\u001a\u00020\u0003H\u0016J\b\u0010\u0013\u001a\u00020\u0003H\u0016R#\u0010\u0005\u001a\u0014\u0012\u0004\u0012\u00020\u0003\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/core/quest/addon/data/TrackToAdyeshach;", "Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "id", "", "(Ljava/lang/String;)V", "cache", "Lcom/google/common/cache/Cache;", "Ljava/util/Optional;", "Link/ptms/adyeshach/core/entity/EntityInstance;", "getCache", "()Lcom/google/common/cache/Cache;", "getId", "()Ljava/lang/String;", "getCachedEntity", "player", "Lorg/bukkit/entity/Player;", "getLocation", "Lorg/bukkit/Location;", "identifier", "toString", "Chemdah"})
public final class TrackToAdyeshach
implements TrackCenter {
    @NotNull
    private final String id;
    @NotNull
    private final Cache<String, Optional<EntityInstance>> cache;

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
    public final Cache<String, Optional<EntityInstance>> getCache() {
        return this.cache;
    }

    @Override
    @NotNull
    public String identifier() {
        return this.id;
    }

    @Override
    @Nullable
    public Location getLocation(@NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Optional npc = (Optional)this.cache.get((Object)player2.getName(), () -> TrackToAdyeshach.getLocation$lambda$0(player2, this));
        Intrinsics.checkNotNullExpressionValue((Object)npc, (String)"npc");
        EntityInstance entityInstance = (EntityInstance)OptionalsKt.getOrNull((Optional)npc);
        return entityInstance != null && (entityInstance = entityInstance.getLocation()) != null ? entityInstance.add(0.0, 1.0, 0.0) : null;
    }

    @Nullable
    public final EntityInstance getCachedEntity(@NotNull Player player2) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Optional optional = (Optional)this.cache.getIfPresent((Object)player2.getName());
        return optional != null ? (EntityInstance)OptionalsKt.getOrNull((Optional)optional) : null;
    }

    @NotNull
    public String toString() {
        return "TrackToAdyeshach(id='" + this.id + "')";
    }

    private static final Optional getLocation$lambda$0(Player $player, TrackToAdyeshach this$0) {
        Intrinsics.checkNotNullParameter((Object)$player, (String)"$player");
        Intrinsics.checkNotNullParameter((Object)this$0, (String)"this$0");
        return $player.isOnline() ? Optional.ofNullable(CollectionsKt.firstOrNull((List)Adyeshach.INSTANCE.api().getEntityFinder().getEntitiesFromId(this$0.id, $player))) : Optional.empty();
    }
}

