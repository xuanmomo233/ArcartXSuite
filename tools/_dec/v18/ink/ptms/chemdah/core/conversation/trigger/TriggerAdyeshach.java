/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  ink.ptms.adyeshach.core.entity.controller.Controller
 *  ink.ptms.adyeshach.core.event.AdyeshachEntityInteractEvent
 *  ink.ptms.adyeshach.impl.entity.controller.ControllerLookAtPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation.trigger;

import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.adyeshach.core.entity.controller.Controller;
import ink.ptms.adyeshach.core.event.AdyeshachEntityInteractEvent;
import ink.ptms.adyeshach.impl.entity.controller.ControllerLookAtPlayer;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.trigger.TriggerAdyeshachKt;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J\u0010\u0010\u0007\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\bH\u0007J\u0010\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\nH\u0007\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/core/conversation/trigger/TriggerAdyeshach;", "", "()V", "onAdyInteract", "", "e", "Link/ptms/adyeshach/core/event/AdyeshachEntityInteractEvent;", "onBegin", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Begin;", "onClosed", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Closed;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nTriggerAdyeshach.kt\nKotlin\n*S Kotlin\n*F\n+ 1 TriggerAdyeshach.kt\nink/ptms/chemdah/core/conversation/trigger/TriggerAdyeshach\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,170:1\n2624#2,3:171\n*S KotlinDebug\n*F\n+ 1 TriggerAdyeshach.kt\nink/ptms/chemdah/core/conversation/trigger/TriggerAdyeshach\n*L\n56#1:171,3\n*E\n"})
public final class TriggerAdyeshach {
    @NotNull
    public static final TriggerAdyeshach INSTANCE = new TriggerAdyeshach();

    private TriggerAdyeshach() {
    }

    @SubscribeEvent
    public final void onBegin(@NotNull ConversationEvents.Begin e) {
        Intrinsics.checkNotNullParameter((Object)((Object)e), (String)"e");
        Object npc = e.getSession().getSource().getEntity();
        if (npc instanceof EntityInstance) {
            ((EntityInstance)npc).setFreeze(true);
            ((EntityInstance)npc).setTag("conversation:" + e.getSession().getPlayer().getName(), (Object)"conversation");
            String[] stringArray = new String[]{"LOOK_PLAYER"};
            if (e.getConversation().hasFlag(stringArray)) {
                if (!((EntityInstance)npc).hasTag("conversation-eye-location")) {
                    ((EntityInstance)npc).setTag("conversation-eye-location", (Object)("" + ((EntityInstance)npc).getLocation().getYaw() + ',' + ((EntityInstance)npc).getLocation().getPitch()));
                }
                if (((EntityInstance)npc).getController(ControllerLookAtPlayer.class) == null) {
                    ((EntityInstance)npc).registerController((Controller)new ControllerLookAtPlayer((EntityInstance)npc, 8.0, 1.0));
                    ((EntityInstance)npc).setTag("conversation-controller", (Object)"true");
                }
            }
        }
    }

    @SubscribeEvent
    public final void onClosed(@NotNull ConversationEvents.Closed e) {
        Intrinsics.checkNotNullParameter((Object)((Object)e), (String)"e");
        Object npc = e.getSession().getSource().getEntity();
        if (npc instanceof EntityInstance) {
            boolean bl;
            String[] $this$none$iv;
            block7: {
                ((EntityInstance)npc).removeTag("conversation:" + e.getSession().getPlayer().getName());
                $this$none$iv = (String[])((EntityInstance)npc).getTags();
                boolean $i$f$none = false;
                if ($this$none$iv instanceof Collection && ((Collection)$this$none$iv).isEmpty()) {
                    bl = true;
                } else {
                    for (Object t : $this$none$iv) {
                        Map.Entry it = (Map.Entry)t;
                        boolean bl2 = false;
                        if (!Intrinsics.areEqual(it.getValue(), (Object)"conversation")) continue;
                        bl = false;
                        break block7;
                    }
                    bl = true;
                }
            }
            if (bl) {
                ((EntityInstance)npc).setFreeze(false);
                if (((EntityInstance)npc).hasTag("conversation-controller")) {
                    ((EntityInstance)npc).removeTag("conversation-controller");
                    ((EntityInstance)npc).unregisterController(ControllerLookAtPlayer.class);
                }
                $this$none$iv = new String[]{"LOOK_PLAYER"};
                if (e.getSession().getConversation().hasFlag($this$none$iv) && ((EntityInstance)npc).hasTag("conversation-eye-location")) {
                    Object object = ((EntityInstance)npc).getTag("conversation-eye-location");
                    Intrinsics.checkNotNull((Object)object);
                    String[] stringArray = new String[]{","};
                    List eye = StringsKt.split$default((CharSequence)object.toString(), (String[])stringArray, (boolean)false, (int)0, (int)6, null);
                    ((EntityInstance)npc).removeTag("conversation-eye-location");
                    EntityInstance.setHeadRotation$default((EntityInstance)((EntityInstance)npc), (float)Float.parseFloat((String)eye.get(0)), (float)Float.parseFloat((String)eye.get(1)), (boolean)false, (int)4, null);
                }
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public final void onAdyInteract(@NotNull AdyeshachEntityInteractEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        if (e.isMainHand() && ChemdahAPI.INSTANCE.getConversationSession(e.getPlayer()) == null) {
            String[] stringArray = new String[]{e.getEntity().getId()};
            Conversation conversation2 = ConversationManager.INSTANCE.getConversation(e.getPlayer(), "adyeshach", e.getEntity(), stringArray);
            if (conversation2 != null) {
                e.setCancelled(true);
                stringArray = new String[]{"NO_MOVE"};
                if (conversation2.hasFlag(stringArray) && !e.getPlayer().isOnGround()) {
                    return;
                }
                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(conversation2, e){
                    final /* synthetic */ Conversation $conversation;
                    final /* synthetic */ AdyeshachEntityInteractEvent $e;
                    {
                        this.$conversation = $conversation;
                        this.$e = $e;
                        super(1);
                    }

                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                        TriggerAdyeshachKt.openByAdyeshach(this.$conversation, this.$e.getPlayer(), this.$e.getEntity(), false);
                    }
                }), (int)15, null);
            }
        }
    }
}

