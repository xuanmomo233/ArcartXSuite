/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  ink.ptms.chemdah.taboolib.module.ui.ClickEvent
 *  ink.ptms.chemdah.taboolib.module.ui.Menu
 *  ink.ptms.chemdah.taboolib.module.ui.MenuBuilderKt
 *  ink.ptms.chemdah.taboolib.module.ui.type.Chest
 *  ink.ptms.chemdah.taboolib.module.ui.type.PageableChest
 *  ink.ptms.chemdah.taboolib.platform.compat.PlaceholderExpansionKt
 *  ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.functions.Function4
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Sound
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.ui;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.module.ui.Include;
import ink.ptms.chemdah.module.ui.Item;
import ink.ptms.chemdah.module.ui.ItemType;
import ink.ptms.chemdah.module.ui.UI;
import ink.ptms.chemdah.module.ui.UIMenuFilter;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.ui.ClickEvent;
import ink.ptms.chemdah.taboolib.module.ui.Menu;
import ink.ptms.chemdah.taboolib.module.ui.MenuBuilderKt;
import ink.ptms.chemdah.taboolib.module.ui.type.Chest;
import ink.ptms.chemdah.taboolib.module.ui.type.PageableChest;
import ink.ptms.chemdah.taboolib.platform.compat.PlaceholderExpansionKt;
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import ink.ptms.chemdah.util.StringKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.lang.reflect.Constructor;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.functions.Function4;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\u0018\u0000 \u000f2\u00020\u0001:\u0001\u000fB\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\u000b\u001a\u00020\f2\b\b\u0002\u0010\r\u001a\u00020\u000eR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/module/ui/UIMenuFilter;", "", "ui", "Link/ptms/chemdah/module/ui/UI;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "(Link/ptms/chemdah/module/ui/UI;Link/ptms/chemdah/core/PlayerProfile;)V", "getProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getUi", "()Link/ptms/chemdah/module/ui/UI;", "open", "", "page", "", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nUIMenuFilter.kt\nKotlin\n*S Kotlin\n*F\n+ 1 UIMenuFilter.kt\nink/ptms/chemdah/module/ui/UIMenuFilter\n+ 2 MenuBuilder.kt\ntaboolib/module/ui/MenuBuilderKt\n*L\n1#1,89:1\n88#2,2:90\n79#2,15:92\n*S KotlinDebug\n*F\n+ 1 UIMenuFilter.kt\nink/ptms/chemdah/module/ui/UIMenuFilter\n*L\n26#1:90,2\n26#1:92,15\n*E\n"})
public final class UIMenuFilter {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final UI ui;
    @NotNull
    private final PlayerProfile profile;
    @NotNull
    private static final Template unavailable = new Template("__unavailable__", (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null), null);

    public UIMenuFilter(@NotNull UI ui2, @NotNull PlayerProfile profile) {
        Intrinsics.checkNotNullParameter((Object)ui2, (String)"ui");
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        this.ui = ui2;
        this.profile = profile;
    }

    @NotNull
    public final UI getUi() {
        return this.ui;
    }

    @NotNull
    public final PlayerProfile getProfile() {
        return this.profile;
    }

    /*
     * WARNING - void declaration
     */
    public final void open(int page) {
        HumanEntity humanEntity = (HumanEntity)this.profile.getPlayer();
        Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)this.profile.getPlayer().getName()), TuplesKt.to((Object)"page", (Object)"%p")};
        String title$iv = UtilsForKetherKt.parseKether(PlaceholderExpansionKt.replacePlaceholder((String)StringKt.replace(this.ui.getName(), pairArray), (Player)this.profile.getPlayer()), this.profile.getPlayer());
        boolean $i$f$openMenu = false;
        try {
            void $this$open_u24lambda_u240;
            void $this$openMenu$iv;
            Class[] instance$iv$iv;
            boolean $i$f$buildMenu = false;
            Class type$iv$iv = PageableChest.class.isInterface() ? Menu.Companion.getImplementation(PageableChest.class) : PageableChest.class;
            Object[] objectArray = new Class[]{String.class};
            Constructor constructor = type$iv$iv.getDeclaredConstructor((Class<?>[])objectArray);
            objectArray = new Object[]{title$iv};
            Object t = constructor.newInstance(objectArray);
            if (t == null) {
                throw new NullPointerException("null cannot be cast to non-null type taboolib.module.ui.type.PageableChest<ink.ptms.chemdah.module.ui.Include>");
            }
            objectArray = instance$iv$iv = (Class[])((PageableChest)t);
            PageableChest pageableChest = (PageableChest)objectArray;
            void var11_11 = $this$openMenu$iv;
            boolean bl = false;
            $this$open_u24lambda_u240.page(page);
            $this$open_u24lambda_u240.rows(this.ui.getMenuFilterRows());
            $this$open_u24lambda_u240.slots(this.ui.getMenuFilterSlot());
            $this$open_u24lambda_u240.elements((Function0)new Function0<List<? extends Include>>(this){
                final /* synthetic */ UIMenuFilter this$0;
                {
                    this.this$0 = $receiver;
                    super(0);
                }

                @NotNull
                public final List<Include> invoke() {
                    return this.this$0.getUi().getInclude();
                }
            });
            $this$open_u24lambda_u240.onBuild(true, (Function1)new Function1<Inventory, Unit>(this){
                final /* synthetic */ UIMenuFilter this$0;
                {
                    this.this$0 = $receiver;
                    super(1);
                }

                public final void invoke(@NotNull Inventory inventory) {
                    Intrinsics.checkNotNullParameter((Object)inventory, (String)"inventory");
                    Item item2 = this.this$0.getUi().getItems().get((Object)((Object)ItemType.QUEST_UNAVAILABLE));
                    Intrinsics.checkNotNull((Object)item2);
                    ItemStack item3 = item2.getItemStack(this.this$0.getProfile(), this.this$0.getUi(), UIMenuFilter.access$getUnavailable$cp());
                    Iterable $this$forEach$iv = this.this$0.getUi().getMenuFilterSlot();
                    boolean $i$f$forEach = false;
                    for (T element$iv : $this$forEach$iv) {
                        int it = ((Number)element$iv).intValue();
                        boolean bl = false;
                        if (!ItemModifierKt.isAir((ItemStack)inventory.getItem(it))) continue;
                        inventory.setItem(it, item3);
                    }
                }
            });
            Chest.onClick$default((Chest)((Chest)$this$open_u24lambda_u240), (boolean)false, (Function1)((Function1)new Function1<ClickEvent, Unit>((PageableChest<Include>)$this$open_u24lambda_u240, this, page){
                final /* synthetic */ PageableChest<Include> $this_openMenu;
                final /* synthetic */ UIMenuFilter this$0;
                final /* synthetic */ int $page;
                {
                    this.$this_openMenu = $receiver;
                    this.this$0 = $receiver2;
                    this.$page = $page;
                    super(1);
                }

                public final void invoke(@NotNull ClickEvent event) {
                    Intrinsics.checkNotNullParameter((Object)event, (String)"event");
                    if (event.getRawSlot() == -999) {
                        if (event.clickEvent().isLeftClick()) {
                            if (this.$this_openMenu.hasPreviousPage()) {
                                this.this$0.open(this.$page - 1);
                            }
                        } else if (event.clickEvent().isRightClick() && this.$this_openMenu.hasNextPage()) {
                            this.this$0.open(this.$page + 1);
                        }
                    }
                }
            }), (int)1, null);
            $this$open_u24lambda_u240.onClick((Function2)new Function2<ClickEvent, Include, Unit>(this){
                final /* synthetic */ UIMenuFilter this$0;
                {
                    this.this$0 = $receiver;
                    super(2);
                }

                public final void invoke(@NotNull ClickEvent event, @NotNull Include include) {
                    Intrinsics.checkNotNullParameter((Object)event, (String)"event");
                    Intrinsics.checkNotNullParameter((Object)include, (String)"include");
                    List list2 = this.this$0.getUi().getPlayerFilters().computeIfAbsent(event.getClicker().getUniqueId(), arg_0 -> open.1.4.invoke$lambda$0(open.1.includes.1.INSTANCE, arg_0));
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"ui.playerFilters.compute\u2026uniqueId) { ArrayList() }");
                    List includes2 = list2;
                    if (includes2.contains(include.getId())) {
                        includes2.remove(include.getId());
                        event.setCurrentItem(include.getNormalItem());
                    } else {
                        includes2.add(include.getId());
                        event.setCurrentItem(include.getActiveItem());
                    }
                    event.getClicker().playSound(event.getClicker().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
                }

                private static final List invoke$lambda$0(Function1 $tmp0, Object p0) {
                    Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                    return (List)$tmp0.invoke(p0);
                }
            });
            $this$open_u24lambda_u240.onGenerate(true, (Function4)new Function4<Player, Include, Integer, Integer, ItemStack>(this){
                final /* synthetic */ UIMenuFilter this$0;
                {
                    this.this$0 = $receiver;
                    super(4);
                }

                @NotNull
                public final ItemStack invoke(@NotNull Player player, @NotNull Include include, int n, int n2) {
                    Intrinsics.checkNotNullParameter((Object)player, (String)"player");
                    Intrinsics.checkNotNullParameter((Object)include, (String)"include");
                    List list2 = this.this$0.getUi().getPlayerFilters().computeIfAbsent(player.getUniqueId(), arg_0 -> open.1.5.invoke$lambda$0(open.1.includes.1.INSTANCE, arg_0));
                    Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"ui.playerFilters.compute\u2026uniqueId) { ArrayList() }");
                    List includes2 = list2;
                    return includes2.contains(include.getId()) ? include.getActiveItem() : include.getNormalItem();
                }

                private static final List invoke$lambda$0(Function1 $tmp0, Object p0) {
                    Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                    return (List)$tmp0.invoke(p0);
                }
            });
            Chest.onClose$default((Chest)((Chest)$this$open_u24lambda_u240), (boolean)false, (boolean)false, (Function1)((Function1)new Function1<InventoryCloseEvent, Unit>(this){
                final /* synthetic */ UIMenuFilter this$0;
                {
                    this.this$0 = $receiver;
                    super(1);
                }

                public final void invoke(@NotNull InventoryCloseEvent it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    ExecutorKt.submit$default((boolean)false, (boolean)false, (long)1L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this.this$0){
                        final /* synthetic */ UIMenuFilter this$0;
                        {
                            this.this$0 = $receiver;
                            super(1);
                        }

                        public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                            Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                            this.this$0.getUi().open(this.this$0.getProfile());
                        }
                    }), (int)11, null);
                }
            }), (int)3, null);
            MenuBuilderKt.openMenu$default((HumanEntity)var11_11, (Inventory)objectArray.build(), (boolean)false, (int)2, null);
        }
        catch (Throwable ex$iv) {
            ex$iv.printStackTrace();
        }
    }

    public static /* synthetic */ void open$default(UIMenuFilter uIMenuFilter, int n, int n2, Object object) {
        if ((n2 & 1) != 0) {
            n = 0;
        }
        uIMenuFilter.open(n);
    }

    public static final /* synthetic */ Template access$getUnavailable$cp() {
        return unavailable;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/ui/UIMenuFilter$Companion;", "", "()V", "unavailable", "Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

