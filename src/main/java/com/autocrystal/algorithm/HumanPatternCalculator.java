package com.autocrystal.algorithm;

import java.util.Random;

/**
 * Generates human-like behavioural patterns for crystal placement and rotation.
 *
 * <p>The algorithm models the following observed human characteristics:
 * <ol>
 *   <li><b>Reaction time</b> – log-normal distribution with fatigue scaling
 *       (delegated to {@link ReactionTimeModel}).
 *   <li><b>Timing jitter</b> – small random offset (±20 %) applied to every
 *       scheduled action timestamp.
 *   <li><b>CPS variation</b> – clicks-per-second drawn from N(10, 1.5²),
 *       clamped to [6, 16], to mirror natural rhythm.
 *   <li><b>Fatigue model</b> – a running counter that increases with each action
 *       and recovers during idle periods.
 *   <li><b>Focus cycles</b> – brief ~3 s "focus windows" where the player
 *       plays sharply, followed by 0.5–2 s "lapse windows" with looser timing.
 *   <li><b>Burst mode</b> – 15 % chance per cycle to enter a 2-action burst
 *       with compressed timing, simulating a skilled player's muscle memory.
 *   <li><b>Rotation noise</b> – tiny Gaussian offsets on yaw/pitch to replicate
 *       the micro-tremor present in even expert mouse movements.
 *   <li><b>Overshoot</b> – 8 % chance that the first rotation attempt overshoots
 *       by 3–8 °, followed by an immediate correction.
 * </ol>
 */
public class HumanPatternCalculator {

    // ── Jitter ────────────────────────────────────────────────────────────────
    /** Relative jitter applied to action timing (±JITTER_FRACTION of interval). */
    private static final double JITTER_FRACTION = 0.20;

    // ── CPS ───────────────────────────────────────────────────────────────────
    private static final double CPS_MEAN  = 10.0;
    private static final double CPS_SIGMA = 1.5;
    private static final double CPS_MIN   = 6.0;
    private static final double CPS_MAX   = 16.0;

    // ── Fatigue ───────────────────────────────────────────────────────────────
    /** Each action increments fatigue by this amount. */
    private static final double FATIGUE_INCREMENT  = 0.004;
    /** Fatigue decays per millisecond of inactivity. */
    private static final double FATIGUE_DECAY_PER_MS = 0.00005;
    /** Fatigue ceiling. */
    private static final double FATIGUE_MAX = 1.0;

    // ── Focus cycles ─────────────────────────────────────────────────────────
    /** Focused window: 2 000 – 5 000 ms */
    private static final long FOCUS_MIN_MS  = 2_000L;
    private static final long FOCUS_RANGE_MS = 3_000L;
    /** Lapse window: 500 – 2 000 ms */
    private static final long LAPSE_MIN_MS  = 500L;
    private static final long LAPSE_RANGE_MS = 1_500L;

    // ── Overshoot ─────────────────────────────────────────────────────────────
    private static final double OVERSHOOT_PROBABILITY = 0.08;
    private static final double OVERSHOOT_MIN_DEG     = 3.0;
    private static final double OVERSHOOT_RANGE_DEG   = 5.0;

    // ── Rotation noise ────────────────────────────────────────────────────────
    /** Std-dev of micro-tremor noise applied to final yaw/pitch (degrees). */
    private static final double ROTATION_NOISE_SIGMA = 0.4;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Random           rng;
    private final ReactionTimeModel rtModel;

    private double fatigue       = 0.0;
    private long   lastActionMs  = 0L;
    private boolean inFocusMode  = true;
    private long    focusEndMs   = 0L;

    public HumanPatternCalculator(Random rng) {
        this.rng     = rng;
        this.rtModel = new ReactionTimeModel(rng);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the number of milliseconds the player would wait before reacting
     * to a new crystal opportunity.
     *
     * @param anticipated {@code true} if the player had already predicted this
     *                    opportunity (e.g. re-placing at the same spot)
     */
    public long getReactionDelayMs(boolean anticipated) {
        updateFatigue();
        long base = anticipated
                ? rtModel.sampleAnticipatedReactionMs(fatigue)
                : rtModel.sampleReactionMs(fatigue);
        return applyFocusMultiplier(base);
    }

    /**
     * Returns the interval in milliseconds between consecutive crystal cycles
     * given the current CPS sample.
     */
    public long getCycleIntervalMs() {
        double cps      = Math.max(CPS_MIN, Math.min(CPS_MAX,
                CPS_MEAN + CPS_SIGMA * rng.nextGaussian()));
        long   interval = (long) (1_000.0 / cps);
        return applyJitter(interval);
    }

    /**
     * Returns {@code true} if the next action is in a "burst" window, causing
     * the caller to compress its timing slightly.
     */
    public boolean isBurstActive() {
        return rng.nextDouble() < 0.15;
    }

    /**
     * Returns the delay (ms) the player waits between placing a crystal and
     * attacking it.  Based on observed human data: ~40–120 ms.
     */
    public long getPlaceToBreakDelayMs() {
        long base = 40L + (long) (80L * rng.nextDouble());
        return applyJitter(base);
    }

    /**
     * Applies human-like rotation noise (micro-tremor) to a target yaw/pitch pair.
     *
     * @param yaw   desired yaw   (degrees)
     * @param pitch desired pitch (degrees)
     * @return a two-element array: {@code [noisyYaw, noisyPitch]}
     */
    public float[] applyRotationNoise(float yaw, float pitch) {
        float noisyYaw   = (float) (yaw   + ROTATION_NOISE_SIGMA * rng.nextGaussian());
        float noisyPitch = (float) (pitch + ROTATION_NOISE_SIGMA * rng.nextGaussian());
        return new float[]{noisyYaw, noisyPitch};
    }

    /**
     * Checks whether the current rotation attempt should overshoot, and if so
     * returns the overshoot magnitude in degrees (positive = overshoot right/up).
     * Returns 0 if no overshoot occurs this attempt.
     */
    public double getOvershootDegrees() {
        if (rng.nextDouble() < OVERSHOOT_PROBABILITY) {
            return OVERSHOOT_MIN_DEG + OVERSHOOT_RANGE_DEG * rng.nextDouble();
        }
        return 0.0;
    }

    /**
     * Applies a slight positional jitter to a placement hit-vector to replicate
     * the natural imprecision of a human aiming at a block face.
     *
     * <p>The offset is intentionally tiny (σ ≈ 0.015 blocks) so it never
     * moves the click outside the target block's face.
     *
     * @param x  ideal x-coordinate
     * @param y  ideal y-coordinate
     * @param z  ideal z-coordinate
     * @return   array {@code [x+jx, y+jy, z+jz]}
     */
    public double[] applyPositionJitter(double x, double y, double z) {
        double sigma = 0.015;
        return new double[]{
                x + sigma * rng.nextGaussian(),
                y + sigma * rng.nextGaussian(),
                z + sigma * rng.nextGaussian()
        };
    }

    /**
     * Records that an action was performed and updates the fatigue counter.
     * Must be called each time the module executes a crystal action.
     */
    public void recordAction() {
        updateFatigue();
        fatigue = Math.min(FATIGUE_MAX, fatigue + FATIGUE_INCREMENT);
        lastActionMs = System.currentTimeMillis();
    }

    /**
     * Returns the current fatigue level in [0, 1].
     * 0 = fully rested, 1 = maximum fatigue.
     */
    public double getFatigue() {
        return fatigue;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════════════════

    /** Decays fatigue based on elapsed idle time and updates focus mode. */
    private void updateFatigue() {
        long now     = System.currentTimeMillis();
        long elapsed = (lastActionMs == 0L) ? 0L : now - lastActionMs;

        if (elapsed > 0) {
            fatigue = Math.max(0.0, fatigue - elapsed * FATIGUE_DECAY_PER_MS);
        }

        // Update focus mode
        if (now >= focusEndMs) {
            inFocusMode = !inFocusMode;
            focusEndMs  = now + (inFocusMode
                    ? FOCUS_MIN_MS  + (long)(FOCUS_RANGE_MS  * rng.nextDouble())
                    : LAPSE_MIN_MS  + (long)(LAPSE_RANGE_MS  * rng.nextDouble()));
        }
    }

    /**
     * Scales a timing value by the focus-mode multiplier.
     * During lapses, add a proportional delay to represent decreased alertness.
     */
    private long applyFocusMultiplier(long baseMs) {
        if (!inFocusMode) {
            // Lapse: 10–35 % slower
            double multiplier = 1.10 + 0.25 * rng.nextDouble();
            return (long) (baseMs * multiplier);
        }
        return baseMs;
    }

    /** Applies ±JITTER_FRACTION relative jitter to a timing interval. */
    private long applyJitter(long intervalMs) {
        double jitterMs = intervalMs * JITTER_FRACTION * (rng.nextDouble() * 2 - 1);
        return Math.max(10L, intervalMs + (long) jitterMs);
    }
}
