package xuanmo.arcartxsuite.loginview.storage;

public record MigratedAuthMeAccount(
    String playerName,
    String realName,
    String passwordHash,
    String hashAlgorithm,
    String email,
    String registrationIp,
    String lastIp,
    long registeredAt,
    long lastLoginAt
) {
}
