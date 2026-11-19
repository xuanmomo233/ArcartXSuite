package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.FormattedCommandAlias;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

final class ModuleCommandBindingsTest {

    private final ModuleCommandBindings bindings = new ModuleCommandBindings();
    private final TestCommandMap commands = new TestCommandMap();
    private final Plugin suite = plugin("ArcartX-Suite");
    private final Plugin otherPlugin = plugin("OtherPlugin");
    private final Logger logger = Logger.getAnonymousLogger();

    @Test
    void disablingProtectionOverridesPluginCommandAndUnregisterRestoresIt() throws Exception {
        PluginCommand original = command("tp", otherPlugin);
        commands.register("otherplugin", original);
        PluginCommand shortcut = command("tp", suite);

        assertTrue(bindings.register(commands, commands.known(), shortcut, false, logger));
        assertSame(shortcut, commands.getCommand("tp"));
        assertSame(shortcut, commands.getCommand("arcartx-suite:tp"));
        assertSame(original, commands.getCommand("otherplugin:tp"));
        assertEquals("tp", shortcut.getLabel());
        assertEquals("插件 OtherPlugin", ModuleCommandBindings.describeSource(original));

        bindings.unregister(commands.known());
        assertSame(original, commands.getCommand("tp"));
        assertNull(commands.getCommand("arcartx-suite:tp"));

        assertTrue(bindings.register(commands, commands.known(), command("tp", suite), true, logger));
        assertSame(original, commands.getCommand("tp"));
        assertEquals("arcartx-suite:tp", commands.getCommand("arcartx-suite:tp").getLabel());
    }

    @Test
    void protectionKeepsExistingAliasEvenWhenBukkitWouldReplaceIt() throws Exception {
        PluginCommand original = command("other", otherPlugin, "tp");
        commands.register("otherplugin", original);
        PluginCommand shortcut = command("tp", suite, "teleport");
        FormattedCommandAlias serverAlias = new FormattedCommandAlias("teleport", new String[]{"other"});
        commands.known().put("teleport", serverAlias);

        assertTrue(bindings.register(commands, commands.known(), shortcut, true, logger));
        assertSame(original, commands.getCommand("tp"));
        assertSame(serverAlias, commands.getCommand("teleport"));
        assertSame(shortcut, commands.getCommand("arcartx-suite:tp"));
        assertSame(shortcut, commands.getCommand("arcartx-suite:teleport"));
        assertEquals("arcartx-suite:tp", shortcut.getLabel());
        assertTrue(shortcut.getAliases().isEmpty());
        assertEquals("服务器配置（commands.yml）", ModuleCommandBindings.describeSource(serverAlias));
        assertTrue(ModuleCommandBindings.describeSource(commands.getCommand("bukkit:version"))
            .startsWith("服务器内置"));
    }

    @Test
    void unregisterKeepsBindingsAddedAfterEssentialsRegistered() throws Exception {
        commands.register("otherplugin", command("tp", otherPlugin));
        assertTrue(bindings.register(commands, commands.known(), command("tp", suite), false, logger));
        PluginCommand replacement = command("tp", otherPlugin);
        commands.known().put("tp", replacement);

        bindings.unregister(commands.known());
        assertSame(replacement, commands.getCommand("tp"));
        assertNull(commands.getCommand("arcartx-suite:tp"));
    }

    @Test
    void refusesToReplaceAnotherSuiteCommand() throws Exception {
        PluginCommand original = command("axs", suite);
        commands.register("arcartx-suite", original);

        assertFalse(bindings.register(commands, commands.known(), command("axs", suite), false, logger));
        assertSame(original, commands.getCommand("axs"));
        assertSame(original, commands.getCommand("arcartx-suite:axs"));
    }

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
            (instance, method, args) -> {
                if (method.getName().equals("getName")) {
                    return name;
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static PluginCommand command(String name, Plugin plugin, String... aliases) throws Exception {
        Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        PluginCommand command = constructor.newInstance(name, plugin);
        command.setAliases(new ArrayList<>(List.of(aliases)));
        return command;
    }

    private static final class TestCommandMap extends SimpleCommandMap {
        TestCommandMap() {
            super(null);
        }

        Map<String, Command> known() {
            return knownCommands;
        }
    }
}
