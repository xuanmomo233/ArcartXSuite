package xuanmo.arcartxsuite.loginview.storage;

/**
 * 从 AuthMe 迁移过来的账号记录，字段含义与 AuthMe 数据库对应。
 *
 * @param playerName     小写用户名
 * @param realName       原始用户名
 * @param passwordHash   密码哈希（BCrypt）
 * @param hashAlgorithm  哈希算法标识
 * @param email          邮箱
 * @param registrationIp 注册 IP
 * @param lastIp         最后登录 IP
 * @param registeredAt   注册时间戳（毫秒）
 * @param lastLoginAt    最后登录时间戳（毫秒）
 */
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
