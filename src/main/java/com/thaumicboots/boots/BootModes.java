package com.thaumicboots.boots;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thaumicboots.TBRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The player's settings on a pair of boots (the original's NBT keys speed, jump, omni, step, inertiacanceling):
 * speed and jump as a fraction of full, 0 to 1; omni-movement and step assist on by default, inertia canceling off.
 */
public record BootModes(double speed, double jump, boolean omni, boolean step, boolean inertia) {
    public static final BootModes DEFAULT = new BootModes(1, 1, true, true, false);

    public static final Codec<BootModes> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0, 1).optionalFieldOf("speed", 1.0).forGetter(BootModes::speed),
            Codec.doubleRange(0, 1).optionalFieldOf("jump", 1.0).forGetter(BootModes::jump),
            Codec.BOOL.optionalFieldOf("omni", true).forGetter(BootModes::omni),
            Codec.BOOL.optionalFieldOf("step", true).forGetter(BootModes::step),
            Codec.BOOL.optionalFieldOf("inertia", false).forGetter(BootModes::inertia)
    ).apply(i, BootModes::new));

    public static final StreamCodec<ByteBuf, BootModes> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, BootModes::speed,
            ByteBufCodecs.DOUBLE, BootModes::jump,
            ByteBufCodecs.BOOL, BootModes::omni,
            ByteBufCodecs.BOOL, BootModes::step,
            ByteBufCodecs.BOOL, BootModes::inertia,
            BootModes::new);

    public static BootModes of(ItemStack stack) {
        return stack.getOrDefault(TBRegistry.MODES.get(), DEFAULT);
    }

    /**
     * The original's stepping: add the step, round to the nearest 5%; from full the next press wraps to 0, and a step
     * past full from below lands on full.
     */
    public static double step(double old, double by) {
        double next = Math.round(20.0 * (old + by)) / 20.0;
        if (old == 1) {
            return 0;
        }
        return next > 1 ? 1 : next;
    }

    public BootModes withSpeed(double v) {
        return new BootModes(v, jump, omni, step, inertia);
    }

    public BootModes withJump(double v) {
        return new BootModes(speed, v, omni, step, inertia);
    }

    public BootModes withOmni(boolean v) {
        return new BootModes(speed, jump, v, step, inertia);
    }

    public BootModes withStep(boolean v) {
        return new BootModes(speed, jump, omni, v, inertia);
    }

    public BootModes withInertia(boolean v) {
        return new BootModes(speed, jump, omni, step, v);
    }
}
