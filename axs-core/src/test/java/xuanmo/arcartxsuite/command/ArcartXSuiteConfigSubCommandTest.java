package xuanmo.arcartxsuite.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.SuiteCoreImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArcartXSuiteConfigSubCommandTest {
    @Test
    void batchApplyRequiresCustomerAccessButRollbackRemainsAvailable() {
        var host = mock(SuiteCoreImpl.class);
        var sender = mock(CommandSender.class);
        var command = new ArcartXSuiteConfigSubCommand(host);

        assertEquals(List.of(), command.tabComplete(new String[]{"apply", "al"}));
        assertEquals(List.of("all"), command.tabComplete(new String[]{"rollback", "al"}));
        command.execute(sender, new String[]{"apply", "all", "--force"});
        verify(sender).sendMessage(contains("批量配置同步需要客户版有效授权"));
        verify(host, never()).getConfigDiagnosticEngine();

        when(host.customerFeaturesAllowed(null)).thenReturn(true);
        assertEquals(List.of("all"), command.tabComplete(new String[]{"apply", "al"}));
        command.execute(sender, new String[]{"apply", "all"});
        verify(host).getConfigDiagnosticEngine();
    }
}
