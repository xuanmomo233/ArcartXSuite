/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.util.ItemMatcherKt
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.ranges.RangesKt
 *  org.bukkit.event.Event
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.PlayerInventory
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.platform.util.ItemMatcherKt;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.ranges.RangesKt;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Dependency(plugin="minecraft")
@MetaInfo(source="chemdah", name="\u73a9\u5bb6\u80cc\u5305\u76ee\u6807", description={"\u68c0\u67e5\u6216\u6d88\u8017\u73a9\u5bb6\u80cc\u5305\u4e2d\u7684\u7269\u54c1", "\u652f\u6301\u4f4d\u7f6e\u3001\u7269\u54c1\u7c7b\u578b\u3001\u6570\u91cf\u3001\u6d88\u8017\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7528\u4e8e\u7269\u54c1\u6536\u96c6\u6216\u6d88\u8017\u7c7b\u4efb\u52a1"}, alias={"\u73a9\u5bb6\u80cc\u5305", "\u80cc\u5305\u7269\u54c1", "inventory"}, params={@ParamInfo(name="position", type="location", description="\u68c0\u6d4b\u4f4d\u7f6e\u6761\u4ef6"), @ParamInfo(name="item", type="string", required=true, description="\u7269\u54c1\u7c7b\u578b\u6216\u9009\u62e9\u5668\uff0c\u5982 'stone' \u6216 'enchanted_sword'"), @ParamInfo(name="amount", type="number", description="\u6240\u9700\u7269\u54c1\u6570\u91cf\uff0c\u9ed8\u8ba4\u4e3a 1"), @ParamInfo(name="consume", type="boolean", description="\u662f\u5426\u6d88\u8017\u7269\u54c1\uff0c\u9ed8\u8ba4\u4e3a false")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0003\u001c\u001d\u001eB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\u0018\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u001bH\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u001a\u0010\b\u001a\u00020\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u0014\u0010\u000e\u001a\u00020\u000fX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u0010R\u0014\u0010\u0011\u001a\u00020\u000fX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010R\u0014\u0010\u0012\u001a\u00020\u0013X\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015\u00a8\u0006\u001f"}, d2={"Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/Event;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "handler", "Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$Handler;", "getHandler", "()Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$Handler;", "setHandler", "(Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$Handler;)V", "isListener", "", "()Z", "isTickable", "name", "", "getName", "()Ljava/lang/String;", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Handler", "HasResult", "TakeResult", "Chemdah"})
public final class IPlayerInventory
extends ObjectiveCountableI<Event> {
    @NotNull
    public static final IPlayerInventory INSTANCE = new IPlayerInventory();
    @NotNull
    private static final String name = "player inventory";
    private static final boolean isListener;
    private static final boolean isTickable;
    @NotNull
    private static Handler handler;

    private IPlayerInventory() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<Event> getEvent() {
        return Event.class;
    }

    @Override
    public boolean isListener() {
        return isListener;
    }

    @Override
    public boolean isTickable() {
        return isTickable;
    }

    @Override
    @NotNull
    public Progress getProgress(@NotNull PlayerProfile profile, @NotNull Task task) {
        boolean consume;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Data data2 = task.getCondition().get("consume");
        boolean bl = data2 != null ? data2.toBoolean() : (consume = false);
        if (consume) {
            return super.getProgress(profile, task);
        }
        Data data3 = task.getCondition().get("item");
        Intrinsics.checkNotNull((Object)data3);
        InferItem item2 = data3.toInferItem();
        Data data4 = task.getCondition().get("amount");
        int amount = data4 != null ? data4.toInt() : 1;
        return handler.getProgress(profile, task, item2, amount);
    }

    @NotNull
    public final Handler getHandler() {
        return handler;
    }

    public final void setHandler(@NotNull Handler handler2) {
        Intrinsics.checkNotNullParameter((Object)handler2, (String)"<set-?>");
        handler = handler2;
    }

    private static final Object _init_$lambda$0(PlayerProfile profile, Task task, Event event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)event, (String)"<anonymous parameter 2>");
        return profile.getPlayer().getLocation();
    }

    private static final Boolean _init_$lambda$1(PlayerProfile profile, Task task, Event event) {
        Boolean bl;
        boolean consume;
        Data data2 = task.getCondition().get("item");
        Intrinsics.checkNotNull((Object)data2);
        InferItem item2 = data2.toInferItem();
        Data data3 = task.getCondition().get("amount");
        int amount = data3 != null ? data3.toInt() : 1;
        Data data4 = task.getCondition().get("consume");
        boolean bl2 = data4 != null ? data4.toBoolean() : (consume = false);
        if (consume) {
            Intrinsics.checkNotNullExpressionValue((Object)profile, (String)"profile");
            bl = handler.takeItem(profile, task, item2, amount).getSuccess();
        } else {
            Intrinsics.checkNotNullExpressionValue((Object)profile, (String)"profile");
            bl = handler.hasItem(profile, task, item2, amount).getSuccess();
        }
        return bl;
    }

    static {
        isTickable = true;
        INSTANCE.addCondition("position", "Location", IPlayerInventory::_init_$lambda$0);
        INSTANCE.addFullCondition("item", "ItemStack", IPlayerInventory::_init_$lambda$1);
        INSTANCE.addPlaceholderCondition("amount", "Number");
        INSTANCE.addPlaceholderCondition("consume", "Boolean");
        handler = new Handler();
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J*\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\u0007\u001a\u0004\u0018\u00010\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016J*\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\u0007\u001a\u0004\u0018\u00010\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016J*\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\u0007\u001a\u0004\u0018\u00010\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$Handler;", "", "()V", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "item", "Link/ptms/chemdah/core/quest/selector/InferItem;", "amount", "", "hasItem", "Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$HasResult;", "takeItem", "Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$TakeResult;", "Chemdah"})
    public static class Handler {
        @NotNull
        public HasResult hasItem(@NotNull PlayerProfile profile, @Nullable Task task, @NotNull InferItem item2, int amount) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            PlayerInventory playerInventory = profile.getPlayer().getInventory();
            Intrinsics.checkNotNullExpressionValue((Object)playerInventory, (String)"profile.player.inventory");
            PlayerInventory inventory = playerInventory;
            int count2 = ItemMatcherKt.countItem((Inventory)((Inventory)inventory), (Function1)((Function1)new Function1<ItemStack, Boolean>(item2){
                final /* synthetic */ InferItem $item;
                {
                    this.$item = $item;
                    super(1);
                }

                @NotNull
                public final Boolean invoke(@NotNull ItemStack it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    return this.$item.isItem(it);
                }
            }));
            Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"player", (Object)count2)};
            return new HasResult(count2 >= amount, MapsKt.hashMapOf((Pair[])pairArray));
        }

        @NotNull
        public TakeResult takeItem(@NotNull PlayerProfile profile, @Nullable Task task, @NotNull InferItem item2, int amount) {
            TakeResult takeResult;
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            HasResult hasItem2 = this.hasItem(profile, task, item2, amount);
            if (hasItem2.getSuccess()) {
                PlayerInventory playerInventory = profile.getPlayer().getInventory();
                Intrinsics.checkNotNullExpressionValue((Object)playerInventory, (String)"profile.player.inventory");
                takeResult = new TakeResult(ItemMatcherKt.takeItem$default((Inventory)((Inventory)playerInventory), (int)amount, null, (Function1)((Function1)new Function1<ItemStack, Boolean>(item2){
                    final /* synthetic */ InferItem $item;
                    {
                        this.$item = $item;
                        super(1);
                    }

                    @NotNull
                    public final Boolean invoke(@NotNull ItemStack it) {
                        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                        return this.$item.isItem(it);
                    }
                }), (int)2, null), 0);
            } else {
                takeResult = new TakeResult(false, hasItem2.getTotalHas());
            }
            return takeResult;
        }

        @NotNull
        public Progress getProgress(@NotNull PlayerProfile profile, @Nullable Task task, @NotNull InferItem item2, int amount) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            PlayerInventory playerInventory = profile.getPlayer().getInventory();
            Intrinsics.checkNotNullExpressionValue((Object)playerInventory, (String)"profile.player.inventory");
            int value2 = RangesKt.coerceAtMost((int)ItemMatcherKt.countItem((Inventory)((Inventory)playerInventory), (Function1)((Function1)new Function1<ItemStack, Boolean>(item2){
                final /* synthetic */ InferItem $item;
                {
                    this.$item = $item;
                    super(1);
                }

                @NotNull
                public final Boolean invoke(@NotNull ItemStack it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    return this.$item.isItem(it);
                }
            })), (int)amount);
            return Progress.Companion.toProgress$default(Progress.Companion, value2, amount, 0.0, 2, null);
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\b\n\u0002\b\u0012\b\u0086\b\u0018\u00002\u00020\u0001B!\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005\u00a2\u0006\u0002\u0010\bJ\t\u0010\u0012\u001a\u00020\u0003H\u00c6\u0003J\u0015\u0010\u0013\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005H\u00c6\u0003J)\u0010\u0014\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u0014\b\u0002\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005H\u00c6\u0001J\u0013\u0010\u0015\u001a\u00020\u00032\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0017\u001a\u00020\u0007H\u00d6\u0001J\t\u0010\u0018\u001a\u00020\u0006H\u00d6\u0001R\u001d\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u000b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0011\u0010\u0010\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\r\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$HasResult;", "", "success", "", "count", "", "", "", "(ZLjava/util/Map;)V", "getCount", "()Ljava/util/Map;", "playerHas", "getPlayerHas", "()I", "getSuccess", "()Z", "totalHas", "getTotalHas", "component1", "component2", "copy", "equals", "other", "hashCode", "toString", "Chemdah"})
    public static final class HasResult {
        private final boolean success;
        @NotNull
        private final Map<String, Integer> count;
        private final int playerHas;
        private final int totalHas;

        public HasResult(boolean success, @NotNull Map<String, Integer> count2) {
            Intrinsics.checkNotNullParameter(count2, (String)"count");
            this.success = success;
            this.count = count2;
            Integer n = this.count.get("player");
            this.playerHas = n != null ? n : 0;
            this.totalHas = CollectionsKt.sumOfInt((Iterable)this.count.values());
        }

        public final boolean getSuccess() {
            return this.success;
        }

        @NotNull
        public final Map<String, Integer> getCount() {
            return this.count;
        }

        public final int getPlayerHas() {
            return this.playerHas;
        }

        public final int getTotalHas() {
            return this.totalHas;
        }

        public final boolean component1() {
            return this.success;
        }

        @NotNull
        public final Map<String, Integer> component2() {
            return this.count;
        }

        @NotNull
        public final HasResult copy(boolean success, @NotNull Map<String, Integer> count2) {
            Intrinsics.checkNotNullParameter(count2, (String)"count");
            return new HasResult(success, count2);
        }

        public static /* synthetic */ HasResult copy$default(HasResult hasResult, boolean bl, Map map, int n, Object object) {
            if ((n & 1) != 0) {
                bl = hasResult.success;
            }
            if ((n & 2) != 0) {
                map = hasResult.count;
            }
            return hasResult.copy(bl, map);
        }

        @NotNull
        public String toString() {
            return "HasResult(success=" + this.success + ", count=" + this.count + ')';
        }

        public int hashCode() {
            int n = this.success ? 1 : 0;
            if (n != 0) {
                n = 1;
            }
            int result = n;
            result = result * 31 + ((Object)this.count).hashCode();
            return result;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HasResult)) {
                return false;
            }
            HasResult hasResult = (HasResult)other;
            if (this.success != hasResult.success) {
                return false;
            }
            return Intrinsics.areEqual(this.count, hasResult.count);
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\b\n\u0002\b\f\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0005H\u00c6\u0003J\u001d\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u00032\b\u0010\u000f\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00d6\u0001J\t\u0010\u0011\u001a\u00020\u0012H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$TakeResult;", "", "success", "", "totalHas", "", "(ZI)V", "getSuccess", "()Z", "getTotalHas", "()I", "component1", "component2", "copy", "equals", "other", "hashCode", "toString", "", "Chemdah"})
    public static final class TakeResult {
        private final boolean success;
        private final int totalHas;

        public TakeResult(boolean success, int totalHas) {
            this.success = success;
            this.totalHas = totalHas;
        }

        public final boolean getSuccess() {
            return this.success;
        }

        public final int getTotalHas() {
            return this.totalHas;
        }

        public final boolean component1() {
            return this.success;
        }

        public final int component2() {
            return this.totalHas;
        }

        @NotNull
        public final TakeResult copy(boolean success, int totalHas) {
            return new TakeResult(success, totalHas);
        }

        public static /* synthetic */ TakeResult copy$default(TakeResult takeResult, boolean bl, int n, int n2, Object object) {
            if ((n2 & 1) != 0) {
                bl = takeResult.success;
            }
            if ((n2 & 2) != 0) {
                n = takeResult.totalHas;
            }
            return takeResult.copy(bl, n);
        }

        @NotNull
        public String toString() {
            return "TakeResult(success=" + this.success + ", totalHas=" + this.totalHas + ')';
        }

        public int hashCode() {
            int n = this.success ? 1 : 0;
            if (n != 0) {
                n = 1;
            }
            int result = n;
            result = result * 31 + Integer.hashCode(this.totalHas);
            return result;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TakeResult)) {
                return false;
            }
            TakeResult takeResult = (TakeResult)other;
            if (this.success != takeResult.success) {
                return false;
            }
            return this.totalHas == takeResult.totalHas;
        }
    }
}

