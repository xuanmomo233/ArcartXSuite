/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.AsyncScheduler
 *  io.papermc.paper.threadedregions.scheduler.EntityScheduler
 *  io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler
 *  io.papermc.paper.threadedregions.scheduler.RegionScheduler
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Entity
 */
package ink.ptms.chemdah.taboolib.platform;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

public class FoliaExecutor {
    public static AsyncScheduler ASYNC_SCHEDULER;
    public static RegionScheduler REGION_SCHEDULER;
    public static GlobalRegionScheduler GLOBAL_REGION_SCHEDULER;
    public static Method GET_ENTITY_SCHEDULER;

    public static EntityScheduler getEntityScheduler(Entity entity) throws InvocationTargetException, IllegalAccessException {
        return (EntityScheduler)GET_ENTITY_SCHEDULER.invoke((Object)entity, new Object[0]);
    }

    static {
        try {
            Method getAsyncSchedulerMethod = Bukkit.class.getDeclaredMethod("getAsyncScheduler", new Class[0]);
            getAsyncSchedulerMethod.setAccessible(true);
            ASYNC_SCHEDULER = (AsyncScheduler)getAsyncSchedulerMethod.invoke((Object)Bukkit.getServer(), new Object[0]);
            Method getRegionSchedulerMethod = Bukkit.class.getDeclaredMethod("getRegionScheduler", new Class[0]);
            getRegionSchedulerMethod.setAccessible(true);
            REGION_SCHEDULER = (RegionScheduler)getRegionSchedulerMethod.invoke((Object)Bukkit.getServer(), new Object[0]);
            Method getGlobalRegionSchedulerMethod = Bukkit.class.getDeclaredMethod("getGlobalRegionScheduler", new Class[0]);
            getGlobalRegionSchedulerMethod.setAccessible(true);
            GLOBAL_REGION_SCHEDULER = (GlobalRegionScheduler)getGlobalRegionSchedulerMethod.invoke((Object)Bukkit.getServer(), new Object[0]);
            Method getEntitySchedulerMethod = Entity.class.getDeclaredMethod("getScheduler", new Class[0]);
            getEntitySchedulerMethod.setAccessible(true);
            GET_ENTITY_SCHEDULER = getEntitySchedulerMethod;
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}

