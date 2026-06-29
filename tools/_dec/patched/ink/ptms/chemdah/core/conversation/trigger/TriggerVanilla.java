/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  kotlin.Metadata
 *  kotlin1822.collections.ArraysKt
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
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.trigger.TriggerVanillaKt;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.util.debug.Debug;
import ink.ptms.chemdah.util.debug.DebugHandlerKt;
import java.util.Arrays;
import kotlin.Metadata;
import kotlin1822.collections.ArraysKt;
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
        Player player2 = e.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
        Player player3 = player2;
        DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "Vanilla \u4ea4\u4e92\u4e8b\u4ef6\uff0c\u5b9e\u4f53: " + e.getRightClicked().getType() + ", \u624b: " + e.getHand());
        if (e.getHand() == EquipmentSlot.HAND && ChemdahAPI.INSTANCE.getConversationSession(player3) == null) {
            Entity entity = e.getRightClicked();
            Intrinsics.checkNotNullExpressionValue((Object)entity, (String)"e.rightClicked");
            Object[] name = TriggerVanillaKt.getDisplayName(entity);
            DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u5b9e\u4f53\u540d\u79f0: " + ArraysKt.joinToString$default((Object[])name, null, null, null, (int)0, null, null, (int)63, null));
            Player player4 = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player4, (String)"e.player");
            Conversation conversation2 = ConversationManager.INSTANCE.getConversation(player4, "minecraft", e.getRightClicked(), (String[])Arrays.copyOf(name, name.length));
            if (conversation2 != null) {
                DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u5339\u914d\u5230\u5bf9\u8bdd: " + conversation2.getId());
                e.setCancelled(true);
                Player player5 = e.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player5, (String)"e.player");
                Entity entity2 = e.getRightClicked();
                Intrinsics.checkNotNullExpressionValue((Object)entity2, (String)"e.rightClicked");
                Conversation.open$default(conversation2, player5, TriggerVanillaKt.createSource(entity2), null, null, 12, null);
            } else {
                DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u672a\u627e\u5230\u5339\u914d\u7684\u5bf9\u8bdd");
            }
        } else if (e.getHand() != EquipmentSlot.HAND) {
            DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u526f\u624b\u4ea4\u4e92\uff0c\u5ffd\u7565");
        } else if (ChemdahAPI.INSTANCE.getConversationSession(player3) != null) {
            DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u73a9\u5bb6\u5df2\u6709\u6d3b\u52a8\u4f1a\u8bdd\uff0c\u963b\u6b62\u65b0\u5bf9\u8bdd");
            Session session = ChemdahAPI.INSTANCE.getConversationSession(player3);
            Intrinsics.checkNotNull((Object)session);
            Session session2 = session;
            DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u4f1a\u8bdd: " + session2.getConversation().getId() + ", \u6709\u6548: " + session2.isValid() + ", \u5df2\u5173\u95ed: " + session2.isClosed());
        }
    }
}

