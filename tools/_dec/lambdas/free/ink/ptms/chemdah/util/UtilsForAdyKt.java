/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.entity.Controllable
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util;

import ink.ptms.adyeshach.core.entity.Controllable;
import ink.ptms.adyeshach.core.entity.EntityInstance;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u0016\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\u001a\u0018\u0010\u0000\u001a\u00020\u0001*\u00020\u00022\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a8\u0006\u0006"}, d2={"controllerMoveWithPathList", "", "Link/ptms/adyeshach/core/entity/EntityInstance;", "pathList", "", "Lorg/bukkit/Location;", "Chemdah"})
public final class UtilsForAdyKt {
    public static final void controllerMoveWithPathList(@NotNull EntityInstance $this$controllerMoveWithPathList, @NotNull List<? extends Location> pathList) {
        Intrinsics.checkNotNullParameter((Object)$this$controllerMoveWithPathList, (String)"<this>");
        Intrinsics.checkNotNullParameter(pathList, (String)"pathList");
        if (!$this$controllerMoveWithPathList.hasVehicle()) {
            Controllable.controllerMoveBy$default((Controllable)((Controllable)$this$controllerMoveWithPathList), pathList, (double)0.0, (boolean)false, (int)6, null);
        }
    }
}

