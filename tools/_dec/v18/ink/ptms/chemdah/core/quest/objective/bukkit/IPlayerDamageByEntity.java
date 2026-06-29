/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.inventory.EntityEquipment
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.AEntityDamage;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.core.quest.selector.InferEntity;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerDamageByEntity;", "Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage;", "Lorg/bukkit/event/entity/EntityDamageByEntityEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerDamageByEntity
extends AEntityDamage<EntityDamageByEntityEvent> {
    @NotNull
    public static final IPlayerDamageByEntity INSTANCE = new IPlayerDamageByEntity();
    @NotNull
    private static final String name = "player damage by entity";
    @NotNull
    private static final Class<EntityDamageByEntityEvent> event = EntityDamageByEntityEvent.class;

    private IPlayerDamageByEntity() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityDamageByEntityEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(EntityDamageByEntityEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.getEntity();
        return entity instanceof Player ? (Player)entity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityDamageByEntityEvent e) {
        InferEntity inferEntity = data2.toInferEntity();
        Intrinsics.checkNotNullExpressionValue((Object)e, (String)"e");
        return inferEntity.isEntity((Entity)BukkitEventKt.getAttacker((EntityDamageByEntityEvent)e));
    }

    private static final Boolean _init_$lambda$2(Data data2, EntityDamageByEntityEvent e) {
        InferItem inferItem = data2.toInferItem();
        Intrinsics.checkNotNullExpressionValue((Object)e, (String)"e");
        LivingEntity livingEntity = BukkitEventKt.getAttacker((EntityDamageByEntityEvent)e);
        Intrinsics.checkNotNull((Object)livingEntity);
        EntityEquipment entityEquipment = livingEntity.getEquipment();
        if (entityEquipment == null || (entityEquipment = entityEquipment.getItemInMainHand()) == null) {
            entityEquipment = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)entityEquipment, (String)"e.attacker!!.equipment?.\u2026mInMainHand ?: EMPTY_ITEM");
        return inferItem.isItem((ItemStack)entityEquipment);
    }

    static {
        INSTANCE.handler(IPlayerDamageByEntity::_init_$lambda$0);
        INSTANCE.addSimpleCondition("attacker", IPlayerDamageByEntity::_init_$lambda$1);
        INSTANCE.addSimpleCondition("weapon", IPlayerDamageByEntity::_init_$lambda$2);
    }
}

