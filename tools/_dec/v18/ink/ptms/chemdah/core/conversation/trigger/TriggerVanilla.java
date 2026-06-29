/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerInteractAtEntityEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation.trigger;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.trigger.TriggerVanillaKt;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import java.util.Arrays;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/conversation/trigger/TriggerVanilla;", "", "()V", "onInteract", "", "e", "Lorg/bukkit/event/player/PlayerInteractAtEntityEvent;", "Chemdah"})
public final class TriggerVanilla {
    @NotNull
    public static final TriggerVanilla INSTANCE = new TriggerVanilla();

    private TriggerVanilla() {
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public final void onInteract(@NotNull PlayerInteractAtEntityEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        if (e.getHand() == EquipmentSlot.HAND) {
            Player player = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
            if (ChemdahAPI.INSTANCE.getConversationSession(player) == null) {
                Entity entity = e.getRightClicked();
                Intrinsics.checkNotNullExpressionValue((Object)entity, (String)"e.rightClicked");
                String[] name = TriggerVanillaKt.getDisplayName(entity);
                Player player2 = e.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
                Conversation conversation2 = ConversationManager.INSTANCE.getConversation(player2, "minecraft", e.getRightClicked(), Arrays.copyOf(name, name.length));
                if (conversation2 != null) {
                    e.setCancelled(true);
                    Player player3 = e.getPlayer();
                    Intrinsics.checkNotNullExpressionValue((Object)player3, (String)"e.player");
                    Entity entity2 = e.getRightClicked();
                    Intrinsics.checkNotNullExpressionValue((Object)entity2, (String)"e.rightClicked");
                    Conversation.open$default(conversation2, player3, TriggerVanillaKt.createSource(entity2), null, null, 12, null);
                }
            }
        }
    }
}

