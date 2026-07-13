package xuanmo.arcartxsuite.afkreward.placeholder;

import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.afkreward.service.AfkRewardService;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.PlayerStats;

public final class AfkRewardPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<AfkRewardService> serviceSupplier;

    public AfkRewardPlaceholderExpansion(JavaPlugin plugin, Supplier<AfkRewardService> serviceSupplier) {
        this.plugin = plugin;
        this.serviceSupplier = serviceSupplier;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axsafkreward";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
            ? "ArcartXSuite" : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return "";

        AfkRewardService.PlayerAfkState state = service.getState(player.getUniqueId());
        PlayerStats stats = service.getStatsSnapshot(player.getUniqueId());

        String id = identifier.toLowerCase();
        return switch (id) {
            case "type" -> {
                if (state == null || state.areaName == null) yield "未挂机";
                yield state.mode == AfkRewardService.AfkMode.MANUAL ? "原地挂机" : "区域挂机";
            }
            case "area" -> (state != null && state.areaName != null) ? state.areaName : "";
            case "status" -> state != null && state.areaName != null ? "挂机中" : "未挂机";
            case "mode" -> state != null && state.areaName != null ? state.mode.name() : "";
            case "session_rewards" -> String.valueOf(service.getSessionRewards(player.getUniqueId()));
            case "session_time" -> formatTime(service.getSessionSeconds(player.getUniqueId()));
            case "daily_seconds" -> String.valueOf(service.getDailySeconds(player.getUniqueId()));
            case "remaining_daily" -> {
                int remaining = service.getRemainingDailySeconds(player.getUniqueId());
                yield remaining >= 0 ? String.valueOf(remaining) : "";
            }
            case "multiplier" -> {
                if (state == null || state.areaName == null) yield "1.00";
                var area = service.getArea(state.areaName);
                yield area == null ? "1.00" : String.format(java.util.Locale.ROOT, "%.2f",
                    service.computeMultiplier(player, state, area));
            }
            case "time" -> formatTime(state != null ? state.seconds : 0);
            case "total_time" -> formatTime(stats != null ? stats.totalSeconds() : 0);
            case "today" -> String.valueOf(stats != null ? stats.todayCount() : 0);
            case "total" -> String.valueOf(stats != null ? stats.totalCount() : 0);
            case "players" -> {
                if (state != null && state.areaName != null) {
                    yield String.valueOf(service.getPlayersInArea(state.areaName));
                }
                yield "0";
            }
            case "next" -> {
                if (state != null && state.areaName != null) {
                    int roundSec = service.getRewardRoundMinutes() * 60;
                    int elapsed = state.seconds - state.lastRewardSeconds;
                    int remain = Math.max(0, roundSec - elapsed);
                    yield String.valueOf(remain);
                }
                yield "0";
            }
            default -> {
                // 支持 %axsafkreward_top_1_name% %axsafkreward_top_1_time% %axsafkreward_top_1_rewards%
                if (id.startsWith("top_")) {
                    yield resolveTopPlaceholder(service, id);
                }
                // 支持 %axsafkreward_area_<name>% %axsafkreward_area_<name>_today% %axsafkreward_area_<name>_status%
                if (id.startsWith("area_")) {
                    yield resolveAreaPlaceholder(service, player, id);
                }
                if (id.equals("total_all")) {
                    int total = 0;
                    for (var area : service.areas().values()) {
                        var ast = service.getAreaStats(player.getUniqueId(), area.name());
                        total += ast.totalSeconds();
                    }
                    yield formatTime(total);
                }
                yield null;
            }
        };
    }

    private String resolveTopPlaceholder(AfkRewardService service, String id) {
        try {
            // id 格式: top_1_name / top_1_time / top_1_rewards
            String[] parts = id.split("_", 4);
            if (parts.length < 3) return "";
            int rank = Integer.parseInt(parts[1]);
            String field = parts[2];
            var board = service.getLeaderboard();
            if (rank < 1 || rank > board.size()) return "";
            PlayerStats entry = board.get(rank - 1);
            return switch (field) {
                case "name" -> entry.playerName();
                case "time" -> formatTime(entry.totalSeconds());
                case "rewards" -> String.valueOf(entry.totalCount());
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveAreaPlaceholder(AfkRewardService service, Player player, String id) {
        try {
            // id 格式: area_<name> 或 area_<name>_today 或 area_<name>_status
            String rest = id.substring("area_".length());
            String areaName;
            String suffix = "";
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore > 0 && (rest.endsWith("_today") || rest.endsWith("_status"))) {
                areaName = rest.substring(0, lastUnderscore);
                suffix = rest.substring(lastUnderscore + 1);
            } else {
                areaName = rest;
            }

            return switch (suffix) {
                case "today" -> {
                    var ast = service.getAreaStats(player.getUniqueId(), areaName);
                    yield formatTime(ast.todaySeconds());
                }
                case "status" -> {
                    var st = service.getState(player.getUniqueId());
                    yield (st != null && areaName.equals(st.areaName)) ? "是" : "否";
                }
                default -> {
                    var ast = service.getAreaStats(player.getUniqueId(), areaName);
                    yield formatTime(ast.totalSeconds());
                }
            };
        } catch (Exception e) {
            return "";
        }
    }

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d时%02d分%02d秒", hours, minutes, seconds);
        }
        return String.format("%02d分%02d秒", minutes, seconds);
    }
}
