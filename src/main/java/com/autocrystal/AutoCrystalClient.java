package com.autocrystal;

import com.autocrystal.config.AutoCrystalConfig;
import com.autocrystal.module.AutoCrystalModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side initializer.
 * Registers the toggle keybind and wires the per-tick update callback.
 */
public class AutoCrystalClient implements ClientModInitializer {

    /** Default keybind: X  (common PvP toggle key) */
    private static KeyBinding toggleKey;
    private static final AutoCrystalModule MODULE = new AutoCrystalModule();

    @Override
    public void onInitializeClient() {
        AutoCrystalConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autocrystal.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "category.autocrystal"
        ));

        // Hook into the client tick to drive the module
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Handle toggle key press – arms or disarms the right-click trigger mode.
            while (toggleKey.wasPressed()) {
                MODULE.setEnabled(!MODULE.isEnabled());
                if (client.player != null) {
                    String state = MODULE.isEnabled()
                            ? "§aArmed – hold right-click on obsidian/bedrock to crystal"
                            : "§cDisarmed";
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("[AutoCrystal] " + state), true);
                }
            }

            // Run the module each tick
            if (client.player != null && client.world != null) {
                MODULE.onTick(client);
            }
        });

        AutoCrystalMod.LOGGER.info("AutoCrystal client initialized. Toggle: X (then hold right-click on obsidian/bedrock)");
    }

    /** Exposes the module for access from mixins or tests. */
    public static AutoCrystalModule getModule() {
        return MODULE;
    }
}
