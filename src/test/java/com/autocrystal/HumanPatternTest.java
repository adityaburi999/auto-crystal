package com.autocrystal;

import com.autocrystal.algorithm.HumanPatternCalculator;
import com.autocrystal.algorithm.ReactionTimeModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.DoubleSummaryStatistics;
import java.util.LongSummaryStatistics;
import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the human-pattern algorithm.
 *
 * <p>These tests verify statistical properties of the distributions rather than
 * exact values, because the algorithm is deliberately stochastic.
 */
class HumanPatternTest {

    private static final int SAMPLE_SIZE = 10_000;

    private Random rng;
    private ReactionTimeModel rtModel;
    private HumanPatternCalculator pattern;

    @BeforeEach
    void setUp() {
        // Fixed seed for reproducible statistical tests
        rng     = new Random(42L);
        rtModel = new ReactionTimeModel(rng);
        pattern = new HumanPatternCalculator(new Random(42L));
    }

    // ── ReactionTimeModel ────────────────────────────────────────────────────

    @Test
    void reactionTime_freshPlayer_withinHumanRange() {
        LongSummaryStatistics stats = LongStream.range(0, SAMPLE_SIZE)
                .map(i -> rtModel.sampleReactionMs(0.0))
                .summaryStatistics();

        // All samples must be in [50, 400] ms
        assertTrue(stats.getMin() >= 50,  "Min RT below floor: " + stats.getMin());
        assertTrue(stats.getMax() <= 400, "Max RT above ceiling: " + stats.getMax());

        // Mean should be roughly 100-160 ms for a fresh player
        double mean = stats.getAverage();
        assertTrue(mean >= 90 && mean <= 200,
                "Mean RT out of expected range: " + mean);
    }

    @Test
    void reactionTime_fatiguedPlayer_isSlowerThanFresh() {
        Random r1 = new Random(1L);
        Random r2 = new Random(1L);
        ReactionTimeModel freshModel   = new ReactionTimeModel(r1);
        ReactionTimeModel fatiguedModel = new ReactionTimeModel(r2);

        double freshMean = LongStream.range(0, SAMPLE_SIZE)
                .mapToDouble(i -> freshModel.sampleReactionMs(0.0))
                .average().orElseThrow();

        double fatiguedMean = LongStream.range(0, SAMPLE_SIZE)
                .mapToDouble(i -> fatiguedModel.sampleReactionMs(1.0))
                .average().orElseThrow();

        assertTrue(fatiguedMean > freshMean,
                "Fatigued mean (" + fatiguedMean + ") should exceed fresh mean (" + freshMean + ")");
    }

    @Test
    void anticipatedReaction_isFasterThanFullReaction() {
        Random r1 = new Random(7L);
        ReactionTimeModel model1 = new ReactionTimeModel(r1);
        double fullMean = LongStream.range(0, SAMPLE_SIZE)
                .mapToDouble(i -> model1.sampleReactionMs(0.0))
                .average().orElseThrow();

        Random r2 = new Random(7L);
        ReactionTimeModel model2 = new ReactionTimeModel(r2);
        double anticipatedMean = LongStream.range(0, SAMPLE_SIZE)
                .mapToDouble(i -> model2.sampleAnticipatedReactionMs(0.0))
                .average().orElseThrow();

        assertTrue(anticipatedMean < fullMean,
                "Anticipated RT mean should be less than full RT mean");
    }

    // ── HumanPatternCalculator – cycle interval ──────────────────────────────

    @Test
    void cycleInterval_withinReasonableCpsRange() {
        // 6–16 CPS → interval range 62 – 167 ms, after ±20 % jitter: 50–200 ms
        LongSummaryStatistics stats = LongStream.range(0, SAMPLE_SIZE)
                .map(i -> pattern.getCycleIntervalMs())
                .summaryStatistics();

        assertTrue(stats.getMin() >= 10,  "Interval too short: " + stats.getMin());
        assertTrue(stats.getMax() <= 250, "Interval too long: "  + stats.getMax());
    }

    @Test
    void cycleInterval_meanMatchesExpectedCps() {
        // Mean CPS = 10 → expected mean interval ≈ 100 ms
        double meanMs = LongStream.range(0, SAMPLE_SIZE)
                .mapToDouble(i -> pattern.getCycleIntervalMs())
                .average().orElseThrow();

        // Allow generous tolerance because of jitter
        assertTrue(meanMs >= 70 && meanMs <= 150,
                "Mean cycle interval out of expected range: " + meanMs);
    }

    // ── HumanPatternCalculator – place-to-break delay ────────────────────────

    @Test
    void placeToBreakDelay_withinHumanRange() {
        LongSummaryStatistics stats = LongStream.range(0, SAMPLE_SIZE)
                .map(i -> pattern.getPlaceToBreakDelayMs())
                .summaryStatistics();

        assertTrue(stats.getMin() >= 10,  "Too fast: " + stats.getMin());
        assertTrue(stats.getMax() <= 200, "Too slow: " + stats.getMax());
    }

    // ── HumanPatternCalculator – rotation noise ──────────────────────────────

    @Test
    void rotationNoise_isSmallAndCenteredOnInput() {
        float inputYaw   = 45.0f;
        float inputPitch = -20.0f;

        DoubleSummaryStatistics yawStats = DoubleStream.generate(
                () -> pattern.applyRotationNoise(inputYaw, inputPitch)[0])
                .limit(SAMPLE_SIZE)
                .summaryStatistics();

        // Mean should be within 0.2° of the input
        assertTrue(Math.abs(yawStats.getAverage() - inputYaw) < 0.2,
                "Rotation noise mean deviates too much: " + yawStats.getAverage());

        // Noise should be small (max observed |offset| < 3°)
        assertTrue(Math.abs(yawStats.getMax() - inputYaw) < 3.0,
                "Max rotation noise too large");
    }

    // ── HumanPatternCalculator – position jitter ─────────────────────────────

    @Test
    void positionJitter_isSmallAndCenteredOnInput() {
        double x = 10.5, y = 64.0, z = -5.5;

        DoubleSummaryStatistics xStats = DoubleStream.generate(
                () -> pattern.applyPositionJitter(x, y, z)[0])
                .limit(SAMPLE_SIZE)
                .summaryStatistics();

        // Mean x should be within 0.1 of input
        assertTrue(Math.abs(xStats.getAverage() - x) < 0.1,
                "Position jitter mean deviates too much: " + xStats.getAverage());

        // Max absolute offset should be < 0.15 blocks (6σ ≈ 0.09)
        assertTrue(Math.abs(xStats.getMax() - x) < 0.15,
                "Position jitter too large: max=" + xStats.getMax());
    }

    // ── HumanPatternCalculator – fatigue ────────────────────────────────────

    @Test
    void fatigue_increasesAfterRepeatedActions() {
        HumanPatternCalculator calc = new HumanPatternCalculator(new Random(99L));
        double before = calc.getFatigue();

        for (int i = 0; i < 100; i++) {
            calc.recordAction();
        }

        double after = calc.getFatigue();
        assertTrue(after > before, "Fatigue should increase after actions");
    }

    @Test
    void fatigue_neverExceedsOne() {
        HumanPatternCalculator calc = new HumanPatternCalculator(new Random(99L));

        for (int i = 0; i < 10_000; i++) {
            calc.recordAction();
        }

        assertTrue(calc.getFatigue() <= 1.0, "Fatigue exceeded 1.0");
    }

    // ── HumanPatternCalculator – overshoot ──────────────────────────────────

    @RepeatedTest(5)
    void overshoot_occursAtApproximatelyExpectedRate() {
        HumanPatternCalculator calc = new HumanPatternCalculator(new Random());
        long overshoots = LongStream.range(0, SAMPLE_SIZE)
                .filter(i -> calc.getOvershootDegrees() > 0)
                .count();

        double rate = (double) overshoots / SAMPLE_SIZE;
        // Expected ~8 %; allow 5–12 % given random variance
        assertTrue(rate >= 0.04 && rate <= 0.14,
                "Overshoot rate out of expected range: " + rate);
    }

    // ── HumanPatternCalculator – burst mode ─────────────────────────────────

    @Test
    void burstMode_occursAtApproximatelyExpectedRate() {
        HumanPatternCalculator calc = new HumanPatternCalculator(new Random(55L));
        long bursts = LongStream.range(0, SAMPLE_SIZE)
                .filter(i -> calc.isBurstActive())
                .count();

        double rate = (double) bursts / SAMPLE_SIZE;
        // Expected ~15 %; allow 10–22 %
        assertTrue(rate >= 0.10 && rate <= 0.22,
                "Burst rate out of expected range: " + rate);
    }
}
