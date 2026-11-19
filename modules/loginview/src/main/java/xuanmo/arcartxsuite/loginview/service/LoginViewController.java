package xuanmo.arcartxsuite.loginview.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;

/** All replies are bound to a captured connection. This controller never owns admission state. */
public final class LoginViewController implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final PacketBridgeAPI packets;
    private final AuthRuntimeCapability runtime;
    private final String ui;
    private final long delayNanos;
    private final Map<UUID, ViewSession> views = new HashMap<>();
    private BukkitTask task;
    private volatile boolean active;

    private static final class ViewSession {
        final Player player;
        final long joinedAt = System.nanoTime();
        boolean forced;
        UUID pendingRefresh;
        AuthSnapshot shownState;
        AuthRuntimeCapability.ExternalPrompt shownPrompt;

        ViewSession(Player player) { this.player = player; }

        void invalidate() {
            shownState = null;
            shownPrompt = null;
        }
    }

    public LoginViewController(JavaPlugin plugin,PacketBridgeAPI packets,AuthRuntimeCapability runtime,String ui,long delay) {
        this.plugin = plugin;
        this.packets = packets;
        this.runtime = runtime;
        this.ui = ui;
        this.delayNanos = delay * 50_000_000L;
    }
    public void start() {
        if (active) return;
        active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) track(player);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, 1L, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        var view = views.get(id);
        if (view != null && view.player == player) views.remove(id);
    }

    private void track(Player player) {
        views.put(player.getUniqueId(), new ViewSession(player));
    }

    private void pulse() {
        if (!active) return;
        long now = System.nanoTime();
        for (var entry : views.entrySet()) {
            var view = entry.getValue();
            if (view.player.isOnline() && now - view.joinedAt >= delayNanos) {
                AuthSnapshot state = runtime.snapshot(entry.getKey());
                reconcile(view, state, runtime.externalPrompt(entry.getKey()), false);
            }
        }
    }

    public void open(Player player, boolean force) {
        var view = currentView(player);
        if (view == null) return;
        UUID id = player.getUniqueId();
        reconcile(view, runtime.snapshot(id), runtime.externalPrompt(id), force);
    }

    private ViewSession currentView(Player player) {
        if (!active || !player.isOnline()) return null;
        UUID id = player.getUniqueId();
        var view = views.get(id);
        if (view != null && view.player == player) return view;
        if (Bukkit.getPlayer(id) != player) return null;
        track(player);
        return views.get(id);
    }

    private void reconcile(ViewSession view, AuthSnapshot state, AuthRuntimeCapability.ExternalPrompt prompt, boolean force) {
        if (state.connectionId() == null) return;
        Player player = view.player;
        if(force && state.admitted() && !state.provider().equals("local-password")) {
            if("server".equals(state.provider())) {
                player.sendMessage("Suite 认证未启用，玩家已正常准入。");
                return;
            }
            player.sendMessage("外部身份的密码请到对应身份提供方修改。"); return;
        }
        if (force) view.forced = true;
        boolean open = packets.isUiOpen(player, ui);
        if (state.admitted() && !view.forced && prompt == null) {
            if (open) packets.closeUiUnsafe(player, ui);
            view.invalidate();
            return;
        }
        if (!open) {
            if (!packets.openUiUnsafe(player, ui)) return;
            view.invalidate();
        }
        if (!force && state.equals(view.shownState) && Objects.equals(prompt, view.shownPrompt)) return;
        String mode=state.admitted()?"change":state.identityVerified()?"requirements":!runtime.passwordAuthenticationAllowed()?"verify"
            :state.registration()==AuthSnapshot.Registration.UNREGISTERED && runtime.passwordRegistrationAllowed()?"register":"login";
        var terms = runtime.terms();
        var payload=new HashMap<String,Object>(Map.of("connection",state.connectionId().toString(),"mode",mode,
            "stage",state.stage().name(),"player",player.getName(),"ready",state.stage()!=AuthSnapshot.Stage.LOADING,
            "requirements",state.requirements().stream().map(Enum::name).toList(),
            "terms",terms.lines(),"termsVersion",terms.version(),"termsEnabled",terms.enabled()));
        payload.put("needTerms",state.requirements().contains(AuthSnapshot.Requirement.TERMS));
        payload.put("needBinding",state.requirements().contains(AuthSnapshot.Requirement.QQ_BINDING));
        payload.put("externalId",prompt==null?"":prompt.requestId().toString());
        payload.put("externalCode",prompt==null?"":prompt.userCode());
        payload.put("externalUrl",prompt==null?"":prompt.verificationUrl());
        payload.put("externalConfirmation",prompt==null?"":prompt.confirmation());
        payload.put("canLink",state.admitted() && "local-password".equals(state.provider()) && runtime.externalLinkAvailable());
        payload.put("message",text(state.reason()));
        if (packets.sendPacket(player, ui, "state", payload)) {
            view.shownState = state;
            view.shownPrompt = prompt;
        }
    }
    public void clientReady(Player player) {
        var view = currentView(player);
        if (view == null) return;
        if (packets.isUiOpen(player, ui)) packets.closeUiUnsafe(player, ui);
        view.invalidate();
        UUID id = player.getUniqueId();
        reconcile(view, runtime.snapshot(id), runtime.externalPrompt(id), false);
    }
    public boolean handle(Player player,String channel,List<String> data) {
        if(!"AXS_auth_v1".equals(channel)) return false;
        if(!active || data==null || data.size()<2 || data.size()>7 || data.stream().anyMatch(v->v==null || v.length()>1024)) return true;
        UUID connection;
        try { connection=UUID.fromString(data.get(1)); } catch(IllegalArgumentException e) { return true; }
        UUID id = player.getUniqueId();
        if(!connection.equals(runtime.snapshot(id).connectionId())) return true;
        var view = currentView(player);
        if (view == null) return true;
        boolean refresh = "refresh".equals(data.get(0));
        CompletionStage<AuthRuntimeCapability.Result> job;
        switch(data.get(0)) {
            case "exit" -> { if(data.size()==2) player.kickPlayer("已退出登录流程。"); return true; }
            case "refresh" -> {
                if (data.size() != 2 || connection.equals(view.pendingRefresh)) return true;
                job = runtime.refresh(id, connection);
                view.pendingRefresh = connection;
            }
            case "external_link" -> {
                if(data.size()!=2) return true;
                job=runtime.beginExternalLink(player.getUniqueId(),connection);
            }
            case "external_confirm" -> {
                if(data.size()!=3) return true;
                UUID request;
                try { request=UUID.fromString(data.get(2)); } catch(IllegalArgumentException e) { return true; }
                job=runtime.confirmExternal(player.getUniqueId(),connection,request);
            }
            case "login","register" -> {
                if(data.size()!=7 || !"1".equals(data.get(5))) return true;
                if(data.get(0).equals("register") && !data.get(2).equals(data.get(3))) {
                    message(player,"两次密码不一致。",false); return true;
                }
                job=runtime.authenticate(player.getUniqueId(),connection,data.get(2).toCharArray(),
                    data.get(0).equals("register"),Boolean.parseBoolean(data.get(4)),data.get(6));
            }
            case "change" -> {
                if(data.size()!=5) return true;
                if(!data.get(3).equals(data.get(4))) { message(player,"两次新密码不一致。",false); return true; }
                job=runtime.changePassword(player.getUniqueId(),connection,data.get(2).toCharArray(),data.get(3).toCharArray());
            }
            case "bind" -> { if(data.size()!=3) return true; job=runtime.confirmBinding(player.getUniqueId(),connection,data.get(2)); }
            case "terms" -> { if(data.size()!=3) return true; job=runtime.acceptTerms(player.getUniqueId(),connection,data.get(2)); }
            default -> { message(player,"不支持的认证操作。",false); return true; }
        }
        job.whenComplete((result,error)->{
            if(!active) return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if (!active || views.get(id) != view || !player.isOnline()) return;
                if (refresh && connection.equals(view.pendingRefresh)) view.pendingRefresh = null;
                AuthSnapshot state = runtime.snapshot(id);
                if (!connection.equals(state.connectionId())) return;
                var prompt = runtime.externalPrompt(id);
                if (error == null && result.success() && prompt == null) view.forced = false;
                view.invalidate();
                reconcile(view, state, prompt, false);
                if(error!=null) message(player,"认证服务暂时不可用。",false);
                else message(player,text(result.code()),result.success());
            });
        });
        return true;
    }
    private String text(String code) {
        return switch(code) {
            case "ADMITTED" -> "验证成功，欢迎进入游戏。";
            case "SERVER_ADMITTED","AUTH_DISABLED" -> "Suite 认证未启用，玩家已正常准入。";
            case "QQ_BINDING_REQUIRED" -> "身份已验证，请在QQ群申请验证码完成绑定。";
            case "BOUND" -> "绑定成功。";
            case "PASSWORD_CHANGED" -> "密码已修改，请重新登录。";
            case "TERMS_REQUIRED" -> "请阅读并确认服务器条款。";
            case "PASSWORD_LENGTH" -> "密码长度不符合服务器要求。";
            case "CREDENTIALS_REJECTED" -> "凭据不正确、账号已存在或暂时处于冷却期。";
            case "BUSY","OVERLOADED" -> "请求处理中，请稍后重试。";
            case "READY" -> "已准备就绪。";
            case "LOADING" -> "正在读取认证状态。";
            case "VERIFIED","REQUIREMENTS" -> "身份已验证，请完成条款确认或QQ绑定。";
            case "REVOKED" -> "会话已撤销，请重新验证身份。";
            case "TRANSPORT_RECONNECT" -> "握手回执不存在、已过期或有冲突，请重新连接。";
            case "IDENTITY_LINK_REQUIRED" -> "身份与已有账号冲突，请联系管理员完成显式关联。";
            case "EXTERNAL_PROVIDER" -> "请到对应身份提供方修改密码。";
            case "UNAVAILABLE" -> "认证服务暂时不可用，可刷新重试。";
            case "OUTCOME_UNKNOWN" -> "上次请求结果尚待确认，请先刷新状态。";
            case "NODE_SCOPE_DENIED" -> "当前服务器尚未获准执行此操作，请联系管理员。";
            case "CENTER_LEASE_EXPIRED" -> "认证中心的准入授权已过期，请刷新验证。";
            case "PASSWORD_ENTRY_DISABLED" -> "当前服务器未启用游戏内密码登录，请联系服务器管理员。";
            case "EXTERNAL_PENDING" -> "用官网或启动器授权，完成后点击刷新状态。";
            case "EXTERNAL_APPROVED" -> "请核对官网或启动器上的核对码，再确认授权。";
            case "EXTERNAL_DENIED" -> "授权已拒绝。可刷新状态重新开始。";
            case "EXTERNAL_NOT_APPROVED" -> "授权尚未完成或已过期，请刷新状态。";
            case "EXTERNAL_LINKED" -> "官网身份已关联，正在更新准入状态。";
            case "RECENT_PASSWORD_REQUIRED" -> "请先用密码重新登录，再关联官网账号。";
            case "EXTERNAL_DISABLED" -> "服务器尚未启用官网授权。";
            case "WAITING_PROXY","TRUSTED_INGRESS_REQUIRED" -> "正在等待可信登录验证，请稍候。";
            case "SIGNED_PROOF_REJECTED" -> "登录证明验证失败，请重新连接或联系管理员。";
            default -> "操作未完成："+code;
        };
    }
    private void message(Player player,String text,boolean success) {
        packets.sendPacket(player,ui,"result",Map.of("success",success,"message",text));
    }
    @Override public void close() {
        active = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        for (var view : views.values()) {
            if (view.player.isOnline() && packets.isUiOpen(view.player, ui)) packets.closeUiUnsafe(view.player, ui);
        }
        views.clear();
    }
}
