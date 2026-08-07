package xuanmo.arcartxsuite.mail.model;

/**
 * 邮件操作结果，封装操作是否成功及面向玩家的提示消息。
 * <p>
 * 关联模块：mail。由 {@link xuanmo.arcartxsuite.mail.service.MailService} 各操作返回。
 *
 * @param success  是否成功
 * @param message  提示消息
 */
public record MailOperationResult(boolean success, String message) {
    /**
     * 构造成功结果。
     *
     * @param message 成功提示消息
     * @return 成功结果
     */
    public static MailOperationResult success(String message) {
        return new MailOperationResult(true, message);
    }

    /**
     * 构造失败结果。
     *
     * @param message 失败提示消息
     * @return 失败结果
     */
    public static MailOperationResult failure(String message) {
        return new MailOperationResult(false, message);
    }
}
