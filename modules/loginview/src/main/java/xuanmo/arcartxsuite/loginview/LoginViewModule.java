package xuanmo.arcartxsuite.loginview;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.*;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.loginview.service.LoginViewController;

/** Host-owned authentication; UI reload never resets identity or admission. */
public final class LoginViewModule extends AbstractAXSModule implements ModuleCommandHandler {
    private LoginViewController controller;
    private long delay;
    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("loginview").name("LoginView").version("2.0.0").mainClass(getClass().getName()).build();
    }
    @Override protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder().configFileName("LoginView.yml").currentVersion(1).build();
    }
    @Override protected void loadConfiguration(@Nullable File file) {
        if(file==null) throw new IllegalArgumentException("LoginView.yml 缺失");
        var config=YamlConfiguration.loadConfiguration(file);
        if(config.getInt("config-version")!=1 || !java.util.Set.of("config-version","open-delay-ticks").containsAll(config.getKeys(false)))
            throw new IllegalArgumentException("不支持的 LoginView 配置；认证策略属于 authentication.yml");
        delay=config.getLong("open-delay-ticks",20);
        if(delay<0 || delay>600) throw new IllegalArgumentException("open-delay-ticks 范围为 0 至 600");
    }
    @Override protected @NotNull ModuleUiSpec uiSpec() {
        return new ModuleUiSpec(Map.of("arcartx/ui/auth_v1.yml","ui/auth_v1.yml"),true);
    }
    @Override protected void startService() {
        var runtime=getCapability(AuthRuntimeCapability.class);
        if(runtime==null) throw new IllegalStateException("宿主认证运行时未就绪");
        var binding=registerModuleUi("ui/auth_v1.yml","AXS:auth_v1",true);
        if(binding.registeredUiId()==null) throw new IllegalStateException("认证界面注册失败");
        controller=new LoginViewController(plugin,packetBridge,runtime,binding.runtimeUiId(),delay);
        controller.start();
    }
    @Override protected void stopService() { if(controller!=null) controller.close(); controller=null; }
    @Override protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of((p,id,data)->controller!=null && controller.handle(p,id,data),0,"AXS_auth_v1","loginview");
    }
    @Override protected @Nullable ClientInitializedHandler createInitializedHandler() {
        return player->{if(controller!=null) controller.clientReady(player);};
    }
    @Override protected @Nullable Object createPlaceholderExpansion() {
        return new xuanmo.arcartxsuite.loginview.placeholder.AuthPlaceholderExpansion(plugin);
    }
    @Override public String commandId() { return "loginview"; }
    @Override public List<String> actions() { return List.of("status","open"); }
    @Override public boolean onCommand(@NotNull CommandSender sender,@NotNull String label,@NotNull String[] args) {
        if(args.length>=3 && args[1].equalsIgnoreCase("open")) {
            Player player=Bukkit.getPlayerExact(args[2]);
            if(player!=null && controller!=null) controller.open(player,true);
            else sender.sendMessage("玩家离线或界面未就绪。");
        } else sender.sendMessage("LoginView 仅负责界面，认证由宿主运行时管理。");
        return true;
    }
}

