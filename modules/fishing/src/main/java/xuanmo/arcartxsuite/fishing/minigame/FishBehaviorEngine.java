package xuanmo.arcartxsuite.fishing.minigame;

import java.util.concurrent.ThreadLocalRandom;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;

public final class FishBehaviorEngine implements FishBehavior {

    private final FishBehaviorType type;
    private final int difficulty;
    private long lastDartTick = 0;
    private double dartTarget = 0.5;
    private double dartSpeed = 0.02;
    private long sinkerPhase = 0;
    private long floaterPhase = 0;

    public FishBehaviorEngine(FishBehaviorType type, int difficulty) {
        this.type = type;
        this.difficulty = difficulty;
    }

    @Override
    public double tick(double currentPosition, double currentVelocity, long tick, int difficulty) {
        double jitter = difficulty / 100.0;
        switch (type) {
            case SMOOTH -> {
                return smoothBehavior(currentPosition, tick, jitter);
            }
            case DART -> {
                return dartBehavior(currentPosition, tick, jitter);
            }
            case SINKER -> {
                return sinkerBehavior(currentPosition, tick, jitter);
            }
            case FLOATER -> {
                return floaterBehavior(currentPosition, tick, jitter);
            }
        }
        return 0.0;
    }

    private double smoothBehavior(double position, long tick, double jitter) {
        double frequency = 0.05 + jitter * 0.05;
        double amplitude = 0.008 + jitter * 0.015;
        double noise = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.004 * jitter;
        return Math.sin(tick * frequency) * amplitude + noise;
    }

    private double dartBehavior(double position, long tick, double jitter) {
        if (tick - lastDartTick > 30 + ThreadLocalRandom.current().nextInt(40)) {
            lastDartTick = tick;
            dartTarget = ThreadLocalRandom.current().nextDouble(0.1, 0.9);
            dartSpeed = 0.015 + jitter * 0.03;
        }
        double diff = dartTarget - position;
        if (Math.abs(diff) < 0.02) {
            return (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.003;
        }
        return Math.signum(diff) * dartSpeed + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.005 * jitter;
    }

    private double sinkerBehavior(double position, long tick, double jitter) {
        sinkerPhase++;
        double base = -0.003 - jitter * 0.008;
        double periodic = Math.sin(sinkerPhase * 0.03) * 0.005 * jitter;
        double noise = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.003 * jitter;
        if (position <= 0.02) {
            return Math.abs(base) + periodic + noise;
        }
        return base + periodic + noise;
    }

    private double floaterBehavior(double position, long tick, double jitter) {
        floaterPhase++;
        double base = 0.003 + jitter * 0.008;
        double periodic = Math.sin(floaterPhase * 0.03) * 0.005 * jitter;
        double noise = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.003 * jitter;
        if (position >= 0.98) {
            return -(base + periodic + noise);
        }
        return base + periodic + noise;
    }
}
