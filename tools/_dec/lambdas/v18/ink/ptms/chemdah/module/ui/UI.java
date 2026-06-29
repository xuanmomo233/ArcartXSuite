/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.xseries.XItemStack
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.comparisons.ComparisonsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.ui;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AcceptResult;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonDepend;
import ink.ptms.chemdah.core.quest.addon.AddonUI;
import ink.ptms.chemdah.core.quest.meta.MetaType;
import ink.ptms.chemdah.module.ui.Include;
import ink.ptms.chemdah.module.ui.Item;
import ink.ptms.chemdah.module.ui.ItemFilter;
import ink.ptms.chemdah.module.ui.ItemQuest;
import ink.ptms.chemdah.module.ui.ItemQuestNoIcon;
import ink.ptms.chemdah.module.ui.ItemType;
import ink.ptms.chemdah.module.ui.UI;
import ink.ptms.chemdah.module.ui.UIMenu;
import ink.ptms.chemdah.module.ui.UITemplate;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.XItemStack;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.comparisons.ComparisonsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000h\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0010\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010!\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001a\u0010.\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u0002000\b0/2\u0006\u00101\u001a\u000202J\u000e\u00103\u001a\u0002042\u0006\u00101\u001a\u000202R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0017\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0017\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u001d\u0010\u0011\u001a\u000e\u0012\u0004\u0012\u00020\u0013\u0012\u0004\u0012\u00020\u00140\u0012\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0017\u001a\u00020\u0018\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0017\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00180\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u000bR\u0011\u0010\u001d\u001a\u00020\u0018\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u001aR\u0017\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00180\b\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u000bR\u0011\u0010!\u001a\u00020\u0018\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u001aR\u0011\u0010#\u001a\u00020\u0018\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010\u001aR\u0011\u0010%\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010'R#\u0010(\u001a\u0014\u0012\u0004\u0012\u00020*\u0012\n\u0012\b\u0012\u0004\u0012\u00020\t0+0)\u00a2\u0006\b\n\u0000\u001a\u0004\b,\u0010-\u00a8\u00065"}, d2={"Link/ptms/chemdah/module/ui/UI;", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "exclude", "", "", "getExclude", "()Ljava/util/List;", "include", "Ljava/util/ArrayList;", "Link/ptms/chemdah/module/ui/Include;", "getInclude", "()Ljava/util/ArrayList;", "items", "Ljava/util/HashMap;", "Link/ptms/chemdah/module/ui/ItemType;", "Link/ptms/chemdah/module/ui/Item;", "getItems", "()Ljava/util/HashMap;", "menuFilterRows", "", "getMenuFilterRows", "()I", "menuFilterSlot", "getMenuFilterSlot", "menuQuestRows", "getMenuQuestRows", "menuQuestSlot", "getMenuQuestSlot", "menuQuestSlotFilter", "getMenuQuestSlotFilter", "menuQuestSlotInfo", "getMenuQuestSlotInfo", "name", "getName", "()Ljava/lang/String;", "playerFilters", "Ljava/util/concurrent/ConcurrentHashMap;", "Ljava/util/UUID;", "", "getPlayerFilters", "()Ljava/util/concurrent/ConcurrentHashMap;", "collectQuests", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/module/ui/UITemplate;", "playerProfile", "Link/ptms/chemdah/core/PlayerProfile;", "open", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nUI.kt\nKotlin\n*S Kotlin\n*F\n+ 1 UI.kt\nink/ptms/chemdah/module/ui/UI\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n*L\n1#1,123:1\n1855#2,2:124\n1549#2:126\n1620#2,3:127\n766#2:130\n857#2,2:131\n1747#2,3:136\n2624#2,3:139\n1054#2:146\n515#3:133\n500#3,2:134\n502#3,4:142\n*S KotlinDebug\n*F\n+ 1 UI.kt\nink/ptms/chemdah/module/ui/UI\n*L\n39#1:124,2\n71#1:126\n71#1:127,3\n71#1:130\n71#1:131,2\n73#1:136,3\n73#1:139,3\n116#1:146\n73#1:133\n73#1:134,2\n73#1:142,4\n*E\n"})
public final class UI {
    @NotNull
    private final ConfigurationSection config;
    @NotNull
    private final String name;
    private final int menuQuestRows;
    @NotNull
    private final List<Integer> menuQuestSlot;
    private final int menuQuestSlotInfo;
    private final int menuQuestSlotFilter;
    private final int menuFilterRows;
    @NotNull
    private final List<Integer> menuFilterSlot;
    @NotNull
    private final ArrayList<Include> include;
    @NotNull
    private final List<String> exclude;
    @NotNull
    private final HashMap<ItemType, Item> items;
    @NotNull
    private final ConcurrentHashMap<UUID, List<String>> playerFilters;

    public UI(@NotNull ConfigurationSection config) {
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        this.config = config;
        String string = this.config.getString("name");
        this.name = String.valueOf(string != null ? UtilKt.colored((String)string) : null);
        this.menuQuestRows = this.config.getInt("menu.quest.rows");
        this.menuQuestSlot = this.config.getIntegerList("menu.quest.slot");
        this.menuQuestSlotInfo = this.config.getInt("menu.quest.methods.info");
        this.menuQuestSlotFilter = this.config.getInt("menu.quest.methods.filter");
        this.menuFilterRows = this.config.getInt("menu.filter.rows");
        this.menuFilterSlot = this.config.getIntegerList("menu.filter.slot");
        this.include = new ArrayList();
        this.exclude = CollectionsKt.toList((Iterable)this.config.getStringList("exclude"));
        this.items = new HashMap();
        this.playerFilters = new ConcurrentHashMap();
        Object object = this.config.getConfigurationSection("include");
        if (object != null && (object = object.getKeys(false)) != null) {
            Iterable $this$forEach$iv = (Iterable)object;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                ConfigurationSection normal;
                ConfigurationSection active;
                String it = (String)element$iv;
                boolean bl = false;
                if (this.config.getConfigurationSection("include." + it + ".active") == null || this.config.getConfigurationSection("include." + it + ".normal") == null) continue;
                ItemStack itemStack = XItemStack.deserialize((ConfigurationSection)active, UI::lambda$2$lambda$0);
                Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"deserialize(active) { it.colored() }");
                ItemStack itemStack2 = XItemStack.deserialize((ConfigurationSection)normal, UI::lambda$2$lambda$1);
                Intrinsics.checkNotNullExpressionValue((Object)itemStack2, (String)"deserialize(normal) { it.colored() }");
                this.include.add(new Include(it, itemStack, itemStack2));
            }
        }
        Map map = this.items;
        ConfigurationSection configurationSection = this.config.getConfigurationSection("item.info");
        Intrinsics.checkNotNull((Object)configurationSection);
        map.put(ItemType.INFO, new Item(configurationSection));
        Map map2 = this.items;
        ConfigurationSection configurationSection2 = this.config.getConfigurationSection("item.filter");
        Intrinsics.checkNotNull((Object)configurationSection2);
        map2.put(ItemType.FILTER, new ItemFilter(configurationSection2));
        Map map3 = this.items;
        ConfigurationSection configurationSection3 = this.config.getConfigurationSection("item.quest.started");
        Intrinsics.checkNotNull((Object)configurationSection3);
        map3.put(ItemType.QUEST_STARTED, new ItemQuest(configurationSection3));
        Map map4 = this.items;
        ConfigurationSection configurationSection4 = this.config.getConfigurationSection("item.quest.started-shared");
        Intrinsics.checkNotNull((Object)configurationSection4);
        map4.put(ItemType.QUEST_STARTED_SHARED, new ItemQuest(configurationSection4));
        Map map5 = this.items;
        ConfigurationSection configurationSection5 = this.config.getConfigurationSection("item.quest.can-start");
        Intrinsics.checkNotNull((Object)configurationSection5);
        map5.put(ItemType.QUEST_CAN_START, new ItemQuest(configurationSection5));
        Map map6 = this.items;
        ConfigurationSection configurationSection6 = this.config.getConfigurationSection("item.quest.cannot-start");
        Intrinsics.checkNotNull((Object)configurationSection6);
        map6.put(ItemType.QUEST_CANNOT_START, new ItemQuestNoIcon(configurationSection6));
        Map map7 = this.items;
        ConfigurationSection configurationSection7 = this.config.getConfigurationSection("item.quest.completed");
        Intrinsics.checkNotNull((Object)configurationSection7);
        map7.put(ItemType.QUEST_COMPLETE, new ItemQuestNoIcon(configurationSection7));
        Map map8 = this.items;
        ConfigurationSection configurationSection8 = this.config.getConfigurationSection("item.quest.unavailable");
        Intrinsics.checkNotNull((Object)configurationSection8);
        map8.put(ItemType.QUEST_UNAVAILABLE, new ItemQuestNoIcon(configurationSection8));
    }

    @NotNull
    public final ConfigurationSection getConfig() {
        return this.config;
    }

    @NotNull
    public final String getName() {
        return this.name;
    }

    public final int getMenuQuestRows() {
        return this.menuQuestRows;
    }

    @NotNull
    public final List<Integer> getMenuQuestSlot() {
        return this.menuQuestSlot;
    }

    public final int getMenuQuestSlotInfo() {
        return this.menuQuestSlotInfo;
    }

    public final int getMenuQuestSlotFilter() {
        return this.menuQuestSlotFilter;
    }

    public final int getMenuFilterRows() {
        return this.menuFilterRows;
    }

    @NotNull
    public final List<Integer> getMenuFilterSlot() {
        return this.menuFilterSlot;
    }

    @NotNull
    public final ArrayList<Include> getInclude() {
        return this.include;
    }

    @NotNull
    public final List<String> getExclude() {
        return this.exclude;
    }

    @NotNull
    public final HashMap<ItemType, Item> getItems() {
        return this.items;
    }

    @NotNull
    public final ConcurrentHashMap<UUID, List<String>> getPlayerFilters() {
        return this.playerFilters;
    }

    public final void open(@NotNull PlayerProfile playerProfile) {
        Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
        this.collectQuests(playerProfile).thenAccept(arg_0 -> UI.open$lambda$3((Function1)new Function1<List<? extends UITemplate>, Unit>(this, playerProfile){
            final /* synthetic */ UI this$0;
            final /* synthetic */ PlayerProfile $playerProfile;
            {
                this.this$0 = $receiver;
                this.$playerProfile = $playerProfile;
                super(1);
            }

            public final void invoke(List<UITemplate> it) {
                Intrinsics.checkNotNullExpressionValue(it, (String)"it");
                UIMenu.open$default(new UIMenu(this.this$0, this.$playerProfile, it), 0, 1, null);
            }
        }, arg_0));
    }

    /*
     * Unable to fully structure code
     */
    @NotNull
    public final CompletableFuture<List<UITemplate>> collectQuests(@NotNull PlayerProfile playerProfile) {
        Intrinsics.checkNotNullParameter((Object)playerProfile, (String)"playerProfile");
        completableFuture = new CompletableFuture<List<UITemplate>>();
        collect = new ArrayList<UITemplate>();
        v0 = this.playerFilters.computeIfAbsent(playerProfile.getUniqueId(), (Function<Object, List>)LambdaMetafactory.metafactory(null, null, null, (Ljava/lang/Object;)Ljava/lang/Object;, collectQuests$lambda$4(kotlin1822.jvm.functions.Function1 java.lang.Object ), (Ljava/lang/Object;)Ljava/util/List;)((Function1)collectQuests.includePlayer.1.INSTANCE));
        Intrinsics.checkNotNullExpressionValue((Object)v0, (String)"playerFilters.computeIfA\u2026uniqueId) { ArrayList() }");
        includePlayer = v0;
        $this$map$iv = this.include;
        $i$f$map = false;
        var8_8 = $this$map$iv;
        destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        $i$f$mapTo = false;
        for (T item$iv$iv : $this$mapTo$iv$iv) {
            var13_16 = (Include)item$iv$iv;
            var23_19 = destination$iv$iv;
            $i$a$-map-UI$collectQuests$include$1 = false;
            var23_19.add(it.getId());
        }
        $this$map$iv = (List)destination$iv$iv;
        $i$f$filter = false;
        $this$mapTo$iv$iv = $this$filter$iv;
        destination$iv$iv = new ArrayList<E>();
        $i$f$filterTo = false;
        for (T element$iv$iv : $this$filterTo$iv$iv) {
            it = (String)element$iv$iv;
            $i$a$-filter-UI$collectQuests$include$2 = false;
            if (!(includePlayer.contains(it) != false || includePlayer.isEmpty() != false)) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        include = (List)destination$iv$iv;
        $this$filter$iv = ChemdahAPI.INSTANCE.getQuestTemplate();
        $i$f$filter = false;
        destination$iv$iv = $this$filter$iv;
        destination$iv$iv = new LinkedHashMap<K, V>();
        $i$f$filterTo = false;
        var12_15 = $this$filterTo$iv$iv.entrySet().iterator();
        while (var12_15.hasNext()) {
            block12: {
                block11: {
                    var14_18 = element$iv$iv = var12_15.next();
                    $i$a$-filter-UI$collectQuests$quests$1 = false;
                    v = (Template)var14_18.getValue();
                    $this$any$iv = MetaType.Companion.type(v);
                    $i$f$any = false;
                    if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                        v1 = false;
                    } else {
                        for (T element$iv : $this$any$iv) {
                            it = (String)element$iv;
                            $i$a$-any-UI$collectQuests$quests$1$1 = false;
                            if (!include.contains(it)) continue;
                            v1 = true;
                            break block11;
                        }
                        v1 = false;
                    }
                }
                if (!v1) ** GOTO lbl-1000
                $this$none$iv = MetaType.Companion.type(v);
                $i$f$none = false;
                if ($this$none$iv instanceof Collection && ((Collection)$this$none$iv).isEmpty()) {
                    v2 = true;
                } else {
                    for (T element$iv : $this$none$iv) {
                        it = (String)element$iv;
                        $i$a$-none-UI$collectQuests$quests$1$2 = false;
                        if (!this.exclude.contains(it)) continue;
                        v2 = false;
                        break block12;
                    }
                    v2 = true;
                }
            }
            if (v2) {
                v3 = true;
            } else lbl-1000:
            // 2 sources

            {
                v3 = false;
            }
            if (!v3) continue;
            destination$iv$iv.put(element$iv$iv.getKey(), element$iv$iv.getValue());
        }
        quests = CollectionsKt.toList((Iterable)destination$iv$iv.values());
        UI.collectQuests$process(quests, playerProfile, collect, completableFuture, 0);
        return completableFuture;
    }

    private static final String lambda$2$lambda$0(String it) {
        Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
        return UtilKt.colored((String)it);
    }

    private static final String lambda$2$lambda$1(String it) {
        Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
        return UtilKt.colored((String)it);
    }

    private static final void open$lambda$3(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final List collectQuests$lambda$4(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (List)$tmp0.invoke(p0);
    }

    private static final void collectQuests$process$lambda$10(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void collectQuests$process(List<? extends Template> quests, PlayerProfile $playerProfile, ArrayList<UITemplate> collect, CompletableFuture<List<UITemplate>> completableFuture, int cur) {
        if (cur < quests.size()) {
            Template quest2 = quests.get(cur);
            AddonUI ui2 = AddonUI.Companion.ui(quest2);
            Quest questById = PlayerProfile.getQuestById$default($playerProfile, quest2.getId(), false, 2, null);
            if (questById != null) {
                if (questById.isOwner($playerProfile.getPlayer())) {
                    collect.add(new UITemplate(quest2, ItemType.QUEST_STARTED));
                    UI.collectQuests$process(quests, $playerProfile, collect, completableFuture, cur + 1);
                } else {
                    collect.add(new UITemplate(quest2, ItemType.QUEST_STARTED_SHARED));
                    UI.collectQuests$process(quests, $playerProfile, collect, completableFuture, cur + 1);
                }
            } else {
                quest2.checkAccept($playerProfile).thenAccept(arg_0 -> UI.collectQuests$process$lambda$10((Function1)new Function1<AcceptResult, Unit>(ui2, quest2, $playerProfile, collect, cur, quests, completableFuture){
                    final /* synthetic */ AddonUI $ui;
                    final /* synthetic */ Template $quest;
                    final /* synthetic */ PlayerProfile $playerProfile;
                    final /* synthetic */ ArrayList<UITemplate> $collect;
                    final /* synthetic */ int $cur;
                    final /* synthetic */ List<Template> $quests;
                    final /* synthetic */ CompletableFuture<List<UITemplate>> $completableFuture;
                    {
                        this.$ui = $ui;
                        this.$quest = $quest;
                        this.$playerProfile = $playerProfile;
                        this.$collect = $collect;
                        this.$cur = $cur;
                        this.$quests = $quests;
                        this.$completableFuture = $completableFuture;
                        super(1);
                    }

                    public final void invoke(AcceptResult cond) {
                        if (cond.getType() == AcceptResult.Type.SUCCESSFUL) {
                            AddonUI addonUI = this.$ui;
                            boolean bl = addonUI != null ? addonUI.getVisibleStart() : false;
                            if (bl && AddonDepend.Companion.isQuestDependCompleted(this.$quest, this.$playerProfile.getPlayer())) {
                                this.$collect.add(new UITemplate(this.$quest, ItemType.QUEST_CAN_START));
                            }
                        } else if (this.$playerProfile.isQuestCompleted(this.$quest.getId())) {
                            AddonUI addonUI = this.$ui;
                            boolean bl = addonUI != null ? addonUI.getVisibleComplete() : false;
                            if (bl) {
                                this.$collect.add(new UITemplate(this.$quest, ItemType.QUEST_COMPLETE));
                            }
                        } else {
                            AddonUI addonUI = this.$ui;
                            boolean bl = addonUI != null ? addonUI.getVisibleStart() : false;
                            if (bl) {
                                this.$collect.add(new UITemplate(this.$quest, ItemType.QUEST_CANNOT_START));
                            }
                        }
                        UI.access$collectQuests$process(this.$quests, this.$playerProfile, this.$collect, this.$completableFuture, this.$cur + 1);
                    }
                }, arg_0));
            }
        } else {
            Iterable $this$sortedByDescending$iv = collect;
            boolean $i$f$sortedByDescending = false;
            completableFuture.complete(CollectionsKt.sortedWith((Iterable)$this$sortedByDescending$iv, (Comparator)new Comparator(){

                public final int compare(T a, T b) {
                    UITemplate it = (UITemplate)b;
                    boolean bl = false;
                    Comparable comparable = Integer.valueOf(it.getItemType().getPriority());
                    it = (UITemplate)a;
                    Comparable comparable2 = comparable;
                    bl = false;
                    return ComparisonsKt.compareValues((Comparable)comparable2, (Comparable)Integer.valueOf(it.getItemType().getPriority()));
                }
            }));
        }
    }

    public static final /* synthetic */ void access$collectQuests$process(List quests, PlayerProfile $playerProfile, ArrayList collect, CompletableFuture completableFuture, int cur) {
        UI.collectQuests$process(quests, $playerProfile, collect, completableFuture, cur);
    }
}

