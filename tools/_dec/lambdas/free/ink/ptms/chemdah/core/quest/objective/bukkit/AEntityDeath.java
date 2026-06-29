/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityDeathEvent
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Abstract;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

@Abstract
@MetaInfo(name="\u5b9e\u4f53\u6b7b\u4ea1\u76ee\u6807", description={"\u5b9e\u4f53\u6b7b\u4ea1\u65f6\u89e6\u53d1", "\u652f\u6301\u5b9e\u4f53\u7c7b\u578b\u3001\u4f24\u5bb3\u503c\u3001\u6b7b\u56e0\u3001\u6389\u843d\u7269\u3001\u7ecf\u9a8c\u503c\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u51fb\u6740\u6570\u91cf"}, alias={"\u51fb\u6740", "\u6b7b\u4ea1", "\u6740\u6b7b"}, params={@ParamInfo(name="position", type="location", description="\u5b9e\u4f53\u6b7b\u4ea1\u7684\u4f4d\u7f6e"), @ParamInfo(name="damage", type="number", description="\u6700\u540e\u4e00\u6b21\u4f24\u5bb3\u503c"), @ParamInfo(name="damage:final", type="number", description="\u6700\u540e\u7684\u4f24\u5bb3\u503c\uff08\u8003\u8651\u62a4\u7532\u7b49\uff09"), @ParamInfo(name="cause", type="string", description="\u5b9e\u4f53\u6b7b\u4ea1\u7684\u539f\u56e0"), @ParamInfo(name="drops", type="itemstack", description="\u5b9e\u4f53\u6389\u843d\u7684\u7269\u54c1"), @ParamInfo(name="exp", type="number", description="\u5b9e\u4f53\u6389\u843d\u7684\u7ecf\u9a8c\u503c"), @ParamInfo(name="revive-health", type="number", description="\u5b9e\u4f53\u7684\u590d\u6d3b\u751f\u547d\u503c")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b'\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u0005\u00a2\u0006\u0002\u0010\u0004\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDeath;", "T", "Lorg/bukkit/event/entity/EntityDeathEvent;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "()V", "Chemdah"})
public abstract class AEntityDeath<T extends EntityDeathEvent>
extends ObjectiveCountableI<T> {
    public AEntityDeath() {
        this.addCondition("position", "Location", AEntityDeath::_init_$lambda$0);
        this.addCondition("damage", "Number", AEntityDeath::_init_$lambda$1);
        this.addCondition("damage:final", "Number", AEntityDeath::_init_$lambda$2);
        this.addCondition("cause", "String", AEntityDeath::_init_$lambda$3);
        this.addCondition("drops", "ItemStack", AEntityDeath::_init_$lambda$4);
        this.addCondition("exp", "Number", AEntityDeath::_init_$lambda$5);
        this.addCondition("revive-health", "Number", AEntityDeath::_init_$lambda$6);
        this.addConditionVariable("damage", AEntityDeath::_init_$lambda$7);
        this.addConditionVariable("damage:final", AEntityDeath::_init_$lambda$8);
        this.addConditionVariable("exp", AEntityDeath::_init_$lambda$9);
        this.addConditionVariable("revive-health", AEntityDeath::_init_$lambda$10);
    }

    private static final Object _init_$lambda$0(PlayerProfile playerProfile2, Task task, EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        EntityDamageEvent entityDamageEvent = it.getEntity().getLastDamageCause();
        return entityDamageEvent != null ? Double.valueOf(entityDamageEvent.getDamage()) : Double.valueOf(0.0);
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        EntityDamageEvent entityDamageEvent = it.getEntity().getLastDamageCause();
        return entityDamageEvent != null ? Double.valueOf(entityDamageEvent.getFinalDamage()) : Double.valueOf(0.0);
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        EntityDamageEvent entityDamageEvent = it.getEntity().getLastDamageCause();
        return String.valueOf(entityDamageEvent != null && (entityDamageEvent = entityDamageEvent.getCause()) != null ? entityDamageEvent.name() : null);
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDrops();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDroppedExp();
    }

    private static final Object _init_$lambda$6(PlayerProfile playerProfile2, Task task, EntityDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Object object = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)it, (String)"getReviveHealth", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
        Intrinsics.checkNotNull((Object)object);
        return object;
    }

    private static final Object _init_$lambda$7(EntityDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        EntityDamageEvent entityDamageEvent = e.getEntity().getLastDamageCause();
        return entityDamageEvent != null ? Double.valueOf(entityDamageEvent.getDamage()) : Double.valueOf(0.0);
    }

    private static final Object _init_$lambda$8(EntityDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        EntityDamageEvent entityDamageEvent = e.getEntity().getLastDamageCause();
        return entityDamageEvent != null ? Double.valueOf(entityDamageEvent.getFinalDamage()) : Double.valueOf(0.0);
    }

    private static final Object _init_$lambda$9(EntityDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        return e.getDroppedExp();
    }

    private static final Object _init_$lambda$10(EntityDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        Object object = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)e, (String)"getReviveHealth", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
        Intrinsics.checkNotNull((Object)object);
        return object;
    }
}

