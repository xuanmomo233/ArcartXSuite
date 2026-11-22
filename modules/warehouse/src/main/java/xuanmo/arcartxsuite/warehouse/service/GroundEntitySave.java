package xuanmo.arcartxsuite.warehouse.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;

/**
 * Optional fast path for Paper 1.20.1. Queues its normal entity-region save.
 * A successful request never substitutes for independent disk confirmation.
 */
final class GroundEntitySave {
    private GroundEntitySave() {
    }

    static boolean available() {
        return BindingHolder.PAPER != null;
    }

    /**
     * Runs on the server thread after the caller has frozen the source entity.
     * Paper serializes the loaded slice here. Its region I/O runs on its own worker.
     */
    static boolean request(Item item, UUID expectedWorld, int expectedChunkX, int expectedChunkZ) {
        if (!Bukkit.isPrimaryThread() || item == null || !item.isValid() || item.isDead() || !item.isPersistent()) {
            return false;
        }
        Binding binding = BindingHolder.PAPER;
        if (binding == null) return false;

        Location position = item.getLocation();
        World world = position.getWorld();
        if (world == null || !world.getUID().equals(expectedWorld)) return false;
        int chunkX = position.getBlockX() >> 4;
        int chunkZ = position.getBlockZ() >> 4;
        if (chunkX != expectedChunkX || chunkZ != expectedChunkZ || !world.isChunkLoaded(chunkX, chunkZ)) return false;

        try {
            return binding.queue(item, world, chunkX, chunkZ);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static final class BindingHolder {
        private static final Binding PAPER = Binding.resolve();
    }

    private record Binding(Method worldHandle, Method entityHandle, Field entitySectionX, Field entitySectionZ,
                           Method canSaveEntity, Method entityLookup, Method lookupByUuid, Field chunkScheduler,
                           Field chunkHolderManager, Method lookupChunkHolder, Method fullChunkReady,
                           Method entityNbtLoaded, Method entityChunk, Method transientSlice,
                           Method sliceChangingStatus, Method saveEntities) {
        private static Binding resolve() {
            if (!Bukkit.getBukkitVersion().startsWith("1.20.1-")) return null;
            try {
                ClassLoader serverLoader = Bukkit.getServer().getClass().getClassLoader();
                Class<?> craftWorld = load(serverLoader, "org.bukkit.craftbukkit.v1_20_R1.CraftWorld");
                Class<?> craftEntity = load(serverLoader, "org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity");
                Class<?> nativeWorld = load(serverLoader, "net.minecraft.server.level.WorldServer");
                Class<?> nativeEntity = load(serverLoader, "net.minecraft.world.entity.Entity");
                Class<?> lookup = load(serverLoader, "io.papermc.paper.chunk.system.entity.EntityLookup");
                String scheduling = "io.papermc.paper.chunk.system.scheduling.";
                Class<?> scheduler = load(serverLoader, scheduling + "ChunkTaskScheduler");
                Class<?> manager = load(serverLoader, scheduling + "ChunkHolderManager");
                Class<?> holder = load(serverLoader, scheduling + "NewChunkHolder");
                Class<?> slices = load(serverLoader, "io.papermc.paper.world.ChunkEntitySlices");

                Method save = holder.getDeclaredMethod("saveEntities", slices, boolean.class);
                if (!save.trySetAccessible()) return null;
                return new Binding(craftWorld.getMethod("getHandle"), craftEntity.getMethod("getHandle"),
                    nativeEntity.getField("sectionX"), nativeEntity.getField("sectionZ"),
                    nativeEntity.getMethod("dG"), nativeWorld.getMethod("getEntityLookup"),
                    lookup.getMethod("get", UUID.class), nativeWorld.getField("chunkTaskScheduler"),
                    scheduler.getField("chunkHolderManager"), manager.getMethod("getChunkHolder", int.class, int.class),
                    holder.getMethod("isFullChunkReady"), holder.getMethod("isEntityChunkNBTLoaded"),
                    holder.getMethod("getEntityChunk"), slices.getMethod("isTransient"),
                    slices.getMethod("isPreventingStatusUpdates"), save);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return null;
            }
        }

        private static Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
            return Class.forName(name, false, loader);
        }

        private boolean queue(Item item, World world, int chunkX, int chunkZ) throws ReflectiveOperationException {
            Object handle = entityHandle.invoke(item);
            if (entitySectionX.getInt(handle) != chunkX || entitySectionZ.getInt(handle) != chunkZ
                || !(boolean) canSaveEntity.invoke(handle)) return false;

            Object nativeWorld = worldHandle.invoke(world);
            Object lookup = entityLookup.invoke(nativeWorld);
            if (lookupByUuid.invoke(lookup, item.getUniqueId()) != handle) return false;

            Object manager = chunkHolderManager.get(chunkScheduler.get(nativeWorld));
            Object holder = lookupChunkHolder.invoke(manager, chunkX, chunkZ);
            if (holder == null || !(boolean) fullChunkReady.invoke(holder)
                || !(boolean) entityNbtLoaded.invoke(holder)) return false;

            Object slices = entityChunk.invoke(holder);
            if (slices == null || (boolean) transientSlice.invoke(slices)
                || (boolean) sliceChangingStatus.invoke(slices)) return false;

            // In the verified 1.20.1 implementation, false is the non-unload path.
            // It skips transient slices and enqueues ENTITY_DATA through RegionFileIOThread.
            return (boolean) saveEntities.invoke(holder, slices, false);
        }
    }
}
