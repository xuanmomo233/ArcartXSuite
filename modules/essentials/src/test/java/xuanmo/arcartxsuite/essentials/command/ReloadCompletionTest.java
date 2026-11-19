package xuanmo.arcartxsuite.essentials.command;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;

class ReloadCompletionTest {
    @Test void reloadOnlyReportsTheFinalResult() {
        verifyResult(null, "success");
        verifyResult(new IllegalStateException("worker failed"), "failure");
    }

    private void verifyResult(Throwable failure, String expected) {
        var completion = new CompletableFuture<Void>();
        var messages = mock(MessageProvider.class);
        when(messages.get(eq("prefix"), any(Object[].class))).thenReturn("");
        when(messages.get(eq("reload.success"), any(Object[].class))).thenReturn("success");
        when(messages.get(eq("reload.failed"), any(Object[].class))).thenReturn("failure");
        var config = EssentialsConfiguration.load(new YamlConfiguration(), Logger.getAnonymousLogger());
        var delegate = new EssentialsCommandDelegate(config, null, null, null, null, () -> null,
            () -> null, () -> null, null, null, name -> null, mock(Plugin.class), () -> messages,
            () -> completion, new HashMap<>(), new HashMap<>(), null);
        var sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            delegate.onCommand(sender, "ess", new String[]{"essentials", "reload"});
            verify(sender, never()).sendMessage(anyString());
            if (failure == null) completion.complete(null);
            else completion.completeExceptionally(failure);
            verify(sender).sendMessage(expected);
        }
    }
}
