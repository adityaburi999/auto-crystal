package com.autocrystal.module;

import com.autocrystal.algorithm.CrystalDamageCalculator;
import com.autocrystal.algorithm.HumanPatternCalculator;
import com.autocrystal.config.AutoCrystalConfig;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Core manual-trigger auto-crystal module.
 *
 * <p>Activation: the player must <b>hold right-click</b> on an obsidian or bedrock block
 * while already holding an End Crystal in their main hand and while at least one enemy
 * player is within range. The module then automatically places and breaks crystals in a
 * loop until the player releases right-click, moves the crosshair away from valid blocks,
 * loses the enemy, or runs out of crystals.
 *
 * <p>The player is <b>never</b> force-switched to an End Crystal — manual hotbar
 * management is required.
 *
 * <h3>Cycle overview</h3>
 * <ol>
 *   <li>Right-click held on obsidian/bedrock + crystal in hand + enemy nearby → activate.
 *   <li>Find the best target player and best crystal placement near that target.
 *   <li>Smoothly rotate toward the placement block (with human-like variability).
 *   <li>Place the crystal (right-click packet with position jitter).
 *   <li>Wait for the crystal entity to spawn in the world.
 *   <li>Check the target's damage-invulnerability tick; wait for it to expire.
 *   <li>Rotate toward the crystal and attack it (left-click).
 *   <li>Brief ramped cooldown, then repeat from step 2 while trigger remains active.
 * </ol>
 *
 * <h3>Human-pattern features</h3>
 * <ul>
 *   <li><b>Ramp-up</b>: the first 15 cycles are progressively faster, mirroring a
 *       player warming up into a fight.
 *   <li><b>Damage-tick analysis</b>: the break is deferred while the target still has
 *       invulnerability frames ({@code hurtTime > 1}) so every hit lands for full damage.
 *   <li><b>No auto-switch</b>: the player must keep an End Crystal in their main hand;
 *       if they switch away the session halts immediately.
 *   <li>All existing human-pattern behaviours (fatigue, focus cycles, overshoot,
 *       distraction pauses, variable rotation speed) remain active.
 * </ul>
 */
public class AutoCrystalModule {

    // ── Configuration shortcuts ───────────────────────────────────────────────
    private static final float  PLACE_RANGE      = 4.5f;
    private static final float  BREAK_RANGE      = 4.5f;
    private static final float  TARGET_RANGE     = 6.0f;
    private static final long   SPAWN_TIMEOUT_MS = 1_000L;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean enabled = false;
    private CrystalState  state = CrystalState.IDLE;

    private PlayerEntity     currentTarget;
    private PlacementTarget  currentPlacement;
    private EndCrystalEntity pendingCrystal;

    /** Timestamp after which the next action may be performed (human delay). */
    private long actionReadyAtMs = 0L;
    /** Time at which we started waiting for a crystal to spawn. */
    private long spawnWaitStartMs = 0L;

    // ── Manual-trigger state ──────────────────────────────────────────────────
    /** {@code true} while the player holds right-click on obsidian/bedrock. */
    private boolean triggerActive = false;

    /**
     * Number of completed cycles in the current trigger session.
     * Used to drive the ramp-up speed curve; resets when the trigger is released.
     */
    private int cycleCount = 0;

    private final HumanPatternCalculator humanPattern;
    private final Random rng = new Random();

    // ── Overshoot simulation ──────────────────────────────────────────────────
    private boolean pendingOvershootCorrection = false;
    private float   overshootYaw   = 0f;
    private float   overshootPitch = 0f;

    public AutoCrystalModule() {
        this.humanPattern = new HumanPatternCalculator(rng);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Public API
    // ═════════════════════════════════════════════════════════════════════════

    public boolean isEnabled()             { return enabled; }
    public void setEnabled(boolean value)  { enabled = value; if (!value) reset(); }

    /**
     * Called once per client tick from {@code AutoCrystalClient}.
     * Drives the state machine forward.
     */
    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) return;

        AutoCrystalConfig cfg = AutoCrystalConfig.getInstance();

        // Evaluate right-click trigger: must hold right-click on obsidian/bedrock
        // while holding a crystal in main hand and an enemy is nearby.
        updateTriggerState(client, cfg);

        // If the trigger is released mid-cycle, abort everything.
        if (!triggerActive) {
            if (state != CrystalState.IDLE) reset();
            return;
        }

        switch (state) {
            case IDLE               -> tickIdle(client, cfg);
            case ACQUIRING_TARGET   -> tickAcquiring(client, cfg);
            case ROTATING_TO_PLACE  -> tickRotatingToPlace(client);
            case PLACING            -> tickPlacing(client, cfg);
            case WAITING_FOR_SPAWN  -> tickWaitingForSpawn(client);
            case ROTATING_TO_BREAK  -> tickRotatingToBreak(client);
            case BREAKING           -> tickBreaking(client);
            case COOLDOWN           -> tickCooldown(client);
        }
    }

    // ── Trigger evaluation ────────────────────────────────────────────────────

    /**
     * Updates {@link #triggerActive} based on three gating conditions:
     * <ol>
     *   <li>The player is holding right-click (use/interact key is pressed).
     *   <li>The player's main hand holds an End Crystal.
     *   <li>The crosshair is aimed at an obsidian or bedrock block.
     *   <li>At least one enemy player is within {@value TARGET_RANGE} blocks.
     * </ol>
     *
     * <p>When the trigger transitions from active → inactive the ramp counter
     * ({@link #cycleCount}) is reset so the next session starts fresh.
     */
    private void updateTriggerState(MinecraftClient client, AutoCrystalConfig cfg) {
        boolean wasActive = triggerActive;
        triggerActive = false;

        if (!client.options.useKey.isPressed()) {
            if (wasActive) cycleCount = 0;
            return;
        }

        // Crystal must be in main hand (no auto-switch)
        if (!isCrystalInMainHand(client)) {
            if (wasActive) cycleCount = 0;
            return;
        }

        // Crosshair must be on obsidian or bedrock
        if (!(client.crosshairTarget instanceof BlockHitResult bhr)) return;
        var block = client.world.getBlockState(bhr.getBlockPos()).getBlock();
        if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) return;

        // An enemy player must be nearby
        if (findBestTarget(client, cfg) == null) return;

        triggerActive = true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  State handlers
    // ═════════════════════════════════════════════════════════════════════════

    private void tickIdle(MinecraftClient client, AutoCrystalConfig cfg) {
        currentTarget    = null;
        currentPlacement = null;
        pendingCrystal   = null;
        // Begin the first cycle with the unanticipated reaction delay.
        transitionTo(CrystalState.ACQUIRING_TARGET,
                humanPattern.getReactionDelayMs(false));
    }

    private void tickAcquiring(MinecraftClient client, AutoCrystalConfig cfg) {
        if (!isReady()) return;

        // Crystal must still be in main hand – player can cancel by switching away.
        if (!isCrystalInMainHand(client)) {
            transitionTo(CrystalState.IDLE, 200L);
            return;
        }

        currentTarget = findBestTarget(client, cfg);
        if (currentTarget == null) {
            transitionTo(CrystalState.IDLE, 200L);
            return;
        }

        currentPlacement = findBestPlacement(client, cfg, currentTarget);
        if (currentPlacement == null || currentPlacement.score < cfg.getMinDamage()) {
            transitionTo(CrystalState.IDLE, 300L);
            return;
        }

        // Rotate toward placement with a ramp-aware reaction delay.
        transitionTo(CrystalState.ROTATING_TO_PLACE,
                humanPattern.getRampedReactionDelayMs(cycleCount));
    }

    private void tickRotatingToPlace(MinecraftClient client) {
        if (currentPlacement == null) { reset(); return; }

        Vec3d target = currentPlacement.hitVec;
        boolean arrived = smoothRotate(client, target);

        // Handle overshoot: if we overshot last tick, stay here for correction
        if (pendingOvershootCorrection) {
            client.player.setYaw(overshootYaw);
            client.player.setPitch(overshootPitch);
            pendingOvershootCorrection = false;
            return;
        }

        if (arrived) {
            transitionTo(CrystalState.PLACING,
                    humanPattern.getPlaceToBreakDelayMs());
        }
    }

    private void tickPlacing(MinecraftClient client, AutoCrystalConfig cfg) {
        if (!isReady() || currentPlacement == null) { reset(); return; }

        // Verify the target is still alive and in range
        if (!isTargetValid(client, cfg)) { reset(); return; }

        // Apply tiny position jitter to look like a human hitting the block face
        double[] jittered = humanPattern.applyPositionJitter(
                currentPlacement.hitVec.x,
                currentPlacement.hitVec.y,
                currentPlacement.hitVec.z);
        Vec3d jitteredHit = new Vec3d(jittered[0], jittered[1], jittered[2]);

        BlockHitResult hitResult = new BlockHitResult(
                jitteredHit,
                Direction.UP,
                currentPlacement.blockPos,
                false
        );

        // interactBlock internally triggers the arm-swing animation
        client.interactionManager.interactBlock(
                client.player, Hand.MAIN_HAND, hitResult);

        humanPattern.recordAction();
        spawnWaitStartMs = System.currentTimeMillis();
        transitionTo(CrystalState.WAITING_FOR_SPAWN, 0L);
    }

    private void tickWaitingForSpawn(MinecraftClient client) {
        // Poll the world for the newly spawned crystal
        pendingCrystal = findNearestCrystalAt(client, currentPlacement.blockPos);

        if (pendingCrystal != null) {
            transitionTo(CrystalState.ROTATING_TO_BREAK,
                    humanPattern.getReactionDelayMs(true));
            return;
        }

        // Timeout: crystal never appeared, start over
        if (System.currentTimeMillis() - spawnWaitStartMs > SPAWN_TIMEOUT_MS) {
            transitionTo(CrystalState.IDLE, 100L);
        }
    }

    private void tickRotatingToBreak(MinecraftClient client) {
        if (pendingCrystal == null || pendingCrystal.isRemoved()) {
            transitionTo(CrystalState.IDLE, 100L);
            return;
        }

        Vec3d crystalPos = pendingCrystal.getPos().add(0, 0.5, 0);
        boolean arrived  = smoothRotate(client, crystalPos);

        if (arrived) {
            transitionTo(CrystalState.BREAKING,
                    humanPattern.getPlaceToBreakDelayMs());
        }
    }

    private void tickBreaking(MinecraftClient client) {
        if (!isReady()) return;

        if (pendingCrystal == null || pendingCrystal.isRemoved()) {
            // Crystal already gone (someone else broke it, or lag); still counts
            transitionTo(CrystalState.COOLDOWN,
                    humanPattern.getRampedCycleIntervalMs(cycleCount));
            return;
        }

        // Verify still in range
        double dist = client.player.getPos()
                .distanceTo(pendingCrystal.getPos());
        if (dist > BREAK_RANGE) {
            transitionTo(CrystalState.IDLE, 100L);
            return;
        }

        // Respect attack cooldown – most anticheats flag attacks sent before
        // the cooldown completes (Grim, Vulcan, Matrix).
        if (client.player.getAttackCooldownProgress(0.0f) < 0.9f) return;

        // Damage-tick analysis: defer the break while the target still has
        // invulnerability frames so every hit lands for full damage.
        if (currentTarget != null && !currentTarget.isRemoved()
                && currentTarget.hurtTime > 1) {
            return; // wait one more tick for invulnerability to expire
        }

        // Attack (left-click) the crystal
        client.interactionManager.attackEntity(client.player, pendingCrystal);
        client.player.networkHandler.sendPacket(
                new HandSwingC2SPacket(Hand.MAIN_HAND));

        humanPattern.recordAction();
        transitionTo(CrystalState.COOLDOWN, humanPattern.getRampedCycleIntervalMs(cycleCount));
    }

    private void tickCooldown(MinecraftClient client) {
        if (!isReady()) return;

        // Increment ramp counter after each successful cycle.
        cycleCount++;

        // If burst mode, shorten the next reaction time.
        boolean burst = humanPattern.isBurstActive();
        long delay    = burst ? 20L : humanPattern.getRampedCycleIntervalMs(cycleCount);

        // Occasionally simulate a distraction (missed input / brief inattention).
        if (humanPattern.shouldSkipCycle()) {
            delay += humanPattern.getDistractionPauseMs();
        }

        // Loop directly back to ACQUIRING_TARGET (not IDLE) so the session
        // continues without needing to re-evaluate the trigger block.
        transitionTo(CrystalState.ACQUIRING_TARGET, delay);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Target & placement discovery
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Returns the nearest living player within range who is not the local player.
     * Prefers the player with the lowest health so kills happen faster.
     */
    private PlayerEntity findBestTarget(MinecraftClient client, AutoCrystalConfig cfg) {
        float range = cfg.getTargetRange();
        Vec3d selfPos = client.player.getPos();

        return client.world.getPlayers().stream()
                .filter(p -> p != client.player)
                .filter(p -> !p.isDead())
                .filter(p -> p.getPos().distanceTo(selfPos) <= range)
                .min(Comparator.comparingDouble(PlayerEntity::getHealth))
                .orElse(null);
    }

    /**
     * Enumerates candidate obsidian/bedrock blocks around the target and returns
     * the placement candidate with the highest score.
     */
    private PlacementTarget findBestPlacement(MinecraftClient client,
                                               AutoCrystalConfig cfg,
                                               PlayerEntity target) {
        Vec3d selfPos   = client.player.getPos();
        float placeRange = cfg.getPlaceRange();
        double maxSelf  = cfg.getMaxSelfDamage();

        List<PlacementTarget> candidates = new ArrayList<>();

        BlockPos targetBlock = target.getBlockPos();
        int radius = 3;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Crystals can be placed on the block at foot level or one below
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = targetBlock.add(dx, dy, dz);
                    if (!isValidPlacementBlock(client, pos)) continue;

                    Vec3d hitVec  = Vec3d.ofCenter(pos).add(0, 0.5, 0);
                    double distSelf = selfPos.distanceTo(hitVec);
                    if (distSelf > placeRange) continue;

                    // Crystal entity spawns centred on the block (x+0.5, z+0.5) at block top (y+1)
                    Vec3d crystalSpawn = new Vec3d(
                            pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                    double tDmg = CrystalDamageCalculator.calculateDamage(target, crystalSpawn);
                    double sDmg = CrystalDamageCalculator.calculateSelfDamage(
                            selfPos, client.player.getArmor(), crystalSpawn);

                    if (sDmg > maxSelf) continue;
                    if (tDmg < cfg.getMinDamage()) continue;

                    candidates.add(new PlacementTarget(pos, hitVec, tDmg, sDmg));
                }
            }
        }

        return candidates.stream()
                .max(Comparator.comparingDouble(p -> p.score))
                .orElse(null);
    }

    /** Returns {@code true} if an End Crystal may legally be placed on this block. */
    private boolean isValidPlacementBlock(MinecraftClient client, BlockPos pos) {
        var block = client.world.getBlockState(pos).getBlock();
        if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) return false;

        // The two blocks above must be air
        BlockPos above1 = pos.up();
        BlockPos above2 = pos.up(2);
        return client.world.getBlockState(above1).isAir()
                && client.world.getBlockState(above2).isAir();
    }

    /** Finds a spawned crystal entity whose base is closest to {@code placedOn}. */
    private EndCrystalEntity findNearestCrystalAt(MinecraftClient client, BlockPos placedOn) {
        Vec3d expectedPos = Vec3d.ofCenter(placedOn).add(0, 1, 0);
        double threshold  = 1.5;

        return (EndCrystalEntity) client.world.getEntitiesByClass(
                        EndCrystalEntity.class,
                        pendingCrystal == null
                                ? client.player.getBoundingBox().expand(PLACE_RANGE + 2)
                                : pendingCrystal.getBoundingBox().expand(1),
                        e -> !e.isRemoved()
                             && e.getPos().distanceTo(expectedPos) < threshold
                ).stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns {@code true} if the player's main hand currently holds an End Crystal.
     * The module never auto-switches; the player must manage their own hotbar.
     */
    private boolean isCrystalInMainHand(MinecraftClient client) {
        return client.player.getMainHandStack().getItem() == Items.END_CRYSTAL;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Rotation helper
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Smoothly rotates the local player toward {@code target} using a
     * speed that varies with the remaining angular distance, making the
     * movement harder to fingerprint with a fixed-rate pattern.
     *
     * <p>On the first call after a new target is set, there is an 8 % chance of
     * overshooting slightly (see {@link HumanPatternCalculator#getOvershootDegrees()}).
     *
     * @return {@code true} when rotation is within 2 ° of the target
     */
    private boolean smoothRotate(MinecraftClient client, Vec3d target) {
        Vec3d eyePos  = client.player.getEyePos();
        Vec3d delta   = target.subtract(eyePos);

        float targetYaw   = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(
                delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));

        // Apply micro-tremor noise to the target angles
        float[] noised = humanPattern.applyRotationNoise(targetYaw, targetPitch);
        targetYaw   = noised[0];
        targetPitch = noised[1];

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();

        float dyaw   = MathHelper.wrapDegrees(targetYaw   - curYaw);
        float dpitch = MathHelper.wrapDegrees(targetPitch - curPitch);

        float dist = MathHelper.sqrt(dyaw * dyaw + dpitch * dpitch);
        if (dist < 2.0f) return true;

        float maxSpeed = humanPattern.getVariableRotationSpeed(dist);
        float step = Math.min(dist, maxSpeed) / dist;
        float newYaw   = curYaw   + dyaw   * step;
        float newPitch = curPitch + dpitch * step;
        newPitch = MathHelper.clamp(newPitch, -90f, 90f);

        // Overshoot simulation (only when we are within one speed-step of target)
        if (!pendingOvershootCorrection && dist <= maxSpeed) {
            double overshoot = humanPattern.getOvershootDegrees();
            if (overshoot > 0) {
                overshootYaw   = newYaw   + (float) overshoot;
                overshootPitch = MathHelper.clamp(newPitch + (float)(overshoot * 0.3f), -90f, 90f);
                pendingOvershootCorrection = true;
            }
        }

        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);
        return false;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Utility
    // ═════════════════════════════════════════════════════════════════════════

    /** Returns {@code true} once the human-like delay has elapsed. */
    private boolean isReady() {
        return System.currentTimeMillis() >= actionReadyAtMs;
    }

    /** Transitions to a new state and schedules it to be ready after {@code delayMs}. */
    private void transitionTo(CrystalState nextState, long delayMs) {
        state = nextState;
        actionReadyAtMs = System.currentTimeMillis() + delayMs;
    }

    /** Returns {@code true} if the current target is still alive and in range. */
    private boolean isTargetValid(MinecraftClient client, AutoCrystalConfig cfg) {
        if (currentTarget == null || currentTarget.isDead()) return false;
        float range = cfg.getTargetRange();
        return client.player.getPos().distanceTo(currentTarget.getPos()) <= range;
    }

    /** Resets the module back to IDLE with zero delay. */
    private void reset() {
        state            = CrystalState.IDLE;
        currentTarget    = null;
        currentPlacement = null;
        pendingCrystal   = null;
        actionReadyAtMs  = 0L;
        triggerActive    = false;
        cycleCount       = 0;
        pendingOvershootCorrection = false;
    }
}
