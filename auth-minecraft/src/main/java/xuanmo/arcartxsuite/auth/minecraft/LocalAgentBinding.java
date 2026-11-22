package xuanmo.arcartxsuite.auth.minecraft;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** Configuration proof that this JVM's startup injector points at the receipt-producing gateway. */
public final class LocalAgentBinding {
    private LocalAgentBinding() {}
    public static void requireMicrosoftSession(List<String> jvmArguments) {
        for (String argument : jvmArguments) {
            if (injector(argument)) {
                throw new IllegalArgumentException("使用 authlib-injector 时应配置 local-mixed，以可信握手回执区分身份来源");
            }
        }
        for (String key : List.of("minecraft.api.env", "minecraft.api.auth.host", "minecraft.api.account.host",
                "minecraft.api.session.host", "minecraft.api.services.host")) {
            if (System.getProperty(key) != null) {
                throw new IllegalArgumentException("微软在线入口不能替换 Minecraft 认证服务环境");
            }
        }
    }

    public static void require(String gateway,List<String> jvmArguments) {
        URI expected=URI.create(gateway); int injectors=0; boolean matched=false;
        for(String argument:jvmArguments) {
            if(!injector(argument)) continue;
            int separator=argument.indexOf('=');
            injectors++;
            if(separator<0) continue;
            URI actual=URI.create(argument.substring(separator+1));
            matched="http".equalsIgnoreCase(actual.getScheme()) && "127.0.0.1".equals(actual.getHost())
                && actual.getPort()==expected.getPort() && actual.getRawUserInfo()==null && actual.getRawQuery()==null
                && actual.getFragment()==null && (actual.getPath().isEmpty() || actual.getPath().equals("/"));
        }
        if(injectors!=1 || !matched) throw new IllegalArgumentException("local-mixed requires exactly one startup authlib-injector using the configured local gateway");
    }

    private static boolean injector(String argument) {
        if (!argument.startsWith("-javaagent:")) return false;
        int separator = argument.indexOf('=');
        String file = argument.substring(11, separator < 0 ? argument.length() : separator);
        try (JarFile jar = new JarFile(Path.of(file).toFile())) {
            var manifest = jar.getManifest();
            String premain = manifest == null ? null : manifest.getMainAttributes().getValue("Premain-Class");
            return premain != null && premain.startsWith("moe.yushi.authlibinjector.");
        } catch (Exception failure) {
            throw new IllegalArgumentException("Cannot verify startup agent binding", failure);
        }
    }
}
