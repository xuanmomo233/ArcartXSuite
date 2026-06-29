/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.kether.ParsedAction
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.library.kether.QuestReader
 *  ink.ptms.chemdah.taboolib.module.kether.KetherConcurrentKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParseBuilderKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt
 *  ink.ptms.chemdah.taboolib.type.BukkitEquipment
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.PlayerInventory
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.module.kether.ActionInventory;
import ink.ptms.chemdah.taboolib.library.kether.ParsedAction;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.library.kether.QuestReader;
import ink.ptms.chemdah.taboolib.module.kether.KetherConcurrentKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParseBuilderKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import ink.ptms.chemdah.taboolib.type.BukkitEquipment;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\b\u0018\u0000 \u00032\u00020\u0001:\u0006\u0003\u0004\u0005\u0006\u0007\bB\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/module/kether/ActionInventory;", "", "()V", "Companion", "InventoryCheck", "InventoryCount", "InventoryEquipment", "InventorySlot", "InventoryTake", "Chemdah"})
public final class ActionInventory {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J(\u0010\u0003\u001a\u001a\u0012\u0006\b\u0001\u0012\u00020\u0001 \u0005*\f\u0012\u0006\b\u0001\u0012\u00020\u0001\u0018\u00010\u00040\u00042\u0006\u0010\u0006\u001a\u00020\u0007H\u0002J\u0012\u0010\b\u001a\f\u0012\b\b\u0001\u0012\u0004\u0018\u00010\u00010\tH\u0007\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/module/kether/ActionInventory$Companion;", "", "()V", "matchAmount", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "kotlin1822.jvm.PlatformType", "it", "Link/ptms/chemdah/taboolib/library/kether/QuestReader;", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"inventory"}, shared=true)
        @NotNull
        public final ScriptActionParser<? extends Object> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        private final ParsedAction<? extends Object> matchAmount(QuestReader it) {
            ParsedAction parsedAction;
            try {
                it.mark();
                it.expect("amount");
                parsedAction = it.nextParsedAction();
            }
            catch (Throwable ex) {
                it.reset();
                parsedAction = KetherParseBuilderKt.literalAction((Object)1);
            }
            return parsedAction;
        }

        public static final /* synthetic */ ParsedAction access$matchAmount(Companion $this, QuestReader it) {
            return $this.matchAmount(it);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001B\u001d\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\u0002\u0010\u0006J\u001c\u0010\n\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u000b2\n\u0010\f\u001a\u00060\rj\u0002`\u000eH\u0016R\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\b\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/kether/ActionInventory$InventoryCheck;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "item", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "amount", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getAmount", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getItem", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class InventoryCheck
    extends ScriptAction<Object> {
        @NotNull
        private final ParsedAction<?> item;
        @NotNull
        private final ParsedAction<?> amount;

        public InventoryCheck(@NotNull ParsedAction<?> item2, @NotNull ParsedAction<?> amount) {
            Intrinsics.checkNotNullParameter(item2, (String)"item");
            Intrinsics.checkNotNullParameter(amount, (String)"amount");
            this.item = item2;
            this.amount = amount;
        }

        @NotNull
        public final ParsedAction<?> getItem() {
            return this.item;
        }

        @NotNull
        public final ParsedAction<?> getAmount() {
            return this.amount;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            return KetherConcurrentKt.int((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)frame, this.amount), (Function1)((Function1)new Function1<Integer, Object>(frame, this){
                final /* synthetic */ QuestContext.Frame $frame;
                final /* synthetic */ InventoryCheck this$0;
                {
                    this.$frame = $frame;
                    this.this$0 = $receiver;
                    super(1);
                }

                @Nullable
                public final Object invoke(int a) {
                    T t = this.$frame.newFrame(this.this$0.getItem()).run().get();
                    Intrinsics.checkNotNullExpressionValue(t, (String)"frame.newFrame(item).run<String>().get()");
                    InferItem.Item item2 = InferItem.Companion.toInferItem((String)t);
                    PlayerInventory playerInventory = UtilsForKetherKt.getBukkitPlayer(this.$frame).getInventory();
                    Intrinsics.checkNotNullExpressionValue((Object)playerInventory, (String)"frame.getBukkitPlayer().inventory");
                    return item2.check((Inventory)playerInventory, a);
                }
            }));
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0011\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\u0002\u0010\u0005J\u001a\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00020\t2\n\u0010\n\u001a\u00060\u000bj\u0002`\fH\u0016R\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/module/kether/ActionInventory$InventoryCount;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "item", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getItem", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nActionInventory.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ActionInventory.kt\nink/ptms/chemdah/module/kether/ActionInventory$InventoryCount\n+ 2 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n*L\n1#1,140:1\n13579#2,2:141\n*S KotlinDebug\n*F\n+ 1 ActionInventory.kt\nink/ptms/chemdah/module/kether/ActionInventory$InventoryCount\n*L\n33#1:141,2\n*E\n"})
    public static final class InventoryCount
    extends ScriptAction<Integer> {
        @NotNull
        private final ParsedAction<?> item;

        public InventoryCount(@NotNull ParsedAction<?> item2) {
            Intrinsics.checkNotNullParameter(item2, (String)"item");
            this.item = item2;
        }

        @NotNull
        public final ParsedAction<?> getItem() {
            return this.item;
        }

        @NotNull
        public CompletableFuture<Integer> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            int checkAmount = 0;
            ItemStack[] itemStackArray = UtilsForKetherKt.getBukkitPlayer(frame).getInventory().getContents();
            Intrinsics.checkNotNullExpressionValue((Object)itemStackArray, (String)"frame.getBukkitPlayer().inventory.contents");
            Object[] $this$forEach$iv = itemStackArray;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                ItemStack itemStack = (ItemStack)element$iv;
                boolean bl = false;
                if (!ItemModifierKt.isNotAir((ItemStack)itemStack)) continue;
                Object t = frame.newFrame(this.item).run().get();
                Intrinsics.checkNotNullExpressionValue(t, (String)"frame.newFrame(item).run<String>().get()");
                InferItem.Item item2 = InferItem.Companion.toInferItem((String)t);
                Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"itemStack");
                if (!item2.match(itemStack)) continue;
                checkAmount += itemStack.getAmount();
            }
            CompletableFuture<Integer> completableFuture = CompletableFuture.completedFuture(checkAmount);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(checkAmount)");
            return completableFuture;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001B%\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0006\u0012\n\u0010\u0007\u001a\u0006\u0012\u0002\b\u00030\u0006\u00a2\u0006\u0002\u0010\bJ\u001c\u0010\u000e\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u000f2\n\u0010\u0010\u001a\u00060\u0011j\u0002`\u0012H\u0016R\u0015\u0010\u0007\u001a\u0006\u0012\u0002\b\u00030\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\n\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/module/kether/ActionInventory$InventoryEquipment;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "equipment", "Link/ptms/chemdah/taboolib/type/BukkitEquipment;", "item", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "amount", "(Link/ptms/chemdah/taboolib/type/BukkitEquipment;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getAmount", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getEquipment", "()Link/ptms/chemdah/taboolib/type/BukkitEquipment;", "getItem", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class InventoryEquipment
    extends ScriptAction<Object> {
        @NotNull
        private final BukkitEquipment equipment;
        @NotNull
        private final ParsedAction<?> item;
        @NotNull
        private final ParsedAction<?> amount;

        public InventoryEquipment(@NotNull BukkitEquipment equipment, @NotNull ParsedAction<?> item2, @NotNull ParsedAction<?> amount) {
            Intrinsics.checkNotNullParameter((Object)equipment, (String)"equipment");
            Intrinsics.checkNotNullParameter(item2, (String)"item");
            Intrinsics.checkNotNullParameter(amount, (String)"amount");
            this.equipment = equipment;
            this.item = item2;
            this.amount = amount;
        }

        @NotNull
        public final BukkitEquipment getEquipment() {
            return this.equipment;
        }

        @NotNull
        public final ParsedAction<?> getItem() {
            return this.item;
        }

        @NotNull
        public final ParsedAction<?> getAmount() {
            return this.amount;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            return KetherConcurrentKt.int((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)frame, this.amount), (Function1)((Function1)new Function1<Integer, Object>(this, frame){
                final /* synthetic */ InventoryEquipment this$0;
                final /* synthetic */ QuestContext.Frame $frame;
                {
                    this.this$0 = $receiver;
                    this.$frame = $frame;
                    super(1);
                }

                /*
                 * Enabled aggressive block sorting
                 */
                @Nullable
                public final Object invoke(int amount) {
                    boolean bl;
                    ItemStack equipment = this.this$0.getEquipment().getItem(UtilsForKetherKt.getBukkitPlayer(this.$frame));
                    if (ItemModifierKt.isNotAir((ItemStack)equipment)) {
                        T t = this.$frame.newFrame(this.this$0.getItem()).run().get();
                        Intrinsics.checkNotNullExpressionValue(t, (String)"frame.newFrame(item).run<String>().get()");
                        if (InferItem.Companion.toInferItem((String)t).match(equipment)) {
                            if (equipment.getAmount() >= amount) {
                                bl = true;
                                return bl;
                            }
                            bl = false;
                            return bl;
                        }
                    }
                    bl = false;
                    return bl;
                }
            }));
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001B)\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0006\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\u0002\u0010\u0007J\u001c\u0010\f\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\r2\n\u0010\u000e\u001a\u00060\u000fj\u0002`\u0010H\u0016R\u0015\u0010\u0006\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\t\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/module/kether/ActionInventory$InventorySlot;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "slot", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "item", "amount", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getAmount", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getItem", "getSlot", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class InventorySlot
    extends ScriptAction<Object> {
        @NotNull
        private final ParsedAction<?> slot;
        @NotNull
        private final ParsedAction<?> item;
        @NotNull
        private final ParsedAction<?> amount;

        public InventorySlot(@NotNull ParsedAction<?> slot, @NotNull ParsedAction<?> item2, @NotNull ParsedAction<?> amount) {
            Intrinsics.checkNotNullParameter(slot, (String)"slot");
            Intrinsics.checkNotNullParameter(item2, (String)"item");
            Intrinsics.checkNotNullParameter(amount, (String)"amount");
            this.slot = slot;
            this.item = item2;
            this.amount = amount;
        }

        @NotNull
        public final ParsedAction<?> getSlot() {
            return this.slot;
        }

        @NotNull
        public final ParsedAction<?> getItem() {
            return this.item;
        }

        @NotNull
        public final ParsedAction<?> getAmount() {
            return this.amount;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletableFuture<Object> future = new CompletableFuture<Object>();
            KetherConcurrentKt.int((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)frame, this.slot), (Function1)((Function1)new Function1<Integer, CompletableFuture<Boolean>>(frame, this, future){
                final /* synthetic */ QuestContext.Frame $frame;
                final /* synthetic */ InventorySlot this$0;
                final /* synthetic */ CompletableFuture<Object> $future;
                {
                    this.$frame = $frame;
                    this.this$0 = $receiver;
                    this.$future = $future;
                    super(1);
                }

                @NotNull
                public final CompletableFuture<Boolean> invoke(int slot) {
                    return KetherConcurrentKt.int((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)this.$frame, this.this$0.getAmount()), (Function1)((Function1)new Function1<Integer, Boolean>(this.$frame, slot, this.this$0, this.$future){
                        final /* synthetic */ QuestContext.Frame $frame;
                        final /* synthetic */ int $slot;
                        final /* synthetic */ InventorySlot this$0;
                        final /* synthetic */ CompletableFuture<Object> $future;
                        {
                            this.$frame = $frame;
                            this.$slot = $slot;
                            this.this$0 = $receiver;
                            this.$future = $future;
                            super(1);
                        }

                        /*
                         * Unable to fully structure code
                         */
                        @NotNull
                        public final Boolean invoke(int amount) {
                            equipment = UtilsForKetherKt.getBukkitPlayer(this.$frame).getInventory().getItem(this.$slot);
                            if (!ItemModifierKt.isNotAir((ItemStack)equipment)) ** GOTO lbl-1000
                            v0 = this.$frame.newFrame(this.this$0.getItem()).run().get();
                            Intrinsics.checkNotNullExpressionValue(v0, (String)"frame.newFrame(item).run<String>().get()");
                            v1 = InferItem.Companion.toInferItem((String)v0);
                            v2 = equipment;
                            Intrinsics.checkNotNull((Object)v2);
                            if (v1.match(v2)) {
                                v3 = equipment.getAmount() >= amount;
                            } else lbl-1000:
                            // 2 sources

                            {
                                v3 = false;
                            }
                            value = v3;
                            return this.$future.complete(value);
                        }
                    }));
                }
            }));
            return future;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001B\u001d\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\u0002\u0010\u0006J\u001c\u0010\n\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u000b2\n\u0010\f\u001a\u00060\rj\u0002`\u000eH\u0016R\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\b\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/kether/ActionInventory$InventoryTake;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "item", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "amount", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getAmount", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getItem", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class InventoryTake
    extends ScriptAction<Object> {
        @NotNull
        private final ParsedAction<?> item;
        @NotNull
        private final ParsedAction<?> amount;

        public InventoryTake(@NotNull ParsedAction<?> item2, @NotNull ParsedAction<?> amount) {
            Intrinsics.checkNotNullParameter(item2, (String)"item");
            Intrinsics.checkNotNullParameter(amount, (String)"amount");
            this.item = item2;
            this.amount = amount;
        }

        @NotNull
        public final ParsedAction<?> getItem() {
            return this.item;
        }

        @NotNull
        public final ParsedAction<?> getAmount() {
            return this.amount;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            return KetherConcurrentKt.int((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)frame, this.amount), (Function1)((Function1)new Function1<Integer, Object>(frame, this){
                final /* synthetic */ QuestContext.Frame $frame;
                final /* synthetic */ InventoryTake this$0;
                {
                    this.$frame = $frame;
                    this.this$0 = $receiver;
                    super(1);
                }

                @Nullable
                public final Object invoke(int a) {
                    T t = this.$frame.newFrame(this.this$0.getItem()).run().get();
                    Intrinsics.checkNotNullExpressionValue(t, (String)"frame.newFrame(item).run<String>().get()");
                    InferItem.Item item2 = InferItem.Companion.toInferItem((String)t);
                    PlayerInventory playerInventory = UtilsForKetherKt.getBukkitPlayer(this.$frame).getInventory();
                    Intrinsics.checkNotNullExpressionValue((Object)playerInventory, (String)"frame.getBukkitPlayer().inventory");
                    return item2.take((Inventory)playerInventory, a);
                }
            }));
        }
    }
}

