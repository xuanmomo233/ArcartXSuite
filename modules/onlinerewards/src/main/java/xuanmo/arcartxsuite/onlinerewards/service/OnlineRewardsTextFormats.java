package xuanmo.arcartxsuite.onlinerewards.service;

/**
 * 在线奖励文本格式化工具。
 * <p>
 * 属于 {@code onlinerewards} 模块，提供将分钟数格式化为「天/小时/分钟」可读文本的静态方法。
 */
public final class OnlineRewardsTextFormats {

    private OnlineRewardsTextFormats() {
    }

    /**
     * 将总分钟数格式化为「x天 x小时 x分钟」的可读文本。
     *
     * @param totalMinutes 总分钟数
     * @return 格式化后的文本
     */
    public static String formatMinutes(int totalMinutes) {
        int safeMinutes = Math.max(0, totalMinutes);
        int days = safeMinutes / (24 * 60);
        int hours = (safeMinutes % (24 * 60)) / 60;
        int minutes = safeMinutes % 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("天");
        }
        if (hours > 0) {
            builder.append(hours).append("小时");
        }
        if (minutes > 0 || builder.length() == 0) {
            builder.append(minutes).append("分钟");
        }
        return builder.toString();
    }
}
