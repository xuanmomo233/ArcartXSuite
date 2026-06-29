/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.event.AdyeshachEntityDamageEvent
 *  ink.ptms.adyeshach.core.event.AdyeshachEntityInteractEvent
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherMathKt
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt
 *  ink.ptms.chemdah.taboolib.module.nms.Packet
 *  ink.ptms.chemdah.taboolib.module.nms.PacketSendEvent
 *  ink.ptms.chemdah.taboolib.platform.event.PlayerWorldContactEvent
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.ranges.RangesKt
 *  net.md_5.bungee.api.chat.BaseComponent
 *  net.md_5.bungee.api.chat.TextComponent
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 *  org.bukkit.event.player.PlayerDropItemEvent
 *  org.bukkit.event.player.PlayerItemHeldEvent
 *  org.bukkit.event.player.PlayerSwapHandItemsEvent
 *  org.bukkit.event.player.PlayerToggleSneakEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation.theme;

import ink.ptms.adyeshach.core.event.AdyeshachEntityDamageEvent;
import ink.ptms.adyeshach.core.event.AdyeshachEntityInteractEvent;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.core.conversation.theme.ThemeChat;
import ink.ptms.chemdah.core.conversation.theme.ThemeChatSettings;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherMathKt;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt;
import ink.ptms.chemdah.taboolib.module.nms.Packet;
import ink.ptms.chemdah.taboolib.module.nms.PacketSendEvent;
import ink.ptms.chemdah.taboolib.platform.event.PlayerWorldContactEvent;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt;
import ink.ptms.chemdah.util.CollectionKt;
import ink.ptms.chemdah.util.FuturesKt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.ranges.RangesKt;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000P\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0003J\u0010\u0010\u0007\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\bH\u0003J\u0010\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\nH\u0003J\u0010\u0010\u000b\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\fH\u0003J\u0010\u0010\r\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u000eH\u0003J\u0010\u0010\u000f\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0010H\u0003J\u0010\u0010\u0011\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0012H\u0003J\u0010\u0010\u0013\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0014H\u0003J\u0010\u0010\u0015\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0016H\u0003J\u0010\u0010\u0017\u001a\u00020\u00042\u0006\u0010\u0018\u001a\u00020\u0019H\u0002\u00a8\u0006\u001a"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeChatListener;", "", "()V", "onChat", "", "e", "Lorg/bukkit/event/player/AsyncPlayerChatEvent;", "onContrast", "Link/ptms/chemdah/taboolib/platform/event/PlayerWorldContactEvent;", "onDamage", "Link/ptms/adyeshach/core/event/AdyeshachEntityDamageEvent;", "onDrop", "Lorg/bukkit/event/player/PlayerDropItemEvent;", "onInteract", "Link/ptms/adyeshach/core/event/AdyeshachEntityInteractEvent;", "onItemHeld", "Lorg/bukkit/event/player/PlayerItemHeldEvent;", "onPacketSend", "Link/ptms/chemdah/taboolib/module/nms/PacketSendEvent;", "onSneak", "Lorg/bukkit/event/player/PlayerToggleSneakEvent;", "onSwap", "Lorg/bukkit/event/player/PlayerSwapHandItemsEvent;", "selectReply", "session", "Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
public final class ThemeChatListener {
    @NotNull
    public static final ThemeChatListener INSTANCE = new ThemeChatListener();

    private ThemeChatListener() {
    }

    private final void selectReply(Session session) {
        block1: {
            block0: {
                if (!session.getNpcTalking()) break block0;
                String[] stringArray = new String[]{"NO_SKIP", "FORCE_DISPLAY"};
                if (!session.getConversation().noFlag(stringArray)) break block1;
                session.setNpcTalking(false);
                break block1;
            }
            PlayerReply cursor = session.getPlayerReplyOnCursor();
            Object object = cursor;
            if (object == null || (object = ((PlayerReply)object).check(session)) == null) break block1;
            FuturesKt.thenTrue(object, (Function0<Unit>)((Function0)new Function0<Unit>(cursor, session){
                final /* synthetic */ PlayerReply $cursor;
                final /* synthetic */ Session $session;
                {
                    this.$cursor = $cursor;
                    this.$session = $session;
                    super(0);
                }

                public final void invoke() {
                    this.$cursor.select(this.$session);
                }
            }));
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    private final void onItemHeld(PlayerItemHeldEvent e) {
        Player player2 = e.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
        Session session = ChemdahAPI.INSTANCE.getConversationSession(player2);
        if (session == null) {
            return;
        }
        Session session2 = session;
        if (Intrinsics.areEqual((Object)session2.getConversation().getOption().getTheme(), (Object)"chat")) {
            ArrayList<PlayerReply> replies;
            if (session2.getNpcTalking()) {
                String[] stringArray = new String[]{"NO_SKIP", "FORCE_DISPLAY"};
                if (session2.getConversation().hasFlag(stringArray)) {
                    e.setCancelled(true);
                    return;
                }
                session2.setNpcTalking(false);
            }
            if (!((Collection)(replies = session2.getPlayerReplyForDisplay())).isEmpty()) {
                int index = CollectionsKt.indexOf((List)replies, (Object)session2.getPlayerReplyOnCursor());
                int select2 = 0;
                if (((ThemeChatSettings)ThemeChat.INSTANCE.getSettings()).getUseScroll()) {
                    int slot = e.getPlayer().getInventory().getHeldItemSlot();
                    if (e.getNewSlot() > e.getPreviousSlot()) {
                        int n = slot != 0 || e.getNewSlot() != 8 ? index + 1 : (select2 = index != 0 ? index - 1 : replies.size() - 1);
                        if (select2 >= replies.size()) {
                            select2 = 0;
                        }
                    } else {
                        int n = slot != 8 || e.getNewSlot() != 0 ? index - 1 : (select2 = index != replies.size() - 1 ? index + 1 : 0);
                        if (select2 < 0) {
                            select2 = replies.size() - 1;
                        }
                    }
                    try {
                        Player player3 = e.getPlayer();
                        Intrinsics.checkNotNullExpressionValue((Object)player3, (String)"e.player");
                        Object[] objectArray = new Object[]{e.getPreviousSlot()};
                        Object object = Reflex.Companion.invokeConstructor(MinecraftServerUtilKt.nmsClass((String)"PacketPlayOutHeldItemSlot"), objectArray);
                        Intrinsics.checkNotNullExpressionValue((Object)object, (String)"nmsClass(\"PacketPlayOutH\u2026nstructor(e.previousSlot)");
                        MinecraftServerUtilKt.sendPacket((Player)player3, (Object)object);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    e.setCancelled(true);
                } else {
                    select2 = RangesKt.coerceAtMost((int)e.getNewSlot(), (int)(replies.size() - 1));
                }
                if (select2 != index) {
                    session2.setPlayerReplyOnCursor(replies.get(select2));
                    ((ThemeChatSettings)ThemeChat.INSTANCE.getSettings()).playSelectSound(session2);
                    ThemeChat.npcTalk$default(ThemeChat.INSTANCE, new CompletableFuture(), session2, session2.getNpcSide(), "", session2.getNpcSide().size(), true, !session2.isFarewell(), false, 64, null);
                }
            }
        }
    }

    @SubscribeEvent
    private final void onSwap(PlayerSwapHandItemsEvent e) {
        Player player2 = e.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
        Session session = ChemdahAPI.INSTANCE.getConversationSession(player2);
        if (session == null) {
            return;
        }
        Session session2 = session;
        Theme<?> theme = session2.getConversation().getTheme();
        if (theme instanceof ThemeChat && CollectionKt.has(((ThemeChatSettings)((ThemeChat)theme).getSettings()).getReplyInteract(), "SWAP")) {
            e.setCancelled(true);
            this.selectReply(session2);
        }
    }

    @SubscribeEvent
    private final void onContrast(PlayerWorldContactEvent e) {
        if (e.isLeftClick() || e.isRightClick()) {
            Session session = ChemdahAPI.INSTANCE.getConversationSession(e.getPlayer());
            if (session == null) {
                return;
            }
            Session session2 = session;
            Theme<?> theme = session2.getConversation().getTheme();
            if (theme instanceof ThemeChat) {
                if (e.isLeftClick() && CollectionKt.has(((ThemeChatSettings)((ThemeChat)theme).getSettings()).getReplyInteract(), "LEFT_CLICK")) {
                    e.setCancelled(true);
                    this.selectReply(session2);
                }
                if (e.isRightClick() && CollectionKt.has(((ThemeChatSettings)((ThemeChat)theme).getSettings()).getReplyInteract(), "RIGHT_CLICK")) {
                    e.setCancelled(true);
                    this.selectReply(session2);
                }
            }
        }
    }

    @SubscribeEvent
    private final void onDamage(AdyeshachEntityDamageEvent e) {
        Session session = ChemdahAPI.INSTANCE.getConversationSession(e.getPlayer());
        if (session == null) {
            return;
        }
        Session session2 = session;
        Theme<?> theme = session2.getConversation().getTheme();
        if (theme instanceof ThemeChat && CollectionKt.has(((ThemeChatSettings)((ThemeChat)theme).getSettings()).getReplyInteract(), "LEFT_CLICK")) {
            e.setCancelled(true);
            this.selectReply(session2);
        }
    }

    @SubscribeEvent
    private final void onInteract(AdyeshachEntityInteractEvent e) {
        Session session = ChemdahAPI.INSTANCE.getConversationSession(e.getPlayer());
        if (session == null) {
            return;
        }
        Session session2 = session;
        Theme<?> theme = session2.getConversation().getTheme();
        if (theme instanceof ThemeChat && CollectionKt.has(((ThemeChatSettings)((ThemeChat)theme).getSettings()).getReplyInteract(), "RIGHT_CLICK")) {
            e.setCancelled(true);
            this.selectReply(session2);
        }
    }

    @SubscribeEvent
    private final void onDrop(PlayerDropItemEvent e) {
        Player player2 = e.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
        Session session = ChemdahAPI.INSTANCE.getConversationSession(player2);
        if (session == null) {
            return;
        }
        Session session2 = session;
        Theme<?> theme = session2.getConversation().getTheme();
        if (theme instanceof ThemeChat && CollectionKt.has(((ThemeChatSettings)((ThemeChat)theme).getSettings()).getReplyInteract(), "DROP")) {
            e.setCancelled(true);
            this.selectReply(session2);
        }
    }

    @SubscribeEvent
    private final void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking()) {
            Player player2 = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
            Session session = ChemdahAPI.INSTANCE.getConversationSession(player2);
            if (session == null) {
                return;
            }
            Session session2 = session;
            Theme<?> theme = session2.getConversation().getTheme();
            if (theme instanceof ThemeChat && CollectionKt.has(((ThemeChatSettings)((ThemeChat)theme).getSettings()).getReplyInteract(), "SNEAK_TOGGLE")) {
                e.setCancelled(true);
                this.selectReply(session2);
            }
        }
    }

    @SubscribeEvent
    private final void onChat(AsyncPlayerChatEvent e) {
        block3: {
            Player player2 = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"e.player");
            Session session = ChemdahAPI.INSTANCE.getConversationSession(player2);
            if (session == null) {
                return;
            }
            Session session2 = session;
            if (!(session2.getConversation().getTheme() instanceof ThemeChat) || session2.getNpcTalking()) break block3;
            String string = e.getMessage();
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.message");
            if (KetherMathKt.isInt((Object)string)) {
                e.setCancelled(true);
                PlayerReply playerReply = (PlayerReply)CollectionsKt.getOrNull((List)session2.getPlayerReplyForDisplay(), (int)(Coerce.toInteger((Object)e.getMessage()) - 1));
                if (playerReply != null) {
                    PlayerReply $this$onChat_u24lambda_u240 = playerReply;
                    boolean bl = false;
                    FuturesKt.thenTrue($this$onChat_u24lambda_u240.check(session2), (Function0<Unit>)((Function0)new Function0<Unit>($this$onChat_u24lambda_u240, session2){
                        final /* synthetic */ PlayerReply $this_run;
                        final /* synthetic */ Session $session;
                        {
                            this.$this_run = $receiver;
                            this.$session = $session;
                            super(0);
                        }

                        public final void invoke() {
                            this.$this_run.select(this.$session);
                        }
                    }));
                }
            }
        }
    }

    @SubscribeEvent
    private final void onPacketSend(PacketSendEvent e) {
        if (Intrinsics.areEqual((Object)e.getPacket().getNameInSpigot(), (Object)"PacketPlayOutChat") && Intrinsics.areEqual((Object)String.valueOf(Packet.read$default((Packet)e.getPacket(), (String)"b", (boolean)false, (int)2, null)), (Object)"GAME_INFO") && ChemdahAPI.INSTANCE.getConversationSession(e.getPlayer()) != null) {
            BaseComponent[] baseComponentArray = (BaseComponent[])Packet.read$default((Packet)e.getPacket(), (String)"components", (boolean)false, (int)2, null);
            if (baseComponentArray == null) {
                return;
            }
            BaseComponent[] components = baseComponentArray;
            String string = TextComponent.toPlainText((BaseComponent[])Arrays.copyOf(components, components.length));
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"toPlainText(*components)");
            String text2 = UtilKt.uncolored((String)string);
            if (!Intrinsics.areEqual((Object)text2, (Object)UtilKt.uncolored((String)BukkitLangKt.asLangText((CommandSender)((CommandSender)e.getPlayer()), (String)"theme-chat-help", (Object[])new Object[0])))) {
                e.setCancelled(true);
            }
        }
    }
}

