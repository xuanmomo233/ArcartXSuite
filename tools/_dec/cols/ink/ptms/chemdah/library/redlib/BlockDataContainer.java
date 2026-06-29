/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib;

import ink.ptms.chemdah.core.quest.QuestDevelopment;
import ink.ptms.chemdah.library.redlib.BlockDataContainerKt;
import ink.ptms.chemdah.library.redlib.BlockDataManager;
import ink.ptms.chemdah.library.redlib.event.DataBlockMoveEvent;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.CommonKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import java.nio.file.Path;
import kotlin.Metadata;
import kotlin1822.NoWhenBranchMatchedException;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPistonEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\b\u001a\u00020\tH\u0003J\b\u0010\n\u001a\u00020\tH\u0003J\u0010\u0010\u000b\u001a\u00020\t2\u0006\u0010\f\u001a\u00020\rH\u0003R\"\u0010\u0005\u001a\u0004\u0018\u00010\u00042\b\u0010\u0003\u001a\u0004\u0018\u00010\u0004@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/library/redlib/BlockDataContainer;", "", "()V", "<set-?>", "Link/ptms/chemdah/library/redlib/BlockDataManager;", "manager", "getManager", "()Link/ptms/chemdah/library/redlib/BlockDataManager;", "close", "", "init", "onBlockMove", "e", "Link/ptms/chemdah/library/redlib/event/DataBlockMoveEvent;", "Chemdah"})
public final class BlockDataContainer {
    @NotNull
    public static final BlockDataContainer INSTANCE = new BlockDataContainer();
    @Nullable
    private static BlockDataManager manager;

    private BlockDataContainer() {
    }

    @Nullable
    public final BlockDataManager getManager() {
        return manager;
    }

    @Awake(value=LifeCycle.LOAD)
    private final void init() {
        CommonKt.registerLifeCycleTask$default((LifeCycle)LifeCycle.ENABLE, (int)0, BlockDataContainer::init$lambda$0, (int)2, null);
    }

    @Awake(value=LifeCycle.DISABLE)
    private final void close() {
        block0: {
            BlockDataManager blockDataManager = manager;
            if (blockDataManager == null) break block0;
            blockDataManager.saveAndClose();
        }
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    private final void onBlockMove(DataBlockMoveEvent e) {
        if (e.getParent() instanceof BlockPistonEvent) {
            Block block = e.getBlock();
            Intrinsics.checkNotNullExpressionValue((Object)block, (String)"e.block");
            if (!BlockDataContainerKt.isAllowPistonMove(block)) {
                e.setCancelled(true);
            }
        }
    }

    private static final void init$lambda$0() {
        if (QuestDevelopment.INSTANCE.getEnableBlockContainer() || QuestDevelopment.INSTANCE.getEnableUniqueBlock()) {
            BlockDataManager blockDataManager;
            Path path = IOKt.getDataFolder().toPath().resolve("blocks.db");
            switch (WhenMappings.$EnumSwitchMapping$0[QuestDevelopment.INSTANCE.getUniqueBlockMode().ordinal()]) {
                case 1: {
                    blockDataManager = BlockDataManager.createAuto(path, true, true);
                    break;
                }
                case 2: {
                    blockDataManager = BlockDataManager.createSQLite(path, true, true);
                    break;
                }
                case 3: {
                    blockDataManager = BlockDataManager.createPDC(true, true);
                    break;
                }
                default: {
                    throw new NoWhenBranchMatchedException();
                }
            }
            manager = blockDataManager;
        }
    }

    @Metadata(mv={1, 8, 0}, k=3, xi=48)
    public final class WhenMappings {
        public static final /* synthetic */ int[] $EnumSwitchMapping$0;

        static {
            int[] nArray = new int[QuestDevelopment.UniqueBlockMode.values().length];
            try {
                nArray[QuestDevelopment.UniqueBlockMode.AUTO.ordinal()] = 1;
            }
            catch (NoSuchFieldError noSuchFieldError) {
                // empty catch block
            }
            try {
                nArray[QuestDevelopment.UniqueBlockMode.SQLITE.ordinal()] = 2;
            }
            catch (NoSuchFieldError noSuchFieldError) {
                // empty catch block
            }
            try {
                nArray[QuestDevelopment.UniqueBlockMode.PDC.ordinal()] = 3;
            }
            catch (NoSuchFieldError noSuchFieldError) {
                // empty catch block
            }
            $EnumSwitchMapping$0 = nArray;
        }
    }
}

