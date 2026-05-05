package com.autocrystal.algorithm;

import java.util.Random;

/**
 * Models human reaction-time behaviour using a log-normal distribution.
 *
 * <p>Cognitive-science research (Luce 1986, Ratcliff & McKoon 2008) shows that
 * human reaction times are well approximated by a log-normal distribution rather
 * than a Gaussian one, because RT cannot go below ~50 ms and the tail is long.
 *
 * <p>Parameters chosen to match competitive PvP players:
 * <ul>
 *   <li>Geometric mean ≈ 120 ms  (μ_log = 4.787)
 *   <li>Geometric std  ≈ 1.35×   (σ_log = 0.30)
 *   <li>Practical range: 50–400 ms
 * </ul>
 */
public class ReactionTimeModel {

    private static final double LOG_MEAN  = 4.787;   // ln(120)
    private static final double LOG_SIGMA = 0.30;
    private static final long   MIN_RT_MS = 50L;
    private static final long   MAX_RT_MS = 400L;

    private final Random rng;

    public ReactionTimeModel(Random rng) {
        this.rng = rng;
    }

    /**
     * Samples a reaction time in milliseconds.
     *
     * @param fatigueFactor  value in [0, 1] that shifts the distribution rightward.
     *                       0 = fresh player, 1 = heavily fatigued player.
     * @return reaction time in milliseconds
     */
    public long sampleReactionMs(double fatigueFactor) {
        // Log-normal sample: exp(μ + σ·Z)
        double z   = rng.nextGaussian();
        double raw = Math.exp(LOG_MEAN + LOG_SIGMA * z);

        // Fatigue shifts the mean upward by up to 80 ms
        double fatigueShift = fatigueFactor * 80.0;
        long   rt            = (long) (raw + fatigueShift);

        return Math.max(MIN_RT_MS, Math.min(MAX_RT_MS, rt));
    }

    /**
     * Samples an "anticipatory" reaction time used when a player has already
     * predicted the next crystal target. This is significantly shorter than a
     * full stimulus-response RT.
     *
     * @param fatigueFactor  same interpretation as {@link #sampleReactionMs}
     * @return anticipated reaction time in milliseconds
     */
    public long sampleAnticipatedReactionMs(double fatigueFactor) {
        // Players with muscle memory react ~30 % faster on anticipated stimuli
        double raw = Math.exp((LOG_MEAN - 0.35) + (LOG_SIGMA * 0.7) * rng.nextGaussian());
        long   rt  = (long) (raw + fatigueFactor * 40.0);
        return Math.max(MIN_RT_MS, Math.min(250L, rt));
    }
}
