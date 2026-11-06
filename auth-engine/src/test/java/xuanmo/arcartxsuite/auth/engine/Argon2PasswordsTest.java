package xuanmo.arcartxsuite.auth.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Argon2PasswordsTest {
    @Test void authenticatesUnicodePasswordWithoutAcceptingHistoricalFormats() {
        var passwords=new Argon2Passwords(); char[] password="测试密码 secret".toCharArray();
        String hash=passwords.hash(password);
        assertTrue(hash.startsWith("$argon2id$"));
        assertTrue(passwords.verify(password,hash));
        assertFalse(passwords.verify("incorrect".toCharArray(),hash));
        assertFalse(passwords.verify(password,"180000$salt$historical"));
        assertFalse(passwords.verify(password,"$2a$10$historical"));
    }
    @Test void rejectsUnboundedHashParametersAndUsesRandomSalts() {
        var passwords=new Argon2Passwords();
        String first=passwords.hash("password123".toCharArray());
        assertNotEquals(first,passwords.hash("password123".toCharArray()));
        assertFalse(passwords.verify("password123".toCharArray(),first.replace("t=2","t=2000000000")));
    }
}
