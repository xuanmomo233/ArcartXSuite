package xuanmo.arcartxsuite.qqbot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotClient;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotEvent;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;

class QQBotCommandRouterTest {
    private QQBotConfiguration configuration() {
        return configuration(true);
    }
    private QQBotConfiguration configuration(boolean enabled) {
        var stream = getClass().getResourceAsStream("/ArcartXQQBot.yml");
        assertNotNull(stream);
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        yaml.set("admin.enabled", enabled);
        yaml.set("admin.remote-console-enabled", false);
        yaml.set("admin.qq-list", List.of(123L));
        yaml.set("custom-commands.危险.type", "server-command");
        yaml.set("custom-commands.危险.permission", 0);
        yaml.set("custom-commands.危险.command", "{args}");
        return QQBotConfiguration.load(yaml, Logger.getAnonymousLogger());
    }
    private OneBotEvent event(String text) {
        var raw = JsonParser.parseString("{\"post_type\":\"message\",\"message_type\":\"group\",\"group_id\":1,\"user_id\":456,\"sender\":{\"role\":\"owner\"}}").getAsJsonObject();
        raw.addProperty("message", text); return new OneBotEvent(raw);
    }
    @Test void qqGroupOwnershipDoesNotGrantGameMailOrConsolePrivileges() {
        var config = configuration();
        var mail = mock(MailDispatchable.class);
        var client = mock(OneBotClient.class);
        var router = new QQBotCommandRouter(mock(JavaPlugin.class), config, client, null,
            mock(QQBotRepository.class), Logger.getAnonymousLogger(), null, () -> mail);
        assertTrue(router.handleCommand(event("#发邮件 Alice reward")));
        assertTrue(router.handleCommand(event("#危险 op Alice")));
        verifyNoInteractions(mail);
        verify(client, times(2)).sendGroupMessage(eq(1L), eq(config.messages().noPermission()));
        router.close();
        assertFalse(router.handleCommand(event("#发邮件 Alice reward")));
    }
    @Test void trustedGameAdministratorsDoNotRequireRemoteConsoleToBeEnabled() {
        var config = configuration();
        assertTrue(config.isAdmin(123L));
        assertFalse(config.isAdmin(456L));
        assertTrue(config.adminEnabled());
        assertFalse(config.remoteConsoleEnabled());
        var client = mock(OneBotClient.class);
        var router = new QQBotCommandRouter(mock(JavaPlugin.class), config, client, null,
            mock(QQBotRepository.class), Logger.getAnonymousLogger(), null, null);
        var raw = event("#cmd op Alice").raw(); raw.addProperty("user_id", 123L);
        assertTrue(router.handleCommand(new OneBotEvent(raw)), "Disabled remote console is rejected even for a trusted administrator");
        verify(client).sendGroupMessage(eq(1L), eq(config.messages().noPermission()));
    }
    @Test void disabledMasterCannotActivateALegacyConfiguredQq() {
        var config = configuration(false);
        assertFalse(config.isAdmin(123L));
        var client = mock(OneBotClient.class);
        var mail = mock(MailDispatchable.class);
        var router = new QQBotCommandRouter(mock(JavaPlugin.class), config, client, null,
            mock(QQBotRepository.class), Logger.getAnonymousLogger(), null, () -> mail);
        var raw = event("#发邮件 Alice reward").raw(); raw.addProperty("user_id", 123L);
        assertTrue(router.handleCommand(new OneBotEvent(raw)));
        verifyNoInteractions(mail);
        verify(client).sendGroupMessage(eq(1L), eq(config.messages().noPermission()));
    }
}
