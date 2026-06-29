/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerBucketEvent
 *  org.bukkit.inventory.ItemStack
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Abstract;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.inventory.ItemStack;

@Abstract
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b'\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u0005\u00a2\u0006\u0002\u0010\u0004\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/APlayerBucket;", "T", "Lorg/bukkit/event/player/PlayerBucketEvent;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "()V", "Chemdah"})
public abstract class APlayerBucket<T extends PlayerBucketEvent>
extends ObjectiveCountableI<T> {
    public APlayerBucket() {
        this.handler(APlayerBucket::_init_$lambda$0);
        this.addCondition("position", "Location", APlayerBucket::_init_$lambda$1);
        this.addCondition("material", "Block", APlayerBucket::_init_$lambda$2);
        this.addCondition("material:clicked", "Block", APlayerBucket::_init_$lambda$3);
        this.addCondition("item", "ItemStack", APlayerBucket::_init_$lambda$4);
        this.addCondition("item:bucket", "ItemStack", APlayerBucket::_init_$lambda$5);
        this.addCondition("face", "String", APlayerBucket::_init_$lambda$6);
        this.addCondition("hand", "String", APlayerBucket::_init_$lambda$7);
    }

    private static final Player _init_$lambda$0(PlayerBucketEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerBucketEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlockClicked().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerBucketEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlock();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerBucketEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlockClicked();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerBucketEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getItemStack();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return itemStack;
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, PlayerBucketEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return new ItemStack(it.getBucket());
    }

    private static final Object _init_$lambda$6(PlayerProfile playerProfile2, Task task, PlayerBucketEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlockFace().name();
    }

    private static final Object _init_$lambda$7(PlayerProfile playerProfile2, Task task, PlayerBucketEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return String.valueOf(Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)it, (String)"getHand", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null));
    }
}

