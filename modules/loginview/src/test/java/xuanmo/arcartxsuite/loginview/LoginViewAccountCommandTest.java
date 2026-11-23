package xuanmo.arcartxsuite.loginview;

import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginViewAccountCommandTest {
    private final LoginViewModule module = new LoginViewModule();
    private final CommandSender sender = mock(CommandSender.class);
    private final String[] args = {"loginview", "account", "Alex"};

    @Test
    void verifiedIdentityRemainsDistinctFromAdmission() {
        UUID profile = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID connection = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alex");
        when(player.getUniqueId()).thenReturn(profile);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(player);
            auth.when(() -> AuthAccess.snapshot(profile)).thenReturn(
                    new AuthSnapshot(profile, connection, 1, account, AuthSnapshot.Registration.REGISTERED,
                            AuthSnapshot.Stage.AWAITING_REQUIREMENTS, "microsoft", Set.of(AuthSnapshot.Requirement.TERMS), ""),
                    new AuthSnapshot(profile, connection, 1, account, AuthSnapshot.Registration.REGISTERED,
                            AuthSnapshot.Stage.ADMITTED, "microsoft", Set.of(), ""));

            assertTrue(module.actions().contains("account"));
            assertFalse(module.allowsWithoutSuiteAdmin(sender, args));
            assertTrue(module.onCommand(sender, "axs", args));
            verify(sender).sendMessage("玩家 Alex | 账号 ID " + account);
            verify(sender).sendMessage("登记状态 已注册 | 身份来源 microsoft");
            verify(sender).sendMessage("身份验证 已通过 | 游戏准入 未放行 | 当前阶段 awaiting_requirements");
            module.onCommand(sender, "axs", args);
            verify(sender).sendMessage("身份验证 已通过 | 游戏准入 已放行 | 当前阶段 admitted");
            auth.verify(() -> AuthAccess.snapshot(profile), times(2));
        }
    }

    @Test
    void distinguishesAnOfflinePlayerFromAnOnlinePlayerWithoutAnAuthSession() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getName()).thenReturn("Alex");
        when(player.getUniqueId()).thenReturn(id);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(null, player);
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(AuthSnapshot.absent(id));

            module.onCommand(sender, "axs", args);
            verify(sender).sendMessage("本服没有名为 Alex 的在线玩家。");
            auth.verifyNoInteractions();
            module.onCommand(sender, "axs", args);
            verify(sender).sendMessage("Alex 的当前连接没有可查询的认证状态。");
        }
    }
}
