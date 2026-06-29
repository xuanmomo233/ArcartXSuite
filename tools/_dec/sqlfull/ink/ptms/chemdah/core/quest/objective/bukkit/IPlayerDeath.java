/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.AEntityDeath;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerDeath;", "Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDeath;", "Lorg/bukkit/event/entity/PlayerDeathEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerDeath
extends AEntityDeath<PlayerDeathEvent> {
    @NotNull
    public static final IPlayerDeath INSTANCE = new IPlayerDeath();
    @NotNull
    private static final String name = "player death";
    @NotNull
    private static final Class<PlayerDeathEvent> event = PlayerDeathEvent.class;

    private IPlayerDeath() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerDeathEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(PlayerDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerDeathEvent e) {
        EntityDamageEvent entityDamageEvent = e.getEntity().getLastDamageCause();
        EntityDamageByEntityEvent entityDamageByEntityEvent = entityDamageEvent instanceof EntityDamageByEntityEvent ? (EntityDamageByEntityEvent)entityDamageEvent : null;
        if (entityDamageByEntityEvent == null) {
            return false;
        }
        EntityDamageByEntityEvent el = entityDamageByEntityEvent;
        InferItem inferItem = data2.toInferItem();
        LivingEntity livingEntity = BukkitEventKt.getAttacker((EntityDamageByEntityEvent)el);
        if (livingEntity == null || (livingEntity = livingEntity.getEquipment()) == null || (livingEntity = livingEntity.getItemInMainHand()) == null) {
            livingEntity = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)livingEntity, (String)"el.attacker?.equipment?.\u2026mInMainHand ?: EMPTY_ITEM");
        return inferItem.isItem((ItemStack)livingEntity);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerDeathEvent e) {
        EntityDamageEvent entityDamageEvent = e.getEntity().getLastDamageCause();
        EntityDamageByEntityEvent entityDamageByEntityEvent = entityDamageEvent instanceof EntityDamageByEntityEvent ? (EntityDamageByEntityEvent)entityDamageEvent : null;
        if (entityDamageByEntityEvent == null) {
            return false;
        }
        EntityDamageByEntityEvent el = entityDamageByEntityEvent;
        LivingEntity livingEntity = BukkitEventKt.getAttacker((EntityDamageByEntityEvent)el);
        if (livingEntity == null) {
            return false;
        }
        return data2.toInferEntity().isEntity((Entity)livingEntity);
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerDeathEvent e) {
        return StringsKt.contains$default((CharSequence)String.valueOf(e.getDeathMessage()), (CharSequence)data2.toString(), (boolean)false, (int)2, null);
    }

    private static final Object _init_$lambda$4(PlayerDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        return String.valueOf(e.getDeathMessage());
    }

    static {
        INSTANCE.handler(IPlayerDeath::_init_$lambda$0);
        INSTANCE.addSimpleCondition("weapon", IPlayerDeath::_init_$lambda$1);
        INSTANCE.addSimpleCondition("attacker", IPlayerDeath::_init_$lambda$2);
        INSTANCE.addSimpleCondition("message", IPlayerDeath::_init_$lambda$3);
        INSTANCE.addConditionVariable("message", IPlayerDeath::_init_$lambda$4);
    }
}

