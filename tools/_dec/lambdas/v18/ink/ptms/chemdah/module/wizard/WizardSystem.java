/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  ink.ptms.chemdah.taboolib.common.io.FileKt
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.Schedule
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.ArraysKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.io.FilesKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.wizard;

import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.api.event.collect.PluginReloadEvent;
import ink.ptms.chemdah.core.quest.QuestLoader;
import ink.ptms.chemdah.module.Module;
import ink.ptms.chemdah.module.wizard.WizardAction;
import ink.ptms.chemdah.module.wizard.WizardInfo;
import ink.ptms.chemdah.module.wizard.WizardInfoLoader;
import ink.ptms.chemdah.module.wizard.WizardSystem;
import ink.ptms.chemdah.taboolib.common.io.FileKt;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.Schedule;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.ArraysKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.io.FilesKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Awake
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000T\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0015J\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u0017\u001a\u00020\u0005J\u000e\u0010\u0018\u001a\u00020\u00132\u0006\u0010\u0019\u001a\u00020\u001aJ\u0010\u0010\u001b\u001a\u00020\u00132\u0006\u0010\u001c\u001a\u00020\u001dH\u0003J\u0010\u0010\u001e\u001a\u00020\u00132\u0006\u0010\u001c\u001a\u00020\u001fH\u0003J\u0010\u0010\u001e\u001a\u00020\u00132\u0006\u0010\u001c\u001a\u00020 H\u0003J\b\u0010!\u001a\u00020\u0013H\u0003J\b\u0010\"\u001a\u00020\u0013H\u0016R\u001d\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u001a\u0010\t\u001a\u00020\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u001d\u0010\u000f\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00100\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\b\u00a8\u0006#"}, d2={"Link/ptms/chemdah/module/wizard/WizardSystem;", "Link/ptms/chemdah/module/Module;", "()V", "actionMap", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Link/ptms/chemdah/module/wizard/WizardAction;", "getActionMap", "()Ljava/util/concurrent/ConcurrentHashMap;", "infoLoader", "Link/ptms/chemdah/module/wizard/WizardInfoLoader;", "getInfoLoader", "()Link/ptms/chemdah/module/wizard/WizardInfoLoader;", "setInfoLoader", "(Link/ptms/chemdah/module/wizard/WizardInfoLoader;)V", "infoMap", "Link/ptms/chemdah/module/wizard/WizardInfo;", "getInfoMap", "cancel", "", "entityInstance", "Link/ptms/adyeshach/core/entity/EntityInstance;", "getWizardInfo", "id", "loadInfo", "file", "Ljava/io/File;", "onQuit", "e", "Lorg/bukkit/event/player/PlayerQuitEvent;", "onSelect", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Pre;", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Select;", "onTick", "reload", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nWizardSystem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 WizardSystem.kt\nink/ptms/chemdah/module/wizard/WizardSystem\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,131:1\n766#2:132\n857#2,2:133\n1549#2:135\n1620#2,3:136\n1855#2,2:139\n766#2:141\n857#2,2:142\n1855#2,2:144\n*S KotlinDebug\n*F\n+ 1 WizardSystem.kt\nink/ptms/chemdah/module/wizard/WizardSystem\n*L\n70#1:132\n70#1:133,2\n72#1:135\n72#1:136,3\n91#1:139,2\n96#1:141\n96#1:142,2\n96#1:144,2\n*E\n"})
public final class WizardSystem
implements Module {
    @NotNull
    public static final WizardSystem INSTANCE = new WizardSystem();
    @NotNull
    private static final ConcurrentHashMap<String, WizardInfo> infoMap = new ConcurrentHashMap();
    @NotNull
    private static final ConcurrentHashMap<String, WizardAction> actionMap = new ConcurrentHashMap();
    @NotNull
    private static WizardInfoLoader infoLoader = new WizardInfoLoader();

    private WizardSystem() {
    }

    @NotNull
    public final ConcurrentHashMap<String, WizardInfo> getInfoMap() {
        return infoMap;
    }

    @NotNull
    public final ConcurrentHashMap<String, WizardAction> getActionMap() {
        return actionMap;
    }

    @NotNull
    public final WizardInfoLoader getInfoLoader() {
        return infoLoader;
    }

    public final void setInfoLoader(@NotNull WizardInfoLoader wizardInfoLoader) {
        Intrinsics.checkNotNullParameter((Object)wizardInfoLoader, (String)"<set-?>");
        infoLoader = wizardInfoLoader;
    }

    @Override
    public void reload() {
        infoMap.clear();
        File folder = new File(IOKt.getDataFolder(), "module/wizard");
        if (FileKt.notfound((File)folder)) {
            IOKt.releaseResourceFile$default((String)"module/wizard/example.yml", (boolean)false, null, (int)4, null);
        }
        ExecutorKt.submit$default((boolean)false, (boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(folder){
            final /* synthetic */ File $folder;
            {
                this.$folder = $folder;
                super(1);
            }

            public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                WizardSystem.INSTANCE.loadInfo(this.$folder);
                Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                    \u5df2\u52a0\u8f7d " + WizardSystem.INSTANCE.getInfoMap().size() + " \u9879\u5f15\u5bfc\u4fe1\u606f\u3002\n                    " + WizardSystem.INSTANCE.getInfoMap().size() + " wizards loaded.\n                "))};
                IOKt.info((Object[])objectArray);
                new PluginReloadEvent.WizardModel().call();
            }
        }), (int)15, null);
    }

    /*
     * WARNING - void declaration
     */
    public final void loadInfo(@NotNull File file) {
        void $this$mapTo$iv$iv;
        File it;
        Iterable $this$filterTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        Iterable $this$filter$iv = FileKt.deep((File)file, (Function1)loadInfo.1.INSTANCE);
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            it = (File)element$iv$iv;
            boolean bl = false;
            if (!(it.isFile() && ArraysKt.contains((Object[])QuestLoader.INSTANCE.getAllowExtension(), (Object)FilesKt.getExtension((File)it)))) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        Iterable $this$map$iv = (List)destination$iv$iv;
        boolean $i$f$map = false;
        $this$filterTo$iv$iv = $this$map$iv;
        destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            it = (File)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(SectionsKt.mapSection((ConfigurationSection)((ConfigurationSection)Configuration.Companion.loadFromFile$default((Configuration.Companion)Configuration.Companion, (File)it, null, (boolean)false, (int)6, null)), (Function1)loadInfo.3.1.INSTANCE));
        }
        List cfr_ignored_0 = (List)destination$iv$iv;
    }

    @Nullable
    public final WizardInfo getWizardInfo(@NotNull String id2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        return infoMap.get(id2);
    }

    public final void cancel(@NotNull EntityInstance entityInstance) {
        Intrinsics.checkNotNullParameter((Object)entityInstance, (String)"entityInstance");
        actionMap.remove(entityInstance.getUniqueId());
    }

    @Schedule(period=10L)
    private final void onTick() {
        Collection<WizardAction> collection = actionMap.values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"actionMap.values");
        Iterable $this$forEach$iv = collection;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            WizardAction it = (WizardAction)element$iv;
            boolean bl = false;
            it.check();
        }
    }

    /*
     * WARNING - void declaration
     */
    @SubscribeEvent
    private final void onQuit(PlayerQuitEvent e) {
        void $this$filterTo$iv$iv;
        Collection<WizardAction> collection = actionMap.values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"actionMap.values");
        Iterable $this$filter$iv = collection;
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            WizardAction it = (WizardAction)element$iv$iv;
            boolean bl = false;
            if (!Intrinsics.areEqual((Object)it.getPlayer().getName(), (Object)e.getPlayer().getName())) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        Iterable $this$forEach$iv = (List)destination$iv$iv;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            WizardAction it = (WizardAction)element$iv;
            boolean bl = false;
            Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
            WizardAction.cancel$default(it, false, 1, null);
            actionMap.remove(it.getEntityInstance().getUniqueId());
        }
    }

    @SubscribeEvent
    private final void onSelect(ConversationEvents.Pre e) {
        Object entity = e.getSession().getSource().getEntity();
        if (entity instanceof EntityInstance) {
            WizardAction wizardAction = actionMap.get(((EntityInstance)entity).getUniqueId());
            if (wizardAction == null) {
                return;
            }
            WizardAction action = wizardAction;
            if (action.getInfo().isConversationDisabled()) {
                e.setCancelled(true);
            }
        }
    }

    @SubscribeEvent
    private final void onSelect(ConversationEvents.Select e) {
        Object entity;
        if (e.getConversation() == null && (entity = e.getSource()) instanceof EntityInstance) {
            WizardAction action = actionMap.get(((EntityInstance)entity).getUniqueId());
            Object object = action;
            boolean bl = object != null && (object = ((WizardAction)object).getInfo()) != null ? ((WizardInfo)object).isConversationDisabled() : false;
            if (bl) {
                e.setCancelled(true);
            }
        }
    }

    static {
        Module.Companion.register(INSTANCE);
    }
}

