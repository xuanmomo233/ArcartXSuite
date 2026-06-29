/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  ink.ptms.chemdah.taboolib.module.ui.ClickEvent
 *  ink.ptms.chemdah.taboolib.module.ui.Menu
 *  ink.ptms.chemdah.taboolib.module.ui.MenuBuilderKt
 *  ink.ptms.chemdah.taboolib.module.ui.type.Basic
 *  ink.ptms.chemdah.taboolib.module.ui.type.Chest
 *  ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.Ref$BooleanRef
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation.theme;

import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.core.conversation.theme.ThemeChest;
import ink.ptms.chemdah.core.conversation.theme.ThemeChestSetting;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.taboolib.module.ui.ClickEvent;
import ink.ptms.chemdah.taboolib.module.ui.Menu;
import ink.ptms.chemdah.taboolib.module.ui.MenuBuilderKt;
import ink.ptms.chemdah.taboolib.module.ui.type.Basic;
import ink.ptms.chemdah.taboolib.module.ui.type.Chest;
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.StringKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import ink.ptms.chemdah.util.UtilsKt;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.Ref;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000P\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\b\u0010\u0004\u001a\u00020\u0005H\u0016J\b\u0010\u0006\u001a\u00020\u0002H\u0016J,\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u0006\u0010\n\u001a\u00020\u000b2\f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\r2\u0006\u0010\u000f\u001a\u00020\u0005H\u0016J\u001e\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00110\b2\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0011H\u0002J$\u0010\u0015\u001a\u00020\u0016*\u00020\u00162\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\u0011H\u0002J\"\u0010\u0015\u001a\u00020\u0016*\u00020\u00162\u0006\u0010\n\u001a\u00020\u000b2\f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\rH\u0002J\u0014\u0010\u001a\u001a\u00020\u000e*\u00020\u000e2\u0006\u0010\n\u001a\u00020\u000bH\u0002\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeChest;", "Link/ptms/chemdah/core/conversation/theme/Theme;", "Link/ptms/chemdah/core/conversation/theme/ThemeChestSetting;", "()V", "allowFarewell", "", "createConfig", "onDisplay", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "session", "Link/ptms/chemdah/core/conversation/Session;", "message", "", "", "canReply", "rows", "", "player", "Lorg/bukkit/entity/Player;", "size", "buildItem", "Lorg/bukkit/inventory/ItemStack;", "reply", "Link/ptms/chemdah/core/conversation/PlayerReply;", "index", "toTitle", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nThemeChest.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ThemeChest.kt\nink/ptms/chemdah/core/conversation/theme/ThemeChest\n+ 2 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,135:1\n9#2:136\n*S KotlinDebug\n*F\n+ 1 ThemeChest.kt\nink/ptms/chemdah/core/conversation/theme/ThemeChest\n*L\n128#1:136\n*E\n"})
public final class ThemeChest
extends Theme<ThemeChestSetting> {
    @NotNull
    public static final ThemeChest INSTANCE = new ThemeChest();

    private ThemeChest() {
    }

    @Override
    @NotNull
    public ThemeChestSetting createConfig() {
        ConfigurationSection configurationSection = ConversationManager.INSTANCE.getConf().getConfigurationSection("theme-chest");
        Intrinsics.checkNotNull((Object)configurationSection);
        return new ThemeChestSetting(configurationSection);
    }

    @Override
    public boolean allowFarewell() {
        return false;
    }

    @Override
    @NotNull
    public CompletableFuture<Void> onDisplay(@NotNull Session session, @NotNull List<String> message2, boolean canReply) {
        Intrinsics.checkNotNullParameter((Object)session, (String)"session");
        Intrinsics.checkNotNullParameter(message2, (String)"message");
        Ref.BooleanRef end = new Ref.BooleanRef();
        return this.createDisplay(session, arg_0 -> ThemeChest.onDisplay$lambda$1(session, message2, canReply, end, arg_0));
    }

    private final ItemStack buildItem(ItemStack $this$buildItem, Session session, PlayerReply reply, int index) {
        String icon;
        Object object = reply.getRoot().get("icon");
        String string = icon = object != null ? object.toString() : null;
        if (icon != null) {
            UtilsKt.setIcon($this$buildItem, icon);
        }
        String build2 = reply.build(session);
        return ItemModifierKt.modifyMeta((ItemStack)$this$buildItem, (Function1)((Function1)new Function1<ItemMeta, Unit>(index, build2, session){
            final /* synthetic */ int $index;
            final /* synthetic */ String $build;
            final /* synthetic */ Session $session;
            {
                this.$index = $index;
                this.$build = $build;
                this.$session = $session;
                super(1);
            }

            /*
             * WARNING - void declaration
             */
            public final void invoke(@NotNull ItemMeta $this$modifyMeta) {
                List list2;
                Intrinsics.checkNotNullParameter((Object)$this$modifyMeta, (String)"$this$modifyMeta");
                String string = $this$modifyMeta.getDisplayName();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"displayName");
                Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"index", (Object)String.valueOf(this.$index)), TuplesKt.to((Object)"player_side", (Object)this.$build), TuplesKt.to((Object)"playerSide", (Object)this.$build)};
                $this$modifyMeta.setDisplayName(StringKt.replace(string, pairArray));
                ItemMeta itemMeta = $this$modifyMeta;
                List list3 = $this$modifyMeta.getLore();
                if (list3 != null) {
                    void $this$mapTo$iv$iv;
                    void $this$map$iv;
                    Iterable iterable = list3;
                    int n = this.$index;
                    String string2 = this.$build;
                    Session session = this.$session;
                    ItemMeta itemMeta2 = itemMeta;
                    boolean $i$f$map = false;
                    void var8_9 = $this$map$iv;
                    Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    boolean $i$f$mapTo = false;
                    for (T item$iv$iv : $this$mapTo$iv$iv) {
                        void line;
                        String string3 = (String)item$iv$iv;
                        Collection collection = destination$iv$iv;
                        boolean bl = false;
                        Intrinsics.checkNotNullExpressionValue((Object)line, (String)"line");
                        KetherFunction ketherFunction = new KetherFunction[]{TuplesKt.to((Object)"index", (Object)String.valueOf(n)), TuplesKt.to((Object)"player_side", (Object)string2), TuplesKt.to((Object)"playerSide", (Object)string2)};
                        String str = StringKt.replace((String)line, ketherFunction);
                        ketherFunction = KetherFunction.INSTANCE;
                        ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)session.getPlayer());
                        List<String> list4 = UtilsForKetherKt.getNamespace();
                        collection.add(KetherFunction.parse$default((KetherFunction)ketherFunction, (String)str, (boolean)false, list4, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, null, (int)106, null));
                    }
                    list2 = (List)destination$iv$iv;
                    itemMeta = itemMeta2;
                } else {
                    list2 = null;
                }
                itemMeta.setLore(list2);
            }
        }));
    }

    private final ItemStack buildItem(ItemStack $this$buildItem, Session session, List<String> message2) {
        String icon = session.getConversation().getRoot().getString("npc icon");
        if (icon != null) {
            UtilsKt.setIcon($this$buildItem, icon);
        }
        return ItemModifierKt.modifyMeta((ItemStack)$this$buildItem, (Function1)((Function1)new Function1<ItemMeta, Unit>(session, message2){
            final /* synthetic */ Session $session;
            final /* synthetic */ List<String> $message;
            {
                this.$session = $session;
                this.$message = $message;
                super(1);
            }

            /*
             * WARNING - void declaration
             */
            public final void invoke(@NotNull ItemMeta $this$modifyMeta) {
                List list2;
                Intrinsics.checkNotNullParameter((Object)$this$modifyMeta, (String)"$this$modifyMeta");
                String string = $this$modifyMeta.getDisplayName();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"displayName");
                $this$modifyMeta.setDisplayName(ThemeChest.access$toTitle(ThemeChest.INSTANCE, string, this.$session));
                ItemMeta itemMeta = $this$modifyMeta;
                List list3 = $this$modifyMeta.getLore();
                if (list3 != null) {
                    void $this$flatMapTo$iv$iv;
                    void $this$flatMap$iv;
                    Iterable iterable = list3;
                    Session session = this.$session;
                    List<String> list4 = this.$message;
                    ItemMeta itemMeta2 = itemMeta;
                    boolean $i$f$flatMap = false;
                    void var6_7 = $this$flatMap$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$flatMapTo = false;
                    for (T element$iv$iv : $this$flatMapTo$iv$iv) {
                        List list5;
                        String line = (String)element$iv$iv;
                        boolean bl = false;
                        KetherFunction ketherFunction = KetherFunction.INSTANCE;
                        ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)session.getPlayer());
                        Iterable<String> iterable2 = UtilsForKetherKt.getNamespace();
                        Intrinsics.checkNotNullExpressionValue((Object)line, (String)"line");
                        String str = KetherFunction.parse$default((KetherFunction)ketherFunction, (String)line, (boolean)false, iterable2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, null, (int)106, null);
                        if (StringsKt.contains$default((CharSequence)str, (CharSequence)"npc_side", (boolean)false, (int)2, null) || StringsKt.contains$default((CharSequence)str, (CharSequence)"npcSide", (boolean)false, (int)2, null)) {
                            void $this$mapTo$iv$iv;
                            Iterable $this$map$iv = list4;
                            boolean $i$f$map = false;
                            iterable2 = $this$map$iv;
                            Collection destination$iv$iv2 = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                            boolean $i$f$mapTo = false;
                            for (T item$iv$iv : $this$mapTo$iv$iv) {
                                void it;
                                String string2 = (String)item$iv$iv;
                                Collection collection = destination$iv$iv2;
                                boolean bl2 = false;
                                Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"npc_side", (Object)it), TuplesKt.to((Object)"npcSide", (Object)it)};
                                collection.add(StringKt.replace(str, pairArray));
                            }
                            list5 = (List)destination$iv$iv2;
                        } else {
                            list5 = CollectionKt.asList((Object)ThemeChest.access$toTitle(ThemeChest.INSTANCE, str, session));
                        }
                        Iterable list$iv$iv = list5;
                        CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
                    }
                    list2 = (List)destination$iv$iv;
                    itemMeta = itemMeta2;
                } else {
                    list2 = null;
                }
                itemMeta.setLore(list2);
            }
        }));
    }

    private final String toTitle(String $this$toTitle, Session session) {
        KetherFunction ketherFunction = new Pair[1];
        ProxyPlayer proxyPlayer = new ProxyPlayer[]{TuplesKt.to((Object)"name", (Object)session.getSource().getName())};
        ketherFunction[0] = TuplesKt.to((Object)"title", (Object)StringKt.replace(session.getConversation().getOption().getTitle(), proxyPlayer));
        String str = UtilKt.colored((String)StringKt.replace($this$toTitle, ketherFunction));
        ketherFunction = KetherFunction.INSTANCE;
        proxyPlayer = AdapterKt.adaptPlayer((Object)session.getPlayer());
        List<String> list2 = UtilsForKetherKt.getNamespace();
        return KetherFunction.parse$default((KetherFunction)ketherFunction, (String)str, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, null, (int)106, null);
    }

    private final CompletableFuture<Integer> rows(Player player2, int size) {
        CompletionStage<Integer> completionStage;
        try {
            KetherShell ketherShell = KetherShell.INSTANCE;
            String string = ((ThemeChestSetting)this.getSettings()).getRows();
            ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)player2);
            List<String> list2 = UtilsForKetherKt.getNamespace();
            CompletionStage completionStage2 = KetherShell.eval$default((KetherShell)ketherShell, (String)string, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, (Function1)((Function1)new Function1<ScriptContext, Unit>(size){
                final /* synthetic */ int $size;
                {
                    this.$size = $size;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$eval) {
                    Intrinsics.checkNotNullParameter((Object)$this$eval, (String)"$this$eval");
                    KetherHelperKt.extend((ScriptContext)$this$eval, (Map)MapsKt.mapOf((Pair)TuplesKt.to((Object)"size", (Object)this.$size)));
                }
            }), (int)42, null).thenApply(ThemeChest::rows$lambda$2);
            Intrinsics.checkNotNullExpressionValue((Object)completionStage2, (String)"size: Int): CompletableF\u2026t\n            }\n        }");
            completionStage = completionStage2;
        }
        catch (Exception ex) {
            KetherHelperKt.printKetherErrorMessage((Throwable)ex, (boolean)true);
            CompletableFuture<Integer> completableFuture = CompletableFuture.completedFuture(1);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"{\n            ex.printKe\u2026pletedFuture(1)\n        }");
            completionStage = completableFuture;
        }
        return completionStage;
    }

    private static final void onDisplay$lambda$1$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void onDisplay$lambda$1(Session $session, List $message, boolean $canReply, Ref.BooleanRef $end, List replies) {
        Intrinsics.checkNotNullParameter((Object)$session, (String)"$session");
        Intrinsics.checkNotNullParameter((Object)$message, (String)"$message");
        Intrinsics.checkNotNullParameter((Object)$end, (String)"$end");
        Intrinsics.checkNotNullParameter((Object)replies, (String)"replies");
        INSTANCE.rows($session.getPlayer(), replies.size()).thenAccept(arg_0 -> ThemeChest.onDisplay$lambda$1$lambda$0((Function1)new Function1<Integer, Unit>($session, (List<? extends PlayerReply>)replies, (List<String>)$message, $canReply, $end){
            final /* synthetic */ Session $session;
            final /* synthetic */ List<PlayerReply> $replies;
            final /* synthetic */ List<String> $message;
            final /* synthetic */ boolean $canReply;
            final /* synthetic */ Ref.BooleanRef $end;
            {
                this.$session = $session;
                this.$replies = $replies;
                this.$message = $message;
                this.$canReply = $canReply;
                this.$end = $end;
                super(1);
            }

            /*
             * WARNING - void declaration
             */
            public final void invoke(Integer rows2) {
                HumanEntity humanEntity = (HumanEntity)this.$session.getPlayer();
                String string = ThemeChest.access$toTitle(ThemeChest.INSTANCE, ((ThemeChestSetting)ThemeChest.INSTANCE.getSettings()).getTitle(), this.$session);
                List<PlayerReply> list2 = this.$replies;
                Session session = this.$session;
                List<String> list3 = this.$message;
                boolean bl = this.$canReply;
                Ref.BooleanRef booleanRef = this.$end;
                boolean $i$f$openMenu = false;
                try {
                    void $this$invoke_u24lambda_u240;
                    void $this$openMenu$iv;
                    Class[] instance$iv$iv;
                    void title$iv;
                    boolean $i$f$buildMenu = false;
                    Class type$iv$iv = Basic.class.isInterface() ? Menu.Companion.getImplementation(Basic.class) : Basic.class;
                    Object[] objectArray = new Class[]{String.class};
                    Constructor<T> constructor = type$iv$iv.getDeclaredConstructor((Class<?>[])objectArray);
                    objectArray = new Object[]{title$iv};
                    T t = constructor.newInstance(objectArray);
                    if (t == null) {
                        throw new NullPointerException("null cannot be cast to non-null type taboolib.module.ui.type.Basic");
                    }
                    objectArray = instance$iv$iv = (Class[])((Basic)t);
                    Basic basic = (Basic)objectArray;
                    void var16_16 = $this$openMenu$iv;
                    boolean bl2 = false;
                    Intrinsics.checkNotNullExpressionValue((Object)rows2, (String)"rows");
                    $this$invoke_u24lambda_u240.rows(rows2.intValue());
                    $this$invoke_u24lambda_u240.onBuild(true, (Function2)new Function2<Player, Inventory, Unit>(list2, session, list3, bl){
                        final /* synthetic */ List<PlayerReply> $replies;
                        final /* synthetic */ Session $session;
                        final /* synthetic */ List<String> $message;
                        final /* synthetic */ boolean $canReply;
                        {
                            this.$replies = $replies;
                            this.$session = $session;
                            this.$message = $message;
                            this.$canReply = $canReply;
                            super(2);
                        }

                        /*
                         * WARNING - void declaration
                         */
                        public final void invoke(@NotNull Player player2, @NotNull Inventory inventory) {
                            void $this$forEachIndexed$iv;
                            Intrinsics.checkNotNullParameter((Object)player2, (String)"<anonymous parameter 0>");
                            Intrinsics.checkNotNullParameter((Object)inventory, (String)"inventory");
                            List<PlayerReply> list2 = this.$replies;
                            Intrinsics.checkNotNullExpressionValue(list2, (String)"replies");
                            Iterable iterable = list2;
                            Session session = this.$session;
                            boolean $i$f$forEachIndexed = false;
                            int index$iv = 0;
                            for (T item$iv : $this$forEachIndexed$iv) {
                                void reply;
                                int n;
                                if ((n = index$iv++) < 0) {
                                    CollectionsKt.throwIndexOverflow();
                                }
                                PlayerReply playerReply = (PlayerReply)item$iv;
                                int index = n;
                                boolean bl = false;
                                if (index >= ((ThemeChestSetting)ThemeChest.INSTANCE.getSettings()).getPlayerSlot().size()) continue;
                                ItemStack rep = !reply.isPlayerSelected(session.getPlayer()) ? ((ThemeChestSetting)ThemeChest.INSTANCE.getSettings()).getPlayerItem() : ((ThemeChestSetting)ThemeChest.INSTANCE.getSettings()).getPlayerItemSelected();
                                inventory.setItem(((Number)((ThemeChestSetting)ThemeChest.INSTANCE.getSettings()).getPlayerSlot().get(index)).intValue(), ThemeChest.access$buildItem(ThemeChest.INSTANCE, rep, session, (PlayerReply)reply, index + 1));
                            }
                            inventory.setItem(((ThemeChestSetting)ThemeChest.INSTANCE.getSettings()).getNpcSlot(), ThemeChest.access$buildItem(ThemeChest.INSTANCE, ((ThemeChestSetting)ThemeChest.INSTANCE.getSettings()).getNpcItem(), this.$session, this.$message));
                            new ConversationEvents.ChestThemeBuild(this.$session, this.$message, this.$canReply, inventory).call();
                        }
                    });
                    $this$invoke_u24lambda_u240.onClick(true, (Function1)new Function1<ClickEvent, Unit>(list2, session, booleanRef){
                        final /* synthetic */ List<PlayerReply> $replies;
                        final /* synthetic */ Session $session;
                        final /* synthetic */ Ref.BooleanRef $end;
                        {
                            this.$replies = $replies;
                            this.$session = $session;
                            this.$end = $end;
                            super(1);
                        }

                        public final void invoke(@NotNull ClickEvent event) {
                            block0: {
                                Intrinsics.checkNotNullParameter((Object)event, (String)"event");
                                List<PlayerReply> list2 = this.$replies;
                                Intrinsics.checkNotNullExpressionValue(list2, (String)"replies");
                                PlayerReply playerReply = (PlayerReply)CollectionsKt.getOrNull(list2, (int)((ThemeChestSetting)ThemeChest.INSTANCE.getSettings()).getPlayerSlot().indexOf(event.getRawSlot()));
                                if (playerReply == null) break block0;
                                PlayerReply playerReply2 = playerReply;
                                Session session = this.$session;
                                Ref.BooleanRef booleanRef = this.$end;
                                PlayerReply $this$invoke_u24lambda_u240 = playerReply2;
                                boolean bl = false;
                                FuturesKt.thenTrue($this$invoke_u24lambda_u240.check(session), (Function0<Unit>)((Function0)new Function0<Unit>(booleanRef, $this$invoke_u24lambda_u240, session, event){
                                    final /* synthetic */ Ref.BooleanRef $end;
                                    final /* synthetic */ PlayerReply $this_run;
                                    final /* synthetic */ Session $session;
                                    final /* synthetic */ ClickEvent $event;
                                    {
                                        this.$end = $end;
                                        this.$this_run = $receiver;
                                        this.$session = $session;
                                        this.$event = $event;
                                        super(0);
                                    }

                                    public final void invoke() {
                                        this.$end.element = true;
                                        this.$this_run.select(this.$session).thenAccept(arg_0 -> onDisplay.1.1.1.1.invoke$lambda$0((Function1)new Function1<Void, Unit>(this.$session, this.$event){
                                            final /* synthetic */ Session $session;
                                            final /* synthetic */ ClickEvent $event;
                                            {
                                                this.$session = $session;
                                                this.$event = $event;
                                                super(1);
                                            }

                                            public final void invoke(Void it) {
                                                if (Intrinsics.areEqual((Object)this.$session.getPlayer().getOpenInventory().getTopInventory(), (Object)this.$event.getInventory())) {
                                                    this.$session.getPlayer().closeInventory();
                                                }
                                            }
                                        }, arg_0));
                                    }

                                    private static final void invoke$lambda$0(Function1 $tmp0, Object p0) {
                                        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                                        $tmp0.invoke(p0);
                                    }
                                }));
                            }
                        }
                    });
                    Chest.onClose$default((Chest)((Chest)$this$invoke_u24lambda_u240), (boolean)false, (boolean)false, (Function1)((Function1)new Function1<InventoryCloseEvent, Unit>(booleanRef, session){
                        final /* synthetic */ Ref.BooleanRef $end;
                        final /* synthetic */ Session $session;
                        {
                            this.$end = $end;
                            this.$session = $session;
                            super(1);
                        }

                        public final void invoke(@NotNull InventoryCloseEvent it) {
                            Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                            if (!this.$end.element) {
                                this.$session.close(true);
                            }
                        }
                    }), (int)3, null);
                    MenuBuilderKt.openMenu$default((HumanEntity)var16_16, (Inventory)objectArray.build(), (boolean)false, (int)2, null);
                }
                catch (Throwable ex$iv) {
                    ex$iv.printStackTrace();
                }
            }
        }, arg_0));
    }

    private static final Integer rows$lambda$2(Object it) {
        Object $this$cint$iv = it;
        boolean $i$f$getCint = false;
        return Coerce.toInteger((Object)$this$cint$iv);
    }

    public static final /* synthetic */ String access$toTitle(ThemeChest $this, String $receiver, Session session) {
        return $this.toTitle($receiver, session);
    }

    public static final /* synthetic */ ItemStack access$buildItem(ThemeChest $this, ItemStack $receiver, Session session, List message2) {
        return $this.buildItem($receiver, session, message2);
    }

    public static final /* synthetic */ ItemStack access$buildItem(ThemeChest $this, ItemStack $receiver, Session session, PlayerReply reply, int index) {
        return $this.buildItem($receiver, session, reply, index);
    }
}

