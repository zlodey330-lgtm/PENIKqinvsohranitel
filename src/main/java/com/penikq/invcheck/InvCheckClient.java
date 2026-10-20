package com.penikq.invcheck;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class InvCheckClient implements ClientModInitializer {
    public static KeyBinding OPEN_KEY;

    @Override
    public void onInitializeClient() {
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.penikqinvcheck.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.penikqinvcheck"
        ));
        SavedData.get();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new InvCheckScreen());
                }
            }
        });
    }
}
