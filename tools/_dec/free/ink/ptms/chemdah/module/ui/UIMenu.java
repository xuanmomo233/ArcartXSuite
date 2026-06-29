/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
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
 *  kotlin1822.io.FilesKt
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
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.ui;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonTrack;
import ink.ptms.chemdah.module.ui.Item;
import ink.ptms.chemdah.module.ui.ItemType;
import ink.ptms.chemdah.module.ui.UI;
import ink.ptms.chemdah.module.ui.UIMenuFilter;
import ink.ptms.chemdah.module.ui.UITemplate;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
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
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.io.FilesKt;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\u0018\u0000 \u00142\u00020\u0001:\u0001\u0014B#\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007\u00a2\u0006\u0002\u0010\tJ\u0010\u0010\u0010\u001a\u00020\u00112\b\b\u0002\u0010\u0012\u001a\u00020\u0013R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0017\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/module/ui/UIMenu;", "", "ui", "Link/ptms/chemdah/module/ui/UI;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "templates", "", "Link/ptms/chemdah/module/ui/UITemplate;", "(Link/ptms/chemdah/module/ui/UI;Link/ptms/chemdah/core/PlayerProfile;Ljava/util/List;)V", "getProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getTemplates", "()Ljava/util/List;", "getUi", "()Link/ptms/chemdah/module/ui/UI;", "open", "", "page", "", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nUIMenu.kt\nKotlin\n*S Kotlin\n*F\n+ 1 UIMenu.kt\nink/ptms/chemdah/module/ui/UIMenu\n+ 2 MenuBuilder.kt\ntaboolib/module/ui/MenuBuilderKt\n*L\n1#1,98:1\n88#2,2:99\n79#2,15:101\n*S KotlinDebug\n*F\n+ 1 UIMenu.kt\nink/ptms/chemdah/module/ui/UIMenu\n*L\n28#1:99,2\n28#1:101,15\n*E\n"})
public final class UIMenu {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final UI ui;
    @NotNull
    private final PlayerProfile profile;
    @NotNull
    private final List<UITemplate> templates;
    @NotNull
    private static final Template unavailable = new Template("__unavailable__", (ConfigurationSection)Configuration.Companion.empty$default((Configuration.Companion)Configuration.Companion, null, (boolean)false, (int)3, null), FilesKt.resolve((File)IOKt.getDataFolder(), (String)"core/quest/__unavailable__.yml"), null);

    public UIMenu(@NotNull UI ui2, @NotNull PlayerProfile profile, @NotNull List<UITemplate> templates) {
        Intrinsics.checkNotNullParameter((Object)ui2, (String)"ui");
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter(templates, (String)"templates");
        this.ui = ui2;
        this.profile = profile;
        this.templates = templates;
    }

    @NotNull
    public final UI getUi() {
        return this.ui;
    }

    @NotNull
    public final PlayerProfile getProfile() {
        return this.profile;
    }

    @NotNull
    public final List<UITemplate> getTemplates() {
        return this.templates;
    }

    /*
     * WARNING - void declaration
     */
    public final void open(int page) {
        Player player2 = this.profile.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"profile.player");
        HumanEntity humanEntity = (HumanEntity)player2;
        Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"name", (Object)this.profile.getPlayer().getName()), TuplesKt.to((Object)"page", (Object)"%p")};
        String string = StringKt.replace(this.ui.getName(), pairArray);
        Player player3 = this.profile.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player3, (String)"profile.player");
        String string2 = PlaceholderExpansionKt.replacePlaceholder((String)string, (Player)player3);
        Player player4 = this.profile.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player4, (String)"profile.player");
        String title$iv = UtilsForKetherKt.parseKether(string2, player4);
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
                throw new NullPointerException("null cannot be cast to non-null type taboolib.module.ui.type.PageableChest<ink.ptms.chemdah.module.ui.UITemplate>");
            }
            objectArray = instance$iv$iv = (Class[])((PageableChest)t);
            PageableChest pageableChest = (PageableChest)objectArray;
            void var11_11 = $this$openMenu$iv;
            boolean bl = false;
            $this$open_u24lambda_u240.page(page);
            $this$open_u24lambda_u240.rows(this.ui.getMenuQuestRows());
            $this$open_u24lambda_u240.slots(this.ui.getMenuQuestSlot());
            $this$open_u24lambda_u240.elements((Function0)new Function0<List<? extends UITemplate>>(this){
                final /* synthetic */ UIMenu this$0;
                {
                    this.this$0 = $receiver;
                    super(0);
                }

                @NotNull
                public final List<UITemplate> invoke() {
                    return this.this$0.getTemplates();
                }
            });
            int n = this.ui.getMenuQuestSlotFilter();
            Item item2 = this.ui.getItems().get((Object)ItemType.FILTER);
            Intrinsics.checkNotNull((Object)item2);
            $this$open_u24lambda_u240.set(n, item2.getItemStack(this.profile, this.ui, unavailable), (Function1)new Function1<ClickEvent, Unit>(this){
                final /* synthetic */ UIMenu this$0;
                {
                    this.this$0 = $receiver;
                    super(1);
                }

                public final void invoke(@NotNull ClickEvent $this$set) {
                    Intrinsics.checkNotNullParameter((Object)$this$set, (String)"$this$set");
                    UIMenuFilter.open$default(new UIMenuFilter(this.this$0.getUi(), this.this$0.getProfile()), 0, 1, null);
                }
            });
            $this$open_u24lambda_u240.onGenerate(true, (Function4)new Function4<Player, UITemplate, Integer, Integer, ItemStack>(this){
                final /* synthetic */ UIMenu this$0;
                {
                    this.this$0 = $receiver;
                    super(4);
                }

                @NotNull
                public final ItemStack invoke(@NotNull Player player2, @NotNull UITemplate template, int n, int n2) {
                    Intrinsics.checkNotNullParameter((Object)player2, (String)"<anonymous parameter 0>");
                    Intrinsics.checkNotNullParameter((Object)template, (String)"template");
                    Item item2 = this.this$0.getUi().getItems().get((Object)((Object)template.getItemType()));
                    Intrinsics.checkNotNull((Object)item2);
                    return item2.getItemStack(this.this$0.getProfile(), this.this$0.getUi(), template.getTemplate());
                }
            });
            $this$open_u24lambda_u240.onBuild(true, (Function1)new Function1<Inventory, Unit>(this){
                final /* synthetic */ UIMenu this$0;
                {
                    this.this$0 = $receiver;
                    super(1);
                }

                public final void invoke(@NotNull Inventory inventory) {
                    Intrinsics.checkNotNullParameter((Object)inventory, (String)"inventory");
                    int n = this.this$0.getUi().getMenuQuestSlotInfo();
                    Item item2 = this.this$0.getUi().getItems().get((Object)((Object)ItemType.INFO));
                    Intrinsics.checkNotNull((Object)item2);
                    inventory.setItem(n, item2.getItemStack(this.this$0.getProfile(), this.this$0.getUi(), UIMenu.access$getUnavailable$cp()));
                    Item item3 = this.this$0.getUi().getItems().get((Object)((Object)ItemType.QUEST_UNAVAILABLE));
                    Intrinsics.checkNotNull((Object)item3);
                    ItemStack item4 = item3.getItemStack(this.this$0.getProfile(), this.this$0.getUi(), UIMenu.access$getUnavailable$cp());
                    Iterable $this$forEach$iv = this.this$0.getUi().getMenuQuestSlot();
                    boolean $i$f$forEach = false;
                    for (T element$iv : $this$forEach$iv) {
                        int it = ((Number)element$iv).intValue();
                        boolean bl = false;
                        if (!ItemModifierKt.isAir((ItemStack)inventory.getItem(it))) continue;
                        inventory.setItem(it, item4);
                    }
                }
            });
            Chest.onClick$default((Chest)((Chest)$this$open_u24lambda_u240), (boolean)false, (Function1)((Function1)new Function1<ClickEvent, Unit>((PageableChest<UITemplate>)$this$open_u24lambda_u240, this, page){
                final /* synthetic */ PageableChest<UITemplate> $this_openMenu;
                final /* synthetic */ UIMenu this$0;
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
            $this$open_u24lambda_u240.onClick((Function2)new Function2<ClickEvent, UITemplate, Unit>(this){
                final /* synthetic */ UIMenu this$0;
                {
                    this.this$0 = $receiver;
                    super(2);
                }

                public final void invoke(@NotNull ClickEvent event, @NotNull UITemplate el) {
                    Intrinsics.checkNotNullParameter((Object)event, (String)"event");
                    Intrinsics.checkNotNullParameter((Object)el, (String)"el");
                    if (el.getItemType() == ItemType.QUEST_STARTED || el.getItemType() == ItemType.QUEST_STARTED_SHARED || el.getItemType() == ItemType.QUEST_CAN_START) {
                        if (Intrinsics.areEqual((Object)el.getTemplate(), (Object)AddonTrack.Companion.getTrackQuest(this.this$0.getProfile()))) {
                            AddonTrack.Companion.setTrackQuest(this.this$0.getProfile(), null);
                            event.getClicker().playSound(event.getClicker().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
                        } else if (AddonTrack.Companion.allowTracked(el.getTemplate())) {
                            AddonTrack.Companion.setTrackQuest(this.this$0.getProfile(), el.getTemplate());
                            event.getClicker().playSound(event.getClicker().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
                        }
                    }
                }
            });
            MenuBuilderKt.openMenu$default((HumanEntity)var11_11, (Inventory)objectArray.build(), (boolean)false, (int)2, null);
        }
        catch (Throwable ex$iv) {
            ex$iv.printStackTrace();
        }
    }

    public static /* synthetic */ void open$default(UIMenu uIMenu, int n, int n2, Object object) {
        if ((n2 & 1) != 0) {
            n = 0;
        }
        uIMenu.open(n);
    }

    public static final /* synthetic */ Template access$getUnavailable$cp() {
        return unavailable;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/ui/UIMenu$Companion;", "", "()V", "unavailable", "Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

