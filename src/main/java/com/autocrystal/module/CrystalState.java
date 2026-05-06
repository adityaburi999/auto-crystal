package com.autocrystal.module;

/**
 * State machine for a single manual-trigger crystal cycle.
 *
 * <p>The cycle is activated by holding right-click on obsidian/bedrock while the
 * player already holds an End Crystal and an enemy is within range.
 *
 * <pre>
 *  IDLE ──► ACQUIRING_TARGET ──► ROTATING_TO_PLACE
 *                                       │
 *                                   PLACING
 *                                       │
 *                              WAITING_FOR_SPAWN
 *                                       │
 *                             ROTATING_TO_BREAK
 *                                       │
 *                                  BREAKING
 *                                       │
 *                                  COOLDOWN
 *                                       │
 *                              ACQUIRING_TARGET ◄──┘
 * </pre>
 *
 * If the right-click trigger is released, the crystal is no longer held, or the
 * target is lost, any state transitions back to IDLE.
 */
public enum CrystalState {
    /** Waiting for the manual right-click trigger on obsidian/bedrock. */
    IDLE,
    /** Finding the best target and best placement position. */
    ACQUIRING_TARGET,
    /** Rotating head toward the placement block face. */
    ROTATING_TO_PLACE,
    /** Waiting for the right-click timing window before placing. */
    PLACING,
    /** Waiting for the End Crystal entity to spawn in the world. */
    WAITING_FOR_SPAWN,
    /** Rotating head toward the spawned crystal. */
    ROTATING_TO_BREAK,
    /** Waiting for the attack timing window before breaking. */
    BREAKING,
    /** Brief cooldown after a full cycle, before seeking the next opportunity. */
    COOLDOWN
}
