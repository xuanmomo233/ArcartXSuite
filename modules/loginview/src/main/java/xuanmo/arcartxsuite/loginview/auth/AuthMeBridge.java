package xuanmo.arcartxsuite.loginview.auth;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class AuthMeBridge {

    private Object api;
    private Method isAuthenticatedMethod;
    private Method isRegisteredMethod;
    private Method checkPasswordMethod;
    private Method registerPlayerMethod;
    private Method forceLoginMethod;
    private Method changePasswordMethod;

    public boolean initialize() {
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMe == null || !authMe.isEnabled()) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, authMe.getClass().getClassLoader());
            api = apiClass.getMethod("getInstance").invoke(null);
            isAuthenticatedMethod = apiClass.getMethod("isAuthenticated", Player.class);
            isRegisteredMethod = apiClass.getMethod("isRegistered", String.class);
            checkPasswordMethod = apiClass.getMethod("checkPassword", String.class, String.class);
            registerPlayerMethod = apiClass.getMethod("registerPlayer", String.class, String.class);
            forceLoginMethod = apiClass.getMethod("forceLogin", Player.class);
            changePasswordMethod = apiClass.getMethod("changePassword", String.class, String.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            api = null;
            return false;
        }
    }

    public boolean available() {
        return api != null;
    }

    public boolean isAuthenticated(Player player) {
        return invokeBoolean(isAuthenticatedMethod, false, player);
    }

    public boolean isRegistered(String playerName) {
        return invokeBoolean(isRegisteredMethod, false, playerName);
    }

    public boolean checkPassword(String playerName, String password) {
        return invokeBoolean(checkPasswordMethod, false, playerName, password);
    }

    public boolean registerPlayer(String playerName, String password) {
        return invokeBoolean(registerPlayerMethod, false, playerName, password);
    }

    public boolean forceLogin(Player player) {
        return invokeBoolean(forceLoginMethod, false, player);
    }

    public boolean changePassword(String playerName, String password) {
        return invokeBoolean(changePasswordMethod, false, playerName, password);
    }

    private boolean invokeBoolean(Method method, boolean defaultValue, Object... args) {
        if (api == null || method == null) {
            return defaultValue;
        }
        try {
            Object result = method.invoke(api, args);
            return result instanceof Boolean value ? value.booleanValue() : defaultValue;
        } catch (ReflectiveOperationException exception) {
            return defaultValue;
        }
    }
}
