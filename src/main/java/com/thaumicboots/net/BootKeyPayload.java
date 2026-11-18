package com.thaumicboots.net;

import com.thaumicboots.TBConfig;
import com.thaumicboots.TBDataMaps;
import com.thaumicboots.TBRegistry;
import com.thaumicboots.ThaumicBoots;
import com.thaumicboots.boots.BootEvents;
import com.thaumicboots.boots.BootModes;
import com.thaumicboots.boots.BootPowers;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A boot key, client to server (the original's seven Packet*Inc / Packet*Toggle messages in one). The server changes
 * the modes component on the worn boots, which syncs back by itself, and answers with an action-bar message where
 * the original printed GTNHLib's above-hotbar text.
 */
public record BootKeyPayload(Action action) implements CustomPacketPayload {
    public enum Action {
        SPEED, SPEED_FINE, JUMP, JUMP_FINE, OMNI, STEP, INERTIA, AIR_JUMP
    }

    public static final Type<BootKeyPayload> TYPE = new Type<>(ThaumicBoots.id("boot_key"));
    public static final StreamCodec<ByteBuf, BootKeyPayload> CODEC = ByteBufCodecs.VAR_INT.map(
            i -> new BootKeyPayload(Action.values()[Math.floorMod(i, Action.values().length)]), p -> p.action().ordinal());

    @Override
    public Type<BootKeyPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, BootKeyPayload::handle);
    }

    private static void handle(BootKeyPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        if (payload.action() == Action.AIR_JUMP) {
            BootPowers.airJumpServer(player);
            return;
        }
        ItemStack boots = BootEvents.worn(player);
        if (TBDataMaps.stats(boots) == null) {
            return;
        }
        BootModes modes = BootModes.of(boots);
        double rate = TBConfig.CHANGE_RATE.get();
        double fine = rate * TBConfig.CHANGE_RATE_MODIFIER.get();
        Component message;
        switch (payload.action()) {
            case SPEED, SPEED_FINE -> {
                modes = modes.withSpeed(BootModes.step(modes.speed(), payload.action() == Action.SPEED ? rate : fine));
                message = percent("message.thaumicboots.speed", modes.speed());
            }
            case JUMP, JUMP_FINE -> {
                modes = modes.withJump(BootModes.step(modes.jump(), payload.action() == Action.JUMP ? rate : fine));
                message = percent("message.thaumicboots.jump", modes.jump());
            }
            case OMNI -> {
                modes = modes.withOmni(!modes.omni());
                message = state("message.thaumicboots.omni", modes.omni());
            }
            case STEP -> {
                modes = modes.withStep(!modes.step());
                message = state("message.thaumicboots.step", modes.step());
            }
            default -> {
                if (TBConfig.ALLOW_INERTIA_CANCELING.get()) {
                    modes = modes.withInertia(!modes.inertia());
                    message = state("message.thaumicboots.inertia", modes.inertia());
                } else {
                    modes = modes.withInertia(false);
                    message = Component.translatable("message.thaumicboots.inertia_disabled").withStyle(ChatFormatting.DARK_RED);
                }
            }
        }
        boots.set(TBRegistry.MODES.get(), modes);
        player.sendOverlayMessage(message);
    }

    /** "Speed Boost: 75%", the number coloured by quarter as the original did. */
    private static Component percent(String key, double value) {
        int pct = (int) Math.round(value * 100);
        ChatFormatting colour = switch (pct / 25) {
            case 0 -> ChatFormatting.DARK_RED;
            case 1 -> ChatFormatting.RED;
            case 2 -> ChatFormatting.DARK_GREEN;
            case 3 -> ChatFormatting.GREEN;
            case 4 -> ChatFormatting.AQUA;
            default -> ChatFormatting.DARK_GRAY;
        };
        return label(key).append(Component.literal(pct + "%").withStyle(colour));
    }

    private static Component state(String key, boolean on) {
        return label(key).append(Component.translatable(on ? "message.thaumicboots.on" : "message.thaumicboots.off")
                .withStyle(on ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED));
    }

    private static MutableComponent label(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GOLD).append(" ");
    }
}
