/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.io.Zip2Kt
 *  kotlin.Metadata
 *  kotlin1822.io.CloseableKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.util.io.BukkitObjectInputStream
 *  org.bukkit.util.io.BukkitObjectOutputStream
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.library.redlib;

import ink.ptms.chemdah.library.redlib.BlockDataContainer;
import ink.ptms.chemdah.library.redlib.BlockDataManager;
import ink.ptms.chemdah.library.redlib.DataBlock;
import ink.ptms.chemdah.taboolib.common.io.Zip2Kt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.io.CloseableKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000J\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0002\u0010\u0012\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\u001a\n\u0010\u0011\u001a\u00020\u0012*\u00020\r\u001a \u0010\u0013\u001a\u000e\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u00160\u0014*\u00020\u00172\b\b\u0002\u0010\u0018\u001a\u00020\u000b\u001a\u0016\u0010\u0019\u001a\u0004\u0018\u00010\u0006*\u00020\r2\b\b\u0002\u0010\u001a\u001a\u00020\u000b\u001a\u001a\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00060\u001c*\u00020\r2\b\b\u0002\u0010\u001a\u001a\u00020\u000b\u001a\n\u0010\u001d\u001a\u00020\u000b*\u00020\r\u001a \u0010\u001e\u001a\u00020\u0017*\u000e\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u00160\u00142\b\b\u0002\u0010\u0018\u001a\u00020\u000b\"\u0011\u0010\u0000\u001a\u00020\u00018F\u00a2\u0006\u0006\u001a\u0004\b\u0002\u0010\u0003\"\u001b\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005*\u00020\u00078F\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\t\"(\u0010\f\u001a\u00020\u000b*\u00020\r2\u0006\u0010\n\u001a\u00020\u000b8F@FX\u0086\u000e\u00a2\u0006\f\u001a\u0004\b\f\u0010\u000e\"\u0004\b\u000f\u0010\u0010\u00a8\u0006\u001f"}, d2={"blockDataContainer", "Link/ptms/chemdah/library/redlib/BlockDataManager;", "getBlockDataContainer", "()Link/ptms/chemdah/library/redlib/BlockDataManager;", "dataContainers", "", "Link/ptms/chemdah/library/redlib/DataBlock;", "Lorg/bukkit/World;", "getDataContainers", "(Lorg/bukkit/World;)Ljava/util/List;", "it", "", "isAllowPistonMove", "Lorg/bukkit/block/Block;", "(Lorg/bukkit/block/Block;)Z", "setAllowPistonMove", "(Lorg/bukkit/block/Block;Z)V", "deleteDataContainer", "", "deserializeToMap", "", "", "", "", "zipped", "getDataContainer", "create", "getDataContainerAsync", "Ljava/util/concurrent/CompletableFuture;", "hasDataContainer", "serializeToByteArray", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nBlockDataContainer.kt\nKotlin\n*S Kotlin\n*F\n+ 1 BlockDataContainer.kt\nink/ptms/chemdah/library/redlib/BlockDataContainerKt\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,110:1\n766#2:111\n857#2,2:112\n1#3:114\n*S KotlinDebug\n*F\n+ 1 BlockDataContainer.kt\nink/ptms/chemdah/library/redlib/BlockDataContainerKt\n*L\n67#1:111\n67#1:112,2\n*E\n"})
public final class BlockDataContainerKt {
    @NotNull
    public static final BlockDataManager getBlockDataContainer() {
        BlockDataManager blockDataManager = BlockDataContainer.INSTANCE.getManager();
        if (blockDataManager == null) {
            throw new IllegalStateException("BlockDataManager has not been initialized".toString());
        }
        return blockDataManager;
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public static final List<DataBlock> getDataContainers(@NotNull World $this$dataContainers) {
        void $this$filterTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)$this$dataContainers, (String)"<this>");
        Set<DataBlock> set2 = BlockDataContainerKt.getBlockDataContainer().getAllLoaded();
        Intrinsics.checkNotNullExpressionValue(set2, (String)"blockDataContainer.allLoaded");
        Iterable $this$filter$iv = set2;
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            DataBlock it = (DataBlock)element$iv$iv;
            boolean bl = false;
            if (!Intrinsics.areEqual((Object)it.getChunkPosition().getWorld().getName(), (Object)$this$dataContainers.getName())) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        return (List)destination$iv$iv;
    }

    public static final boolean isAllowPistonMove(@NotNull Block $this$isAllowPistonMove) {
        Intrinsics.checkNotNullParameter((Object)$this$isAllowPistonMove, (String)"<this>");
        DataBlock dataBlock = BlockDataContainerKt.getDataContainer($this$isAllowPistonMove, false);
        return dataBlock != null ? Intrinsics.areEqual((Object)dataBlock.getBoolean("general.pistonMove"), (Object)true) : false;
    }

    public static final void setAllowPistonMove(@NotNull Block $this$isAllowPistonMove, boolean it) {
        Intrinsics.checkNotNullParameter((Object)$this$isAllowPistonMove, (String)"<this>");
        DataBlock dataBlock = BlockDataContainerKt.getDataContainer($this$isAllowPistonMove, true);
        Intrinsics.checkNotNull((Object)dataBlock);
        dataBlock.set("general.pistonMove", it);
    }

    @Nullable
    public static final DataBlock getDataContainer(@NotNull Block $this$getDataContainer, boolean create) {
        Intrinsics.checkNotNullParameter((Object)$this$getDataContainer, (String)"<this>");
        return BlockDataContainerKt.getBlockDataContainer().getDataBlock($this$getDataContainer, create);
    }

    public static /* synthetic */ DataBlock getDataContainer$default(Block block, boolean bl, int n, Object object) {
        if ((n & 1) != 0) {
            bl = true;
        }
        return BlockDataContainerKt.getDataContainer(block, bl);
    }

    @NotNull
    public static final CompletableFuture<DataBlock> getDataContainerAsync(@NotNull Block $this$getDataContainerAsync, boolean create) {
        Intrinsics.checkNotNullParameter((Object)$this$getDataContainerAsync, (String)"<this>");
        CompletableFuture<DataBlock> completableFuture = BlockDataContainerKt.getBlockDataContainer().getDataBlockAsync($this$getDataContainerAsync, create);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"blockDataContainer.getDataBlockAsync(this, create)");
        return completableFuture;
    }

    public static /* synthetic */ CompletableFuture getDataContainerAsync$default(Block block, boolean bl, int n, Object object) {
        if ((n & 1) != 0) {
            bl = true;
        }
        return BlockDataContainerKt.getDataContainerAsync(block, bl);
    }

    public static final void deleteDataContainer(@NotNull Block $this$deleteDataContainer) {
        block0: {
            Intrinsics.checkNotNullParameter((Object)$this$deleteDataContainer, (String)"<this>");
            DataBlock dataBlock = BlockDataContainerKt.getDataContainer($this$deleteDataContainer, false);
            if (dataBlock == null) break block0;
            DataBlock it = dataBlock;
            boolean bl = false;
            BlockDataContainerKt.getBlockDataContainer().remove(it);
        }
    }

    public static final boolean hasDataContainer(@NotNull Block $this$hasDataContainer) {
        Intrinsics.checkNotNullParameter((Object)$this$hasDataContainer, (String)"<this>");
        return BlockDataContainerKt.getDataContainer($this$hasDataContainer, false) != null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @NotNull
    public static final Map<String, Object> deserializeToMap(@NotNull byte[] $this$deserializeToMap, boolean zipped) {
        Intrinsics.checkNotNullParameter((Object)$this$deserializeToMap, (String)"<this>");
        Closeable closeable = new ByteArrayInputStream(zipped ? Zip2Kt.unzip((byte[])$this$deserializeToMap) : $this$deserializeToMap);
        Throwable throwable = null;
        try {
            Map map;
            ByteArrayInputStream byteArrayInputStream = (ByteArrayInputStream)closeable;
            boolean bl = false;
            Closeable closeable2 = (Closeable)new BukkitObjectInputStream((InputStream)byteArrayInputStream);
            Throwable throwable2 = null;
            try {
                BukkitObjectInputStream bukkitObjectInputStream = (BukkitObjectInputStream)closeable2;
                boolean bl2 = false;
                Object object = bukkitObjectInputStream.readObject();
                Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type kotlin.collections.Map<kotlin.String, kotlin.Any>");
                map = (Map)object;
            }
            catch (Throwable throwable3) {
                try {
                    try {
                        throwable2 = throwable3;
                        throw throwable3;
                    }
                    catch (Throwable throwable4) {
                        CloseableKt.closeFinally((Closeable)closeable2, throwable2);
                        throw throwable4;
                    }
                }
                catch (Throwable throwable5) {
                    throwable = throwable5;
                    throw throwable5;
                }
            }
            CloseableKt.closeFinally((Closeable)closeable2, (Throwable)throwable2);
            Map map2 = map;
            return map2;
        }
        finally {
            CloseableKt.closeFinally((Closeable)closeable, (Throwable)throwable);
        }
    }

    public static /* synthetic */ Map deserializeToMap$default(byte[] byArray, boolean bl, int n, Object object) {
        if ((n & 1) != 0) {
            bl = true;
        }
        return BlockDataContainerKt.deserializeToMap(byArray, bl);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @NotNull
    public static final byte[] serializeToByteArray(@NotNull Map<String, ? extends Object> $this$serializeToByteArray, boolean zipped) {
        Intrinsics.checkNotNullParameter($this$serializeToByteArray, (String)"<this>");
        Closeable closeable = new ByteArrayOutputStream();
        Throwable throwable = null;
        try {
            byte[] byArray;
            ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream)closeable;
            boolean bl = false;
            Closeable closeable2 = (Closeable)new BukkitObjectOutputStream((OutputStream)byteArrayOutputStream);
            Throwable throwable2 = null;
            try {
                byte[] byArray2;
                BukkitObjectOutputStream bukkitObjectOutputStream = (BukkitObjectOutputStream)closeable2;
                boolean bl2 = false;
                bukkitObjectOutputStream.writeObject($this$serializeToByteArray);
                byte[] bytes = byteArrayOutputStream.toByteArray();
                if (zipped) {
                    Intrinsics.checkNotNullExpressionValue((Object)bytes, (String)"bytes");
                    byArray2 = Zip2Kt.zip((byte[])bytes);
                } else {
                    Intrinsics.checkNotNullExpressionValue((Object)bytes, (String)"bytes");
                    byArray2 = bytes;
                }
                byArray = byArray2;
            }
            catch (Throwable throwable3) {
                try {
                    try {
                        throwable2 = throwable3;
                        throw throwable3;
                    }
                    catch (Throwable throwable4) {
                        CloseableKt.closeFinally((Closeable)closeable2, throwable2);
                        throw throwable4;
                    }
                }
                catch (Throwable throwable5) {
                    throwable = throwable5;
                    throw throwable5;
                }
            }
            CloseableKt.closeFinally((Closeable)closeable2, (Throwable)throwable2);
            byte[] byArray3 = byArray;
            return byArray3;
        }
        finally {
            CloseableKt.closeFinally((Closeable)closeable, (Throwable)throwable);
        }
    }

    public static /* synthetic */ byte[] serializeToByteArray$default(Map map, boolean bl, int n, Object object) {
        if ((n & 1) != 0) {
            bl = true;
        }
        return BlockDataContainerKt.serializeToByteArray(map, bl);
    }
}

