/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.PlayerDeathEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.AEntityDeath;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u6b7b\u4ea1\u76ee\u6807", description={"\u73a9\u5bb6\u6b7b\u4ea1", "\u652f\u6301\u653b\u51fb\u8005\u3001\u6b66\u5668\u3001\u6b7b\u4ea1\u6d88\u606f\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u6b7b\u4ea1\u6b21\u6570"}, alias={"\u6b7b\u4ea1", "\u9635\u4ea1", "\u88ab\u51fb\u6740"}, params={@ParamInfo(name="weapon", type="ItemStack", description="\u653b\u51fb\u8005\u4f7f\u7528\u7684\u6b66\u5668"), @ParamInfo(name="attacker", type="Entity", description="\u653b\u51fb\u8005\u5b9e\u4f53"), @ParamInfo(name="message", type="String", description="\u6b7b\u4ea1\u6d88\u606f")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerDeath;", "Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDeath;", "Lorg/bukkit/event/entity/PlayerDeathEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerDeath
extends AEntityDeath<PlayerDeathEvent> {
    @NotNull
    public static final IPlayerDeath INSTANCE = new IPlayerDeath();
    @NotNull
    private static final String name = "player death";

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
        return PlayerDeathEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        EntityDamageEvent entityDamageEvent = it.getEntity().getLastDamageCause();
        EntityDamageByEntityEvent el = entityDamageEvent instanceof EntityDamageByEntityEvent ? (EntityDamageByEntityEvent)entityDamageEvent : null;
        EntityDamageByEntityEvent entityDamageByEntityEvent = el;
        if (entityDamageByEntityEvent == null || (entityDamageByEntityEvent = BukkitEventKt.getAttacker((EntityDamageByEntityEvent)entityDamageByEntityEvent)) == null || (entityDamageByEntityEvent = entityDamageByEntityEvent.getEquipment()) == null || (entityDamageByEntityEvent = entityDamageByEntityEvent.getItemInMainHand()) == null) {
            entityDamageByEntityEvent = UnitsKt.getEMPTY_ITEM();
        }
        return entityDamageByEntityEvent;
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerDeathEvent it) {
        EntityDamageByEntityEvent el;
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        EntityDamageEvent entityDamageEvent = it.getEntity().getLastDamageCause();
        EntityDamageByEntityEvent entityDamageByEntityEvent = el = entityDamageEvent instanceof EntityDamageByEntityEvent ? (EntityDamageByEntityEvent)entityDamageEvent : null;
        return entityDamageByEntityEvent != null ? BukkitEventKt.getAttacker((EntityDamageByEntityEvent)entityDamageByEntityEvent) : null;
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return String.valueOf(it.getDeathMessage());
    }

    private static final Object _init_$lambda$4(PlayerDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        return String.valueOf(e.getDeathMessage());
    }

    static {
        INSTANCE.handler(IPlayerDeath::_init_$lambda$0);
        INSTANCE.addCondition("weapon", "ItemStack", IPlayerDeath::_init_$lambda$1);
        INSTANCE.addCondition("attacker", "Entity", IPlayerDeath::_init_$lambda$2);
        INSTANCE.addCondition("message", "String", IPlayerDeath::_init_$lambda$3);
        INSTANCE.addConditionVariable("message", IPlayerDeath::_init_$lambda$4);
    }
}

