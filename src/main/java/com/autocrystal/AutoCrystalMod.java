package com.autocrystal;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side initializer for the AutoCrystal mod.
 * Most logic is client-side; this class handles shared registration.
 */
public class AutoCrystalMod implements ModInitializer {

    public static final String MOD_ID = "autocrystal";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("AutoCrystal mod initializing…");
    }
}
