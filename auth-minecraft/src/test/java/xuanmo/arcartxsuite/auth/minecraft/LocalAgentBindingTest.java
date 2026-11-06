package xuanmo.arcartxsuite.auth.minecraft;

import java.nio.file.*;
import java.util.List;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalAgentBindingTest {
    @TempDir Path temp;
    private String agent(String name,String url) throws Exception {
        Path jar=temp.resolve(name); var manifest=new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version","1.0");
        manifest.getMainAttributes().putValue("Premain-Class","moe.yushi.authlibinjector.Premain");
        try(var out=new JarOutputStream(Files.newOutputStream(jar),manifest)) { }
        return "-javaagent:"+jar+"="+url;
    }
    @Test void mustMatchTheActualStartupAgentEndpoint() throws Exception {
        String configured="http://127.0.0.1:25599";
        assertDoesNotThrow(()->LocalAgentBinding.require(configured,List.of(agent("first.jar",configured+"/"))));
        assertThrows(IllegalArgumentException.class,()->LocalAgentBinding.require(configured,List.of(agent("wrong.jar","http://127.0.0.1:25598"))));
        assertThrows(IllegalArgumentException.class,()->LocalAgentBinding.require(configured,List.of()));
    }
    @Test void multipleInjectorsAreAmbiguousEvenWhenOneMatches() throws Exception {
        var arguments=List.of(agent("first.jar","http://127.0.0.1:25599"),agent("second.jar","http://127.0.0.1:25598"));
        assertThrows(IllegalArgumentException.class,()->LocalAgentBinding.require("http://127.0.0.1:25599",arguments));
    }
}
