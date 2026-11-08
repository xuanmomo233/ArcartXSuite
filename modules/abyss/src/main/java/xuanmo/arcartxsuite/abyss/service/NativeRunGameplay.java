package xuanmo.arcartxsuite.abyss.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import xuanmo.arcartxsuite.abyss.instance.FailReason;
import xuanmo.arcartxsuite.abyss.instance.LeaveReason;
import xuanmo.arcartxsuite.abyss.ui.AbyssUiService;
import xuanmo.arcartxsuite.api.capability.AbyssReviveAccess;

/** Abyss-owned player interactions consuming the same durable services as integrations. */
public final class NativeRunGameplay {
    public record Row(String title, String detail, String target) { }
    private static final class View {
        String mode;
        UUID run;
        String cursor;
        String next;
        int rescuePage;
        boolean loading;
        long query;
        String notice = "";
        final List<String> previous = new ArrayList<>();
    }
    private static final class Channel {
        UUID run, actor, target, operation;
        Location origin;
        long started, progressAt;
        boolean reserved, committing, cancelled;
    }
    private final AbyssService service;
    private final java.util.function.LongSupplier clock;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Channel> channels = new HashMap<>();
    private final Map<UUID, UUID> wipeWindows = new HashMap<>();
    private final java.util.Set<UUID> leaving = new java.util.HashSet<>();

    NativeRunGameplay(AbyssService service) { this(service, System::currentTimeMillis); }
    NativeRunGameplay(AbyssService service, java.util.function.LongSupplier clock) {
        this.service = service; this.clock = clock;
    }

    private AbyssUiService ui() {
        return service.uiFacade() instanceof AbyssUiService ui ? ui : null;
    }
    public void closeView(Player player) { views.remove(player.getUniqueId()); }

    public void open(Player player, String mode) {
        if (ui() == null) { player.sendMessage("§e战术面板需要启用 Abyss UI。"); return; }
        var view = new View();
        view.mode = mode.equals("loot") ? "loot" : "rescue";
        var run = service.instances().getInstance(player);
        view.run = run == null ? null : run.getInstanceId();
        views.put(player.getUniqueId(), view);
        if (view.run != null && view.mode.equals("rescue")) inspectPrevious(player, view);
        else if (view.run != null) render(player, view);
        else service.runLedger().recentRun(player.getUniqueId()).whenComplete((recent, error) -> service.runOnMain(() -> {
            if (!current(player, view)) return;
            view.run = recent;
            if (error != null) show(player, view, "读取本局记录失败，请刷新重试。", List.of(), false, false);
            else if (recent == null) show(player, view, "还没有可查看的本局记录。", List.of(), false, false);
            else { view.mode = "loot"; render(player, view); }
        }));
    }

    private void inspectPrevious(Player player, View view) {
        service.reviveTransactions().lastNative(view.run, player.getUniqueId()).whenComplete((operation, error) ->
            service.runOnMain(() -> {
                if (!current(player, view)) return;
                if (error != null) { show(player, view, "上次救援读取失败，请刷新补查。", List.of(), false, false); return; }
                if (operation == null) { render(player, view); return; }
                service.reviveAccess().query(RunOperations.NATIVE_OWNER, operation).whenComplete((receipt, failure) ->
                    service.runOnMain(() -> {
                        if (!current(player, view)) return;
                        if (failure != null) { show(player, view, "上次救援结果待确认，请刷新补查。", List.of(), false, false); return; }
                        view.notice = switch (receipt.state()) {
                            case UNKNOWN, COMMITTING -> "上次救援待核账，保留原编号；";
                            case COMMITTED -> "上次救援已确认成功；";
                            default -> "";
                        };
                        if (receipt.state() == AbyssReviveAccess.State.RESERVED && !channels.containsKey(player.getUniqueId())) {
                            service.reviveAccess().release(RunOperations.NATIVE_OWNER, operation)
                                .whenComplete((released, releaseError) -> service.runOnMain(() -> {
                                    if (!current(player, view)) return;
                                    if (releaseError != null) show(player, view, "上次预留释放待确认，请刷新。", List.of(), false, false);
                                    else render(player, view);
                                }));
                        } else render(player, view);
                    }));
            }));
    }

    public void page(Player player, int direction) {
        var view = views.get(player.getUniqueId());
        if (view == null || view.loading) return;
        if (view.mode.equals("rescue")) {
            view.rescuePage = Math.max(0, view.rescuePage + direction);
        } else if (direction > 0 && view.next != null) {
            view.previous.add(view.cursor == null ? "" : view.cursor);
            view.cursor = view.next;
        } else if (direction < 0 && !view.previous.isEmpty()) {
            String previous = view.previous.remove(view.previous.size() - 1);
            view.cursor = previous.isEmpty() ? null : previous;
        } else return;
        render(player, view);
    }

    private boolean current(Player player, View view) {
        return service.running() && player.isOnline() && views.get(player.getUniqueId()) == view;
    }
    private void render(Player player, View view) {
        view.loading = true;
        long query = ++view.query;
        if (view.mode.equals("loot")) {
            service.runLootAccess().snapshot(view.run, player.getUniqueId(), view.cursor, 8)
                .whenComplete((page, error) -> service.runOnMain(() -> {
                    if (!current(player, view) || view.query != query) return;
                    if (error != null) { show(player, view, "读取失败，请刷新重试。", List.of(), false, false); return; }
                    if (!page.code().equals("OK")) {
                        view.cursor = null; view.next = null; view.previous.clear();
                        show(player, view, "记录状态：" + page.code() + "；可刷新重新读取。", List.of(), false, false);
                        return;
                    }
                    var rows = page.entries().stream().map(entry -> new Row(
                        entry.displayName() + " × " + java.math.BigDecimal.valueOf(entry.quantity()).stripTrailingZeros().toPlainString(),
                        (entry.quality() == null ? "" : entry.quality() + " · ") + lootState(entry.state()) + " · " + entry.source(), "")).toList();
                    view.next = page.nextCursor();
                    show(player, view, "已登记奖励 · 地面掉落请自行拾取 · " + page.runState(),
                        rows, !view.previous.isEmpty(), view.next != null);
                }));
            return;
        }
        var run = service.instances().getInstance(view.run);
        if (run == null || !run.hasPlayer(player.getUniqueId())) {
            show(player, view, "当前没有可救援的副本，请切换到战利品回顾。", List.of(), false, false);
            return;
        }
        var policy = run.getTemplate().requirements().nativeRescue();
        var dead = run.getPlayers().stream().filter(run::isPlayerCurrentlyDead)
            .filter(target -> !target.equals(player) || policy.allowSelf()).toList();
        int start = Math.min(view.rescuePage * 8, Math.max(0, dead.size() - 1) / 8 * 8);
        view.rescuePage = start / 8;
        var members = dead.subList(start, Math.min(dead.size(), start + 8));
        var names = members.stream().map(target -> target.equals(player) ? "自救" : "救援 " + target.getName()).toList();
        var futures = members.stream().map(target -> service.reviveAccess()
            .reviveStatus(view.run, target.getUniqueId()).toCompletableFuture()).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> service.runOnMain(() -> {
            if (!current(player, view) || view.query != query) return;
            if (error != null) { show(player, view, "额度读取失败，请刷新重试。", List.of(), false, false); return; }
            var rows = new ArrayList<Row>();
            for (int index = 0; index < members.size(); index++) {
                var status = futures.get(index).join();
                rows.add(new Row(names.get(index), "剩余 " + status.remaining() + "/" + status.maximum()
                    + " · 已用 " + status.used(), status.remaining() > 0 ? members.get(index).getUniqueId().toString() : ""));
            }
            String hint = !policy.enabled() ? "本副本未启用原生救援，可在流程工作台开启。"
                : "读条 " + policy.channelSeconds() + " 秒；移动、受伤或离场会中断。";
            show(player, view, view.notice + hint, rows, start > 0, start + 8 < dead.size());
        }));
    }

    private void show(Player player, View view, String hint, List<Row> rows, boolean previous, boolean next) {
        view.loading = false;
        if (ui() != null && current(player, view))
            ui().openRunPanel(player, view.mode, hint, rows, previous, next);
    }
    private static String lootState(xuanmo.arcartxsuite.api.capability.AbyssRunLootAccess.State state) {
        return switch (state) {
            case PENDING -> "§e待发放";
            case HOST_ACCEPTED -> "§a宿主已接收";
            case DELIVERED -> "§a已发放";
            case UNKNOWN -> "§c待核账";
        };
    }

    public String canRescue(AbyssInstance run, UUID actorId, UUID targetId) {
        if (run == null || !run.admitted() || run.designMode() || run.getStatus() != AbyssStatus.ACTIVE) return "NOT_ACTIVE";
        var policy = run.getTemplate().requirements().nativeRescue();
        if (!policy.enabled() || run.getTemplate().requirements().reviveLimit() == 0) return "NATIVE_DISABLED";
        var actor = Bukkit.getPlayer(actorId);
        var target = Bukkit.getPlayer(targetId);
        if (actor == null || target == null || !actor.isOnline() || !target.isOnline()
                || !run.hasPlayer(actorId) || !run.hasPlayer(targetId)
                || actor.getWorld() != run.getWorld() || target.getWorld() != run.getWorld()) return "NOT_A_MEMBER";
        if (!run.isPlayerCurrentlyDead(target)) return "NOT_DEAD";
        if (actor.isDead() || target.isDead()) return "WAIT_RESPAWN";
        if (actorId.equals(targetId) ? !policy.allowSelf() : run.isPlayerCurrentlyDead(actor)) return "RESCUER_UNAVAILABLE";
        if (policy.range() > 0 && actor.getLocation().distanceSquared(target.getLocation()) > policy.range() * policy.range())
            return "OUT_OF_RANGE";
        return null;
    }

    public void rescue(Player actor, UUID target) {
        var run = service.instances().getInstance(actor);
        var view = views.get(actor.getUniqueId());
        if (view == null || !view.mode.equals("rescue") || run == null || !run.getInstanceId().equals(view.run)) {
            actor.sendMessage("§e战术面板已过期，请重新打开。");
            return;
        }
        String invalid = canRescue(run, actor.getUniqueId(), target);
        if (invalid != null) { actor.sendMessage("§e" + reasonText(invalid)); return; }
        if (channels.containsKey(actor.getUniqueId())) { actor.sendMessage("§e已有救援正在处理。"); return; }
        var channel = new Channel();
        channel.run = run.getInstanceId(); channel.actor = actor.getUniqueId(); channel.target = target;
        channel.operation = UUID.randomUUID(); channel.origin = actor.getLocation().clone();
        channels.put(channel.actor, channel);
        service.reviveTransactions().lastNative(channel.run, channel.actor).whenComplete((previous, error) -> service.runOnMain(() -> {
            if (!active(channel)) return;
            if (error != null) { end(channel, "读取上次救援失败，请稍后重试。"); return; }
            if (previous == null) { reserve(channel); return; }
            service.reviveAccess().query(RunOperations.NATIVE_OWNER, previous).whenComplete((receipt, failure) -> service.runOnMain(() -> {
                if (!active(channel)) return;
                if (failure != null || receipt.state() == AbyssReviveAccess.State.UNKNOWN
                        || receipt.state() == AbyssReviveAccess.State.COMMITTING) {
                    end(channel, "上次救援结果待核账，已保留原操作编号。"); return;
                }
                service.reviveAccess().release(RunOperations.NATIVE_OWNER, previous).whenComplete((released, releaseError) ->
                    service.runOnMain(() -> {
                        if (!active(channel)) return;
                        if (releaseError != null) end(channel, "上次预留仍待确认，请稍后重试。");
                        else reserve(channel);
                    }));
            }));
        }));
    }
    private boolean active(Channel channel) { return channels.get(channel.actor) == channel && !channel.cancelled; }
    private void reserve(Channel channel) {
        service.reviveAccess().reviveStatus(channel.run, channel.target).whenComplete((status, error) -> service.runOnMain(() -> {
            if (!active(channel)) return;
            if (error != null) { end(channel, "额度读取失败，请重试。"); return; }
            service.reviveTransactions().reserveNative(channel.operation, channel.run, channel.actor, channel.target, status.revision())
                .whenComplete((receipt, failure) -> service.runOnMain(() -> {
                    if (failure != null || receipt.state() != AbyssReviveAccess.State.RESERVED) {
                        if (active(channel)) end(channel, failure == null ? "救援未开始：" + reasonText(receipt.code()) : "救援请求待确认，请再次打开面板补查。");
                        return;
                    }
                    channel.reserved = true;
                    if (!active(channel)) { service.reviveAccess().release(RunOperations.NATIVE_OWNER, channel.operation); return; }
                    channel.started = clock.getAsLong();
                    Player actor = Bukkit.getPlayer(channel.actor);
                    if (actor != null && ui() != null) ui().closeRunPanel(actor);
                }));
        }));
    }

    private String interrupted(Channel channel) {
        var run = service.instances().getInstance(channel.run);
        String invalid = canRescue(run, channel.actor, channel.target);
        if (invalid != null) return invalid;
        var actor = Bukkit.getPlayer(channel.actor);
        return actor.getWorld() != channel.origin.getWorld()
            || actor.getLocation().distanceSquared(channel.origin) > 0.25 ? "MOVE_INTERRUPTED" : null;
    }
    public boolean canCommit(UUID operation, UUID actor) {
        var channel = channels.get(actor);
        var run = channel == null ? null : service.instances().getInstance(channel.run);
        return channel != null && channel.operation.equals(operation) && channel.committing && !channel.cancelled
            && interrupted(channel) == null && run != null
            && clock.getAsLong() - channel.started >= run.getTemplate().requirements().nativeRescue().channelSeconds() * 1000L;
    }

    public void tick() {
        for (var channel : List.copyOf(channels.values())) {
            if (channel.committing) continue;
            String invalid = interrupted(channel);
            if (invalid != null) { interrupt(channel.actor, invalid); continue; }
            if (!channel.reserved) continue;
            var run = service.instances().getInstance(channel.run);
            long now = clock.getAsLong();
            long remaining = run.getTemplate().requirements().nativeRescue().channelSeconds() * 1000L - (now - channel.started);
            if (remaining <= 0) {
                channel.committing = true;
                service.reviveAccess().commit(RunOperations.NATIVE_OWNER, channel.operation)
                    .whenComplete((receipt, error) -> service.runOnMain(() -> end(channel,
                        error != null ? "救援结果待确认，可重新打开面板补查。"
                        : receipt.state() == AbyssReviveAccess.State.COMMITTED ? "§a救援成功。"
                        : "救援结果：" + receipt.state() + " / " + receipt.code())));
            } else if (now - channel.progressAt >= 500) {
                channel.progressAt = now;
                var actor = Bukkit.getPlayer(channel.actor);
                actor.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent("§e救援中 " + ((remaining + 999) / 1000) + " 秒 · 请保持原位"));
            }
        }
    }

    public void interrupt(UUID actor, String reason) {
        var channel = channels.get(actor);
        if (channel == null) return;
        channel.cancelled = true;
        if (channel.committing) return;
        if (channel.reserved) service.reviveAccess().release(RunOperations.NATIVE_OWNER, channel.operation);
        end(channel, reason);
    }
    private void end(Channel channel, String message) {
        channels.remove(channel.actor, channel);
        var actor = Bukkit.getPlayer(channel.actor);
        if (actor != null && actor.isOnline()) actor.sendMessage("§e" + reasonText(message));
    }

    private static String reasonText(String code) {
        return switch (code) {
            case "NOT_ACTIVE" -> "副本已结束";
            case "NATIVE_DISABLED" -> "本副本未启用救援";
            case "NOT_A_MEMBER" -> "成员已离场";
            case "NOT_DEAD" -> "目标已经恢复";
            case "RESCUER_UNAVAILABLE" -> "当前不能执行救援";
            case "OUT_OF_RANGE" -> "距离目标过远";
            case "MOVE_INTERRUPTED" -> "移动中断救援";
            case "WAIT_RESPAWN" -> "请先完成原版重生，再进行救援";
            case "EXHAUSTED" -> "本局额度已耗尽";
            case "TARGET_RESERVED" -> "已有成员正在救援该目标";
            case "STALE_REVISION" -> "额度状态已变化，请重试";
            default -> code;
        };
    }

    public boolean teamWipe(AbyssInstance run) {
        var policy = run.getTemplate().requirements().nativeRescue();
        if (!policy.enabled() || !policy.allowSelf() || run.getTemplate().requirements().reviveLimit() == 0) return false;
        UUID window = UUID.randomUUID();
        wipeWindows.put(run.getInstanceId(), window);
        for (var player : run.getPlayers()) {
            player.sendMessage("§c全队阵亡！§e请在 " + policy.wipeWindowSeconds() + " 秒内通过 /abyss run 完成自救。");
            if (!player.isDead()) open(player, "rescue");
        }
        service.scheduleOnce(run, () -> {
            if (!window.equals(wipeWindows.get(run.getInstanceId()))) return;
            wipeWindows.remove(run.getInstanceId());
            if (run.getStatus() == AbyssStatus.ACTIVE && run.getAlivePlayerCount() == 0) {
                service.runLifecycleScript(run, "all_death", null);
                service.failDungeon(run, FailReason.TEAM_WIPE);
            }
        }, policy.wipeWindowSeconds() * 20L);
        return true;
    }
    void revived(AbyssInstance run) {
        wipeWindows.remove(run.getInstanceId());
        run.clearTeamWipeHandled();
    }
    void detached(UUID run) {
        wipeWindows.remove(run);
        for (var channel : List.copyOf(channels.values())) if (channel.run.equals(run)) interrupt(channel.actor, "副本已结束");
    }
    void shutdown() {
        for (var actor : List.copyOf(channels.keySet())) interrupt(actor, "服务关闭");
        views.clear(); wipeWindows.clear();
    }

    public void leave(Player player) {
        var run = service.instances().getInstance(player);
        if (run == null) { player.sendMessage("§e当前不在副本中。"); return; }
        if (run.designMode() || !run.admitted()) { service.playerExit(run, player, LeaveReason.QUIT); return; }
        if (!leaving.add(player.getUniqueId())) return;
        interrupt(player.getUniqueId(), "离场中断救援");
        service.runOperations().leaveNative(run.getInstanceId(), player.getUniqueId()).whenComplete((receipt, error) ->
            service.runOnMain(() -> {
                leaving.remove(player.getUniqueId());
                if (error != null) player.sendMessage("§e撤离结果待确认，重试会补查原操作。");
                else if (receipt.state() != xuanmo.arcartxsuite.api.capability.AbyssRunAccess.OperationState.COMPLETED)
                    player.sendMessage("§e撤离状态：" + receipt.code() + "；可重试补查。");
                else if (ui() != null) ui().closeExitedStats(player, run.getInstanceId());
            }));
    }

    public void leaveFromPanel(Player player) {
        var view = views.get(player.getUniqueId());
        var run = service.instances().getInstance(player);
        if (view == null || run == null || !run.getInstanceId().equals(view.run)) {
            player.sendMessage("§e战术面板已过期，请重新打开。");
            return;
        }
        leave(player);
    }
}
