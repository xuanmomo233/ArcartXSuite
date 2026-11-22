package xuanmo.arcartxsuite.auth.protocol;

import java.util.UUID;
import java.util.regex.Pattern;

/** Shared paths and profile formats for the AuthCenter launcher and native session gateway. */
public final class MinecraftSessionProtocol {
    public static final String API_ROOT = "/v1/external/yggdrasil/";
    public static final String JOIN = API_ROOT + "sessionserver/session/minecraft/join";
    public static final String HAS_JOINED = API_ROOT + "sessionserver/session/minecraft/hasJoined";
    public static final String PROFILE = API_ROOT + "sessionserver/session/minecraft/profile/";
    public static final String LAUNCHER_EXCHANGE = "/v1/external/launcher/minecraft-session";
    public static final int TOKEN_SECONDS = 300;
    private static final Pattern CENTER_NAME = Pattern.compile("AXS_[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_HASH = Pattern.compile("-?[0-9a-f]{1,40}");

    private MinecraftSessionProtocol() {}

    public static boolean isCenterName(String name) {
        return name != null && CENTER_NAME.matcher(name).matches();
    }

    public static boolean isServerHash(String hash) {
        return hash != null && SERVER_HASH.matcher(hash).matches();
    }

    public static String profileId(UUID profile) { return profile.toString().replace("-", ""); }
}
