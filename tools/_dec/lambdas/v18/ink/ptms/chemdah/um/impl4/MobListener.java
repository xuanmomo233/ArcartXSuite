/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Ghost
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  io.lumine.xikage.mythicmobs.MythicMobs
 *  io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDeathEvent
 *  io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobSpawnEvent
 *  io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent
 *  io.lumine.xikage.mythicmobs.mobs.ActiveMob
 *  kotlin.Metadata
 *  kotlin1822.Result
 *  kotlin1822.ResultKt
 *  kotlin1822.Unit
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.entity.LivingEntity
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.impl4;

import ink.ptms.chemdah.taboolib.common.platform.Ghost;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.um.Mob;
import ink.ptms.chemdah.um.MobType;
import ink.ptms.chemdah.um.event.MobDeathEvent;
import ink.ptms.chemdah.um.event.MobSpawnEvent;
import ink.ptms.chemdah.um.event.MythicReloadEvent;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDeathEvent;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobSpawnEvent;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Result;
import kotlin1822.ResultKt;
import kotlin1822.Unit;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J\u0010\u0010\u0007\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\bH\u0007J\u0010\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\nH\u0007\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/um/impl4/MobListener;", "", "()V", "onMobDeathEvent", "", "event", "Lio/lumine/xikage/mythicmobs/api/bukkit/events/MythicMobDeathEvent;", "onMobSpawnEvent", "Lio/lumine/xikage/mythicmobs/api/bukkit/events/MythicMobSpawnEvent;", "onMythicReloadEvent", "Lio/lumine/xikage/mythicmobs/api/bukkit/events/MythicReloadedEvent;", "implementation-v4"})
@SourceDebugExtension(value={"SMAP\nMobListener.kt\nKotlin\n*S Kotlin\n*F\n+ 1 MobListener.kt\nink/ptms/um/impl4/MobListener\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,49:1\n1#2:50\n*E\n"})
public final class MobListener {
    @NotNull
    public static final MobListener INSTANCE = new MobListener();

    private MobListener() {
    }

    @Ghost
    @SubscribeEvent
    public final void onMobDeathEvent(@NotNull MythicMobDeathEvent event) {
        Object object;
        Object $this$onMobDeathEvent_u24lambda_u240;
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        Object object2 = this;
        try {
            $this$onMobDeathEvent_u24lambda_u240 = object2;
            boolean bl = false;
            $this$onMobDeathEvent_u24lambda_u240 = Result.constructor-impl((Object)event.getMob());
        }
        catch (Throwable bl) {
            $this$onMobDeathEvent_u24lambda_u240 = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)bl));
        }
        object2 = $this$onMobDeathEvent_u24lambda_u240;
        Throwable throwable = Result.exceptionOrNull-impl((Object)object2);
        if (throwable == null) {
            object = object2;
        } else {
            ActiveMob activeMob;
            Throwable it = throwable;
            boolean bl = false;
            ActiveMob activeMob2 = activeMob = MythicMobs.inst().getMobManager().getMythicMobInstance(event.getEntity());
            if (activeMob2 == null) {
                return;
            }
            Intrinsics.checkNotNull((Object)activeMob2);
            object = activeMob;
        }
        ActiveMob activeMob = (ActiveMob)object;
        Intrinsics.checkNotNull((Object)activeMob);
        Mob mob = new ink.ptms.chemdah.um.impl4.Mob(activeMob);
        LivingEntity livingEntity = event.getKiller();
        List list2 = event.getDrops();
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getDrops(...)");
        event.setDrops(new MobDeathEvent(mob, livingEntity, list2).fire().getDrop());
    }

    @Ghost
    @SubscribeEvent
    public final void onMobSpawnEvent(@NotNull MythicMobSpawnEvent event) {
        Object object;
        Object $this$onMobSpawnEvent_u24lambda_u244;
        Object object2;
        Object object3;
        Object $this$onMobSpawnEvent_u24lambda_u242;
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        Object object4 = this;
        try {
            $this$onMobSpawnEvent_u24lambda_u242 = object4;
            boolean bl = false;
            $this$onMobSpawnEvent_u24lambda_u242 = Result.constructor-impl((Object)event.getMob());
        }
        catch (Throwable bl) {
            $this$onMobSpawnEvent_u24lambda_u242 = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)bl));
        }
        object4 = $this$onMobSpawnEvent_u24lambda_u242;
        Throwable throwable = Result.exceptionOrNull-impl((Object)object4);
        if (throwable == null) {
            object3 = object4;
        } else {
            Throwable it = throwable;
            boolean bl = false;
            ActiveMob activeMob = object2 = MythicMobs.inst().getMobManager().getMythicMobInstance(event.getEntity());
            if (activeMob == null) {
                return;
            }
            Intrinsics.checkNotNull((Object)activeMob);
            object3 = object2;
        }
        ActiveMob activeMob = (ActiveMob)object3;
        Intrinsics.checkNotNull((Object)activeMob);
        ink.ptms.chemdah.um.impl4.Mob mob4 = new ink.ptms.chemdah.um.impl4.Mob(activeMob);
        Object object5 = this;
        MobType mobType = mob4.getType();
        Mob mob = mob4;
        try {
            $this$onMobSpawnEvent_u24lambda_u244 = object5;
            boolean bl = false;
            $this$onMobSpawnEvent_u24lambda_u244 = Result.constructor-impl((Object)event.getMobLevel());
        }
        catch (Throwable bl) {
            $this$onMobSpawnEvent_u24lambda_u244 = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)bl));
        }
        Object object6 = $this$onMobSpawnEvent_u24lambda_u244;
        Mob mob2 = mob;
        MobType mobType2 = mobType;
        object5 = object6;
        Throwable throwable2 = Result.exceptionOrNull-impl((Object)object5);
        if (throwable2 == null) {
            object = object5;
        } else {
            $this$onMobSpawnEvent_u24lambda_u244 = throwable2;
            mobType = mobType2;
            mob = mob2;
            boolean bl = false;
            object6 = 0.0;
            mob2 = mob;
            mobType2 = mobType;
            object = object6;
        }
        double d = ((Number)object).doubleValue();
        MobType mobType3 = mobType2;
        Mob mob3 = mob2;
        MobSpawnEvent e = new MobSpawnEvent(mob3, mobType3, d).fire();
        object5 = this;
        try {
            MobListener $this$onMobSpawnEvent_u24lambda_u246 = (MobListener)object5;
            boolean bl = false;
            event.setMobLevel(e.getLevel());
            object2 = Result.constructor-impl((Object)Unit.INSTANCE);
        }
        catch (Throwable throwable3) {
            object2 = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)throwable3));
        }
        if (e.isCancelled()) {
            event.setCancelled();
        }
    }

    @Ghost
    @SubscribeEvent
    public final void onMythicReloadEvent(@NotNull MythicReloadedEvent event) {
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        new MythicReloadEvent().fire();
    }
}

