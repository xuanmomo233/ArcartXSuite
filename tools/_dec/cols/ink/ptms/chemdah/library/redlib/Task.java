/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib;

import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Task {
    private final int task;
    private final TaskType type;
    private final Plugin plugin;

    public static Task syncDelayed(Runnable run2) {
        return Task.syncDelayed((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2);
    }

    public static Task syncDelayed(Plugin plugin2, Runnable run2) {
        return Task.syncDelayed(plugin2, run2, 0L);
    }

    public static Task syncDelayed(Consumer<Task> run2) {
        return Task.syncDelayed((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2);
    }

    public static Task syncDelayed(Plugin plugin2, Consumer<Task> run2) {
        return Task.syncDelayed(plugin2, run2, 0L);
    }

    public static Task syncDelayed(Runnable run2, long delay) {
        return Task.syncDelayed((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2, delay);
    }

    public static Task syncDelayed(Plugin plugin2, Runnable run2, long delay) {
        return Task.syncDelayed(plugin2, (Task t) -> run2.run(), delay);
    }

    public static Task syncDelayed(Consumer<Task> run2, long delay) {
        return Task.syncDelayed((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2, delay);
    }

    public static Task syncDelayed(Plugin plugin2, Consumer<Task> run2, long delay) {
        Task[] task = new Task[]{null};
        task[0] = new Task(Bukkit.getScheduler().scheduleSyncDelayedTask(plugin2, () -> run2.accept(task[0]), delay), TaskType.SYNC_DELAYED, plugin2);
        return task[0];
    }

    public static Task syncRepeating(Runnable run2, long delay, long period) {
        return Task.syncRepeating((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2, delay, period);
    }

    public static Task syncRepeating(Plugin plugin2, Runnable run2, long delay, long period) {
        return Task.syncRepeating(plugin2, (Task t) -> run2.run(), delay, period);
    }

    public static Task syncRepeating(Consumer<Task> run2, long delay, long period) {
        return Task.syncRepeating((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2, delay, period);
    }

    public static Task syncRepeating(Plugin plugin2, Consumer<Task> run2, long delay, long period) {
        Task[] task = new Task[]{null};
        task[0] = new Task(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin2, () -> run2.accept(task[0]), delay, period), TaskType.SYNC_REPEATING, plugin2);
        return task[0];
    }

    public static Task asyncDelayed(Runnable run2) {
        return Task.asyncDelayed((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2);
    }

    public static Task asyncDelayed(Plugin plugin2, Runnable run2) {
        return Task.asyncDelayed(plugin2, (Task t) -> run2.run(), 0L);
    }

    public static Task asyncDelayed(Consumer<Task> run2) {
        return Task.asyncDelayed((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2);
    }

    public static Task asyncDelayed(Plugin plugin2, Consumer<Task> run2) {
        return Task.asyncDelayed(plugin2, run2, 0L);
    }

    public static Task asyncDelayed(Runnable run2, long delay) {
        return Task.asyncDelayed((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2, delay);
    }

    public static Task asyncDelayed(Plugin plugin2, Runnable run2, long delay) {
        return Task.asyncDelayed(plugin2, (Task t) -> run2.run(), delay);
    }

    public static Task asyncDelayed(Consumer<Task> run2, long delay) {
        return Task.asyncDelayed((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2, delay);
    }

    public static Task asyncDelayed(Plugin plugin2, Consumer<Task> run2, long delay) {
        Task[] task = new Task[]{null};
        task[0] = new Task(Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin2, () -> run2.accept(task[0]), delay), TaskType.ASYNC_DELAYED, plugin2);
        return task[0];
    }

    public static Task asyncRepeating(Consumer<Task> run2, long delay, long period) {
        return Task.asyncRepeating((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2, delay, period);
    }

    public static Task asyncRepeating(Plugin plugin2, Consumer<Task> run2, long delay, long period) {
        Task[] task = new Task[]{null};
        task[0] = new Task(Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin2, () -> run2.accept(task[0]), delay, period), TaskType.ASYNC_REPEATING, plugin2);
        return task[0];
    }

    public static Task asyncRepeating(Runnable run2, long delay, long period) {
        return Task.asyncRepeating((Plugin)JavaPlugin.getProvidingPlugin(Task.class), run2, delay, period);
    }

    public static Task asyncRepeating(Plugin plugin2, Runnable run2, long delay, long period) {
        return Task.asyncRepeating(plugin2, (Task t) -> run2.run(), delay, period);
    }

    private Task(int task, TaskType type, Plugin plugin2) {
        this.task = task;
        this.type = type;
        this.plugin = plugin2;
    }

    public TaskType getType() {
        return this.type;
    }

    public boolean isQueued() {
        return Bukkit.getScheduler().isQueued(this.task);
    }

    public boolean isCurrentlyRunning() {
        return Bukkit.getScheduler().isCurrentlyRunning(this.task);
    }

    public void cancel() {
        Bukkit.getScheduler().cancelTask(this.task);
    }

    public Plugin getPlugin() {
        return this.plugin;
    }

    public static enum TaskType {
        SYNC_DELAYED,
        ASYNC_DELAYED,
        SYNC_REPEATING,
        ASYNC_REPEATING;

    }
}

