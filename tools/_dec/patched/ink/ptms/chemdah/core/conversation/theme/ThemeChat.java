/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common5.util.StringQualifyKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle
 *  ink.ptms.chemdah.taboolib.module.chat.Components
 *  ink.ptms.chemdah.taboolib.module.chat.RawMessage
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.Ref$BooleanRef
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.RangesKt
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation.theme;

import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.LineFormat;
import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.core.conversation.theme.ThemeChatSettings;
import ink.ptms.chemdah.core.conversation.theme.WorldSide;
import ink.ptms.chemdah.core.quest.QuestDevelopment;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common5.util.StringQualifyKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle;
import ink.ptms.chemdah.taboolib.module.chat.Components;
import ink.ptms.chemdah.taboolib.module.chat.RawMessage;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.StringKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import ink.ptms.chemdah.util.UtilsKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.Ref;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.RangesKt;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\n\b\u00c6\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u00012\u00020\u0003B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0004JL\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\r2\u0006\u0010\u0011\u001a\u00020\r2\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\fH\u0002J>\u0010\u0014\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u0010\u001a\u00020\r2\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\f2\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u0016H\u0002J\b\u0010\u0018\u001a\u00020\u0002H\u0016J\u0018\u0010\u0019\u001a\u00020\r2\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\u001a\u001a\u00020\u0013H\u0002J\b\u0010\u001b\u001a\u00020\nH\u0002J\u0016\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u001e0\u001d2\u0006\u0010\u0007\u001a\u00020\bH\u0016J\u0010\u0010\u001f\u001a\u00020\u00062\u0006\u0010 \u001a\u00020!H\u0003J,\u0010\"\u001a\b\u0012\u0004\u0012\u00020\u001e0\u001d2\u0006\u0010\u0007\u001a\u00020\b2\f\u0010#\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u0015\u001a\u00020\u0016H\u0016J\u0010\u0010$\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016J\u0016\u0010%\u001a\b\u0012\u0004\u0012\u00020\u001e0\u001d2\u0006\u0010\u0007\u001a\u00020\bH\u0016J\f\u0010&\u001a\u00020\n*\u00020\nH\u0002JP\u0010'\u001a\u00020\u0006*\b\u0012\u0004\u0012\u00020\u001e0\u001d2\u0006\u0010\u0007\u001a\u00020\b2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u0011\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010(\u001a\u00020\u00162\u0006\u0010\u0015\u001a\u00020\u00162\b\b\u0002\u0010)\u001a\u00020\u0016J^\u0010*\u001a\u00020\u0006*\b\u0012\u0004\u0012\u00020\u001e0\u001d2\u0006\u0010\u0007\u001a\u00020\b2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u0011\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00162\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\f2\u0006\u0010)\u001a\u00020\u0016H\u0002\u00a8\u0006+"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeChat;", "Link/ptms/chemdah/core/conversation/theme/Theme;", "Link/ptms/chemdah/core/conversation/theme/ThemeChatSettings;", "Link/ptms/chemdah/core/conversation/theme/WorldSide;", "()V", "appendMessage", "", "session", "Link/ptms/chemdah/core/conversation/Session;", "mainJson", "Link/ptms/chemdah/taboolib/module/chat/RawMessage;", "messages", "", "", "lineIndex", "", "lineFormat", "printMessage", "replies", "Link/ptms/chemdah/core/conversation/PlayerReply;", "appendReply", "canReply", "", "animationStopped", "createConfig", "getReplyFormat", "reply", "newJson", "onBegin", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "onClosed", "e", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Closed;", "onDisplay", "message", "onPostDisplay", "onReset", "fixed", "npcTalk", "stopAnimation", "noSpace", "renderNpcTalk", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nThemeChat.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ThemeChat.kt\nink/ptms/chemdah/core/conversation/theme/ThemeChat\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,374:1\n1549#2:375\n1620#2,3:376\n1864#2,2:379\n1864#2,3:381\n1866#2:384\n1549#2:385\n1620#2,3:386\n1855#2,2:389\n1864#2,3:392\n1864#2,3:395\n1#3:391\n*S KotlinDebug\n*F\n+ 1 ThemeChat.kt\nink/ptms/chemdah/core/conversation/theme/ThemeChat\n*L\n69#1:375\n69#1:376,3\n71#1:379,2\n72#1:381,3\n71#1:384\n184#1:385\n184#1:386,3\n188#1:389,2\n236#1:392,3\n276#1:395,3\n*E\n"})
public final class ThemeChat
extends Theme<ThemeChatSettings>
implements WorldSide {
    @NotNull
    public static final ThemeChat INSTANCE = new ThemeChat();

    private ThemeChat() {
    }

    @Override
    @NotNull
    public ThemeChatSettings createConfig() {
        ConfigurationSection configurationSection = ConversationManager.INSTANCE.getConf().getConfigurationSection("theme-chat");
        Intrinsics.checkNotNull((Object)configurationSection);
        return new ThemeChatSettings(configurationSection);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> onReset(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        return this.createDisplay(session, arg_0 -> ThemeChat.onReset$lambda$0(session, arg_0));
    }

    @Override
    @NotNull
    public CompletableFuture<Void> onBegin(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        String[] stringArray = new String[]{"NO_EFFECT:PARTICLE"};
        if (session.getConversation().noFlag(stringArray)) {
            Player player2 = session.getPlayer();
            Particle particle = XParticle.CLOUD.get();
            Intrinsics.checkNotNull((Object)particle);
            Location location = session.getOrigin().clone().add(0.0, 0.5, 0.0);
            Intrinsics.checkNotNullExpressionValue((Object)location, (String)"session.origin.clone().add(0.0, 0.5, 0.0)");
            UtilsKt.sendTo$default(player2, particle, location, null, 0, 0.0, null, 60, null);
        }
        return super.onBegin(session);
    }

    /*
     * WARNING - void declaration
     */
    @Override
    @NotNull
    public CompletableFuture<Void> onDisplay(@NotNull Session session, @NotNull List<String> message2, boolean canReply) {
        void $this$mapTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        Intrinsics.checkNotNullParameter(message2, (String)"message");
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        long d = 0L;
        Ref.BooleanRef isCancelled = new Ref.BooleanRef();
        session.setNpcTalking(true);
        Iterable $this$map$iv = UtilKt.colored(message2);
        boolean $i$f$map = false;
        Iterable iterable = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            String string = (String)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(((ThemeChatSettings)INSTANCE.getSettings()).getAnimation() ? StringQualifyKt.printed((String)it, (String)"_") : CollectionsKt.listOf((Object)it));
        }
        List messageAnimated = (List)destination$iv$iv;
        Iterable $this$forEachIndexed$iv = messageAnimated;
        boolean $i$f$forEachIndexed = false;
        int index$iv = 0;
        for (Object item$iv : $this$forEachIndexed$iv) {
            void messageText;
            int n;
            Object item$iv$iv;
            if ((n = index$iv++) < 0) {
                CollectionsKt.throwIndexOverflow();
            }
            item$iv$iv = (List)item$iv;
            int index = n;
            boolean bl = false;
            Iterable $this$forEachIndexed$iv2 = (Iterable)messageText;
            boolean $i$f$forEachIndexed2 = false;
            int index$iv2 = 0;
            for (Object item$iv2 : $this$forEachIndexed$iv2) {
                void printMessage;
                int n2;
                if ((n2 = index$iv2++) < 0) {
                    CollectionsKt.throwIndexOverflow();
                }
                String string = (String)item$iv2;
                int printIndex = n2;
                boolean bl2 = false;
                boolean stopAnimation = printIndex + 1 == messageText.size();
                long l = d;
                d = l + 1L;
                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)(((ThemeChatSettings)INSTANCE.getSettings()).getSpeed() * l), (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(session, future, message2, (String)printMessage, index, stopAnimation, canReply, isCancelled){
                    final /* synthetic */ Session $session;
                    final /* synthetic */ CompletableFuture<Void> $future;
                    final /* synthetic */ List<String> $message;
                    final /* synthetic */ String $printMessage;
                    final /* synthetic */ int $index;
                    final /* synthetic */ boolean $stopAnimation;
                    final /* synthetic */ boolean $canReply;
                    final /* synthetic */ Ref.BooleanRef $isCancelled;
                    {
                        this.$session = $session;
                        this.$future = $future;
                        this.$message = $message;
                        this.$printMessage = $printMessage;
                        this.$index = $index;
                        this.$stopAnimation = $stopAnimation;
                        this.$canReply = $canReply;
                        this.$isCancelled = $isCancelled;
                        super(1);
                    }

                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                        if (this.$session.isValid()) {
                            if (this.$session.getNpcTalking()) {
                                ThemeChat.npcTalk$default(ThemeChat.INSTANCE, this.$future, this.$session, this.$message, this.$printMessage, this.$index, this.$stopAnimation, this.$canReply, false, 64, null);
                            } else if (!this.$isCancelled.element) {
                                this.$isCancelled.element = true;
                                if (this.$session.isFarewell()) {
                                    QuestDevelopment.INSTANCE.releaseTransmit(this.$session.getPlayer());
                                }
                                ThemeChat.npcTalk$default(ThemeChat.INSTANCE, this.$future, this.$session, this.$session.getNpcSide(), "", 999, true, !this.$session.isFarewell(), false, 64, null);
                                this.$future.complete(null);
                            }
                        } else if (!this.$isCancelled.element) {
                            this.$isCancelled.element = true;
                            if (QuestDevelopment.INSTANCE.getEnableMessageTransmit()) {
                                RawMessage.sendTo$default((RawMessage)ThemeChat.access$newJson(ThemeChat.INSTANCE), (ProxyCommandSender)AdapterKt.adaptCommandSender((Object)this.$session.getPlayer()), null, (int)2, null);
                                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)1L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this.$session){
                                    final /* synthetic */ Session $session;
                                    {
                                        this.$session = $session;
                                        super(1);
                                    }

                                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                                        Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                                        QuestDevelopment.INSTANCE.releaseTransmit(this.$session.getPlayer());
                                    }
                                }), (int)11, null);
                            }
                            ThemeChat.INSTANCE.npcTalk(this.$future, this.$session, this.$message, this.$printMessage, this.$index, this.$stopAnimation, this.$canReply, true);
                            this.$future.complete(null);
                        }
                    }
                }), (int)11, null);
            }
        }
        if (d == 0L) {
            future.complete(null);
        }
        return future;
    }

    @Override
    public void onPostDisplay(@NotNull Session session) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        session.setNpcTalking(false);
    }

    public final void npcTalk(@NotNull CompletableFuture<Void> $this$npcTalk, @NotNull Session session, @NotNull List<String> messages, @NotNull String printMessage, int lineIndex, boolean stopAnimation, boolean canReply, boolean noSpace) {
        String help;
        boolean animationStopped;
        Intrinsics.checkNotNullParameter($this$npcTalk, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        Intrinsics.checkNotNullParameter(messages, (String)"messages");
        Intrinsics.checkNotNullParameter((Object)printMessage, (String)"printMessage");
        boolean bl = animationStopped = lineIndex + 1 >= messages.size() && stopAnimation;
        if (animationStopped) {
            FuturesKt.applyWithError(session.getConversation().getPlayerSide().checkReply(session), (Function1)new Function1<List<? extends PlayerReply>, Unit>($this$npcTalk, session, messages, printMessage, lineIndex, canReply, noSpace){
                final /* synthetic */ CompletableFuture<Void> $this_npcTalk;
                final /* synthetic */ Session $session;
                final /* synthetic */ List<String> $messages;
                final /* synthetic */ String $printMessage;
                final /* synthetic */ int $lineIndex;
                final /* synthetic */ boolean $canReply;
                final /* synthetic */ boolean $noSpace;
                {
                    this.$this_npcTalk = $receiver;
                    this.$session = $session;
                    this.$messages = $messages;
                    this.$printMessage = $printMessage;
                    this.$lineIndex = $lineIndex;
                    this.$canReply = $canReply;
                    this.$noSpace = $noSpace;
                    super(1);
                }

                public final void invoke(@NotNull List<? extends PlayerReply> replies) {
                    Intrinsics.checkNotNullParameter(replies, (String)"replies");
                    ThemeChat.access$renderNpcTalk(ThemeChat.INSTANCE, this.$this_npcTalk, this.$session, this.$messages, this.$printMessage, this.$lineIndex, this.$canReply, true, replies, this.$noSpace);
                }
            });
        } else {
            this.renderNpcTalk($this$npcTalk, session, messages, printMessage, lineIndex, canReply, false, (List<? extends PlayerReply>)session.getPlayerReplyForDisplay(), noSpace);
        }
        String string = help = BukkitLangKt.asLangTextOrNull((CommandSender)((CommandSender)session.getPlayer()), (String)"theme-chat-help", (Object[])new Object[0]);
        boolean bl2 = string != null ? ((CharSequence)string).length() > 0 : false;
        if (bl2) {
            AdapterKt.adaptPlayer((Object)session.getPlayer()).sendActionBar(help);
        }
    }

    public static /* synthetic */ void npcTalk$default(ThemeChat themeChat, CompletableFuture completableFuture, Session session, List list2, String string, int n, boolean bl, boolean bl2, boolean bl3, int n2, Object object) {
        if ((n2 & 0x40) != 0) {
            bl3 = false;
        }
        themeChat.npcTalk(completableFuture, session, list2, string, n, bl, bl2, bl3);
    }

    /*
     * WARNING - void declaration
     */
    private final void renderNpcTalk(CompletableFuture<Void> $this$renderNpcTalk, Session session, List<String> messages, String printMessage, int lineIndex, boolean canReply, boolean animationStopped, List<? extends PlayerReply> replies, boolean noSpace) {
        RawMessage mainJson = noSpace || animationStopped && QuestDevelopment.INSTANCE.hasTransmitMessages(session.getPlayer()) && !canReply ? this.fixed(new RawMessage(null, 1, null)) : this.newJson();
        try {
            void $this$mapTo$iv$iv;
            Iterable $this$map$iv = ((ThemeChatSettings)this.getSettings()).getFormat();
            boolean $i$f$map = false;
            Iterable iterable = $this$map$iv;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                void it;
                String string = (String)item$iv$iv;
                Collection collection = destination$iv$iv;
                boolean bl = false;
                KetherFunction ketherFunction = KetherFunction.INSTANCE;
                ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)session.getPlayer());
                List<String> list2 = UtilsForKetherKt.getNamespace();
                collection.add(UtilKt.colored((String)KetherFunction.parse$default((KetherFunction)ketherFunction, (String)it, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, (Function1)((Function1)new Function1<ScriptContext, Unit>(session){
                    final /* synthetic */ Session $session;
                    {
                        this.$session = $session;
                        super(1);
                    }

                    public final void invoke(@NotNull ScriptContext $this$parse) {
                        Intrinsics.checkNotNullParameter((Object)$this$parse, (String)"$this$parse");
                        KetherHelperKt.extend((ScriptContext)$this$parse, this.$session.getVariables());
                    }
                }), (int)42, null)));
            }
            List mainFormat2 = (List)destination$iv$iv;
            Iterable $this$forEach$iv = mainFormat2;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                String format = (String)element$iv;
                boolean bl = false;
                String[] stringArray = new String[]{"title"};
                if (StringKt.contains(format, stringArray)) {
                    stringArray = new Pair[]{TuplesKt.to((Object)"title", (Object)session.getTitle())};
                    mainJson.append(Components.parseSimpleToLegacyRaw$default((Components)Components.INSTANCE, (String)StringKt.replace(format, stringArray), null, (int)2, null)).newLine();
                    continue;
                }
                stringArray = new String[]{"npc_side", "npcSide"};
                if (StringKt.contains(format, stringArray)) {
                    INSTANCE.appendMessage(session, mainJson, messages, lineIndex, format, printMessage, replies);
                    continue;
                }
                stringArray = new String[]{"reply"};
                if (StringKt.contains(format, stringArray)) {
                    INSTANCE.appendReply(session, mainJson, format, replies, canReply, animationStopped);
                    continue;
                }
                mainJson.append(Components.parseSimpleToLegacyRaw$default((Components)Components.INSTANCE, (String)format, null, (int)2, null)).newLine();
            }
        }
        catch (Throwable ex) {
            ex.printStackTrace();
        }
        if (animationStopped && !canReply && QuestDevelopment.INSTANCE.getEnableMessageTransmit()) {
            RawMessage.sendTo$default((RawMessage)this.newJson(), (ProxyCommandSender)AdapterKt.adaptCommandSender((Object)session.getPlayer()), null, (int)2, null);
            QuestDevelopment.INSTANCE.releaseTransmit(session.getPlayer());
        }
        if (!((Collection)((ThemeChatSettings)this.getSettings()).getFormat()).isEmpty()) {
            RawMessage.sendTo$default((RawMessage)mainJson, (ProxyCommandSender)AdapterKt.adaptCommandSender((Object)session.getPlayer()), null, (int)2, null);
        }
        if (animationStopped) {
            $this$renderNpcTalk.complete(null);
        }
    }

    /*
     * WARNING - void declaration
     */
    private final void appendMessage(Session session, RawMessage mainJson, List<String> messages, int lineIndex, String lineFormat, String printMessage, List<? extends PlayerReply> replies) {
        LineFormat lineFormat2;
        LineFormat mlf = null;
        String string = session.getConversation().getFormat();
        if (string != null) {
            String it = string;
            boolean bl = false;
            lineFormat2 = ((ThemeChatSettings)INSTANCE.getSettings()).getFormatLine().get(it);
        } else {
            lineFormat2 = mlf = null;
        }
        if (mlf == null && ((ThemeChatSettings)this.getSettings()).getFormatLine().containsKey("default")) {
            LineFormat lineFormat3 = ((ThemeChatSettings)this.getSettings()).getFormatLine().get("default");
            Intrinsics.checkNotNull((Object)lineFormat3);
            mlf = lineFormat3;
        }
        Iterable $this$forEachIndexed$iv = UtilKt.colored(messages);
        boolean $i$f$forEachIndexed = false;
        int index$iv = 0;
        for (Object item$iv : $this$forEachIndexed$iv) {
            String newLine;
            String[] stringArray;
            int n;
            if ((n = index$iv++) < 0) {
                CollectionsKt.throwIndexOverflow();
            }
            String string2 = (String)item$iv;
            int i = n;
            boolean bl = false;
            if (lineIndex > i) {
                void fully;
                Object object = mlf;
                if (object == null || (object = ((LineFormat)object).format((String)fully, i, messages.size())) == null) {
                    object = fully;
                }
                LineFormat npcSide = object;
                stringArray = new String[]{"npc_side", "npcSide"};
                newLine = StringKt.replace(lineFormat, stringArray, StringsKt.replace$default((String)((Object)npcSide), (String)"\\n", (String)"\n", (boolean)false, (int)4, null));
                mainJson.append(Components.parseSimpleToLegacyRaw$default((Components)Components.INSTANCE, (String)newLine, null, (int)2, null)).newLine();
                continue;
            }
            if (lineIndex == i) {
                Object object = mlf;
                if (object == null || (object = ((LineFormat)object).format(printMessage, i, messages.size())) == null) {
                    object = printMessage;
                }
                Object pm = object;
                stringArray = new String[]{"npc_side", "npcSide"};
                newLine = StringKt.replace(lineFormat, stringArray, StringsKt.replace$default((String)pm, (String)"\\n", (String)"\n", (boolean)false, (int)4, null));
                mainJson.append(Components.parseSimpleToLegacyRaw$default((Components)Components.INSTANCE, (String)newLine, null, (int)2, null)).newLine();
                continue;
            }
            mainJson.newLine();
        }
        int rs = RangesKt.coerceAtLeast((int)replies.size(), (int)1);
        if (rs + messages.size() < ((ThemeChatSettings)this.getSettings()).getSpaceFilling()) {
            int n = ((ThemeChatSettings)this.getSettings()).getSpaceFilling() - (rs + messages.size());
            int n2 = 0;
            while (n2 < n) {
                int it = n2++;
                boolean bl = false;
                mainJson.newLine();
            }
        }
    }

    /*
     * WARNING - void declaration
     */
    private final void appendReply(Session session, RawMessage mainJson, String lineFormat, List<? extends PlayerReply> replies, boolean canReply, boolean animationStopped) {
        session.getPlayerReplyForDisplay().clear();
        session.getPlayerReplyForDisplay().addAll((Collection<PlayerReply>)replies);
        if (canReply) {
            int len = 0;
            Iterable $this$forEachIndexed$iv = replies;
            boolean $i$f$forEachIndexed = false;
            int index$iv = 0;
            for (Object item$iv : $this$forEachIndexed$iv) {
                void reply;
                int n;
                if ((n = index$iv++) < 0) {
                    CollectionsKt.throwIndexOverflow();
                }
                PlayerReply playerReply = (PlayerReply)item$iv;
                int idx = n;
                boolean bl = false;
                String text2 = reply.build(session);
                String[] stringArray = new String[]{"player_side", "playerSide"};
                String string = StringKt.replace(INSTANCE.getReplyFormat(session, (PlayerReply)reply), stringArray, StringsKt.replace$default((String)text2, (String)"\\n", (String)"\n", (boolean)false, (int)4, null));
                stringArray = new String[]{"index"};
                String replyText = StringKt.replace(string, stringArray, idx + 1);
                if (((ThemeChatSettings)INSTANCE.getSettings()).isSingleLineEnabled()) {
                    if ((len += StringKt.realLength(UtilKt.uncolored((String)text2))) >= ((ThemeChatSettings)INSTANCE.getSettings()).getSingleLineAutoSwap()) {
                        len = 0;
                        mainJson.newLine();
                    }
                    if (reply.isSwapLine() && idx > 0) {
                        mainJson.newLine();
                    }
                    if (animationStopped) {
                        stringArray = new Pair[]{TuplesKt.to((Object)"reply", (Object)replyText)};
                        mainJson.append(Components.parseSimpleToLegacyRaw$default((Components)Components.INSTANCE, (String)StringKt.replace(lineFormat, stringArray), null, (int)2, null)).runCommand("/session reply " + reply.getRid());
                        if (((ThemeChatSettings)INSTANCE.getSettings()).getHoverText()) {
                            mainJson.hoverText(text2);
                        }
                        if (idx + 1 >= replies.size()) continue;
                        String separator = Intrinsics.areEqual((Object)((ThemeChatSettings)INSTANCE.getSettings()).getSingleLineReplySeparator(), (Object)"\\n") ? "\n" : ((ThemeChatSettings)INSTANCE.getSettings()).getSingleLineReplySeparator();
                        mainJson.append(separator);
                        continue;
                    }
                    if (idx != 0) continue;
                    mainJson.append(((ThemeChatSettings)INSTANCE.getSettings()).getTalking());
                    continue;
                }
                if (animationStopped) {
                    stringArray = new Pair[]{TuplesKt.to((Object)"reply", (Object)replyText)};
                    mainJson.append(Components.parseSimpleToLegacyRaw$default((Components)Components.INSTANCE, (String)StringKt.replace(lineFormat, stringArray), null, (int)2, null)).runCommand("/session reply " + reply.getRid());
                    if (((ThemeChatSettings)INSTANCE.getSettings()).getHoverText()) {
                        mainJson.hoverText(text2);
                    }
                    mainJson.newLine();
                    continue;
                }
                if (idx == 0) {
                    mainJson.append(((ThemeChatSettings)INSTANCE.getSettings()).getTalking());
                }
                mainJson.newLine();
            }
            if (((ThemeChatSettings)this.getSettings()).isSingleLineEnabled()) {
                mainJson.newLine();
            }
        }
    }

    /*
     * WARNING - void declaration
     */
    private final RawMessage newJson() {
        RawMessage rawMessage;
        RawMessage rawMessage2 = rawMessage = new RawMessage(null, 1, null);
        ThemeChat themeChat = this;
        boolean bl = false;
        int n = ((ThemeChatSettings)INSTANCE.getSettings()).getSpaceLine();
        int n2 = 0;
        while (n2 < n) {
            void json;
            int it = n2++;
            boolean bl2 = false;
            json.newLine();
        }
        return themeChat.fixed(rawMessage);
    }

    private final RawMessage fixed(RawMessage $this$fixed) {
        return $this$fixed.append("\n").runCommand("/CHEMDAH!!d3486345-e35d-326a-b5c5-787de3814770!");
    }

    private final String getReplyFormat(Session session, PlayerReply reply) {
        ThemeChatSettings.ReplyFormat replyFormat;
        if (reply.getFormat() != null && ((ThemeChatSettings)this.getSettings()).getCustomSelect().containsKey(reply.getFormat())) {
            ThemeChatSettings.ReplyFormat replyFormat2 = ((ThemeChatSettings)this.getSettings()).getCustomSelect().get(reply.getFormat());
            Intrinsics.checkNotNull((Object)replyFormat2);
            replyFormat = replyFormat2;
        } else {
            replyFormat = reply.isPlayerSelected(session.getPlayer()) ? ((ThemeChatSettings)this.getSettings()).getSelected() : ((ThemeChatSettings)this.getSettings()).getSelect();
        }
        ThemeChatSettings.ReplyFormat format = replyFormat;
        return Intrinsics.areEqual((Object)session.getPlayerReplyOnCursor(), (Object)reply) ? format.getSelect() : format.getOther();
    }

    @SubscribeEvent
    private final void onClosed(ConversationEvents.Closed e) {
        if (!QuestDevelopment.INSTANCE.getEnableMessageTransmit()) {
            return;
        }
        if (e.getSession().getConversation().getTheme() instanceof ThemeChat && !e.getSession().getNpcTalking() && e.getRefuse()) {
            RawMessage.sendTo$default((RawMessage)this.newJson(), (ProxyCommandSender)AdapterKt.adaptCommandSender((Object)e.getSession().getPlayer()), null, (int)2, null);
            QuestDevelopment.INSTANCE.releaseTransmit(e.getSession().getPlayer());
        }
    }

    private static final void onReset$lambda$0(Session $session, List it) {
        Intrinsics.checkNotNullParameter((Object)$session, (String)"$session");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        if (!((ThemeChatSettings)INSTANCE.getSettings()).getUseScroll()) {
            $session.setPlayerReplyOnCursor((PlayerReply)CollectionsKt.getOrNull((List)it, (int)RangesKt.coerceAtMost((int)$session.getPlayer().getInventory().getHeldItemSlot(), (int)(it.size() - 1))));
        }
    }

    public static final /* synthetic */ void access$renderNpcTalk(ThemeChat $this, CompletableFuture $receiver, Session session, List messages, String printMessage, int lineIndex, boolean canReply, boolean animationStopped, List replies, boolean noSpace) {
        $this.renderNpcTalk($receiver, session, messages, printMessage, lineIndex, canReply, animationStopped, replies, noSpace);
    }

    public static final /* synthetic */ RawMessage access$newJson(ThemeChat $this) {
        return $this.newJson();
    }
}

