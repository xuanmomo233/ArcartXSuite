/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.bukkit.AEntityDamage;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerDamageByBlock;", "Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage;", "Lorg/bukkit/event/entity/EntityDamageByBlockEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerDamageByBlock
extends AEntityDamage<EntityDamageByBlockEvent> {
    @NotNull
    public static final IPlayerDamageByBlock INSTANCE = new IPlayerDamageByBlock();
    @NotNull
    private static final String name = "player damage by block";
    @NotNull
    private static final Class<EntityDamageByBlockEvent> event = EntityDamageByBlockEvent.class;

    private IPlayerDamageByBlock() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityDamageByBlockEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(EntityDamageByBlockEvent it) {
        Entity entity;
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDamager() != null ? ((entity = it.getEntity()) instanceof Player ? (Player)entity : null) : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityDamageByBlockEvent e) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = e.getDamager();
        Intrinsics.checkNotNull((Object)block);
        return inferBlock.isBlock(block);
    }

    static {
        INSTANCE.handler(IPlayerDamageByBlock::_init_$lambda$0);
        INSTANCE.addSimpleCondition("block", IPlayerDamageByBlock::_init_$lambda$1);
    }
}

