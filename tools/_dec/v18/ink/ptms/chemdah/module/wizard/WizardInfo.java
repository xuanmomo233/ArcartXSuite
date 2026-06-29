/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  ink.ptms.adyeshach.core.util.UtilsKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.common5.util.String2TimeKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.navigation.Node
 *  ink.ptms.chemdah.taboolib.module.navigation.NodeEntity
 *  ink.ptms.chemdah.taboolib.module.navigation.Path
 *  ink.ptms.chemdah.taboolib.module.navigation.PathFinder
 *  ink.ptms.chemdah.taboolib.module.navigation.UtilsKt
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.wizard;

import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.chemdah.module.wizard.WizardAction;
import ink.ptms.chemdah.module.wizard.WizardSystem;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.common5.util.String2TimeKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.navigation.Node;
import ink.ptms.chemdah.taboolib.module.navigation.NodeEntity;
import ink.ptms.chemdah.taboolib.module.navigation.Path;
import ink.ptms.chemdah.taboolib.module.navigation.PathFinder;
import ink.ptms.chemdah.taboolib.module.navigation.UtilsKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000r\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0002\b\b\n\u0002\u0010\u0006\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010:\u001a\b\u0012\u0004\u0012\u00020\u001d0;2\u0006\u0010<\u001a\u00020=2\u0006\u0010>\u001a\u00020?H\u0016J\b\u0010@\u001a\u00020\u001dH\u0016J\u000e\u0010A\u001a\b\u0012\u0004\u0012\u00020%0$H\u0016J\b\u0010B\u001a\u00020CH\u0016J\u0018\u0010D\u001a\u00020E2\u0006\u0010<\u001a\u00020=2\u0006\u0010>\u001a\u00020?H\u0016R\u001a\u0010\u0005\u001a\u00020\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\nR\u001c\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u001c\u0010\u0011\u001a\u0004\u0018\u00010\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u000e\"\u0004\b\u0013\u0010\u0010R\u001a\u0010\u0014\u001a\u00020\u0015X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u0017\"\u0004\b\u0018\u0010\u0019R\u0011\u0010\u001a\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u000eR\u001a\u0010\u001c\u001a\u00020\u001dX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001c\u0010\u001e\"\u0004\b\u001f\u0010 R\u001a\u0010!\u001a\u00020\u001dX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b!\u0010\u001e\"\u0004\b\"\u0010 R \u0010#\u001a\b\u0012\u0004\u0012\u00020%0$X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b&\u0010'\"\u0004\b(\u0010)R!\u0010*\u001a\u0012\u0012\u0004\u0012\u00020%0+j\b\u0012\u0004\u0012\u00020%`,\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010.R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b/\u00100R\u001a\u00101\u001a\u00020\u0015X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b2\u0010\u0017\"\u0004\b3\u0010\u0019R\u001c\u00104\u001a\u0004\u0018\u000105X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b6\u00107\"\u0004\b8\u00109\u00a8\u0006F"}, d2={"Link/ptms/chemdah/module/wizard/WizardInfo;", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "eventCooldown", "", "getEventCooldown", "()J", "setEventCooldown", "(J)V", "eventOnContinue", "", "getEventOnContinue", "()Ljava/lang/String;", "setEventOnContinue", "(Ljava/lang/String;)V", "eventOnWaiting", "getEventOnWaiting", "setEventOnWaiting", "finishDistance", "", "getFinishDistance", "()D", "setFinishDistance", "(D)V", "id", "getId", "isConversationDisabled", "", "()Z", "setConversationDisabled", "(Z)V", "isPathListValid", "setPathListValid", "nodes", "", "Lorg/bukkit/Location;", "getNodes", "()Ljava/util/List;", "setNodes", "(Ljava/util/List;)V", "pathList", "Ljava/util/ArrayList;", "Lkotlin1822/collections/ArrayList;", "getPathList", "()Ljava/util/ArrayList;", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "waitingDistance", "getWaitingDistance", "setWaitingDistance", "world", "Lorg/bukkit/World;", "getWorld", "()Lorg/bukkit/World;", "setWorld", "(Lorg/bukkit/World;)V", "apply", "Ljava/util/concurrent/CompletableFuture;", "player", "Lorg/bukkit/entity/Player;", "entityInstance", "Link/ptms/adyeshach/core/entity/EntityInstance;", "checkPathListValid", "createPathList", "init", "", "newAction", "Link/ptms/chemdah/module/wizard/WizardAction;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nWizardInfo.kt\nKotlin\n*S Kotlin\n*F\n+ 1 WizardInfo.kt\nink/ptms/chemdah/module/wizard/WizardInfo\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,159:1\n1549#2:160\n1620#2,2:161\n1622#2:164\n1549#2:165\n1620#2,3:166\n11#3:163\n*S KotlinDebug\n*F\n+ 1 WizardInfo.kt\nink/ptms/chemdah/module/wizard/WizardInfo\n*L\n42#1:160\n42#1:161,2\n42#1:164\n137#1:165\n137#1:166,3\n42#1:163\n*E\n"})
public class WizardInfo {
    @NotNull
    private final ConfigurationSection root;
    @NotNull
    private final String id;
    @Nullable
    private World world;
    @NotNull
    private List<? extends Location> nodes;
    private double finishDistance;
    private double waitingDistance;
    @Nullable
    private String eventOnWaiting;
    @Nullable
    private String eventOnContinue;
    private long eventCooldown;
    private boolean isConversationDisabled;
    @NotNull
    private final ArrayList<Location> pathList;
    private boolean isPathListValid;

    /*
     * WARNING - void declaration
     */
    public WizardInfo(@NotNull ConfigurationSection root2) {
        void $this$mapTo$iv$iv;
        void $this$map$iv;
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.root = root2;
        this.id = this.root.getName();
        this.world = Bukkit.getWorld((String)String.valueOf(this.root.getString("in")));
        Iterable iterable = this.root.getStringList("nodes");
        WizardInfo wizardInfo = this;
        boolean $i$f$map = false;
        void var4_5 = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            String string = (String)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            String[] stringArray = new String[]{" "};
            Object $this$cdouble$iv = StringsKt.split$default((CharSequence)((CharSequence)it), (String[])stringArray, (boolean)false, (int)0, (int)6, null).get(0);
            boolean $i$f$getCdouble = false;
            double d = Coerce.toDouble($this$cdouble$iv);
            $this$cdouble$iv = new String[]{" "};
            $this$cdouble$iv = StringsKt.split$default((CharSequence)((CharSequence)it), $this$cdouble$iv, (boolean)false, (int)0, (int)6, null).get(1);
            $i$f$getCdouble = false;
            double d2 = Coerce.toDouble($this$cdouble$iv);
            $this$cdouble$iv = new String[]{" "};
            $this$cdouble$iv = StringsKt.split$default((CharSequence)((CharSequence)it), $this$cdouble$iv, (boolean)false, (int)0, (int)6, null).get(2);
            $i$f$getCdouble = false;
            collection.add(new Location(this.world, d, d2, Coerce.toDouble($this$cdouble$iv)));
        }
        wizardInfo.nodes = (List)destination$iv$iv;
        this.finishDistance = this.root.getDouble("finish-distance", 2.0);
        this.waitingDistance = this.root.getDouble("waiting-distance");
        this.eventOnWaiting = this.root.getString("event.waiting");
        this.eventOnContinue = this.root.getString("event.continue");
        String string = this.root.getString("event-cooldown");
        this.eventCooldown = string != null ? String2TimeKt.parseMillis((String)string) : 0L;
        this.isConversationDisabled = this.root.getBoolean("disable-conversation");
        this.pathList = new ArrayList();
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    @Nullable
    public final World getWorld() {
        return this.world;
    }

    public final void setWorld(@Nullable World world) {
        this.world = world;
    }

    @NotNull
    public final List<Location> getNodes() {
        return this.nodes;
    }

    public final void setNodes(@NotNull List<? extends Location> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.nodes = list2;
    }

    public final double getFinishDistance() {
        return this.finishDistance;
    }

    public final void setFinishDistance(double d) {
        this.finishDistance = d;
    }

    public final double getWaitingDistance() {
        return this.waitingDistance;
    }

    public final void setWaitingDistance(double d) {
        this.waitingDistance = d;
    }

    @Nullable
    public final String getEventOnWaiting() {
        return this.eventOnWaiting;
    }

    public final void setEventOnWaiting(@Nullable String string) {
        this.eventOnWaiting = string;
    }

    @Nullable
    public final String getEventOnContinue() {
        return this.eventOnContinue;
    }

    public final void setEventOnContinue(@Nullable String string) {
        this.eventOnContinue = string;
    }

    public final long getEventCooldown() {
        return this.eventCooldown;
    }

    public final void setEventCooldown(long l) {
        this.eventCooldown = l;
    }

    public final boolean isConversationDisabled() {
        return this.isConversationDisabled;
    }

    public final void setConversationDisabled(boolean bl) {
        this.isConversationDisabled = bl;
    }

    @NotNull
    public final ArrayList<Location> getPathList() {
        return this.pathList;
    }

    public final boolean isPathListValid() {
        return this.isPathListValid;
    }

    public final void setPathListValid(boolean bl) {
        this.isPathListValid = bl;
    }

    public void init() {
        this.pathList.addAll((Collection<Location>)this.createPathList());
        this.isPathListValid = this.checkPathListValid();
    }

    @NotNull
    public WizardAction newAction(@NotNull Player player, @NotNull EntityInstance entityInstance) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)entityInstance, (String)"entityInstance");
        return new WizardAction(player, entityInstance, this);
    }

    @NotNull
    public CompletableFuture<Boolean> apply(@NotNull Player player, @NotNull EntityInstance entityInstance) {
        CompletableFuture<Boolean> completableFuture;
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)entityInstance, (String)"entityInstance");
        if (this.isPathListValid) {
            WizardAction action = this.newAction(player, entityInstance).check();
            ((Map)WizardSystem.INSTANCE.getActionMap()).put(entityInstance.getUniqueId(), action);
            action.start();
            completableFuture = action.getOnFinish();
        } else {
            Object[] objectArray = new Object[]{"[Wizard] Invalid path list: " + this.id};
            IOKt.warning((Object[])objectArray);
            CompletableFuture<Boolean> completableFuture2 = CompletableFuture.completedFuture(false);
            completableFuture = completableFuture2;
            Intrinsics.checkNotNullExpressionValue(completableFuture2, (String)"{\n            warning(\"[\u2026edFuture(false)\n        }");
        }
        return completableFuture;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public List<Location> createPathList() {
        if (this.world == null) {
            Object[] objectArray = new Object[]{"[Wizard] World not found: " + this.id};
            IOKt.warning((Object[])objectArray);
            return CollectionsKt.emptyList();
        }
        List path = new ArrayList();
        int n = this.nodes.size() - 1;
        for (int i = 0; i < n; ++i) {
            void $this$mapTo$iv$iv;
            List pathList;
            Location a = this.nodes.get(i);
            Location b = this.nodes.get(i + 1);
            Path path2 = PathFinder.findPath$default((PathFinder)UtilsKt.createPathfinder((NodeEntity)new NodeEntity(a, 2.0, 0.0, 0.0, false, false, false, null, null, 0.0f, 1020, null)), (Location)b, (float)32.0f, (int)0, (float)0.0f, (int)12, null);
            List list2 = pathList = path2 != null ? path2.getNodes() : null;
            if (pathList == null) continue;
            Collection collection = path;
            Iterable $this$map$iv = pathList;
            boolean $i$f$map = false;
            Iterable iterable = $this$map$iv;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                void it;
                Node node = (Node)item$iv$iv;
                Collection collection2 = destination$iv$iv;
                boolean bl = false;
                Vector vector = it.asBlockPos();
                World world = this.world;
                Intrinsics.checkNotNull((Object)world);
                Location location = vector.toLocation(world);
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.asBlockPos().toLocation(world!!)");
                collection2.add(location);
            }
            Iterable iterable2 = (List)destination$iv$iv;
            CollectionsKt.addAll((Collection)collection, (Iterable)iterable2);
        }
        return path;
    }

    public boolean checkPathListValid() {
        int n = this.pathList.size() - 1;
        for (int i = 0; i < n; ++i) {
            Location b;
            Location a;
            Intrinsics.checkNotNullExpressionValue((Object)this.pathList.get(i), (String)"pathList[i]");
            Intrinsics.checkNotNullExpressionValue((Object)this.pathList.get(i + 1), (String)"pathList[i + 1]");
            if (!(ink.ptms.adyeshach.core.util.UtilsKt.safeDistance((Location)a, (Location)b) > 2.0)) continue;
            String at = "" + a.getBlockX() + ',' + a.getBlockY() + ',' + a.getBlockZ();
            String bt = "" + b.getBlockX() + ',' + b.getBlockY() + ',' + b.getBlockZ();
            Object[] objectArray = new Object[]{"[Wizard] Discontinuous path list: " + this.id + " (" + at + " -> " + bt + " distance: " + Coerce.format((double)ink.ptms.adyeshach.core.util.UtilsKt.safeDistance((Location)a, (Location)b)) + ')'};
            IOKt.warning((Object[])objectArray);
            return false;
        }
        return !((Collection)this.pathList).isEmpty();
    }
}

