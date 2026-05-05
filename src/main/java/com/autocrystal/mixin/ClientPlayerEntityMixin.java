package com.autocrystal.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into {@link ClientPlayerEntity#tick()} to give the AutoCrystal module
 * a second, lower-level update opportunity.
 *
 * <p>The primary update path uses {@code ClientTickEvents.END_CLIENT_TICK}
 * in {@code AutoCrystalClient}. This mixin is kept intentionally minimal and
 * is only used for features that must run inside the player tick (e.g. final
 * rotation overrides applied after vanilla movement processing).
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        // Currently a no-op: all logic is driven via ClientTickEvents.
        // Reserved for future per-tick overrides (e.g., post-move rotation clamping).
    }
}
