/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.platform.util.ItemMatcherKt;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.ranges.RangesKt;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u001cB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\u0018\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u001bH\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u001a\u0010\b\u001a\u00020\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u0014\u0010\u000e\u001a\u00020\u000fX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u0010R\u0014\u0010\u0011\u001a\u00020\u000fX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010R\u0014\u0010\u0012\u001a\u00020\u0013X\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015\u00a8\u0006\u001d"}, d2={"Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/Event;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "handler", "Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$Handler;", "getHandler", "()Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$Handler;", "setHandler", "(Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$Handler;)V", "isListener", "", "()Z", "isTickable", "name", "", "getName", "()Ljava/lang/String;", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Handler", "Chemdah"})
public final class IPlayerInventory
extends ObjectiveCountableI<Event> {
    @NotNull
    public static final IPlayerInventory INSTANCE = new IPlayerInventory();
    @NotNull
    private static final String name = "player inventory";
    @NotNull
    private static final Class<Event> event = Event.class;
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
        return event;
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

    public final void setHandler(@NotNull Handler handler) {
        Intrinsics.checkNotNullParameter((Object)handler, (String)"<set-?>");
        IPlayerInventory.handler = handler;
    }

    private static final Boolean _init_$lambda$0(PlayerProfile profile, Task task, Event event) {
        Data data2 = task.getCondition().get("position");
        Intrinsics.checkNotNull((Object)data2);
        InferArea inferArea = data2.toPosition();
        Location location = profile.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"profile.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$1(PlayerProfile profile, Task task, Event event) {
        Data data2 = task.getCondition().get("item");
        Intrinsics.checkNotNull((Object)data2);
        InferItem item2 = data2.toInferItem();
        Data data3 = task.getCondition().get("amount");
        int amount = data3 != null ? data3.toInt() : 1;
        Data data4 = task.getCondition().get("consume");
        boolean consume = data4 != null ? data4.toBoolean() : false;
        Intrinsics.checkNotNullExpressionValue((Object)profile, (String)"profile");
        Intrinsics.checkNotNullExpressionValue((Object)task, (String)"task");
        return handler.takeItem(profile, task, item2, amount, consume);
    }

    static {
        isTickable = true;
        INSTANCE.addFullCondition("position", IPlayerInventory::_init_$lambda$0);
        INSTANCE.addFullCondition("item", IPlayerInventory::_init_$lambda$1);
        handler = new Handler();
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J(\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016J0\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u000f\u001a\u00020\u000eH\u0016\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/objective/other/IPlayerInventory$Handler;", "", "()V", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "item", "Link/ptms/chemdah/core/quest/selector/InferItem;", "amount", "", "takeItem", "", "consume", "Chemdah"})
    public static class Handler {
        public boolean takeItem(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull InferItem item2, int amount, boolean consume) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)task, (String)"task");
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            PlayerInventory playerInventory = profile.getPlayer().getInventory();
            Intrinsics.checkNotNullExpressionValue((Object)playerInventory, (String)"profile.player.inventory");
            PlayerInventory inventory = playerInventory;
            boolean hasItem2 = ItemMatcherKt.hasItem((Inventory)((Inventory)inventory), (int)amount, (Function1)((Function1)new Function1<ItemStack, Boolean>(item2){
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
            if (hasItem2 && consume) {
                ItemMatcherKt.takeItem$default((Inventory)((Inventory)inventory), (int)amount, null, (Function1)((Function1)new Function1<ItemStack, Boolean>(item2){
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
                }), (int)2, null);
            }
            return hasItem2;
        }

        @NotNull
        public Progress getProgress(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull InferItem item2, int amount) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)task, (String)"task");
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
}

