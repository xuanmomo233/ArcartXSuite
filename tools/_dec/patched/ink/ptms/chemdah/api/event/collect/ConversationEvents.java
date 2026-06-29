/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.Inventory
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.api.event.collect;

import ink.ptms.chemdah.core.conversation.AgentType;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.Option;
import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import java.io.File;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u000e\u0018\u00002\u00020\u0001:\f\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\r\u000eB\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents;", "", "()V", "Agent", "Begin", "Cancelled", "ChestThemeBuild", "Close", "Closed", "Load", "Post", "Pre", "ReplyClosed", "Select", "SelectReply", "Chemdah"})
public final class ConversationEvents {

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$Agent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "session", "Link/ptms/chemdah/core/conversation/Session;", "agentType", "Link/ptms/chemdah/core/conversation/AgentType;", "(Link/ptms/chemdah/core/conversation/Conversation;Link/ptms/chemdah/core/conversation/Session;Link/ptms/chemdah/core/conversation/AgentType;)V", "getAgentType", "()Link/ptms/chemdah/core/conversation/AgentType;", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class Agent
    extends BukkitProxyEvent {
        @NotNull
        private final Conversation conversation;
        @NotNull
        private final Session session;
        @NotNull
        private final AgentType agentType;

        public Agent(@NotNull Conversation conversation2, @NotNull Session session, @NotNull AgentType agentType) {
            Intrinsics.checkNotNullParameter((Object)conversation2, (String)"conversation");
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            Intrinsics.checkNotNullParameter((Object)((Object)agentType), (String)"agentType");
            this.conversation = conversation2;
            this.session = session;
            this.agentType = agentType;
        }

        @NotNull
        public final Conversation getConversation() {
            return this.conversation;
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        @NotNull
        public final AgentType getAgentType() {
            return this.agentType;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\n\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bR\u0014\u0010\t\u001a\u00020\u00078VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$Begin;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "session", "Link/ptms/chemdah/core/conversation/Session;", "relay", "", "(Link/ptms/chemdah/core/conversation/Conversation;Link/ptms/chemdah/core/conversation/Session;Z)V", "allowCancelled", "getAllowCancelled", "()Z", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "getRelay", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class Begin
    extends BukkitProxyEvent {
        @NotNull
        private final Conversation conversation;
        @NotNull
        private final Session session;
        private final boolean relay;

        public Begin(@NotNull Conversation conversation2, @NotNull Session session, boolean relay) {
            Intrinsics.checkNotNullParameter((Object)conversation2, (String)"conversation");
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            this.conversation = conversation2;
            this.session = session;
            this.relay = relay;
        }

        public /* synthetic */ Begin(Conversation conversation2, Session session, boolean bl, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 4) != 0) {
                bl = false;
            }
            this(conversation2, session, bl);
        }

        @NotNull
        public final Conversation getConversation() {
            return this.conversation;
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        public final boolean getRelay() {
            return this.relay;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\n\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bR\u0014\u0010\t\u001a\u00020\u00078VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$Cancelled;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "session", "Link/ptms/chemdah/core/conversation/Session;", "relay", "", "(Link/ptms/chemdah/core/conversation/Conversation;Link/ptms/chemdah/core/conversation/Session;Z)V", "allowCancelled", "getAllowCancelled", "()Z", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "getRelay", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class Cancelled
    extends BukkitProxyEvent {
        @NotNull
        private final Conversation conversation;
        @NotNull
        private final Session session;
        private final boolean relay;

        public Cancelled(@NotNull Conversation conversation2, @NotNull Session session, boolean relay) {
            Intrinsics.checkNotNullParameter((Object)conversation2, (String)"conversation");
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            this.conversation = conversation2;
            this.session = session;
            this.relay = relay;
        }

        public /* synthetic */ Cancelled(Conversation conversation2, Session session, boolean bl, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 4) != 0) {
                bl = false;
            }
            this(conversation2, session, bl);
        }

        @NotNull
        public final Conversation getConversation() {
            return this.conversation;
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        public final boolean getRelay() {
            return this.relay;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\t\u0018\u00002\u00020\u0001B+\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\t\u001a\u00020\n\u00a2\u0006\u0002\u0010\u000bR\u0014\u0010\f\u001a\u00020\b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u000eR\u0011\u0010\u0010\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0017\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019\u00a8\u0006\u001a"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$ChestThemeBuild;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "session", "Link/ptms/chemdah/core/conversation/Session;", "message", "", "", "canReply", "", "inventory", "Lorg/bukkit/inventory/Inventory;", "(Link/ptms/chemdah/core/conversation/Session;Ljava/util/List;ZLorg/bukkit/inventory/Inventory;)V", "allowCancelled", "getAllowCancelled", "()Z", "getCanReply", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "getInventory", "()Lorg/bukkit/inventory/Inventory;", "getMessage", "()Ljava/util/List;", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class ChestThemeBuild
    extends BukkitProxyEvent {
        @NotNull
        private final Session session;
        @NotNull
        private final List<String> message;
        private final boolean canReply;
        @NotNull
        private final Inventory inventory;
        @NotNull
        private final Conversation conversation;

        public ChestThemeBuild(@NotNull Session session, @NotNull List<String> message2, boolean canReply, @NotNull Inventory inventory) {
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            Intrinsics.checkNotNullParameter(message2, (String)"message");
            Intrinsics.checkNotNullParameter((Object)inventory, (String)"inventory");
            this.session = session;
            this.message = message2;
            this.canReply = canReply;
            this.inventory = inventory;
            this.conversation = this.session.getConversation();
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        @NotNull
        public final List<String> getMessage() {
            return this.message;
        }

        public final boolean getCanReply() {
            return this.canReply;
        }

        @NotNull
        public final Inventory getInventory() {
            return this.inventory;
        }

        @NotNull
        public final Conversation getConversation() {
            return this.conversation;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$Close;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "session", "Link/ptms/chemdah/core/conversation/Session;", "refuse", "", "(Link/ptms/chemdah/core/conversation/Session;Z)V", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "getRefuse", "()Z", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class Close
    extends BukkitProxyEvent {
        @NotNull
        private final Session session;
        private final boolean refuse;
        @NotNull
        private final Conversation conversation;

        public Close(@NotNull Session session, boolean refuse) {
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            this.session = session;
            this.refuse = refuse;
            this.conversation = this.session.getConversation();
        }

        public /* synthetic */ Close(Session session, boolean bl, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 2) != 0) {
                bl = false;
            }
            this(session, bl);
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        public final boolean getRefuse() {
            return this.refuse;
        }

        @NotNull
        public final Conversation getConversation() {
            return this.conversation;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\tR\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$Closed;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "session", "Link/ptms/chemdah/core/conversation/Session;", "refuse", "", "(Link/ptms/chemdah/core/conversation/Session;Z)V", "allowCancelled", "getAllowCancelled", "()Z", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "getRefuse", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class Closed
    extends BukkitProxyEvent {
        @NotNull
        private final Session session;
        private final boolean refuse;
        @NotNull
        private final Conversation conversation;

        public Closed(@NotNull Session session, boolean refuse) {
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            this.session = session;
            this.refuse = refuse;
            this.conversation = this.session.getConversation();
        }

        public /* synthetic */ Closed(Session session, boolean bl, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 2) != 0) {
                bl = false;
            }
            this(session, bl);
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        public final boolean getRefuse() {
            return this.refuse;
        }

        @NotNull
        public final Conversation getConversation() {
            return this.conversation;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\u0018\u00002\u00020\u0001B\u001f\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bR\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$Load;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "file", "Ljava/io/File;", "option", "Link/ptms/chemdah/core/conversation/Option;", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Ljava/io/File;Link/ptms/chemdah/core/conversation/Option;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getFile", "()Ljava/io/File;", "getOption", "()Link/ptms/chemdah/core/conversation/Option;", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "Chemdah"})
    public static final class Load
    extends BukkitProxyEvent {
        @Nullable
        private final File file;
        @NotNull
        private final Option option;
        @NotNull
        private final ConfigurationSection root;

        public Load(@Nullable File file, @NotNull Option option, @NotNull ConfigurationSection root2) {
            Intrinsics.checkNotNullParameter((Object)option, (String)"option");
            Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
            this.file = file;
            this.option = option;
            this.root = root2;
        }

        @Nullable
        public final File getFile() {
            return this.file;
        }

        @NotNull
        public final Option getOption() {
            return this.option;
        }

        @NotNull
        public final ConfigurationSection getRoot() {
            return this.root;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\n\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bR\u0014\u0010\t\u001a\u00020\u00078VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$Post;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "session", "Link/ptms/chemdah/core/conversation/Session;", "relay", "", "(Link/ptms/chemdah/core/conversation/Conversation;Link/ptms/chemdah/core/conversation/Session;Z)V", "allowCancelled", "getAllowCancelled", "()Z", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "getRelay", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class Post
    extends BukkitProxyEvent {
        @NotNull
        private final Conversation conversation;
        @NotNull
        private final Session session;
        private final boolean relay;

        public Post(@NotNull Conversation conversation2, @NotNull Session session, boolean relay) {
            Intrinsics.checkNotNullParameter((Object)conversation2, (String)"conversation");
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            this.conversation = conversation2;
            this.session = session;
            this.relay = relay;
        }

        public /* synthetic */ Post(Conversation conversation2, Session session, boolean bl, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 4) != 0) {
                bl = false;
            }
            this(conversation2, session, bl);
        }

        @NotNull
        public final Conversation getConversation() {
            return this.conversation;
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        public final boolean getRelay() {
            return this.relay;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\b\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$Pre;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "session", "Link/ptms/chemdah/core/conversation/Session;", "relay", "", "(Link/ptms/chemdah/core/conversation/Conversation;Link/ptms/chemdah/core/conversation/Session;Z)V", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "getRelay", "()Z", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class Pre
    extends BukkitProxyEvent {
        @NotNull
        private final Conversation conversation;
        @NotNull
        private final Session session;
        private final boolean relay;

        public Pre(@NotNull Conversation conversation2, @NotNull Session session, boolean relay) {
            Intrinsics.checkNotNullParameter((Object)conversation2, (String)"conversation");
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            this.conversation = conversation2;
            this.session = session;
            this.relay = relay;
        }

        public /* synthetic */ Pre(Conversation conversation2, Session session, boolean bl, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 4) != 0) {
                bl = false;
            }
            this(conversation2, session, bl);
        }

        @NotNull
        public final Conversation getConversation() {
            return this.conversation;
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        public final boolean getRelay() {
            return this.relay;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0014\u0010\u0005\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$ReplyClosed;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "session", "Link/ptms/chemdah/core/conversation/Session;", "(Link/ptms/chemdah/core/conversation/Session;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class ReplyClosed
    extends BukkitProxyEvent {
        @NotNull
        private final Session session;
        @NotNull
        private final Conversation conversation;

        public ReplyClosed(@NotNull Session session) {
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            this.session = session;
            this.conversation = this.session.getConversation();
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        @NotNull
        public final Conversation getConversation() {
            return this.conversation;
        }

        public boolean getAllowCancelled() {
            return false;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u000e\u0018\u00002\u00020\u0001B7\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u0007\u0012\b\u0010\b\u001a\u0004\u0018\u00010\t\u0012\b\u0010\n\u001a\u0004\u0018\u00010\u000b\u00a2\u0006\u0002\u0010\fR\u001c\u0010\b\u001a\u0004\u0018\u00010\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u0017\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0013\u0010\n\u001a\u0004\u0018\u00010\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$Select;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "namespace", "", "id", "", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "source", "", "(Lorg/bukkit/entity/Player;Ljava/lang/String;Ljava/util/List;Link/ptms/chemdah/core/conversation/Conversation;Ljava/lang/Object;)V", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "setConversation", "(Link/ptms/chemdah/core/conversation/Conversation;)V", "getId", "()Ljava/util/List;", "getNamespace", "()Ljava/lang/String;", "getPlayer", "()Lorg/bukkit/entity/Player;", "getSource", "()Ljava/lang/Object;", "Chemdah"})
    public static final class Select
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private final String namespace;
        @NotNull
        private final List<String> id;
        @Nullable
        private Conversation conversation;
        @Nullable
        private final Object source;

        public Select(@NotNull Player player2, @NotNull String namespace, @NotNull List<String> id2, @Nullable Conversation conversation2, @Nullable Object source) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)namespace, (String)"namespace");
            Intrinsics.checkNotNullParameter(id2, (String)"id");
            this.player = player2;
            this.namespace = namespace;
            this.id = id2;
            this.conversation = conversation2;
            this.source = source;
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
        }

        @NotNull
        public final String getNamespace() {
            return this.namespace;
        }

        @NotNull
        public final List<String> getId() {
            return this.id;
        }

        @Nullable
        public final Conversation getConversation() {
            return this.conversation;
        }

        public final void setConversation(@Nullable Conversation conversation2) {
            this.conversation = conversation2;
        }

        @Nullable
        public final Object getSource() {
            return this.source;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/api/event/collect/ConversationEvents$SelectReply;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "session", "Link/ptms/chemdah/core/conversation/Session;", "reply", "Link/ptms/chemdah/core/conversation/PlayerReply;", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/conversation/Session;Link/ptms/chemdah/core/conversation/PlayerReply;)V", "getPlayer", "()Lorg/bukkit/entity/Player;", "getReply", "()Link/ptms/chemdah/core/conversation/PlayerReply;", "getSession", "()Link/ptms/chemdah/core/conversation/Session;", "Chemdah"})
    public static final class SelectReply
    extends BukkitProxyEvent {
        @NotNull
        private final Player player;
        @NotNull
        private final Session session;
        @NotNull
        private final PlayerReply reply;

        public SelectReply(@NotNull Player player2, @NotNull Session session, @NotNull PlayerReply reply) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Intrinsics.checkNotNullParameter((Object)session, (String)"session");
            Intrinsics.checkNotNullParameter((Object)reply, (String)"reply");
            this.player = player2;
            this.session = session;
            this.reply = reply;
        }

        @NotNull
        public final Player getPlayer() {
            return this.player;
        }

        @NotNull
        public final Session getSession() {
            return this.session;
        }

        @NotNull
        public final PlayerReply getReply() {
            return this.reply;
        }
    }
}

