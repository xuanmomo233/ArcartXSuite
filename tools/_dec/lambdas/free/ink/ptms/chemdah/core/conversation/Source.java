/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation;

import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\b&\u0018\u0000 \u001a*\u0004\b\u0000\u0010\u00012\u00020\u0002:\u0001\u001aB\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00028\u0000\u00a2\u0006\u0002\u0010\u0006J\u0015\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0005\u001a\u00028\u0000H&\u00a2\u0006\u0002\u0010\u0012J\u0018\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u0004H\u0016J\u001b\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00028\u0000\u00a2\u0006\u0002\u0010\u0006R\u001c\u0010\u0005\u001a\u00028\u0000X\u0086\u000e\u00a2\u0006\u0010\n\u0002\u0010\u000b\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\nR\u001a\u0010\u0003\u001a\u00020\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000f\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/core/conversation/Source;", "T", "", "name", "", "entity", "(Ljava/lang/String;Ljava/lang/Object;)V", "getEntity", "()Ljava/lang/Object;", "setEntity", "(Ljava/lang/Object;)V", "Ljava/lang/Object;", "getName", "()Ljava/lang/String;", "setName", "(Ljava/lang/String;)V", "getOriginLocation", "Lorg/bukkit/Location;", "(Ljava/lang/Object;)Lorg/bukkit/Location;", "transfer", "", "player", "Lorg/bukkit/entity/Player;", "newId", "update", "", "Companion", "Chemdah"})
public abstract class Source<T> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private String name;
    private T entity;

    public Source(@NotNull String name, T entity) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        this.name = name;
        this.entity = entity;
    }

    @NotNull
    public final String getName() {
        return this.name;
    }

    public final void setName(@NotNull String string) {
        Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
        this.name = string;
    }

    public final T getEntity() {
        return this.entity;
    }

    public final void setEntity(T t) {
        this.entity = t;
    }

    public boolean transfer(@NotNull Player player2, @NotNull String newId) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)newId, (String)"newId");
        return false;
    }

    @NotNull
    public abstract Location getOriginLocation(T var1);

    public final void update(@NotNull String name, T entity) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        this.name = name;
        this.entity = entity;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001c\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bJ\u001c\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\t\u001a\u00020\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/core/conversation/Source$Companion;", "", "()V", "of", "Link/ptms/chemdah/core/conversation/Source;", "name", "", "origin", "Lorg/bukkit/Location;", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Source<Object> of(@NotNull String name, @NotNull Location origin) {
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Intrinsics.checkNotNullParameter((Object)origin, (String)"origin");
            Unit unit = Unit.INSTANCE;
            return new Source<Object>(name, origin, unit){
                final /* synthetic */ Location $origin;
                {
                    this.$origin = $origin;
                    super($name, $super_call_param$1);
                }

                @NotNull
                public Location getOriginLocation(@NotNull Object entity) {
                    Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                    return this.$origin;
                }
            };
        }

        @NotNull
        public final Source<Object> of(@NotNull String name, @NotNull Player player2) {
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            return new Source<Object>(name, player2){
                final /* synthetic */ Player $player;
                {
                    this.$player = $player;
                    super($name, $player);
                }

                @NotNull
                public Location getOriginLocation(@NotNull Object entity) {
                    Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                    Location location = this.$player.getEyeLocation().add(this.$player.getEyeLocation().getDirection());
                    Intrinsics.checkNotNullExpressionValue((Object)location, (String)"player.eyeLocation.add(p\u2026er.eyeLocation.direction)");
                    return location;
                }
            };
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

