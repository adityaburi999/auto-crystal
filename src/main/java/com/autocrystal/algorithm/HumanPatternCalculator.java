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
 *       Consecutive bursts are suppressed to avoid machine-like patterns.
 *   <li><b>Rotation noise</b> – tiny Gaussian offsets on yaw/pitch to replicate
 *       the micro-tremor present in even expert mouse movements.
 *   <li><b>Overshoot</b> – 8 % chance that the first rotation attempt overshoots
 *       by 3–8 °, followed by an immediate correction.
 *   <li><b>Distraction pauses</b> – ~1.5 % chance per cycle of a 0.6–2.6 s
 *       pause simulating momentary inattention or a missed input.
 *   <li><b>Variable rotation speed</b> – coarse tracking uses 30–45 °/tick,
 *       moderate 20–35 °/tick, fine correction 10–20 °/tick.
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
    private static final double ROTATION_NOISE_SIGMA = 0.35;

    // ── Distraction ───────────────────────────────────────────────────────────
    /** Probability per cycle of a brief distraction pause (missed input). */
    private static final double DISTRACTION_PROBABILITY = 0.015;
    /** Range of the distraction pause: 600 – 2 600 ms. */
    private static final long   DISTRACTION_MIN_MS   = 600L;
    private static final long   DISTRACTION_RANGE_MS = 2_000L;

    // ── Variable rotation speed ───────────────────────────────────────────────
    /** Max °/tick when far from target (coarse tracking). */
    private static final float ROT_SPEED_FAR_MIN  = 30f;
    private static final float ROT_SPEED_FAR_RANGE = 15f;   // 30–45°/tick
    /** Max °/tick when moderately close to target. */
    private static final float ROT_SPEED_MID_MIN  = 20f;
    private static final float ROT_SPEED_MID_RANGE = 15f;   // 20–35°/tick
    /** Max °/tick for fine-grained correction (< 10°). */
    private static final float ROT_SPEED_FINE_MIN = 10f;
    private static final float ROT_SPEED_FINE_RANGE = 10f;  // 10–20°/tick

    // ── Burst cooldown ────────────────────────────────────────────────────────
    /**
     * Number of remaining cycles during which a burst is blocked.
     * Randomised to 2–4 after each burst to avoid an alternating pattern.
     */
    private int burstCooldownCycles = 0;

    // ── Rotation speed zone (hysteresis) ─────────────────────────────────────
    /**
     * Last rotation-speed zone (1 = far, 2 = mid, 3 = fine).
     * Retained between ticks so that the zone only switches when the angular
     * distance has moved well past the boundary, eliminating rapid oscillation.
     */
    private int rotSpeedZone = 0; // 0 = unset

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
     *
     * <p>After each burst a randomised cooldown of 2–4 non-burst cycles is
     * enforced, preventing the alternating on/off pattern that Boolean
     * suppression would produce.
     */
    public boolean isBurstActive() {
        if (burstCooldownCycles > 0) {
            burstCooldownCycles--;
            return false;
        }
        if (rng.nextDouble() < 0.15) {
            burstCooldownCycles = 2 + rng.nextInt(3); // 2–4 cycles
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if the module should skip this cycle entirely,
     * simulating a brief moment of inattention or a missed input.
     *
     * <p>Fires with ~{@value #DISTRACTION_PROBABILITY} probability and should
     * be checked once per cooldown/idle transition.
     */
    public boolean shouldSkipCycle() {
        return rng.nextDouble() < DISTRACTION_PROBABILITY;
    }

    /**
     * Returns a random distraction pause duration in milliseconds.
     * Only meaningful when {@link #shouldSkipCycle()} returns {@code true}.
     */
    public long getDistractionPauseMs() {
        return DISTRACTION_MIN_MS + (long)(DISTRACTION_RANGE_MS * rng.nextDouble());
    }

    /**
     * Returns the maximum rotation speed (degrees per tick) appropriate for
     * the given angular distance to the target.
     *
     * <p>Zones with hysteresis (± 3°) prevent rapid speed oscillation when
     * the angular distance hovers near a boundary:
     * <ul>
     *   <li>&gt; 45° (or &gt; 42° while already in mid/fine) – coarse (30–45°/tick)
     *   <li>10–45° (or 7–47° while already in mid) – moderate (20–35°/tick)
     *   <li>&lt; 10° (or &lt; 13° while already in fine) – fine (10–20°/tick)
     * </ul>
     *
     * @param angularDist total angular distance remaining (degrees)
     * @return maximum degrees to rotate this tick
     */
    public float getVariableRotationSpeed(float angularDist) {
        // Determine zone with ±3° hysteresis around each boundary.
        if (rotSpeedZone != 1 && angularDist > 47f) {
            rotSpeedZone = 1;
        } else if (rotSpeedZone == 1 && angularDist < 42f) {
            rotSpeedZone = 2;
        } else if (rotSpeedZone != 3 && angularDist < 8f) {
            rotSpeedZone = 3;
        } else if (rotSpeedZone == 3 && angularDist > 13f) {
            rotSpeedZone = 2;
        } else if (rotSpeedZone == 0) {
            // Initial assignment
            rotSpeedZone = angularDist > 45f ? 1 : angularDist < 10f ? 3 : 2;
        }

        return switch (rotSpeedZone) {
            case 1  -> ROT_SPEED_FAR_MIN  + ROT_SPEED_FAR_RANGE  * (float) rng.nextDouble();
            case 3  -> ROT_SPEED_FINE_MIN + ROT_SPEED_FINE_RANGE * (float) rng.nextDouble();
            default -> ROT_SPEED_MID_MIN  + ROT_SPEED_MID_RANGE  * (float) rng.nextDouble();
        };
    }

    /** Resets the rotation speed zone so the next call picks a fresh zone. */
    public void resetRotationZone() {
        rotSpeedZone = 0;
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
