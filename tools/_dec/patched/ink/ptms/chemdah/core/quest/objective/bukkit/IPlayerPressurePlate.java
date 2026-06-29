/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u538b\u529b\u677f\u8e29\u8e0f\u76ee\u6807", description={"\u73a9\u5bb6\u8e29\u8e0f\u538b\u529b\u677f\u65f6\u89e6\u53d1", "\u652f\u6301\u538b\u529b\u677f\u4f4d\u7f6e\u548c\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u8e29\u8e0f\u6b21\u6570"}, alias={"\u538b\u529b\u677f", "\u8e29\u8e0f", "\u89e6\u53d1\u538b\u529b\u677f"}, params={@ParamInfo(name="position", type="Location", description="\u538b\u529b\u677f\u4f4d\u7f6e"), @ParamInfo(name="material", type="Block", description="\u538b\u529b\u677f\u6750\u6599")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerPressurePlate;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerInteractEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerPressurePlate
extends ObjectiveCountableI<PlayerInteractEvent> {
    @NotNull
    public static final IPlayerPressurePlate INSTANCE = new IPlayerPressurePlate();
    @NotNull
    private static final String name = "pressure plate";

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
        return PlayerInteractEvent.class;
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
        Player player2 = it.getPlayer();
        return player2;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Block block = it.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        return block.getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Block block = it.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        return block;
    }

    static {
        INSTANCE.handler(IPlayerPressurePlate::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerPressurePlate::_init_$lambda$1);
        INSTANCE.addCondition("material", "Block", IPlayerPressurePlate::_init_$lambda$2);
    }
}

