package xuanmo.arcartxsuite.regions.service;

import java.util.EnumMap;
import java.util.Locale;

/** 可按需开启的主线程直方图。记录时不创建样本对象。 */
public final class RegionTimings {
    public enum Phase {
        CANDIDATES("候选查找与边界过滤"), SORT("匹配区域排序"), GUARD("移动及传送判定"),
        TRANSITION("移动监视回调"), CROSSING("进出事件处理"), EVENT_BUS("事件总线回调");

        private final String label;
        Phase(String label) { this.label = label; }
    }

    private final EnumMap<Phase, Histogram> phases = new EnumMap<>(Phase.class);
    private boolean enabled;

    public void start() {
        phases.clear();
        for (Phase phase : Phase.values()) phases.put(phase, new Histogram());
        enabled = true;
    }

    public long begin() {
        return enabled ? System.nanoTime() : 0;
    }

    public void finish(Phase phase, long start) {
        if (start != 0) phases.get(phase).record(System.nanoTime() - start);
    }

    public java.util.List<String> stop() {
        enabled = false;
        return phases.entrySet().stream().map(entry -> entry.getValue().format(entry.getKey().label)).toList();
    }

    private static final class Histogram {
        private final long[] buckets = new long[64];
        private long count;
        private long sum;
        private long max;

        void record(long nanos) {
            buckets[64 - Long.numberOfLeadingZeros(nanos)]++;
            count++;
            sum += nanos;
            max = Math.max(max, nanos);
        }

        String format(String label) {
            if (count == 0) return label + " · 无样本";
            long threshold = count - count / 100;
            long seen = 0;
            int bucket = 0;
            while ((seen += buckets[bucket]) < threshold) bucket++;
            double p99Upper = Math.min(max, Math.scalb(1.0, bucket) - 1);
            return String.format(Locale.ROOT, "%s · %d 次 · 均值 %.2f µs · P99 ≤ %.2f µs · 最大 %.2f µs",
                label, count, sum / (1000.0 * count), p99Upper / 1000, max / 1000.0);
        }
    }
}
