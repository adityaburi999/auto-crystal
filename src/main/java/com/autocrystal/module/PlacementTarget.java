package com.autocrystal.module;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Holds an evaluated crystal placement candidate.
 *
 * <p>A placement candidate is an obsidian/bedrock block adjacent to the target
 * entity on which an End Crystal can legally be placed.
 */
public final class PlacementTarget {

    /** The block position of the obsidian/bedrock block to place the crystal on. */
    public final BlockPos blockPos;

    /** World-space centre of the top face of {@link #blockPos}. */
    public final Vec3d hitVec;

    /** Predicted damage this placement would deal to the primary target. */
    public final double targetDamage;

    /** Predicted self-damage for the local player. */
    public final double selfDamage;

    /**
     * Simple score combining target damage and self-damage penalty.
     * Higher is better.
     */
    public final double score;

    public PlacementTarget(BlockPos blockPos, Vec3d hitVec,
                           double targetDamage, double selfDamage) {
        this.blockPos     = blockPos;
        this.hitVec       = hitVec;
        this.targetDamage = targetDamage;
        this.selfDamage   = selfDamage;
        this.score        = targetDamage - selfDamage * 0.5;
    }

    @Override
    public String toString() {
        return String.format("PlacementTarget{pos=%s, dmg=%.1f, self=%.1f, score=%.1f}",
                blockPos, targetDamage, selfDamage, score);
    }
}
