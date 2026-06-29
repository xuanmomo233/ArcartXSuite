/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  ink.ptms.adyeshach.core.entity.manager.Manager
 *  ink.ptms.adyeshach.core.util.UtilsKt
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation.trigger;

import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.adyeshach.core.entity.manager.Manager;
import ink.ptms.adyeshach.core.util.UtilsKt;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.Source;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000(\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\u001a\u0010\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001*\u00020\u0002\u001a\u0012\u0010\u0003\u001a\u00020\u0004*\u00020\u00022\u0006\u0010\u0005\u001a\u00020\u0006\u001a*\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b*\u00020\n2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\u00022\b\b\u0002\u0010\f\u001a\u00020\u0004\u001a$\u0010\r\u001a\n\u0012\u0004\u0012\u00020\t\u0018\u00010\b*\u00020\u00022\u0006\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\f\u001a\u00020\u0004\u00a8\u0006\u000e"}, d2={"createSource", "Link/ptms/chemdah/core/conversation/Source;", "Link/ptms/adyeshach/core/entity/EntityInstance;", "isValidDistance", "", "player", "Lorg/bukkit/entity/Player;", "openByAdyeshach", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/conversation/Session;", "Link/ptms/chemdah/core/conversation/Conversation;", "entityInstance", "look", "openConversation", "Chemdah"})
public final class TriggerAdyeshachKt {
    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static final boolean isValidDistance(@NotNull EntityInstance $this$isValidDistance, @NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)$this$isValidDistance, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        if (!Intrinsics.areEqual((Object)player.getWorld(), (Object)$this$isValidDistance.getWorld())) return false;
        Location location = player.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"player.location");
        if (!(UtilsKt.safeDistance((Location)location, (Location)$this$isValidDistance.getLocation()) < 10.0)) return false;
        return true;
    }

    @Nullable
    public static final CompletableFuture<Session> openConversation(@NotNull EntityInstance $this$openConversation, @NotNull Player player, boolean look) {
        Intrinsics.checkNotNullParameter((Object)$this$openConversation, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        String[] stringArray = new String[]{$this$openConversation.getId()};
        Conversation conversation2 = ConversationManager.INSTANCE.getConversation(player, "adyeshach", $this$openConversation, stringArray);
        return conversation2 != null ? TriggerAdyeshachKt.openByAdyeshach(conversation2, player, $this$openConversation, look) : null;
    }

    public static /* synthetic */ CompletableFuture openConversation$default(EntityInstance entityInstance, Player player, boolean bl, int n, Object object) {
        if ((n & 2) != 0) {
            bl = true;
        }
        return TriggerAdyeshachKt.openConversation(entityInstance, player, bl);
    }

    @NotNull
    public static final Source<EntityInstance> createSource(@NotNull EntityInstance $this$createSource) {
        Intrinsics.checkNotNullParameter((Object)$this$createSource, (String)"<this>");
        String string = $this$createSource.getDisplayName();
        return new Source<EntityInstance>($this$createSource, string){
            final /* synthetic */ EntityInstance $this_createSource;
            {
                this.$this_createSource = $receiver;
                super($super_call_param$1, $receiver);
            }

            /*
             * WARNING - void declaration
             */
            public boolean transfer(@NotNull Player player, @NotNull String newId) {
                List<E> list2;
                block6: {
                    block5: {
                        Object v2;
                        block4: {
                            void $this$filterTo$iv$iv;
                            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                            Intrinsics.checkNotNullParameter((Object)newId, (String)"newId");
                            Manager manager = this.$this_createSource.getManager();
                            List<E> entities = manager != null ? manager.getEntities() : null;
                            list2 = entities;
                            if (list2 == null) break block5;
                            Iterable $this$filter$iv = list2;
                            boolean $i$f$filter = false;
                            Iterable iterable = $this$filter$iv;
                            Collection destination$iv$iv = new ArrayList<E>();
                            boolean $i$f$filterTo = false;
                            for (T element$iv$iv : $this$filterTo$iv$iv) {
                                EntityInstance it = (EntityInstance)element$iv$iv;
                                boolean bl = false;
                                if (!TriggerAdyeshachKt.isValidDistance(it, player)) continue;
                                destination$iv$iv.add(element$iv$iv);
                            }
                            Iterable $this$firstOrNull$iv = (List)destination$iv$iv;
                            boolean $i$f$firstOrNull = false;
                            for (T element$iv : $this$firstOrNull$iv) {
                                EntityInstance it = (EntityInstance)element$iv;
                                boolean bl = false;
                                if (!Intrinsics.areEqual((Object)it.getId(), (Object)newId)) continue;
                                v2 = element$iv;
                                break block4;
                            }
                            v2 = null;
                        }
                        if ((list2 = (EntityInstance)v2) != null) break block6;
                    }
                    return false;
                }
                List<E> nearby = list2;
                this.update(nearby.getDisplayName(), nearby);
                return true;
            }

            @NotNull
            public Location getOriginLocation(@NotNull EntityInstance entity) {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                Location location = entity.getLocation().add(0.0, entity.getEntitySize().getHeight(), 0.0);
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"entity.getLocation().add\u2026y.entitySize.height, 0.0)");
                return location;
            }
        };
    }

    @NotNull
    public static final CompletableFuture<Session> openByAdyeshach(@NotNull Conversation $this$openByAdyeshach, @NotNull Player player, @NotNull EntityInstance entityInstance, boolean look) {
        Intrinsics.checkNotNullParameter((Object)$this$openByAdyeshach, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)entityInstance, (String)"entityInstance");
        Source<EntityInstance> source = TriggerAdyeshachKt.createSource(entityInstance);
        if (look) {
            Vector vector = source.getOriginLocation(source.getEntity()).subtract(player.getEyeLocation()).toVector().normalize();
            Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"source.getOriginLocation\u2026n).toVector().normalize()");
            Vector direction = vector;
            Location location = player.getLocation().clone();
            Intrinsics.checkNotNullExpressionValue((Object)location, (String)"player.location.clone()");
            Location temp = location;
            temp.setDirection(direction);
            player.teleport(temp);
        }
        return Conversation.open$default($this$openByAdyeshach, player, source, null, arg_0 -> TriggerAdyeshachKt.openByAdyeshach$lambda$0(entityInstance, arg_0), 4, null);
    }

    public static /* synthetic */ CompletableFuture openByAdyeshach$default(Conversation conversation2, Player player, EntityInstance entityInstance, boolean bl, int n, Object object) {
        if ((n & 4) != 0) {
            bl = true;
        }
        return TriggerAdyeshachKt.openByAdyeshach(conversation2, player, entityInstance, bl);
    }

    private static final void openByAdyeshach$lambda$0(EntityInstance $entityInstance, Session it) {
        Intrinsics.checkNotNullParameter((Object)$entityInstance, (String)"$entityInstance");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        it.getVariables().put("@manager", $entityInstance.getManager());
        it.getVariables().put("@entities", CollectionsKt.listOf((Object)$entityInstance));
    }
}

