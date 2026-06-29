/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.event.Event
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.HandlerList
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package ink.ptms.chemdah.library.redlib;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class EventListener<T extends Event>
implements Listener {
    private final BiConsumer<EventListener<T>, T> handler;
    private final Class<T> eventClass;

    public EventListener(Class<T> eventClass, EventPriority priority, BiConsumer<EventListener<T>, T> handler2) {
        this((Plugin)JavaPlugin.getProvidingPlugin(EventListener.class), eventClass, priority, handler2);
    }

    public EventListener(Plugin plugin2, Class<T> eventClass, EventPriority priority, BiConsumer<EventListener<T>, T> handler2) {
        this.handler = handler2;
        this.eventClass = eventClass;
        Bukkit.getPluginManager().registerEvent(eventClass, (Listener)this, priority, (l, e) -> this.handleEvent(e), plugin2);
    }

    public EventListener(Class<T> eventClass, EventPriority priority, Consumer<T> handler2) {
        this((Plugin)JavaPlugin.getProvidingPlugin(EventListener.class), eventClass, priority, handler2);
    }

    public EventListener(Plugin plugin2, Class<T> eventClass, EventPriority priority, Consumer<T> handler2) {
        this(plugin2, eventClass, priority, (EventListener<T> l, T e) -> handler2.accept(e));
    }

    public EventListener(Class<T> eventClass, BiConsumer<EventListener<T>, T> handler2) {
        this((Plugin)JavaPlugin.getProvidingPlugin(EventListener.class), eventClass, handler2);
    }

    public EventListener(Plugin plugin2, Class<T> eventClass, BiConsumer<EventListener<T>, T> handler2) {
        this(plugin2, eventClass, EventPriority.NORMAL, handler2);
    }

    public EventListener(Class<T> eventClass, Consumer<T> handler2) {
        this((Plugin)JavaPlugin.getProvidingPlugin(EventListener.class), eventClass, handler2);
    }

    public EventListener(Plugin plugin2, Class<T> eventClass, Consumer<T> handler2) {
        this(plugin2, eventClass, EventPriority.NORMAL, handler2);
    }

    @EventHandler
    public void handleEvent(T event) {
        if (event.getClass().equals(this.eventClass)) {
            this.handler.accept(this, (EventListener)event);
        }
    }

    public void unregister() {
        HandlerList.unregisterAll((Listener)this);
    }
}

