/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.util.UtilsKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  kotlin.Deprecated
 *  kotlin.Metadata
 *  kotlin.ReplaceWith
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.core.conversation.AgentType;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Source;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.StringKt;
import ink.ptms.chemdah.util.UtilsKt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Deprecated;
import kotlin.Metadata;
import kotlin.ReplaceWith;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
 * Illegal identifiers - consider using --renameillegalidents true
 */
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000z\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0007\n\u0002\u0010\u0006\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0015\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\u0015\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\b\u0003\b\u0016\u0018\u00002\u00020\u0001B3\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\n\u0010\t\u001a\u0006\u0012\u0002\b\u00030\n\u00a2\u0006\u0002\u0010\u000bBG\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\n\u0010\t\u001a\u0006\u0012\u0002\b\u00030\n\u0012\u0014\u0010\f\u001a\u0010\u0012\u0004\u0012\u00020\u000e\u0012\u0006\u0012\u0004\u0018\u00010\u00010\r\u00a2\u0006\u0002\u0010\u000fJ\u0018\u0010V\u001a\b\u0012\u0004\u0012\u00020X0W2\b\b\u0002\u0010Y\u001a\u00020\u001dH\u0016J(\u0010Z\u001a\u00020[2\u0006\u0010\\\u001a\u00020\u00032\u0016\b\u0002\u0010]\u001a\u0010\u0012\u0004\u0012\u00020\u000e\u0012\u0006\u0012\u0004\u0018\u00010\u00010^H\u0016J\b\u0010_\u001a\u00020[H\u0016J\u000e\u0010`\u001a\b\u0012\u0004\u0012\u00020X0WH\u0016R\u0011\u0010\u0010\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u0015\"\u0004\b\u0016\u0010\u0017R\u0011\u0010\u0018\u001a\u00020\u00198F\u00a2\u0006\u0006\u001a\u0004\b\u001a\u0010\u001bR$\u0010\u001e\u001a\u00020\u001d2\u0006\u0010\u001c\u001a\u00020\u001d@DX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001e\u0010\u001f\"\u0004\b \u0010!R\u001a\u0010\"\u001a\u00020\u001dX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\"\u0010\u001f\"\u0004\b#\u0010!R*\u0010%\u001a\u00020\u001d2\u0006\u0010$\u001a\u00020\u001d8F@FX\u0087\u000e\u00a2\u0006\u0012\u0012\u0004\b&\u0010'\u001a\u0004\b%\u0010\u001f\"\u0004\b(\u0010!R\u001a\u0010)\u001a\u00020\u001dX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b)\u0010\u001f\"\u0004\b*\u0010!R\u001a\u0010+\u001a\u00020\u001dX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b+\u0010\u001f\"\u0004\b,\u0010!R\u0014\u0010-\u001a\u00020\u001d8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b-\u0010\u001fR\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b.\u0010/\"\u0004\b0\u00101R*\u00102\u001a\u0012\u0012\u0004\u0012\u00020\u000e03j\b\u0012\u0004\u0012\u00020\u000e`4X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b5\u00106\"\u0004\b7\u00108R\u001a\u00109\u001a\u00020\u001dX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b:\u0010\u001f\"\u0004\b;\u0010!R\u001a\u0010\u0006\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b<\u0010/\"\u0004\b=\u00101R\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b>\u0010?R*\u0010@\u001a\u0012\u0012\u0004\u0012\u00020A03j\b\u0012\u0004\u0012\u00020A`4X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bB\u00106\"\u0004\bC\u00108R\u001c\u0010D\u001a\u0004\u0018\u00010AX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bE\u0010F\"\u0004\bG\u0010HR.\u0010I\u001a\u0004\u0018\u00010A2\b\u0010$\u001a\u0004\u0018\u00010A8F@FX\u0087\u000e\u00a2\u0006\u0012\u0012\u0004\bJ\u0010'\u001a\u0004\bK\u0010F\"\u0004\bL\u0010HR\u001e\u0010\t\u001a\u0006\u0012\u0002\b\u00030\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bM\u0010N\"\u0004\bO\u0010PR\u0011\u0010Q\u001a\u00020\u000e8F\u00a2\u0006\u0006\u001a\u0004\bR\u0010SR\u001f\u0010\f\u001a\u0010\u0012\u0004\u0012\u00020\u000e\u0012\u0006\u0012\u0004\u0018\u00010\u00010\r\u00a2\u0006\b\n\u0000\u001a\u0004\bT\u0010U\u00a8\u0006a"}, d2={"Link/ptms/chemdah/core/conversation/Session;", "", "conversation", "Link/ptms/chemdah/core/conversation/Conversation;", "location", "Lorg/bukkit/Location;", "origin", "player", "Lorg/bukkit/entity/Player;", "source", "Link/ptms/chemdah/core/conversation/Source;", "(Link/ptms/chemdah/core/conversation/Conversation;Lorg/bukkit/Location;Lorg/bukkit/Location;Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/conversation/Source;)V", "variables", "", "", "(Link/ptms/chemdah/core/conversation/Conversation;Lorg/bukkit/Location;Lorg/bukkit/Location;Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/conversation/Source;Ljava/util/Map;)V", "beginTime", "", "getBeginTime", "()J", "getConversation", "()Link/ptms/chemdah/core/conversation/Conversation;", "setConversation", "(Link/ptms/chemdah/core/conversation/Conversation;)V", "distance", "", "getDistance", "()D", "<set-?>", "", "isClosed", "()Z", "setClosed", "(Z)V", "isFarewell", "setFarewell", "value", "isNext", "isNext$annotations", "()V", "setNext", "isSelected", "setSelected", "isTransitioning", "setTransitioning", "isValid", "getLocation", "()Lorg/bukkit/Location;", "setLocation", "(Lorg/bukkit/Location;)V", "npcSide", "Ljava/util/ArrayList;", "Lkotlin1822/collections/ArrayList;", "getNpcSide", "()Ljava/util/ArrayList;", "setNpcSide", "(Ljava/util/ArrayList;)V", "npcTalking", "getNpcTalking", "setNpcTalking", "getOrigin", "setOrigin", "getPlayer", "()Lorg/bukkit/entity/Player;", "playerReplyForDisplay", "Link/ptms/chemdah/core/conversation/PlayerReply;", "getPlayerReplyForDisplay", "setPlayerReplyForDisplay", "playerReplyOnCursor", "getPlayerReplyOnCursor", "()Link/ptms/chemdah/core/conversation/PlayerReply;", "setPlayerReplyOnCursor", "(Link/ptms/chemdah/core/conversation/PlayerReply;)V", "playerSide", "getPlayerSide$annotations", "getPlayerSide", "setPlayerSide", "getSource", "()Link/ptms/chemdah/core/conversation/Source;", "setSource", "(Link/ptms/chemdah/core/conversation/Source;)V", "title", "getTitle", "()Ljava/lang/String;", "getVariables", "()Ljava/util/Map;", "close", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "refuse", "goto", "", "next", "vars", "", "reload", "resetTheme", "Chemdah"})
public class Session {
    @NotNull
    private Conversation conversation;
    @NotNull
    private Location location;
    @NotNull
    private Location origin;
    @NotNull
    private final Player player;
    @NotNull
    private Source<?> source;
    @NotNull
    private final Map<String, Object> variables;
    @NotNull
    private ArrayList<String> npcSide;
    private boolean npcTalking;
    @Nullable
    private PlayerReply playerReplyOnCursor;
    @NotNull
    private ArrayList<PlayerReply> playerReplyForDisplay;
    private boolean isTransitioning;
    private boolean isClosed;
    private boolean isSelected;
    private boolean isFarewell;
    private final long beginTime;

    public Session(@NotNull Conversation conversation2, @NotNull Location location, @NotNull Location origin, @NotNull Player player, @NotNull Source<?> source, @NotNull Map<String, Object> variables2) {
        Intrinsics.checkNotNullParameter((Object)conversation2, (String)"conversation");
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        Intrinsics.checkNotNullParameter((Object)origin, (String)"origin");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter(source, (String)"source");
        Intrinsics.checkNotNullParameter(variables2, (String)"variables");
        this.conversation = conversation2;
        this.location = location;
        this.origin = origin;
        this.player = player;
        this.source = source;
        this.variables = variables2;
        this.npcSide = new ArrayList();
        this.playerReplyForDisplay = new ArrayList();
        this.beginTime = System.currentTimeMillis();
        this.variables.put("@Sender", this.player);
    }

    @NotNull
    public final Conversation getConversation() {
        return this.conversation;
    }

    public final void setConversation(@NotNull Conversation conversation2) {
        Intrinsics.checkNotNullParameter((Object)conversation2, (String)"<set-?>");
        this.conversation = conversation2;
    }

    @NotNull
    public final Location getLocation() {
        return this.location;
    }

    public final void setLocation(@NotNull Location location) {
        Intrinsics.checkNotNullParameter((Object)location, (String)"<set-?>");
        this.location = location;
    }

    @NotNull
    public final Location getOrigin() {
        return this.origin;
    }

    public final void setOrigin(@NotNull Location location) {
        Intrinsics.checkNotNullParameter((Object)location, (String)"<set-?>");
        this.origin = location;
    }

    @NotNull
    public final Player getPlayer() {
        return this.player;
    }

    @NotNull
    public final Source<?> getSource() {
        return this.source;
    }

    public final void setSource(@NotNull Source<?> source) {
        Intrinsics.checkNotNullParameter(source, (String)"<set-?>");
        this.source = source;
    }

    @NotNull
    public final Map<String, Object> getVariables() {
        return this.variables;
    }

    public Session(@NotNull Conversation conversation2, @NotNull Location location, @NotNull Location origin, @NotNull Player player, @NotNull Source<?> source) {
        Intrinsics.checkNotNullParameter((Object)conversation2, (String)"conversation");
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        Intrinsics.checkNotNullParameter((Object)origin, (String)"origin");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter(source, (String)"source");
        this(conversation2, location, origin, player, source, new HashMap());
    }

    public boolean isValid() {
        Session session = ConversationManager.INSTANCE.getSessions().get(this.player.getName());
        return session != null && session == this && session.conversation == this.conversation;
    }

    @NotNull
    public final String getTitle() {
        Object object = this.variables.get("title");
        if (object == null || (object = object.toString()) == null) {
            object = this.conversation.getOption().getTitle();
        }
        Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)this.source.getName())};
        return StringKt.replace((String)object, pairArray);
    }

    public final double getDistance() {
        Location location = this.player.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"player.location");
        return ink.ptms.adyeshach.core.util.UtilsKt.safeDistance((Location)this.origin, (Location)location) - ink.ptms.adyeshach.core.util.UtilsKt.safeDistance((Location)this.origin, (Location)this.location);
    }

    @NotNull
    public final ArrayList<String> getNpcSide() {
        return this.npcSide;
    }

    public final void setNpcSide(@NotNull ArrayList<String> arrayList) {
        Intrinsics.checkNotNullParameter(arrayList, (String)"<set-?>");
        this.npcSide = arrayList;
    }

    public final boolean getNpcTalking() {
        return this.npcTalking;
    }

    public final void setNpcTalking(boolean bl) {
        this.npcTalking = bl;
    }

    @Nullable
    public final PlayerReply getPlayerReplyOnCursor() {
        return this.playerReplyOnCursor;
    }

    public final void setPlayerReplyOnCursor(@Nullable PlayerReply playerReply) {
        this.playerReplyOnCursor = playerReply;
    }

    @NotNull
    public final ArrayList<PlayerReply> getPlayerReplyForDisplay() {
        return this.playerReplyForDisplay;
    }

    public final void setPlayerReplyForDisplay(@NotNull ArrayList<PlayerReply> arrayList) {
        Intrinsics.checkNotNullParameter(arrayList, (String)"<set-?>");
        this.playerReplyForDisplay = arrayList;
    }

    public final boolean isTransitioning() {
        return this.isTransitioning;
    }

    public final void setTransitioning(boolean bl) {
        this.isTransitioning = bl;
    }

    public final boolean isClosed() {
        return this.isClosed;
    }

    protected final void setClosed(boolean bl) {
        this.isClosed = bl;
    }

    public final boolean isSelected() {
        return this.isSelected;
    }

    public final void setSelected(boolean bl) {
        this.isSelected = bl;
    }

    public final boolean isFarewell() {
        return this.isFarewell;
    }

    public final void setFarewell(boolean bl) {
        this.isFarewell = bl;
    }

    public final long getBeginTime() {
        return this.beginTime;
    }

    @NotNull
    public CompletableFuture<Void> close(boolean refuse) {
        if (this.isClosed || UtilsKt.callIfFailed(new ConversationEvents.Close(this, refuse))) {
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
            return completableFuture;
        }
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        this.conversation.agent(this, refuse ? AgentType.REFUSE_ASYNC : AgentType.END_ASYNC);
        FuturesKt.acceptWithError(this.conversation.agent(this, refuse ? AgentType.REFUSE : AgentType.END), (Function0<Unit>)((Function0)new Function0<Unit>(this, future, refuse){
            final /* synthetic */ Session this$0;
            final /* synthetic */ CompletableFuture<Void> $future;
            final /* synthetic */ boolean $refuse;
            {
                this.this$0 = $receiver;
                this.$future = $future;
                this.$refuse = $refuse;
                super(0);
            }

            public final void invoke() {
                Object $this$cbool$iv = this.this$0.getVariables().get("@Cancelled");
                boolean $i$f$getCbool = false;
                if (Coerce.toBoolean((Object)$this$cbool$iv)) {
                    this.$future.complete(null);
                    return;
                }
                FuturesKt.acceptWithError(this.this$0.getConversation().getOption().getThemeInstance().onClose(this.this$0), (Function0<Unit>)((Function0)new Function0<Unit>(this.$future, this.this$0, this.$refuse){
                    final /* synthetic */ CompletableFuture<Void> $future;
                    final /* synthetic */ Session this$0;
                    final /* synthetic */ boolean $refuse;
                    {
                        this.$future = $future;
                        this.this$0 = $receiver;
                        this.$refuse = $refuse;
                        super(0);
                    }

                    public final void invoke() {
                        this.$future.complete(null);
                        this.this$0.setClosed(true);
                        ConversationManager.INSTANCE.getSessions().remove(this.this$0.getPlayer().getName());
                        new ConversationEvents.Closed(this.this$0, this.$refuse).call();
                    }
                }));
            }
        }));
        return future;
    }

    public static /* synthetic */ CompletableFuture close$default(Session session, boolean bl, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: close");
        }
        if ((n & 1) != 0) {
            bl = false;
        }
        return session.close(bl);
    }

    public void reload() {
        this.npcSide.clear();
        this.playerReplyOnCursor = null;
        this.playerReplyForDisplay.clear();
        this.isSelected = false;
        this.isFarewell = false;
        this.variables.put("@Sender", this.player);
    }

    @NotNull
    public CompletableFuture<Void> resetTheme() {
        return this.conversation.getTheme().onReset(this);
    }

    public void goto(@NotNull Conversation next, @NotNull Map<String, ? extends Object> vars2) {
        Intrinsics.checkNotNullParameter((Object)next, (String)"next");
        Intrinsics.checkNotNullParameter(vars2, (String)"vars");
        this.isTransitioning = true;
        this.conversation.agent(this, AgentType.GOTO);
        this.npcSide.clear();
        this.playerReplyOnCursor = null;
        this.playerReplyForDisplay.clear();
        this.variables.clear();
        this.variables.putAll(vars2);
        this.conversation = next;
    }

    public static /* synthetic */ void goto$default(Session session, Conversation conversation2, Map map, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: goto");
        }
        if ((n & 2) != 0) {
            map = MapsKt.emptyMap();
        }
        session.goto(conversation2, map);
    }

    @Nullable
    public final PlayerReply getPlayerSide() {
        return this.playerReplyOnCursor;
    }

    public final void setPlayerSide(@Nullable PlayerReply value2) {
        this.playerReplyOnCursor = value2;
    }

    @Deprecated(message="Use playerReplyOnCursor", replaceWith=@ReplaceWith(expression="playerReplyOnCursor", imports={}))
    public static /* synthetic */ void getPlayerSide$annotations() {
    }

    public final boolean isNext() {
        return this.isTransitioning;
    }

    public final void setNext(boolean value2) {
        this.isTransitioning = value2;
    }

    @Deprecated(message="Use isTransitioning", replaceWith=@ReplaceWith(expression="isTransitioning", imports={}))
    public static /* synthetic */ void isNext$annotations() {
    }
}

