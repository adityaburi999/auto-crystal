package com.autocrystal.module;

/**
 * State machine for a single auto-crystal cycle.
 *
 * <pre>
 *  IDLE ──► ACQUIRING_TARGET ──► SWITCHING_ITEM
 *                                       │
 *                               ROTATING_TO_PLACE
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
 *                                    IDLE ◄──┘
 * </pre>
 *
 * If the module is disabled or the target is lost, any state transitions to IDLE.
 */
public enum CrystalState {
    /** No active target; nothing to do. */
    IDLE,
    /** Finding the best target and best placement position. */
    ACQUIRING_TARGET,
    /** Switching to End Crystal item in hotbar (with human-like delay). */
    SWITCHING_ITEM,
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
