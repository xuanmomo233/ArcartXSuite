package xuanmo.arcartxsuite.auth.minecraft;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** Configuration proof that this JVM's startup injector points at the receipt-producing gateway. */
public final class LocalAgentBinding {
    private LocalAgentBinding() {}
    public static void require(String gateway,List<String> jvmArguments) {
        URI expected=URI.create(gateway); int injectors=0; boolean matched=false;
        for(String argument:jvmArguments) {
            if(!argument.startsWith("-javaagent:")) continue;
            int separator=argument.indexOf('=');
            String file=argument.substring(11,separator<0?argument.length():separator);
            try(JarFile jar=new JarFile(Path.of(file).toFile())) {
                var manifest=jar.getManifest();
                String premain=manifest==null?null:manifest.getMainAttributes().getValue("Premain-Class");
                if(premain==null || !premain.startsWith("moe.yushi.authlibinjector.")) continue;
                injectors++;
                if(separator<0) continue;
                URI actual=URI.create(argument.substring(separator+1));
                matched="http".equalsIgnoreCase(actual.getScheme()) && "127.0.0.1".equals(actual.getHost())
                    && actual.getPort()==expected.getPort() && actual.getRawUserInfo()==null && actual.getRawQuery()==null
                    && actual.getFragment()==null && (actual.getPath().isEmpty() || actual.getPath().equals("/"));
            } catch(Exception e) { throw new IllegalArgumentException("Cannot verify startup agent binding",e); }
        }
        if(injectors!=1 || !matched) throw new IllegalArgumentException("local-mixed requires exactly one startup authlib-injector using the configured local gateway");
    }
}
