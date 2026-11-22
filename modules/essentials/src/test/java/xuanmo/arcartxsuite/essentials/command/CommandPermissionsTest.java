package xuanmo.arcartxsuite.essentials.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

final class CommandPermissionsTest {
    @Test
    void menuAndAdminAccessDoNotGrantIndividualActions() {
        CommandSender member = sender("menu", "home", "warp", "tpa");
        assertTrue(EssentialsCommandDelegate.hasActionPermission(member, "menu"));
        assertTrue(EssentialsCommandDelegate.hasActionPermission(member, "home"));
        assertTrue(EssentialsCommandDelegate.hasActionPermission(member, "tpaccept"));
        assertFalse(EssentialsCommandDelegate.hasActionPermission(member, "sethome"));
        assertFalse(EssentialsCommandDelegate.hasActionPermission(member, "delhome"));

        CommandSender assistant = sender("admin", "unban");
        assertTrue(EssentialsCommandDelegate.hasActionPermission(assistant, "admin"));
        assertTrue(EssentialsCommandDelegate.hasActionPermission(assistant, "unban"));
        for (String action : new String[]{"ban", "tempban", "time", "weather", "setwarp", "delwarp", "setspawn", "sudo"}) {
            assertFalse(EssentialsCommandDelegate.hasActionPermission(assistant, action));
        }
    }

    private static CommandSender sender(String... grants) {
        Set<String> nodes = Set.of(grants);
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
            new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                if (method.getName().equals("hasPermission")) {
                    return nodes.stream().anyMatch(node -> ("axs.essentials." + node).equals(args[0]));
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }
}
