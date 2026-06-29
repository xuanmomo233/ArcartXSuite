/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityDeathEvent
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.AEntityDeath;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerKill;", "Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDeath;", "Lorg/bukkit/event/entity/EntityDeathEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerKill
extends AEntityDeath<EntityDeathEvent> {
    @NotNull
    public static final IPlayerKill INSTANCE = new IPlayerKill();
    @NotNull
    private static final String name = "player kill";
    @NotNull
    private static final Class<EntityDeathEvent> event = EntityDeathEvent.class;

    private IPlayerKill() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityDeathEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getKiller();
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityDeathEvent e) {
        InferItem inferItem = data2.toInferItem();
        Player player = e.getEntity().getKiller();
        Intrinsics.checkNotNull((Object)player);
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.entity.killer!!.inventory.itemInMainHand");
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$2(Data data2, EntityDeathEvent e) {
        return data2.toInferEntity().isEntity((Entity)e.getEntity());
    }

    static {
        INSTANCE.handler(IPlayerKill::_init_$lambda$0);
        INSTANCE.addSimpleCondition("weapon", IPlayerKill::_init_$lambda$1);
        INSTANCE.addSimpleCondition("victim", IPlayerKill::_init_$lambda$2);
    }
}

