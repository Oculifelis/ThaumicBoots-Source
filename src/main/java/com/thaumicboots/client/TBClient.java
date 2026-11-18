package com.thaumicboots.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thaumicboots.ThaumicBoots;
import com.thaumicboots.boots.BootMotion;
import com.thaumicboots.net.BootKeyPayload;
import com.thaumicboots.net.BootKeyPayload.Action;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The original's BootKeys (six unbound keys under "Thaumic Boots") and the local player's boot movement.
 */
public final class TBClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(ThaumicBoots.id("boots"));
    private static final KeyMapping JUMP = key("jump_toggle");
    private static final KeyMapping SPEED = key("speed_toggle");
    private static final KeyMapping OMNI = key("omni_toggle");
    private static final KeyMapping INERTIA = key("inertia_toggle");
    private static final KeyMapping MODIFIER = key("increment_modifier");
    private static final KeyMapping STEP = key("step_assist");

    private TBClient() {
    }

    private static KeyMapping key(String name) {
        return new KeyMapping("key.thaumicboots." + name, InputConstants.UNKNOWN.getValue(), CATEGORY);
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(TBClient::keys);
        com.thaumicboots.boots.BootPowers.airJumpNotice = () -> ClientPacketDistributor.sendToServer(new BootKeyPayload(Action.AIR_JUMP));
        NeoForge.EVENT_BUS.addListener(TBClient::clientTick);
        NeoForge.EVENT_BUS.addListener(TBClient::playerTick);
    }

    private static void keys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        for (KeyMapping k : new KeyMapping[] {JUMP, SPEED, OMNI, INERTIA, MODIFIER, STEP}) {
            event.register(k);
        }
    }

    /** One key per tick, in the original's order; the modifier key held makes speed and jump steps finer. */
    private static void clientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        boolean fine = MODIFIER.isDown();
        Action action = null;
        if (JUMP.consumeClick()) {
            action = fine ? Action.JUMP_FINE : Action.JUMP;
        } else if (SPEED.consumeClick()) {
            action = fine ? Action.SPEED_FINE : Action.SPEED;
        } else if (OMNI.consumeClick()) {
            action = Action.OMNI;
        } else if (INERTIA.consumeClick()) {
            action = Action.INERTIA;
        } else if (STEP.consumeClick()) {
            action = Action.STEP;
        }
        if (action != null) {
            ClientPacketDistributor.sendToServer(new BootKeyPayload(action));
        }
    }

    /** Before the local player moves this tick. */
    private static void playerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof LocalPlayer player && player == Minecraft.getInstance().player) {
            BootMotion.tick(player, Minecraft.getInstance().options.keyJump.isDown());
        }
    }
}
