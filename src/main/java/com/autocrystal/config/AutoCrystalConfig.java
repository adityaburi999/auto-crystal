package com.autocrystal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent configuration for the AutoCrystal module.
 *
 * <p>Saved as JSON in {@code .minecraft/config/autocrystal.json}.
 * All getters return validated values even if the file is missing or corrupt.
 */
public class AutoCrystalConfig {

    // ── Default values ────────────────────────────────────────────────────────
    private static final float  DEFAULT_TARGET_RANGE   = 6.0f;
    private static final float  DEFAULT_PLACE_RANGE    = 4.5f;
    private static final double DEFAULT_MIN_DAMAGE     = 4.0;
    private static final double DEFAULT_MAX_SELF_DAMAGE = 10.0;
    private static final double DEFAULT_REACTION_MS    = 120.0;

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static AutoCrystalConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "autocrystal.json";

    // ── Fields (serialized) ───────────────────────────────────────────────────

    /** Range within which to search for targets (blocks). */
    private float  targetRange    = DEFAULT_TARGET_RANGE;

    /** Range within which crystals may be placed (blocks). */
    private float  placeRange     = DEFAULT_PLACE_RANGE;

    /** Minimum predicted target damage required to place a crystal. */
    private double minDamage      = DEFAULT_MIN_DAMAGE;

    /** Maximum self-damage before a placement is skipped. */
    private double maxSelfDamage  = DEFAULT_MAX_SELF_DAMAGE;

    /**
     * Base reaction time in milliseconds. Actual timing is sampled from a
     * log-normal distribution centred on this value.
     */
    private double reactionTimeMs = DEFAULT_REACTION_MS;

    /** Whether the human-pattern algorithm is active. */
    private boolean humanize      = true;

    /** Whether the fatigue model is active. */
    private boolean fatigueEnabled = true;

    // ── Singleton access ──────────────────────────────────────────────────────

    private AutoCrystalConfig() {}

    public static AutoCrystalConfig getInstance() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    /** Loads config from disk, or creates defaults if not present. */
    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                INSTANCE = GSON.fromJson(reader, AutoCrystalConfig.class);
                if (INSTANCE == null) INSTANCE = new AutoCrystalConfig();
            } catch (IOException | com.google.gson.JsonParseException e) {
                INSTANCE = new AutoCrystalConfig();
            }
        } else {
            INSTANCE = new AutoCrystalConfig();
            save();
        }
        INSTANCE.validate();
    }

    /** Saves the current configuration to disk. */
    public static void save() {
        if (INSTANCE == null) return;
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            // Non-fatal; defaults will be used
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** Clamps all values to sane ranges after deserialization. */
    private void validate() {
        targetRange    = Math.max(1f, Math.min(10f, targetRange));
        placeRange     = Math.max(1f, Math.min(6f,  placeRange));
        minDamage      = Math.max(1.0, Math.min(30.0, minDamage));
        maxSelfDamage  = Math.max(0.0, Math.min(36.0, maxSelfDamage));
        reactionTimeMs = Math.max(50.0, Math.min(500.0, reactionTimeMs));
    }

    // ── Getters / setters ────────────────────────────────────────────────────

    public float  getTargetRange()    { return targetRange;    }
    public float  getPlaceRange()     { return placeRange;     }
    public double getMinDamage()      { return minDamage;      }
    public double getMaxSelfDamage()  { return maxSelfDamage;  }
    public double getReactionTimeMs() { return reactionTimeMs; }
    public boolean isHumanize()       { return humanize;       }
    public boolean isFatigueEnabled() { return fatigueEnabled; }

    public void setTargetRange(float v)    { targetRange    = v; validate(); save(); }
    public void setPlaceRange(float v)     { placeRange     = v; validate(); save(); }
    public void setMinDamage(double v)     { minDamage      = v; validate(); save(); }
    public void setMaxSelfDamage(double v) { maxSelfDamage  = v; validate(); save(); }
    public void setReactionTimeMs(double v){ reactionTimeMs = v; validate(); save(); }
    public void setHumanize(boolean v)     { humanize       = v; save(); }
    public void setFatigueEnabled(boolean v){ fatigueEnabled = v; save(); }
}
