/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.AEntityDamage;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerAttack;", "Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage;", "Lorg/bukkit/event/entity/EntityDamageByEntityEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerAttack
extends AEntityDamage<EntityDamageByEntityEvent> {
    @NotNull
    public static final IPlayerAttack INSTANCE = new IPlayerAttack();
    @NotNull
    private static final String name = "player attack";
    @NotNull
    private static final Class<EntityDamageByEntityEvent> event = EntityDamageByEntityEvent.class;

    private IPlayerAttack() {
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
        LivingEntity livingEntity = BukkitEventKt.getAttacker((EntityDamageByEntityEvent)it);
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityDamageByEntityEvent e) {
        InferItem inferItem = data2.toInferItem();
        Intrinsics.checkNotNullExpressionValue((Object)e, (String)"e");
        LivingEntity livingEntity = BukkitEventKt.getAttacker((EntityDamageByEntityEvent)e);
        Intrinsics.checkNotNull((Object)livingEntity, (String)"null cannot be cast to non-null type org.bukkit.entity.Player");
        ItemStack itemStack = ((Player)livingEntity).getInventory().getItemInMainHand();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.attacker as Player).inventory.itemInMainHand");
        return inferItem.isItem(itemStack);
    }

    static {
        INSTANCE.handler(IPlayerAttack::_init_$lambda$0);
        INSTANCE.addSimpleCondition("weapon", IPlayerAttack::_init_$lambda$1);
    }
}

