package xuanmo.arcartxsuite.module;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.ModuleLoadException;
import static org.junit.jupiter.api.Assertions.*;

class ModuleDescriptorParserTest {
    private byte[] module(String api) throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var jar=new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry("module.yml"));
            jar.write(("id: example\nmain: example.Module\n"+api).getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return bytes.toByteArray();
    }
    @Test void rejectsOldContractBeforeLoadingAnyModuleClass() throws Exception {
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 1.0\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 2.0\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 3.0\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 3.1\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 3.2\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 3.3\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 3.4\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 3.5\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 3.6\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 3.7\n")));
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("api-version: 3.8\n")));
    }
    @Test void missingApiVersionDoesNotFallBackToLegacyContract() {
        assertThrows(ModuleLoadException.class,()->ModuleDescriptorParser.parse(module("")));
    }
    @Test void acceptsTheExplicitNewContract() throws Exception {
        assertEquals("example",ModuleDescriptorParser.parse(module("api-version: 3.9\n")).id());
    }
}
