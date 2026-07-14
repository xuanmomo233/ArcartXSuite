package xuanmo.arcartxsuite.api.util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TemporaryOpExecutor {

    private static final Object LEDGER_LOCK = new Object();
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        "op", "deop", "stop", "restart", "reload", "rl", "whitelist",
        "ban", "ban-ip", "pardon", "kick", "pex", "lp", "luckperms",
        "perm", "perms", "permission", "permissions", "manuadd", "manuaddp",
        "bukkit:op", "minecraft:op"
    );
    private static final Set<UUID> PENDING_PLAYERS = new HashSet<>();
    private static final Logger FALLBACK_LOGGER =
        Logger.getLogger(TemporaryOpExecutor.class.getName());

    private static File ledgerFile;
    private static Logger logger = FALLBACK_LOGGER;
    private static boolean initialized;
    private static boolean warnedUninitialized;

    private TemporaryOpExecutor() {
    }

    public static void init(File file, Logger injectedLogger) {
        Objects.requireNonNull(file, "file");
        synchronized (LEDGER_LOCK) {
            ledgerFile = file;
            logger = injectedLogger == null ? FALLBACK_LOGGER : injectedLogger;
            initialized = true;
            warnedUninitialized = false;
            PENDING_PLAYERS.clear();
        }
    }

    public static void reconcile() {
        synchronized (LEDGER_LOCK) {
            if (!initialized || ledgerFile == null) {
                logger.warning("TemporaryOpExecutor 尚未初始化，无法进行临时 OP 台账对账。");
                return;
            }
            Set<UUID> stalePlayers;
            try {
                stalePlayers = readLedgerLocked();
            } catch (IOException exception) {
                logger.log(Level.WARNING, "读取临时 OP 台账失败，跳过启动对账。", exception);
                return;
            }
            for (UUID uuid : stalePlayers) {
                try {
                    Bukkit.getOfflinePlayer(uuid).setOp(false);
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING,
                        "清理临时 OP 台账玩家失败: " + uuid, exception);
                }
            }
            PENDING_PLAYERS.clear();
            try {
                writeLedgerLocked();
            } catch (IOException exception) {
                logger.log(Level.WARNING, "清空临时 OP 台账失败。", exception);
            }
            if (!stalePlayers.isEmpty()) {
                logger.info("已清理上次异常退出遗留的临时 OP 玩家: "
                    + stalePlayers.stream().map(UUID::toString).sorted().toList());
            }
        }
    }

    public static boolean execute(Player player, String command) {
        Objects.requireNonNull(player, "player");
        if (!isAllowedCommand(player, command)) {
            return false;
        }

        boolean wasOp = player.isOp();
        if (wasOp) {
            return Bukkit.dispatchCommand(player, commandWithoutLeadingSlash(command));
        }

        boolean tracked = trackBeforeElevation(player);
        if (!tracked && initialized) {
            return false;
        }
        try {
            player.setOp(true);
            return Bukkit.dispatchCommand(player, commandWithoutLeadingSlash(command));
        } finally {
            boolean restored = false;
            try {
                player.setOp(false);
                restored = true;
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING,
                    "恢复临时 OP 失败，台账将保留以便下次启动清理: " + player.getName(),
                    exception);
            }
            if (tracked && restored) {
                untrackAfterRestoration(player.getUniqueId());
            }
        }
    }

    private static boolean isAllowedCommand(Player player, String command) {
        if (command == null || command.isBlank()) {
            warnRejected(player, command, "命令为空");
            return false;
        }
        for (int index = 0; index < command.length(); index++) {
            if (Character.isISOControl(command.charAt(index))) {
                warnRejected(player, command, "命令包含控制字符");
                return false;
            }
        }
        String token = commandWithoutLeadingSlash(command).trim();
        int separator = -1;
        for (int index = 0; index < token.length(); index++) {
            if (Character.isWhitespace(token.charAt(index))) {
                separator = index;
                break;
            }
        }
        String commandName = (separator < 0 ? token : token.substring(0, separator))
            .toLowerCase(Locale.ROOT);
        int namespaceSeparator = commandName.lastIndexOf(':');
        String canonicalName = namespaceSeparator >= 0
            ? commandName.substring(namespaceSeparator + 1)
            : commandName;
        if (BLOCKED_COMMANDS.contains(commandName)
            || BLOCKED_COMMANDS.contains(canonicalName)) {
            warnRejected(player, command, "命令属于临时 OP 黑名单");
            return false;
        }
        return true;
    }

    private static String commandWithoutLeadingSlash(String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private static boolean trackBeforeElevation(Player player) {
        synchronized (LEDGER_LOCK) {
            if (!initialized || ledgerFile == null) {
                if (!warnedUninitialized) {
                    logger.warning("TemporaryOpExecutor 尚未初始化，临时 OP 将跳过持久台账。");
                    warnedUninitialized = true;
                }
                return false;
            }
            UUID uuid = player.getUniqueId();
            PENDING_PLAYERS.add(uuid);
            try {
                writeLedgerLocked();
                return true;
            } catch (IOException exception) {
                PENDING_PLAYERS.remove(uuid);
                logger.log(Level.WARNING,
                    "写入临时 OP 台账失败，拒绝提升玩家权限: " + player.getName(), exception);
                return false;
            }
        }
    }

    private static void untrackAfterRestoration(UUID uuid) {
        synchronized (LEDGER_LOCK) {
            PENDING_PLAYERS.remove(uuid);
            try {
                writeLedgerLocked();
            } catch (IOException exception) {
                PENDING_PLAYERS.add(uuid);
                logger.log(Level.WARNING,
                    "移除临时 OP 台账记录失败，将保留记录以便下次启动清理: " + uuid,
                    exception);
            }
        }
    }

    private static Set<UUID> readLedgerLocked() throws IOException {
        Set<UUID> result = new HashSet<>();
        if (!ledgerFile.exists()) {
            return result;
        }
        for (String line : Files.readAllLines(ledgerFile.toPath(), StandardCharsets.UTF_8)) {
            String value = line.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException exception) {
                logger.warning("忽略无效的临时 OP 台账 UUID: " + value);
            }
        }
        return result;
    }

    private static void writeLedgerLocked() throws IOException {
        Path target = ledgerFile.toPath();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String content = PENDING_PLAYERS.stream()
            .map(UUID::toString)
            .sorted(Comparator.naturalOrder())
            .reduce((left, right) -> left + "\n" + right)
            .map(value -> value + "\n")
            .orElse("");
        Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            try {
                Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void warnRejected(Player player, String command, String reason) {
        logger.warning("拒绝临时 OP 命令: player=" + player.getName()
            + " reason=" + reason + " command=" + sanitizeForLog(command));
    }

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "<null>";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "?");
        return sanitized.length() > 512 ? sanitized.substring(0, 512) + "..." : sanitized;
    }
}
