package xuanmo.arcartxsuite.loginview.storage;

public record LoginViewAccount(
    String lowerName,
    String realName,
    String passwordHash,
    String hashAlgorithm,
    String email,
    String registrationIp,
    String lastIp,
    long registeredAt,
    long lastLoginAt,
    boolean migrated
) {
}
