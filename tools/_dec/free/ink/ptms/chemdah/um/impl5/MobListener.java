/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Ghost
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  io.lumine.mythic.api.mobs.MythicMob
 *  io.lumine.mythic.bukkit.events.MythicMobDeathEvent
 *  io.lumine.mythic.bukkit.events.MythicMobSpawnEvent
 *  io.lumine.mythic.bukkit.events.MythicReloadedEvent
 *  io.lumine.mythic.core.mobs.ActiveMob
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.LivingEntity
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.common.platform.Ghost;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.um.event.MobDeathEvent;
import ink.ptms.chemdah.um.event.MobSpawnEvent;
import ink.ptms.chemdah.um.event.MythicReloadEvent;
import ink.ptms.chemdah.um.impl5.Mob;
import ink.ptms.chemdah.um.impl5.MobType;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.bukkit.events.MythicReloadedEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J\u0010\u0010\u0007\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\bH\u0007J\u0010\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\nH\u0007\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/um/impl5/MobListener;", "", "()V", "onMobDeathEvent", "", "event", "Lio/lumine/mythic/bukkit/events/MythicMobDeathEvent;", "onMobSpawnEvent", "Lio/lumine/mythic/bukkit/events/MythicMobSpawnEvent;", "onMythicReloadEvent", "Lio/lumine/mythic/bukkit/events/MythicReloadedEvent;", "implementation-v5"})
public final class MobListener {
    @NotNull
    public static final MobListener INSTANCE = new MobListener();

    private MobListener() {
    }

    @Ghost
    @SubscribeEvent
    public final void onMobDeathEvent(@NotNull MythicMobDeathEvent event) {
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        ActiveMob activeMob = event.getMob();
        Intrinsics.checkNotNullExpressionValue((Object)activeMob, (String)"getMob(...)");
        ink.ptms.chemdah.um.Mob mob = new Mob(activeMob);
        LivingEntity livingEntity = event.getKiller();
        List list2 = event.getDrops();
        Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"getDrops(...)");
        event.setDrops(new MobDeathEvent(mob, livingEntity, list2).fire().getDrop());
    }

    @Ghost
    @SubscribeEvent
    public final void onMobSpawnEvent(@NotNull MythicMobSpawnEvent event) {
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        ActiveMob activeMob = event.getMob();
        Intrinsics.checkNotNullExpressionValue((Object)activeMob, (String)"getMob(...)");
        ink.ptms.chemdah.um.Mob mob = new Mob(activeMob);
        MythicMob mythicMob = event.getMob().getType();
        Intrinsics.checkNotNullExpressionValue((Object)mythicMob, (String)"getType(...)");
        MobSpawnEvent e = new MobSpawnEvent(mob, new MobType(mythicMob), event.getMobLevel()).fire();
        event.setMobLevel(e.getLevel());
        event.setCancelled(e.isCancelled());
    }

    @Ghost
    @SubscribeEvent
    public final void onMythicReloadEvent(@NotNull MythicReloadedEvent event) {
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        new MythicReloadEvent().fire();
    }
}

