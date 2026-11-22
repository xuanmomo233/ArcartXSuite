import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeTargetTest {
    @Test
    void universalPreservesExistingWindowsLinuxArchitectures() {
        for (String path : new String[]{"Windows/x86/", "Windows/x86_64/", "Windows/aarch64/",
                "Linux/x86_64/", "Linux/arm/", "Linux/ppc64/", "Linux-Musl/aarch64/"}) {
            assertTrue(RuntimeTarget.UNIVERSAL.keeps("org/sqlite/native/" + path + "library"), path);
        }
        assertFalse(RuntimeTarget.UNIVERSAL.keeps("org/sqlite/native/Mac/aarch64/library"));
        assertFalse(RuntimeTarget.UNIVERSAL.keeps("org/sqlite/native/Linux-Android/aarch64/library"));
    }

    @Test
    void preciseTargetsKeepOnlyMatchingNativeAssets() {
        var windows = RuntimeTarget.WINDOWS_X86_64;
        assertTrue(windows.keeps("native/axs-native.enc"));
        assertFalse(windows.keeps("native/libaxs-native.enc"));
        assertTrue(windows.keeps("org/sqlite/native/Windows/x86_64/sqlitejdbc.dll"));
        assertFalse(windows.keeps("org/sqlite/native/Windows/aarch64/sqlitejdbc.dll"));
        var linux = RuntimeTarget.LINUX_X86_64;
        assertTrue(linux.keeps("native/libaxs-native.enc"));
        assertFalse(linux.keeps("native/axs-native.enc"));
        assertTrue(linux.keeps("org/sqlite/native/Linux/x86_64/libsqlitejdbc.so"));
        assertTrue(linux.keeps("org/sqlite/native/Linux-Musl/x86_64/libsqlitejdbc.so"));
        assertFalse(linux.keeps("org/sqlite/native/Linux/aarch64/libsqlitejdbc.so"));
        for (RuntimeTarget target : RuntimeTarget.values()) {
            assertTrue(target.keeps("xuanmo/arcartxsuite/SuiteCoreImpl.class"));
            assertTrue(target.keeps("com/mysql/cj/jdbc/Driver.class"));
        }
    }

    @Test
    void rejectsUnknownTargetsInsteadOfSilentlyShippingWrongResources() {
        assertEquals(RuntimeTarget.LINUX_X86_64, RuntimeTarget.parse("linux-x86_64"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeTarget.parse("linxu"));
    }
}
