/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.util.LazyMakerKt
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.citizensnpcs.api.CitizensAPI
 *  net.citizensnpcs.api.npc.NPC
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerInteractAtEntityEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation.trigger;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.trigger.TriggerCitizens;
import ink.ptms.chemdah.core.conversation.trigger.TriggerCitizensKt;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.util.debug.Debug;
import ink.ptms.chemdah.util.debug.DebugHandlerKt;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bH\u0007J\u0010\u0010\f\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\rH\u0007J\f\u0010\u000e\u001a\u0004\u0018\u00010\u000f*\u00020\u0010R\u001b\u0010\u0003\u001a\u00020\u00048FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0006\u0010\u0007\u001a\u0004\b\u0003\u0010\u0005\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/core/conversation/trigger/TriggerCitizens;", "", "()V", "isCitizensHooked", "", "()Z", "isCitizensHooked$delegate", "Lkotlin1822/Lazy;", "onBegin", "", "e", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Begin;", "onInteract", "Lorg/bukkit/event/player/PlayerInteractAtEntityEvent;", "toNPC", "Lnet/citizensnpcs/api/npc/NPC;", "Lorg/bukkit/entity/Entity;", "Chemdah"})
public final class TriggerCitizens {
    @NotNull
    public static final TriggerCitizens INSTANCE = new TriggerCitizens();
    @NotNull
    private static final Lazy isCitizensHooked$delegate = LazyMakerKt.unsafeLazy((Function0)isCitizensHooked.2.INSTANCE);

    private TriggerCitizens() {
    }

    public final boolean isCitizensHooked() {
        Lazy lazy = isCitizensHooked$delegate;
        return (Boolean)lazy.getValue();
    }

    @SubscribeEvent
    public final void onBegin(@NotNull ConversationEvents.Begin e) {
        Intrinsics.checkNotNullParameter((Object)((Object)e), (String)"e");
        if (!this.isCitizensHooked()) {
            return;
        }
        Object npc = e.getSession().getSource().getEntity();
        if (npc instanceof NPC) {
            String[] stringArray = new String[]{"LOOK_PLAYER"};
            if (e.getConversation().hasFlag(stringArray)) {
                ((NPC)npc).faceLocation(e.getSession().getPlayer().getLocation());
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public final void onInteract(@NotNull PlayerInteractAtEntityEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        if (!this.isCitizensHooked()) {
            return;
        }
        Player player2 = e.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
        Player player3 = player2;
        DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "Citizens \u4ea4\u4e92\u4e8b\u4ef6\uff0c\u5b9e\u4f53: " + e.getRightClicked().getType() + ", \u624b: " + e.getHand());
        if (e.getHand() == EquipmentSlot.HAND && e.getRightClicked().hasMetadata("NPC") && ChemdahAPI.INSTANCE.getConversationSession(player3) == null) {
            NPC nPC = CitizensAPI.getNPCRegistry().getNPC(e.getRightClicked());
            if (nPC == null) {
                return;
            }
            NPC npc = nPC;
            DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u8bc6\u522b\u5230 NPC: ID=" + npc.getId() + ", \u540d\u79f0=" + npc.getFullName());
            Player player4 = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player4, (String)"e.player");
            String[] stringArray = new String[]{String.valueOf(npc.getId())};
            Conversation conversation2 = ConversationManager.INSTANCE.getConversation(player4, "citizens", npc, stringArray);
            if (conversation2 != null) {
                DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u5339\u914d\u5230\u5bf9\u8bdd: " + conversation2.getId());
                e.setCancelled(true);
                Player player5 = e.getPlayer();
                Intrinsics.checkNotNullExpressionValue((Object)player5, (String)"e.player");
                Conversation.open$default(conversation2, player5, TriggerCitizensKt.createSource(npc), null, null, 12, null);
            } else {
                DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u672a\u627e\u5230\u5339\u914d\u7684\u5bf9\u8bdd");
            }
        } else if (e.getHand() != EquipmentSlot.HAND) {
            DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u526f\u624b\u4ea4\u4e92\uff0c\u5ffd\u7565");
        } else if (!e.getRightClicked().hasMetadata("NPC")) {
            DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u975e NPC \u5b9e\u4f53\uff0c\u5ffd\u7565");
        } else if (ChemdahAPI.INSTANCE.getConversationSession(player3) != null) {
            DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u73a9\u5bb6\u5df2\u6709\u6d3b\u52a8\u4f1a\u8bdd\uff0c\u963b\u6b62\u65b0\u5bf9\u8bdd");
            Session session = ChemdahAPI.INSTANCE.getConversationSession(player3);
            Intrinsics.checkNotNull((Object)session);
            Session session2 = session;
            DebugHandlerKt.debug((Entity)player3, Debug.CONVERSATION, "\u4f1a\u8bdd: " + session2.getConversation().getId() + ", \u6709\u6548: " + session2.isValid() + ", \u5df2\u5173\u95ed: " + session2.isClosed());
        }
    }

    @Nullable
    public final NPC toNPC(@NotNull Entity $this$toNPC) {
        Intrinsics.checkNotNullParameter((Object)$this$toNPC, (String)"<this>");
        return CitizensAPI.getNPCRegistry().getNPC($this$toNPC);
    }
}

