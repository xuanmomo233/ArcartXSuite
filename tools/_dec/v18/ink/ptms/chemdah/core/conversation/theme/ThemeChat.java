/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyParticle
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common.util.Location
 *  ink.ptms.chemdah.taboolib.common5.util.StringQualifyKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.chat.Components
 *  ink.ptms.chemdah.taboolib.module.chat.RawMessage
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLocationKt
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
 *  org.bukkit.command.CommandSender
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
import ink.ptms.chemdah.taboolib.common.platform.ProxyParticle;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common.util.Location;
import ink.ptms.chemdah.taboolib.common5.util.StringQualifyKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.chat.Components;
import ink.ptms.chemdah.taboolib.module.chat.RawMessage;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLocationKt;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.StringKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
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
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\b\u00c6\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u00012\u00020\u0003B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0004JL\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\r2\u0006\u0010\u0011\u001a\u00020\r2\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\fH\u0002J>\u0010\u0014\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u0010\u001a\u00020\r2\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\f2\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u0016H\u0002J\b\u0010\u0018\u001a\u00020\u0002H\u0016J\u0018\u0010\u0019\u001a\u00020\r2\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\u001a\u001a\u00020\u0013H\u0002J\b\u0010\u001b\u001a\u00020\nH\u0002J\u0016\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u001e0\u001d2\u0006\u0010\u0007\u001a\u00020\bH\u0016J\u0010\u0010\u001f\u001a\u00020\u00062\u0006\u0010 \u001a\u00020!H\u0003J,\u0010\"\u001a\b\u0012\u0004\u0012\u00020\u001e0\u001d2\u0006\u0010\u0007\u001a\u00020\b2\f\u0010#\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u0015\u001a\u00020\u0016H\u0016J\u0016\u0010$\u001a\b\u0012\u0004\u0012\u00020\u001e0\u001d2\u0006\u0010\u0007\u001a\u00020\bH\u0016J\f\u0010%\u001a\u00020\n*\u00020\nH\u0002JP\u0010&\u001a\u00020\u0006*\b\u0012\u0004\u0012\u00020\u001e0\u001d2\u0006\u0010\u0007\u001a\u00020\b2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u0011\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010'\u001a\u00020\u00162\u0006\u0010\u0015\u001a\u00020\u00162\b\b\u0002\u0010(\u001a\u00020\u0016\u00a8\u0006)"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeChat;", "Link/ptms/chemdah/core/conversation/theme/Theme;", "Link/ptms/chemdah/core/conversation/theme/ThemeChatSettings;", "Link/ptms/chemdah/core/conversation/theme/WorldSide;", "()V", "appendMessage", "", "session", "Link/ptms/chemdah/core/conversation/Session;", "mainJson", "Link/ptms/chemdah/taboolib/module/chat/RawMessage;", "messages", "", "", "lineIndex", "", "lineFormat", "printMessage", "replies", "Link/ptms/chemdah/core/conversation/PlayerReply;", "appendReply", "canReply", "", "animationStopped", "createConfig", "getReplyFormat", "reply", "newJson", "onBegin", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "onClosed", "e", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Closed;", "onDisplay", "message", "onReset", "fixed", "npcTalk", "stopAnimation", "noSpace", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nThemeChat.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ThemeChat.kt\nink/ptms/chemdah/core/conversation/theme/ThemeChat\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,361:1\n1549#2:362\n1620#2,3:363\n1864#2,2:366\n1864#2,3:368\n1866#2:371\n1864#2,3:373\n1864#2,3:376\n1#3:372\n*S KotlinDebug\n*F\n+ 1 ThemeChat.kt\nink/ptms/chemdah/core/conversation/theme/ThemeChat\n*L\n83#1:362\n83#1:363,3\n85#1:366,2\n86#1:368,3\n85#1:371\n223#1:373,3\n263#1:376,3\n*E\n"})
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
            ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)session.getPlayer());
            org.bukkit.Location location = session.getOrigin().clone().add(0.0, 0.5, 0.0);
            Intrinsics.checkNotNullExpressionValue((Object)location, (String)"session.origin.clone().add(0.0, 0.5, 0.0)");
            ProxyParticle.sendTo$default((ProxyParticle)ProxyParticle.CLOUD, (ProxyPlayer)proxyPlayer, (Location)BukkitLocationKt.toProxyLocation((org.bukkit.Location)location), null, (int)0, (double)0.0, null, (int)60, null);
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
        future.thenAccept(arg_0 -> ThemeChat.onDisplay$lambda$4((Function1)new Function1<Void, Unit>(session){
            final /* synthetic */ Session $session;
            {
                this.$session = $session;
                super(1);
            }

            public final void invoke(Void it) {
                this.$session.setNpcTalking(false);
            }
        }, arg_0));
        if (d == 0L) {
            future.complete(null);
        }
        return future;
    }

    public final void npcTalk(@NotNull CompletableFuture<Void> $this$npcTalk, @NotNull Session session, @NotNull List<String> messages, @NotNull String printMessage, int lineIndex, boolean stopAnimation, boolean canReply, boolean noSpace) {
        String help;
        Intrinsics.checkNotNullParameter($this$npcTalk, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        Intrinsics.checkNotNullParameter(messages, (String)"messages");
        Intrinsics.checkNotNullParameter((Object)printMessage, (String)"printMessage");
        FuturesKt.applyWithError(session.getConversation().getPlayerSide().checkReply(session), (Function1)new Function1<List<? extends PlayerReply>, Unit>(lineIndex, messages, stopAnimation, noSpace, session, canReply, $this$npcTalk, printMessage){
            final /* synthetic */ int $lineIndex;
            final /* synthetic */ List<String> $messages;
            final /* synthetic */ boolean $stopAnimation;
            final /* synthetic */ boolean $noSpace;
            final /* synthetic */ Session $session;
            final /* synthetic */ boolean $canReply;
            final /* synthetic */ CompletableFuture<Void> $this_npcTalk;
            final /* synthetic */ String $printMessage;
            {
                this.$lineIndex = $lineIndex;
                this.$messages = $messages;
                this.$stopAnimation = $stopAnimation;
                this.$noSpace = $noSpace;
                this.$session = $session;
                this.$canReply = $canReply;
                this.$this_npcTalk = $receiver;
                this.$printMessage = $printMessage;
                super(1);
            }

            /*
             * WARNING - void declaration
             */
            public final void invoke(@NotNull List<? extends PlayerReply> replies) {
                Intrinsics.checkNotNullParameter(replies, (String)"replies");
                boolean animationStopped = this.$lineIndex + 1 >= this.$messages.size() && this.$stopAnimation;
                RawMessage mainJson = this.$noSpace || animationStopped && QuestDevelopment.INSTANCE.hasTransmitMessages(this.$session.getPlayer()) && !this.$canReply ? ThemeChat.access$fixed(ThemeChat.INSTANCE, new RawMessage(null, 1, null)) : ThemeChat.access$newJson(ThemeChat.INSTANCE);
                try {
                    void $this$forEach$iv;
                    String[] stringArray;
                    void $this$mapTo$iv$iv;
                    Iterable $this$map$iv;
                    Iterable iterable = ((ThemeChatSettings)ThemeChat.INSTANCE.getSettings()).getFormat();
                    Session session = this.$session;
                    boolean $i$f$map = false;
                    void var8_8 = $this$map$iv;
                    Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    boolean $i$f$mapTo = false;
                    for (T item$iv$iv : $this$mapTo$iv$iv) {
                        void it;
                        String string = (String)item$iv$iv;
                        Collection collection = destination$iv$iv;
                        boolean bl = false;
                        KetherFunction ketherFunction = KetherFunction.INSTANCE;
                        stringArray = AdapterKt.adaptPlayer((Object)session.getPlayer());
                        List<String> list2 = UtilsForKetherKt.getNamespace();
                        collection.add(UtilKt.colored((String)KetherFunction.parse$default((KetherFunction)ketherFunction, (String)it, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)stringArray), null, (Function1)((Function1)new Function1<ScriptContext, Unit>(session){
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
                    $this$map$iv = mainFormat2;
                    session = this.$session;
                    List<String> list3 = this.$messages;
                    int n = this.$lineIndex;
                    String string = this.$printMessage;
                    boolean bl = this.$canReply;
                    boolean $i$f$forEach = false;
                    for (T element$iv : $this$forEach$iv) {
                        String format = (String)element$iv;
                        boolean bl2 = false;
                        if (StringsKt.contains$default((CharSequence)format, (CharSequence)"title", (boolean)false, (int)2, null)) {
                            stringArray = new Pair[]{TuplesKt.to((Object)"title", (Object)session.getTitle())};
                            mainJson.append(Components.parseSimpleToLegacyRaw$default((Components)Components.INSTANCE, (String)StringKt.replace(format, stringArray), null, (int)2, null)).newLine();
                            continue;
                        }
                        stringArray = new String[]{"npc_side", "npcSide"};
                        if (StringKt.contains(format, stringArray)) {
                            ThemeChat.access$appendMessage(ThemeChat.INSTANCE, session, mainJson, list3, n, format, string, replies);
                            continue;
                        }
                        if (StringsKt.contains$default((CharSequence)format, (CharSequence)"reply", (boolean)false, (int)2, null)) {
                            ThemeChat.access$appendReply(ThemeChat.INSTANCE, session, mainJson, format, replies, bl, animationStopped);
                            continue;
                        }
                        mainJson.append(Components.parseSimpleToLegacyRaw$default((Components)Components.INSTANCE, (String)format, null, (int)2, null)).newLine();
                    }
                }
                catch (Throwable ex) {
                    ex.printStackTrace();
                }
                if (animationStopped && !this.$canReply && QuestDevelopment.INSTANCE.getEnableMessageTransmit()) {
                    RawMessage.sendTo$default((RawMessage)ThemeChat.access$newJson(ThemeChat.INSTANCE), (ProxyCommandSender)AdapterKt.adaptCommandSender((Object)this.$session.getPlayer()), null, (int)2, null);
                    QuestDevelopment.INSTANCE.releaseTransmit(this.$session.getPlayer());
                }
                if (!((Collection)((ThemeChatSettings)ThemeChat.INSTANCE.getSettings()).getFormat()).isEmpty()) {
                    RawMessage.sendTo$default((RawMessage)mainJson, (ProxyCommandSender)AdapterKt.adaptCommandSender((Object)this.$session.getPlayer()), null, (int)2, null);
                }
                if (animationStopped) {
                    this.$this_npcTalk.complete(null);
                }
            }
        });
        String string = help = BukkitLangKt.asLangTextOrNull((CommandSender)((CommandSender)session.getPlayer()), (String)"theme-chat-help", (Object[])new Object[0]);
        boolean bl = string != null ? ((CharSequence)string).length() > 0 : false;
        if (bl) {
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

    private static final void onDisplay$lambda$4(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    public static final /* synthetic */ RawMessage access$fixed(ThemeChat $this, RawMessage $receiver) {
        return $this.fixed($receiver);
    }

    public static final /* synthetic */ RawMessage access$newJson(ThemeChat $this) {
        return $this.newJson();
    }

    public static final /* synthetic */ void access$appendMessage(ThemeChat $this, Session session, RawMessage mainJson, List messages, int lineIndex, String lineFormat, String printMessage, List replies) {
        $this.appendMessage(session, mainJson, messages, lineIndex, lineFormat, printMessage, replies);
    }

    public static final /* synthetic */ void access$appendReply(ThemeChat $this, Session session, RawMessage mainJson, String lineFormat, List replies, boolean canReply, boolean animationStopped) {
        $this.appendReply(session, mainJson, lineFormat, replies, canReply, animationStopped);
    }
}

