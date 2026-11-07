package xuanmo.arcartxsuite.auth.runtime;

import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.*;
import xuanmo.arcartxsuite.api.storage.StorageManager;

/** Host lifecycle shared by the standalone adapter and the remote center adapter. */
public interface HostAuthentication extends AuthRuntimeCapability,BindingService,AutoCloseable {
    void start();
    default boolean requiresAdmission() { return true; }
    @Override void close();
    static HostAuthentication create(JavaPlugin plugin,StorageManager storage,Supplier<EventBusCapability> events) {
        var settings=AuthenticationSettings.load(plugin);
        return settings.center()==null?new AuthenticationRuntime(plugin,storage,events,settings):new CenterAuthenticationRuntime(plugin,events,settings);
    }
    static LoginViewAuthentication forLoginView(JavaPlugin plugin,StorageManager storage,Supplier<EventBusCapability> events) {
        return new LoginViewAuthentication(
            () -> new ServerAdmissionRuntime(plugin,events,java.time.ZoneId.of("Asia/Shanghai")),
            () -> create(plugin,storage,events));
    }
}
