package xuanmo.arcartxsuite.security;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeTargetValidationTest {
    @Test
    void acceptsHotspotWindowsAndLinuxX64Aliases() {
        for (String arch : new String[]{"amd64", "AMD64", "x86_64", "x64"}) {
            assertNull(NativeBridge.RuntimeTarget.validate(profile("windows-x86_64", "windows", "x86_64"), "Windows 11", arch));
            assertNull(NativeBridge.RuntimeTarget.validate(profile("linux-x86_64", "linux", "x86_64"), "Linux", arch));
        }
    }

    @Test
    void rejectsWrongOsAndCpuWithActionableMessage() {
        var windows = profile("windows-x86_64", "windows", "x86_64");
        assertTrue(NativeBridge.RuntimeTarget.validate(windows, "Linux", "amd64").contains("universal"));
        assertNotNull(NativeBridge.RuntimeTarget.validate(windows, "Windows 11", "aarch64"));
        assertNotNull(NativeBridge.RuntimeTarget.validate(windows, "Darwin", "amd64"));
    }

    @Test
    void universalRetainsBothOsWithoutAddingCpuRestrictions() {
        var universal = profile("universal", "windows,linux", "any");
        assertNull(NativeBridge.RuntimeTarget.validate(universal, "Windows Server 2022", "aarch64"));
        assertNull(NativeBridge.RuntimeTarget.validate(universal, "LINUX", "ppc64"));
        assertNotNull(NativeBridge.RuntimeTarget.validate(universal, "Mac OS X", "amd64"));
    }

    @Test
    void rejectsMalformedOrInconsistentManifests() {
        assertNotNull(NativeBridge.RuntimeTarget.validate(new Properties(), "Linux", "amd64"));
        var wrong = profile("linux-x86_64", "windows", "x86_64");
        assertNotNull(NativeBridge.RuntimeTarget.validate(wrong, "Windows 11", "amd64"));
        wrong = profile("linux-x86_64", "linux", "x86_64");
        wrong.setProperty("format", "2");
        assertNotNull(NativeBridge.RuntimeTarget.validate(wrong, "Linux", "amd64"));
    }

    private static Properties profile(String id, String os, String arch) {
        var properties = new Properties();
        properties.setProperty("format", "1");
        properties.setProperty("target", id);
        properties.setProperty("os", os);
        properties.setProperty("arch", arch);
        return properties;
    }
}
