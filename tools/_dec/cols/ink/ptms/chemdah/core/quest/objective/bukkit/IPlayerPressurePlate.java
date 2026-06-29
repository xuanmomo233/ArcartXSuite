/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerPressurePlate;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerInteractEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerPressurePlate
extends ObjectiveCountableI<PlayerInteractEvent> {
    @NotNull
    public static final IPlayerPressurePlate INSTANCE = new IPlayerPressurePlate();
    @NotNull
    private static final String name = "pressure plate";
    @NotNull
    private static final Class<PlayerInteractEvent> event = PlayerInteractEvent.class;

    private IPlayerPressurePlate() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerInteractEvent> getEvent() {
        return event;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private static final Player _init_$lambda$0(PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        if (it.getAction() != Action.PHYSICAL) return null;
        Block block = it.getClickedBlock();
        Object object = block;
        if (block == null) return null;
        Material material = object.getType();
        object = material;
        if (material == null) return null;
        String string = object.name();
        object = string;
        if (string == null) return null;
        if (!StringsKt.endsWith$default((String)object, (String)"PRESSURE_PLATE", (boolean)false, (int)2, null)) return null;
        boolean bl = true;
        if (!bl) return null;
        Player player = it.getPlayer();
        return player;
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerInteractEvent e) {
        InferArea inferArea = data2.toPosition();
        Block block = e.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        Location location = block.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.clickedBlock!!.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerInteractEvent e) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = e.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        return inferBlock.isBlock(block);
    }

    static {
        INSTANCE.handler(IPlayerPressurePlate::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerPressurePlate::_init_$lambda$1);
        INSTANCE.addSimpleCondition("material", IPlayerPressurePlate::_init_$lambda$2);
    }
}

