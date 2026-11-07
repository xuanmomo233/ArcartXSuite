package xuanmo.arcartxsuite.loginview.service;

import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;

/** All replies are bound to a captured connection. This controller never owns admission state. */
public final class LoginViewController implements AutoCloseable {
    private final JavaPlugin plugin;
    private final PacketBridgeAPI packets;
    private final AuthRuntimeCapability runtime;
    private final String ui;
    private final long delay;
    private record View(AuthSnapshot snapshot,AuthRuntimeCapability.ExternalPrompt prompt) {}
    private final Map<UUID, View> shown=new HashMap<>();
    private final Set<UUID> visible=new HashSet<>(), forced=new HashSet<>();
    private final Map<UUID, Long> joined=new HashMap<>();
    private BukkitTask task;
    private boolean active;
    public LoginViewController(JavaPlugin plugin,PacketBridgeAPI packets,AuthRuntimeCapability runtime,String ui,long delay) {
        this.plugin=plugin; this.packets=packets; this.runtime=runtime; this.ui=ui; this.delay=delay;
    }
    public void start() {
        active=true;
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            Set<UUID> online=new HashSet<>();
            for(Player p:Bukkit.getOnlinePlayers()) {
                UUID id=p.getUniqueId(); online.add(id);
                long first=joined.computeIfAbsent(id, ignored->System.nanoTime());
                if(System.nanoTime()-first>=delay*50_000_000L) open(p,false);
            }
            shown.keySet().retainAll(online); joined.keySet().retainAll(online);
            visible.retainAll(online); forced.retainAll(online);
        },1,10);
    }
    public void open(Player player,boolean force) {
        if(!active || !player.isOnline()) return;
        UUID id=player.getUniqueId(); AuthSnapshot state=runtime.snapshot(id);
        var prompt=runtime.externalPrompt(id);
        var view=new View(state,prompt);
        if(state.connectionId()==null) return;
        if(force && state.admitted() && !state.provider().equals("local-password")) {
            if("server".equals(state.provider())) {
                player.sendMessage("Suite 认证未启用，玩家已正常准入。");
                return;
            }
            player.sendMessage("外部身份的密码请到对应身份提供方修改。"); return;
        }
        if(force) forced.add(id);
        if(state.admitted() && !forced.contains(id) && prompt==null) {
            if(visible.remove(id)) packets.closeUiUnsafe(player,ui);
            shown.put(id,view); return;
        }
        if(visible.add(id)) packets.openUiUnsafe(player,ui);
        if(!force && view.equals(shown.get(id))) return;
        shown.put(id,view);
        String mode=state.admitted()?"change":state.identityVerified()?"requirements":!runtime.passwordAuthenticationAllowed()?"verify"
            :state.registration()==AuthSnapshot.Registration.UNREGISTERED && runtime.passwordRegistrationAllowed()?"register":"login";
        var payload=new HashMap<String,Object>(Map.of("connection",state.connectionId().toString(),"mode",mode,
            "stage",state.stage().name(),"player",player.getName(),"ready",state.stage()!=AuthSnapshot.Stage.LOADING,
            "requirements",state.requirements().stream().map(Enum::name).toList(),
            "terms",runtime.terms().lines(),"termsVersion",runtime.terms().version(),"termsEnabled",runtime.terms().enabled()));
        payload.put("needTerms",state.requirements().contains(AuthSnapshot.Requirement.TERMS));
        payload.put("needBinding",state.requirements().contains(AuthSnapshot.Requirement.QQ_BINDING));
        payload.put("externalId",prompt==null?"":prompt.requestId().toString());
        payload.put("externalCode",prompt==null?"":prompt.userCode());
        payload.put("externalUrl",prompt==null?"":prompt.verificationUrl());
        payload.put("externalConfirmation",prompt==null?"":prompt.confirmation());
        payload.put("canLink",state.admitted() && "local-password".equals(state.provider()) && runtime.externalLinkAvailable());
        payload.put("message",text(state.reason()));
        packets.sendPacket(player,ui,"state",payload);
    }
    public void clientReady(Player player) {
        UUID id=player.getUniqueId();
        if(visible.remove(id)) packets.closeUiUnsafe(player,ui);
        shown.remove(id);
        open(player,false);
    }
    public boolean handle(Player player,String channel,List<String> data) {
        if(!"AXS_auth_v1".equals(channel)) return false;
        if(!active || data==null || data.size()<2 || data.size()>7 || data.stream().anyMatch(v->v==null || v.length()>1024)) return true;
        UUID connection;
        try { connection=UUID.fromString(data.get(1)); } catch(IllegalArgumentException e) { return true; }
        if(!connection.equals(runtime.snapshot(player.getUniqueId()).connectionId())) return true;
        CompletionStage<AuthRuntimeCapability.Result> job;
        switch(data.get(0)) {
            case "exit" -> { if(data.size()==2) player.kickPlayer("已退出登录流程。"); return true; }
            case "refresh" -> job=runtime.refresh(player.getUniqueId(),connection);
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
                if(!active || !player.isOnline() || !connection.equals(runtime.snapshot(player.getUniqueId()).connectionId())) return;
                if(error==null && result.success() && runtime.externalPrompt(player.getUniqueId())==null) forced.remove(player.getUniqueId());
                shown.remove(player.getUniqueId()); open(player,false);
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
        active=false; if(task!=null) task.cancel();
        for(Player p:Bukkit.getOnlinePlayers()) if(visible.contains(p.getUniqueId())) packets.closeUiUnsafe(p,ui);
        shown.clear(); visible.clear(); forced.clear(); joined.clear();
    }
}
