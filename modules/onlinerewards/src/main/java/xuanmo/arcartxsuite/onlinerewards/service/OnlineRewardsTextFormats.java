package xuanmo.arcartxsuite.onlinerewards.service;

public final class OnlineRewardsTextFormats {

    private OnlineRewardsTextFormats() {
    }

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
