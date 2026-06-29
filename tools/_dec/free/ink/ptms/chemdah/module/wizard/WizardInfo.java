/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.common5.util.String2TimeKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle
 *  ink.ptms.chemdah.taboolib.module.navigation.Node
 *  ink.ptms.chemdah.taboolib.module.navigation.NodeEntity
 *  ink.ptms.chemdah.taboolib.module.navigation.Path
 *  ink.ptms.chemdah.taboolib.module.navigation.PathFinder
 *  ink.ptms.chemdah.taboolib.module.navigation.UtilsKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.wizard;

import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.chemdah.module.wizard.WizardAction;
import ink.ptms.chemdah.module.wizard.WizardPathCache;
import ink.ptms.chemdah.module.wizard.WizardSystem;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.common5.util.String2TimeKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle;
import ink.ptms.chemdah.taboolib.module.navigation.Node;
import ink.ptms.chemdah.taboolib.module.navigation.NodeEntity;
import ink.ptms.chemdah.taboolib.module.navigation.Path;
import ink.ptms.chemdah.taboolib.module.navigation.PathFinder;
import ink.ptms.chemdah.taboolib.module.navigation.UtilsKt;
import ink.ptms.chemdah.util.LocationKt;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0088\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u0015\n\u0000\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0002\b\r\n\u0002\u0010\u0006\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0016\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u001e\u0010M\u001a\b\u0012\u0004\u0012\u00020'0N2\u0006\u0010O\u001a\u00020P2\u0006\u0010Q\u001a\u00020RH\u0016J\b\u0010S\u001a\u00020'H\u0016J\b\u0010T\u001a\u00020\u0011H\u0002J\u000e\u0010U\u001a\b\u0012\u0004\u0012\u0002040\bH\u0016J\b\u0010V\u001a\u00020WH\u0016J\u0018\u0010X\u001a\u00020Y2\u0006\u0010O\u001a\u00020P2\u0006\u0010Q\u001a\u00020RH\u0016J\b\u0010Z\u001a\u00020WH\u0016J\b\u0010[\u001a\u00020WH\u0002R\u0016\u0010\u0007\u001a\n\u0012\u0004\u0012\u00020\t\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\n\u001a\u00020\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u001c\u0010\u0010\u001a\u0004\u0018\u00010\u0011X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u0013\"\u0004\b\u0014\u0010\u0015R\u001c\u0010\u0016\u001a\u0004\u0018\u00010\u0011X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\u0013\"\u0004\b\u0018\u0010\u0015R\u001c\u0010\u0019\u001a\u0004\u0018\u00010\u0011X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001a\u0010\u0013\"\u0004\b\u001b\u0010\u0015R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u001dR\u001a\u0010\u001e\u001a\u00020\u001fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b \u0010!\"\u0004\b\"\u0010#R\u0011\u0010$\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010\u0013R\u001a\u0010&\u001a\u00020'X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b&\u0010(\"\u0004\b)\u0010*R\u001a\u0010+\u001a\u00020'X\u0096\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b+\u0010(\"\u0004\b,\u0010*R\u001a\u0010-\u001a\u00020.X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b/\u00100\"\u0004\b1\u00102R \u00103\u001a\b\u0012\u0004\u0012\u0002040\bX\u0096\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b5\u00106\"\u0004\b7\u00108R$\u00109\u001a\u0012\u0012\u0004\u0012\u0002040:j\b\u0012\u0004\u0012\u000204`;X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b<\u0010=R\u000e\u0010>\u001a\u00020.X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010?\u001a\u00020\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b@\u0010\r\"\u0004\bA\u0010\u000fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\bB\u0010CR\u001a\u0010D\u001a\u00020\u001fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bE\u0010!\"\u0004\bF\u0010#R\u001c\u0010G\u001a\u0004\u0018\u00010HX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bI\u0010J\"\u0004\bK\u0010L\u00a8\u0006\\"}, d2={"Link/ptms/chemdah/module/wizard/WizardInfo;", "", "file", "Ljava/io/File;", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Ljava/io/File;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "cachedCoords", "", "", "eventCooldown", "", "getEventCooldown", "()J", "setEventCooldown", "(J)V", "eventOnContinue", "", "getEventOnContinue", "()Ljava/lang/String;", "setEventOnContinue", "(Ljava/lang/String;)V", "eventOnFinish", "getEventOnFinish", "setEventOnFinish", "eventOnWaiting", "getEventOnWaiting", "setEventOnWaiting", "getFile", "()Ljava/io/File;", "finishDistance", "", "getFinishDistance", "()D", "setFinishDistance", "(D)V", "id", "getId", "isConversationDisabled", "", "()Z", "setConversationDisabled", "(Z)V", "isPathListValid", "setPathListValid", "maxRetry", "", "getMaxRetry", "()I", "setMaxRetry", "(I)V", "nodes", "Lorg/bukkit/Location;", "getNodes", "()Ljava/util/List;", "setNodes", "(Ljava/util/List;)V", "pathList", "Ljava/util/ArrayList;", "Lkotlin1822/collections/ArrayList;", "getPathList", "()Ljava/util/ArrayList;", "retryCount", "retryDelay", "getRetryDelay", "setRetryDelay", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "waitingDistance", "getWaitingDistance", "setWaitingDistance", "world", "Lorg/bukkit/World;", "getWorld", "()Lorg/bukkit/World;", "setWorld", "(Lorg/bukkit/World;)V", "apply", "Ljava/util/concurrent/CompletableFuture;", "player", "Lorg/bukkit/entity/Player;", "entityInstance", "Link/ptms/adyeshach/core/entity/EntityInstance;", "checkPathListValid", "computeCacheKey", "createPathList", "init", "", "newAction", "Link/ptms/chemdah/module/wizard/WizardAction;", "sendParticle", "tryInit", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nWizardInfo.kt\nKotlin\n*S Kotlin\n*F\n+ 1 WizardInfo.kt\nink/ptms/chemdah/module/wizard/WizardInfo\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,266:1\n1549#2:267\n1620#2,2:268\n1622#2:271\n1549#2:272\n1620#2,3:273\n1855#2,2:276\n1549#2:278\n1620#2,3:279\n12#3:270\n*S KotlinDebug\n*F\n+ 1 WizardInfo.kt\nink/ptms/chemdah/module/wizard/WizardInfo\n*L\n46#1:267\n46#1:268,2\n46#1:271\n145#1:272\n145#1:273,3\n225#1:276,2\n244#1:278\n244#1:279,3\n46#1:270\n*E\n"})
public class WizardInfo {
    @NotNull
    private final File file;
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
    @Nullable
    private String eventOnFinish;
    private long eventCooldown;
    private boolean isConversationDisabled;
    private int maxRetry;
    private long retryDelay;
    private int retryCount;
    @Nullable
    private List<int[]> cachedCoords;
    @NotNull
    private final ArrayList<Location> pathList;
    private boolean isPathListValid;

    /*
     * WARNING - void declaration
     */
    public WizardInfo(@NotNull File file, @NotNull ConfigurationSection root2) {
        void $this$mapTo$iv$iv;
        void $this$map$iv;
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.file = file;
        this.root = root2;
        this.id = this.root.getName();
        this.world = Bukkit.getWorld((String)String.valueOf(this.root.getString("in")));
        Iterable iterable = this.root.getStringList("nodes");
        WizardInfo wizardInfo = this;
        boolean $i$f$map = false;
        void var5_6 = $this$map$iv;
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
        this.eventOnFinish = this.root.getString("event.finish");
        String string = this.root.getString("event-cooldown");
        this.eventCooldown = string != null ? String2TimeKt.parseMillis((String)string) : 0L;
        this.isConversationDisabled = this.root.getBoolean("disable-conversation");
        this.maxRetry = this.root.getInt("init-retry", 3);
        this.retryDelay = this.root.getLong("init-retry-delay", 20L);
        this.pathList = new ArrayList();
    }

    @NotNull
    public final File getFile() {
        return this.file;
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
    public List<Location> getNodes() {
        return this.nodes;
    }

    public void setNodes(@NotNull List<? extends Location> list2) {
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

    @Nullable
    public final String getEventOnFinish() {
        return this.eventOnFinish;
    }

    public final void setEventOnFinish(@Nullable String string) {
        this.eventOnFinish = string;
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

    public final int getMaxRetry() {
        return this.maxRetry;
    }

    public final void setMaxRetry(int n) {
        this.maxRetry = n;
    }

    public final long getRetryDelay() {
        return this.retryDelay;
    }

    public final void setRetryDelay(long l) {
        this.retryDelay = l;
    }

    @NotNull
    public ArrayList<Location> getPathList() {
        return this.pathList;
    }

    public boolean isPathListValid() {
        return this.isPathListValid;
    }

    public void setPathListValid(boolean bl) {
        this.isPathListValid = bl;
    }

    public void init() {
        this.tryInit();
    }

    /*
     * WARNING - void declaration
     */
    private final void tryInit() {
        Object[] objectArray;
        if (this.world == null) {
            this.world = Bukkit.getWorld((String)String.valueOf(this.root.getString("in")));
        }
        this.getPathList().clear();
        String cacheKey = this.computeCacheKey();
        if (this.cachedCoords == null) {
            this.cachedCoords = WizardPathCache.INSTANCE.load(this.id, cacheKey);
        }
        if (this.cachedCoords != null) {
            if (this.world != null) {
                void $this$mapTo$iv$iv;
                void $this$map$iv;
                ArrayList<Location> arrayList = this.getPathList();
                List<int[]> list2 = this.cachedCoords;
                Intrinsics.checkNotNull(list2);
                objectArray = (Object[])list2;
                ArrayList<Location> arrayList2 = arrayList;
                boolean $i$f$map = false;
                void var4_10 = $this$map$iv;
                Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (Object item$iv$iv : $this$mapTo$iv$iv) {
                    void it;
                    int[] nArray = (int[])item$iv$iv;
                    Collection collection = destination$iv$iv;
                    boolean bl = false;
                    collection.add(new Location(this.world, (double)it[0], (double)it[1], (double)it[2]));
                }
                arrayList2.addAll((List)destination$iv$iv);
                this.setPathListValid(this.checkPathListValid());
                if (this.isPathListValid()) {
                    objectArray = new Object[]{"[Wizard] Path loaded from cache: " + this.id + " (" + this.getPathList().size() + " points)"};
                    IOKt.info((Object[])objectArray);
                    this.cachedCoords = null;
                    return;
                }
                this.cachedCoords = null;
            } else {
                if (this.retryCount < this.maxRetry) {
                    int n = this.retryCount;
                    this.retryCount = n + 1;
                    Object[] objectArray2 = new Object[]{"[Wizard] Cached path found for " + this.id + " but world not loaded, retry " + this.retryCount + '/' + this.maxRetry + "..."};
                    IOKt.info((Object[])objectArray2);
                    ExecutorKt.submit$default((boolean)false, (boolean)false, (long)this.retryDelay, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this){
                        final /* synthetic */ WizardInfo this$0;
                        {
                            this.this$0 = $receiver;
                            super(1);
                        }

                        public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                            Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                            WizardInfo.access$tryInit(this.this$0);
                        }
                    }), (int)11, null);
                } else {
                    Object[] objectArray3 = new Object[]{"[Wizard] World not loaded for " + this.id + " after " + this.maxRetry + " retries"};
                    IOKt.warning((Object[])objectArray3);
                }
                return;
            }
        }
        this.getPathList().clear();
        this.getPathList().addAll((Collection<Location>)this.createPathList());
        this.setPathListValid(this.checkPathListValid());
        if (this.isPathListValid()) {
            WizardPathCache.INSTANCE.save(this.id, cacheKey, (List<? extends Location>)this.getPathList());
            if (this.retryCount > 0) {
                objectArray = new Object[]{"[Wizard] Path init succeeded for " + this.id + " after " + this.retryCount + " retries, cached (" + this.getPathList().size() + " points)"};
                IOKt.info((Object[])objectArray);
            } else {
                objectArray = new Object[]{"[Wizard] Path init succeeded for " + this.id + ", cached (" + this.getPathList().size() + " points)"};
                IOKt.info((Object[])objectArray);
            }
        }
        if (!this.isPathListValid() && this.retryCount < this.maxRetry) {
            int n = this.retryCount;
            this.retryCount = n + 1;
            Object[] objectArray4 = new Object[]{"[Wizard] Path init failed for " + this.id + ", retry " + this.retryCount + '/' + this.maxRetry + " in " + this.retryDelay + "t..."};
            IOKt.warning((Object[])objectArray4);
            ExecutorKt.submit$default((boolean)false, (boolean)false, (long)this.retryDelay, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this){
                final /* synthetic */ WizardInfo this$0;
                {
                    this.this$0 = $receiver;
                    super(1);
                }

                public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                    Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                    WizardInfo.access$tryInit(this.this$0);
                }
            }), (int)11, null);
        }
    }

    private final String computeCacheKey() {
        String worldName = String.valueOf(this.root.getString("in"));
        String nodesStr = CollectionsKt.joinToString$default((Iterable)this.root.getStringList("nodes"), (CharSequence)";", null, null, (int)0, null, null, (int)62, null);
        return worldName + '|' + nodesStr;
    }

    @NotNull
    public WizardAction newAction(@NotNull Player player2, @NotNull EntityInstance entityInstance) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)entityInstance, (String)"entityInstance");
        return new WizardAction(player2, entityInstance, this);
    }

    @NotNull
    public CompletableFuture<Boolean> apply(@NotNull Player player2, @NotNull EntityInstance entityInstance) {
        CompletableFuture<Boolean> completableFuture;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)entityInstance, (String)"entityInstance");
        if (this.isPathListValid()) {
            WizardAction action = this.newAction(player2, entityInstance).check();
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

    public void sendParticle() {
        Iterable $this$forEach$iv = this.getPathList();
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Location it = (Location)element$iv;
            boolean bl = false;
            World world = this.world;
            if (world == null) continue;
            Particle particle = XParticle.HAPPY_VILLAGER.get();
            Intrinsics.checkNotNull((Object)particle);
            world.spawnParticle(particle, it.getX() + 0.5, it.getY() + 0.5, it.getZ() + 0.5, 10, 0.0, 0.0, 0.0, 0.0);
        }
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
        int n = this.getNodes().size() - 1;
        for (int i = 0; i < n; ++i) {
            void $this$mapTo$iv$iv;
            List pathList;
            Location a = this.getNodes().get(i);
            Location b = this.getNodes().get(i + 1);
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
        int n = this.getPathList().size() - 1;
        for (int i = 0; i < n; ++i) {
            Location b;
            Location a;
            Intrinsics.checkNotNullExpressionValue((Object)this.getPathList().get(i), (String)"pathList[i]");
            Intrinsics.checkNotNullExpressionValue((Object)this.getPathList().get(i + 1), (String)"pathList[i + 1]");
            if (!(LocationKt.safeDistance(a, b) > 2.0)) continue;
            String at = "" + a.getBlockX() + ',' + a.getBlockY() + ',' + a.getBlockZ();
            String bt = "" + b.getBlockX() + ',' + b.getBlockY() + ',' + b.getBlockZ();
            Object[] objectArray = new Object[]{"[Wizard] Discontinuous path list: " + this.id + " (" + at + " -> " + bt + " distance: " + Coerce.format((double)LocationKt.safeDistance(a, b)) + ')'};
            IOKt.warning((Object[])objectArray);
            return false;
        }
        return !((Collection)this.getPathList()).isEmpty();
    }

    public static final /* synthetic */ void access$tryInit(WizardInfo $this) {
        $this.tryInit();
    }
}

