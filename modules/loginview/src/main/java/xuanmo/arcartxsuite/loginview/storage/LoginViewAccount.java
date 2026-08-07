package xuanmo.arcartxsuite.loginview.storage;

/**
 * 登录账号记录。
 *
 * @param lowerName       小写用户名（用于唯一索引）
 * @param realName        原始用户名（保留大小写）
 * @param passwordHash    密码哈希值
 * @param hashAlgorithm   哈希算法标识（如 AXS_PBKDF2_SHA256 / BCRYPT）
 * @param email           邮箱（可为空）
 * @param registrationIp  注册 IP
 * @param lastIp          最后登录 IP
 * @param registeredAt    注册时间戳（毫秒）
 * @param lastLoginAt     最后登录时间戳（毫秒）
 * @param migrated        是否从 AuthMe 迁移而来
 */
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
