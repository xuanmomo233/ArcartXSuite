package xuanmo.arcartxsuite.condition;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.util.TemporaryOpExecutor;
import xuanmo.arcartxsuite.module.AxsLog;

/**
 * ArcartXSuite 自有的 Aria / JS 玩家门面对象。
 * <p>
 * 脚本中以裸名 {@code player} 访问，例如：
 * {@code player.getLevel()}、{@code player.papi('%vault_eco_balance%')}、
 * {@code player.command('warp vip')}。
 * <p>
 * 设计约定：所有方法均为“单层调用”，返回原始值（数字 / 字符串 / 布尔）或执行副作用，
 * 避免 Aria 链式调用返回对象不自动回包的限制（如需链式请用 {@link #bukkit()} 在 JS 中调用）。
 */
public final class AriaPlayer {

    private final Player player;
    private final PlaceholderResolverAPI papi;

    public AriaPlayer(@NotNull Player player, @Nullable PlaceholderResolverAPI papi) {
        this.player = player;
        this.papi = papi;
    }

    /** 逃生舱：返回原生 Bukkit Player（JS 有真实反射可链式调用；Aria 中不建议链式）。 */
    public @NotNull Player bukkit() {
        return player;
    }

    // ── 基本信息 ─────────────────────────────────────────────
    public @NotNull String getName() {
        return player.getName();
    }

    public @NotNull String getUuid() {
        return player.getUniqueId().toString();
    }

    @SuppressWarnings("deprecation")
    public @NotNull String getDisplayName() {
        return player.getDisplayName();
    }

    public int getLevel() {
        return player.getLevel();
    }

    public float getExp() {
        return player.getExp();
    }

    public int getFood() {
        return player.getFoodLevel();
    }

    public double getHealth() {
        return player.getHealth();
    }

    public @NotNull String getGameMode() {
        return player.getGameMode().name();
    }

    public @NotNull String getWorld() {
        return player.getWorld().getName();
    }

    public double getX() {
        return player.getLocation().getX();
    }

    public double getY() {
        return player.getLocation().getY();
    }

    public double getZ() {
        return player.getLocation().getZ();
    }

    public int getBlockX() {
        return player.getLocation().getBlockX();
    }

    public int getBlockY() {
        return player.getLocation().getBlockY();
    }

    public int getBlockZ() {
        return player.getLocation().getBlockZ();
    }

    public boolean isSneaking() {
        return player.isSneaking();
    }

    public boolean isSprinting() {
        return player.isSprinting();
    }

    public boolean isFlying() {
        return player.isFlying();
    }

    public boolean isOp() {
        return player.isOp();
    }

    // ── 权限 / 变量 ──────────────────────────────────────────
    /** 推荐使用的权限检查方法。 */
    public boolean hasPermission(@NotNull String permission) {
        return player.hasPermission(permission);
    }


    /** 用当前玩家上下文解析 PlaceholderAPI 占位符，返回字符串（PAPI 不可用时原样返回）。 */
    public @NotNull String papi(@NotNull String input) {
        if (papi == null) {
            return input;
        }
        String result = papi.applyPlaceholders(player, input);
        return result == null ? input : result;
    }

    /** {@link #papi(String)} 后解析为数字，无法解析时返回 0。 */
    public double papiNumber(@NotNull String input) {
        String resolved = papi(input);
        return parseNumber(resolved, input);
    }

    // ── 动作（副作用）────────────────────────────────────────
    /** 以玩家身份执行命令（等同玩家自己输入），返回是否执行成功。 */
    public boolean command(@NotNull String command) {
        return player.performCommand(strip(papi(command)));
    }

    /** 以控制台身份执行命令，返回是否执行成功。 */
    public boolean console(@NotNull String command) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strip(papi(command)));
    }

    /** 临时给玩家 OP 权限执行命令，执行后恢复原状态。 */
    public boolean op(@NotNull String command) {
        return TemporaryOpExecutor.execute(
            player,
            () -> player.performCommand(strip(papi(command)))
        );
    }

    /** 推荐使用的消息发送方法。 */
    public void msg(@NotNull String message) {
        player.sendMessage(color(papi(message)));
    }


    @SuppressWarnings("deprecation")
    public void title(@NotNull String title, @NotNull String subtitle) {
        player.sendTitle(color(papi(title)), color(papi(subtitle)), 10, 60, 10);
    }

    @SuppressWarnings("deprecation")
    public void title(@NotNull String title, @NotNull String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(color(papi(title)), color(papi(subtitle)), fadeIn, stay, fadeOut);
    }

    public void sound(@NotNull String name) {
        sound(name, 1.0F, 1.0F);
    }

    public void sound(@NotNull String name, double volume, double pitch) {
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
        } catch (IllegalArgumentException ignored) {
            try {
                player.playSound(player.getLocation(), name.toLowerCase(Locale.ROOT), (float) volume, (float) pitch);
            } catch (IllegalArgumentException secondException) {
                AxsLog.logger().fine("[Script] 无效声音名，音符枚举和字符串播放均失败: " + name);
                throw secondException;
            }
        }
    }

    public void close() {
        player.closeInventory();
    }

    // ── 内部工具 ─────────────────────────────────────────────
    private static String strip(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static double parseNumber(String text, String input) {
        if (text == null || text.isBlank()) {
            AxsLog.logger().fine(
                "[Script] papiNumber 解析失败，按 0 处理: " + input
            );
            return 0.0D;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException exception) {
            AxsLog.logger().fine(
                "[Script] papiNumber 解析失败，按 0 处理: " + input + " -> " + text
            );
            return 0.0D;
        }
    }
}
