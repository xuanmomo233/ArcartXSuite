package xuanmo.arcartxsuite.loginview.auth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class AuthMeBridge {

    private fr.xephi.authme.api.v3.AuthMeApi api;

    public boolean initialize() {
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMe == null || !authMe.isEnabled()) {
            return false;
        }
        try {
            api = fr.xephi.authme.api.v3.AuthMeApi.getInstance();
            return api != null;
        } catch (Exception | LinkageError exception) {
            api = null;
            return false;
        }
    }

    public boolean available() {
        return api != null;
    }

    public boolean isAuthenticated(Player player) {
        if (api == null) {
            return false;
        }
        try {
            return api.isAuthenticated(player);
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    public boolean isRegistered(String playerName) {
        if (api == null) {
            return false;
        }
        try {
            return api.isRegistered(playerName);
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    public boolean checkPassword(String playerName, String password) {
        if (api == null) {
            return false;
        }
        try {
            return api.checkPassword(playerName, password);
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    public boolean registerPlayer(String playerName, String password) {
        if (api == null) {
            return false;
        }
        try {
            return api.registerPlayer(playerName, password);
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    public boolean forceLogin(Player player) {
        if (api == null) {
            return false;
        }
        try {
            api.forceLogin(player);
            return true;
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }

    public boolean changePassword(String playerName, String password) {
        if (api == null) {
            return false;
        }
        try {
            api.changePassword(playerName, password);
            return true;
        } catch (Exception | LinkageError exception) {
            return false;
        }
    }
}
