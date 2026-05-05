package com.autocrystal.algorithm;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Calculates the explosion damage that an End Crystal detonation would deal.
 *
 * <p>End Crystal explosion power = 6.0 (same as a charged creeper).
 * This class mirrors the relevant parts of {@code ServerWorld#createExplosion}
 * and {@code Explosion#collectBlocksAndDamageEntities} so that the module can
 * predict damage without actually detonating anything.
 *
 * <p>The formula used is the vanilla "impact" model:
 * <pre>
 *   distanceFactor = distance / (2 * power)
 *   exposure       = 1.0  (simplified – line-of-sight raycast omitted on client)
 *   impact         = (1.0 - distanceFactor) * exposure
 *   rawDamage      = (impact * impact + impact) / 2 * 7 * (2 * power) + 1
 * </pre>
 * Armour reduction is approximated using the entity's current armour value.
 */
public class CrystalDamageCalculator {

    /** Vanilla End Crystal explosion power. */
    public static final float CRYSTAL_POWER = 6.0f;

    private CrystalDamageCalculator() {}

    /**
     * Returns the predicted explosion damage in half-hearts dealt to {@code target}
     * if a crystal placed at {@code crystalPos} were detonated.
     *
     * @param target     the entity that would receive damage
     * @param crystalPos the world position of the End Crystal (centre)
     * @return predicted damage in game damage units (1 unit = half a heart)
     */
    public static double calculateDamage(LivingEntity target, Vec3d crystalPos) {
        Vec3d targetCenter = target.getPos().add(0, target.getHeight() / 2.0, 0);
        double distance    = crystalCenter(crystalPos).distanceTo(targetCenter);
        double radius      = CRYSTAL_POWER * 2.0;

        if (distance > radius) {
            return 0.0;
        }

        double distanceFactor = distance / radius;
        // Simplified: assume full exposure (no block occlusion check client-side)
        double impact     = (1.0 - distanceFactor);
        double rawDamage  = (impact * impact + impact) / 2.0 * 7.0 * radius + 1.0;

        // Rough armour reduction (vanilla caps blast armour at 20 out of 20 pts)
        double armourValue = target.getArmor();
        double armourFactor = 1.0 - (armourValue / 25.0) * 0.75;

        return rawDamage * armourFactor;
    }

    /**
     * Calculates expected damage to the local player at {@code selfPos}.
     *
     * @param selfPos    the local player's foot position
     * @param selfArmor  the local player's armour value (0–20)
     * @param crystalPos the world position of the End Crystal
     * @return predicted self-damage in game units
     */
    public static double calculateSelfDamage(Vec3d selfPos, double selfArmor, Vec3d crystalPos) {
        Vec3d selfCenter = selfPos.add(0, 0.9, 0);  // ~eye-level of a crouching player
        double distance  = crystalCenter(crystalPos).distanceTo(selfCenter);
        double radius    = CRYSTAL_POWER * 2.0;

        if (distance > radius) {
            return 0.0;
        }

        double distanceFactor = distance / radius;
        double impact         = (1.0 - distanceFactor);
        double rawDamage      = (impact * impact + impact) / 2.0 * 7.0 * radius + 1.0;
        double armourFactor   = 1.0 - (Math.min(selfArmor, 20.0) / 25.0) * 0.75;

        return rawDamage * armourFactor;
    }

    /**
     * Returns the effective detonation origin of a crystal.
     *
     * <p>Callers of {@link #calculateDamage} and {@link #calculateSelfDamage}
     * pass the End Crystal entity position directly (already centred at
     * X+0.5, Z+0.5 and at block-top Y+1). No offset is needed here.
     */
    private static Vec3d crystalCenter(Vec3d base) {
        return base;
    }
}
