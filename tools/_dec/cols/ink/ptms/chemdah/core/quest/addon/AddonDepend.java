/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ObjectiveEvents;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.TemplateGroup;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.module.configuration.ConfigNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="depend")
@Option(type=Option.Type.ANY)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0006\b\u0007\u0018\u0000 \u000e2\u00020\u0001:\u0001\u000eB\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R \u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\r\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonDepend;", "Link/ptms/chemdah/core/quest/addon/Addon;", "root", "", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Ljava/lang/Object;Link/ptms/chemdah/core/quest/QuestContainer;)V", "depend", "", "", "getDepend", "()Ljava/util/List;", "setDepend", "(Ljava/util/List;)V", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAddonDepend.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonDepend.kt\nink/ptms/chemdah/core/quest/addon/AddonDepend\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,120:1\n1360#2:121\n1446#2,2:122\n1549#2:124\n1620#2,3:125\n1448#2,3:128\n766#2:131\n857#2,2:132\n*S KotlinDebug\n*F\n+ 1 AddonDepend.kt\nink/ptms/chemdah/core/quest/addon/AddonDepend\n*L\n53#1:121\n53#1:122,2\n57#1:124\n57#1:125,3\n53#1:128,3\n61#1:131\n61#1:132,2\n*E\n"})
public final class AddonDepend
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private List<String> depend;
    @ConfigNode(value="default-quest.allow-early-acceptance")
    private static boolean allowEarlyAcceptance = true;

    /*
     * WARNING - void declaration
     */
    public AddonDepend(@NotNull Object root2, @NotNull QuestContainer questContainer) {
        void $this$filterTo$iv$iv;
        void $this$filter$iv;
        String it;
        void $this$flatMapTo$iv$iv;
        Iterable $this$flatMap$iv;
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(root2, questContainer);
        Iterable iterable = CollectionKt.asList((Object)root2);
        AddonDepend addonDepend = this;
        boolean $i$f$flatMap = false;
        void var5_6 = $this$flatMap$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$flatMapTo = false;
        for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
            List list2;
            it = (String)element$iv$iv;
            boolean bl = false;
            if (StringsKt.startsWith$default((String)it, (String)"group:", (boolean)false, (int)2, null)) {
                Object object = ChemdahAPI.INSTANCE.getQuestTemplateGroup(StringsKt.substringAfter$default((String)it, (String)"group:", null, (int)2, null));
                if (object != null && (object = ((TemplateGroup)object).getQuests()) != null) {
                    void $this$mapTo$iv$iv;
                    Iterable $this$map$iv = (Iterable)object;
                    boolean $i$f$map = false;
                    Iterable iterable2 = $this$map$iv;
                    Collection destination$iv$iv2 = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    boolean $i$f$mapTo = false;
                    for (Object item$iv$iv : $this$mapTo$iv$iv) {
                        void q;
                        Template template = (Template)item$iv$iv;
                        Collection collection = destination$iv$iv2;
                        boolean bl2 = false;
                        collection.add(q.getId());
                    }
                    list2 = (List)destination$iv$iv2;
                } else {
                    list2 = CollectionsKt.emptyList();
                }
            } else {
                list2 = CollectionsKt.listOf((Object)it);
            }
            Iterable list$iv$iv = list2;
            CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
        }
        $this$flatMap$iv = (List)destination$iv$iv;
        boolean $i$f$filter = false;
        $this$flatMapTo$iv$iv = $this$filter$iv;
        destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            it = (String)element$iv$iv;
            boolean bl = false;
            boolean bl3 = !StringsKt.isBlank((CharSequence)it);
            if (!bl3) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        addonDepend.depend = (List)destination$iv$iv;
    }

    @NotNull
    public final List<String> getDepend() {
        return this.depend;
    }

    public final void setDepend(@NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.depend = list2;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0003J\u0010\u0010\r\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\u000eH\u0003J\u0010\u0010\u000f\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\u0010H\u0003J\u0012\u0010\u0011\u001a\n\u0012\u0004\u0012\u00020\u0013\u0018\u00010\u0012*\u00020\u0014J\u0012\u0010\u0015\u001a\u00020\u0004*\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0017R\u001e\u0010\u0003\u001a\u00020\u00048\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\b\u00a8\u0006\u0018"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonDepend$Companion;", "", "()V", "allowEarlyAcceptance", "", "getAllowEarlyAcceptance", "()Z", "setAllowEarlyAcceptance", "(Z)V", "onObjectiveAccepted", "", "e", "Link/ptms/chemdah/api/event/collect/QuestEvents$Accept$Pre;", "onObjectiveCompletePre", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Complete$Pre;", "onObjectiveContinuePre", "Link/ptms/chemdah/api/event/collect/ObjectiveEvents$Continue$Pre;", "depend", "", "", "Link/ptms/chemdah/core/quest/QuestContainer;", "isQuestDependCompleted", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nAddonDepend.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonDepend.kt\nink/ptms/chemdah/core/quest/addon/AddonDepend$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,120:1\n1726#2,3:121\n1726#2,3:124\n*S KotlinDebug\n*F\n+ 1 AddonDepend.kt\nink/ptms/chemdah/core/quest/addon/AddonDepend$Companion\n*L\n79#1:121,3\n85#1:124,3\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        public final boolean getAllowEarlyAcceptance() {
            return allowEarlyAcceptance;
        }

        public final void setAllowEarlyAcceptance(boolean bl) {
            allowEarlyAcceptance = bl;
        }

        @Nullable
        public final List<String> depend(@NotNull QuestContainer $this$depend) {
            Intrinsics.checkNotNullParameter((Object)$this$depend, (String)"<this>");
            AddonDepend addonDepend = (AddonDepend)$this$depend.addon("depend");
            return addonDepend != null ? addonDepend.getDepend() : null;
        }

        public final boolean isQuestDependCompleted(@NotNull QuestContainer $this$isQuestDependCompleted, @NotNull Player player) {
            Intrinsics.checkNotNullParameter((Object)$this$isQuestDependCompleted, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            QuestContainer questContainer = $this$isQuestDependCompleted;
            if (questContainer instanceof Template) {
                boolean bl;
                block12: {
                    List<String> list2 = this.depend($this$isQuestDependCompleted);
                    if (list2 == null) {
                        return true;
                    }
                    List<String> depends = list2;
                    Iterable $this$all$iv = depends;
                    boolean $i$f$all = false;
                    if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                        bl = true;
                    } else {
                        for (Object element$iv : $this$all$iv) {
                            String it = (String)element$iv;
                            boolean bl2 = false;
                            if (ChemdahAPI.INSTANCE.getChemdahProfile(player).isQuestCompleted(it)) continue;
                            bl = false;
                            break block12;
                        }
                        bl = true;
                    }
                }
                return bl;
            }
            if (questContainer instanceof Task) {
                boolean bl;
                block13: {
                    List<String> list3 = this.depend($this$isQuestDependCompleted);
                    if (list3 == null) {
                        return true;
                    }
                    List<String> depends = list3;
                    HashMap<String, Task> tasks = ((Task)$this$isQuestDependCompleted).getTemplate().getTaskMap();
                    Iterable $this$all$iv = depends;
                    boolean $i$f$all = false;
                    if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                        bl = true;
                    } else {
                        for (Object element$iv : $this$all$iv) {
                            boolean bl3;
                            String it = (String)element$iv;
                            boolean bl4 = false;
                            if (tasks.containsKey(it)) {
                                Task task = tasks.get(it);
                                Intrinsics.checkNotNull((Object)task);
                                bl3 = task.isCompleted(ChemdahAPI.INSTANCE.getChemdahProfile(player));
                            } else {
                                bl3 = ChemdahAPI.INSTANCE.getChemdahProfile(player).isQuestCompleted(it);
                            }
                            if (bl3) continue;
                            bl = false;
                            break block13;
                        }
                        bl = true;
                    }
                }
                return bl;
            }
            return true;
        }

        @SubscribeEvent
        private final void onObjectiveContinuePre(ObjectiveEvents.Continue.Pre e) {
            if (!this.isQuestDependCompleted(e.getTask(), e.getPlayerProfile().getPlayer())) {
                e.setCancelled(true);
            }
        }

        @SubscribeEvent
        private final void onObjectiveCompletePre(ObjectiveEvents.Complete.Pre e) {
            if (!this.isQuestDependCompleted(e.getTask(), e.getPlayerProfile().getPlayer()) || !this.isQuestDependCompleted(e.getQuest().getTemplate(), e.getPlayerProfile().getPlayer())) {
                e.setCancelled(true);
            }
        }

        @SubscribeEvent
        private final void onObjectiveAccepted(QuestEvents.Accept.Pre e) {
            if (!this.isQuestDependCompleted(e.getQuest(), e.getPlayerProfile().getPlayer()) && !this.getAllowEarlyAcceptance()) {
                e.setCancelled(true);
            }
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

