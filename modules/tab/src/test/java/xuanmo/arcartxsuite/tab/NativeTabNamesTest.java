package xuanmo.arcartxsuite.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class NativeTabNamesTest {
    @Test
    void writesOnlyChangesAndRestoresTheOriginalName() {
        UUID id = UUID.randomUUID();
        var account = account(id, "Original");
        var nativeNames = new NativeTabNames();

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(account.player());
            nativeNames.apply(Map.of(account.player(), "First"));
            nativeNames.apply(Map.of(account.player(), "First"));
            nativeNames.apply(Map.of(account.player(), "Updated"));
            assertEquals("Updated", account.read());
            nativeNames.close();
        }
        assertEquals("Original", account.read());
        verify(account.player(), times(3)).setPlayerListName(nullable(String.class));
    }

    @Test
    void yieldsToAnExternalRename() {
        UUID id = UUID.randomUUID();
        var account = account(id, "Original");
        var nativeNames = new NativeTabNames();

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(account.player());
            nativeNames.apply(Map.of(account.player(), "Suite"));
            account.current().set("Other plugin");
            nativeNames.apply(Map.of(account.player(), "Updated"));
            nativeNames.close();
        }
        assertEquals("Other plugin", account.read());
        verify(account.player(), times(1)).setPlayerListName(nullable(String.class));
    }

    @Test
    void restoresOnRemovalOfAnActivePlayer() {
        UUID id = UUID.randomUUID();
        var account = account(id, "Original");
        var nativeNames = new NativeTabNames();

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(account.player());
            nativeNames.apply(Map.of(account.player(), "Suite"));
            nativeNames.apply(Map.of());
            assertEquals("Original", account.read());
            nativeNames.close();
        }
        verify(account.player(), times(2)).setPlayerListName(nullable(String.class));
    }

    @Test
    void tracksTheNewBaselineOnReconnectWithTheSameUuid() {
        UUID id = UUID.randomUUID();
        var previous = account(id, "First connection");
        var reconnect = account(id, "Second connection");
        var nativeNames = new NativeTabNames();
        nativeNames.apply(Map.of(previous.player(), "Suite first"));

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(reconnect.player());
            nativeNames.apply(Map.of(reconnect.player(), "Suite second"));
            nativeNames.close();
        }
        assertEquals("Second connection", reconnect.read());
        verify(previous.player(), times(1)).setPlayerListName(nullable(String.class));
    }

    private static Account account(UUID id, String initial) {
        Player player = mock(Player.class);
        var value = new AtomicReference<>(initial);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getPlayerListName()).thenAnswer(ignored -> value.get());
        doAnswer(call -> {
            value.set(call.getArgument(0));
            return null;
        }).when(player).setPlayerListName(nullable(String.class));
        return new Account(player, value);
    }

    private record Account(Player player, AtomicReference<String> current) {
        String read() {
            return current.get();
        }
    }
}
