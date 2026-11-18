package com.thaumicboots;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The original's "boots" config section, as a server config (synced to clients, so the inertia switch is the
 * server's). The module switches of the original are gone: each compat recipe and research entry carries a
 * {@code neoforge:mod_loaded} condition instead.
 */
public final class TBConfig {
    public static final ModConfigSpec SPEC;
    /** How far one press of the speed or jump key moves the setting (fraction of full). */
    public static final ModConfigSpec.DoubleValue CHANGE_RATE;
    /** Multiplies the change rate while the modulation-modifier key is held. */
    public static final ModConfigSpec.DoubleValue CHANGE_RATE_MODIFIER;
    /** Whether the flight inertia canceling toggle may be turned on at all. */
    public static final ModConfigSpec.BooleanValue ALLOW_INERTIA_CANCELING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("boots");
        CHANGE_RATE = b.comment("The rate that boot jump and speed modifiers change when the Jump/Speed Modulation key is pressed.")
                .defineInRange("default_change_rate", 0.25, 0.01, 1.0);
        CHANGE_RATE_MODIFIER = b.comment("The value that modifies the boot change rate when the Modulation Modifier key is pressed.")
                .defineInRange("change_rate_modifier", 0.4, 0.01, 1.0);
        ALLOW_INERTIA_CANCELING = b.comment("Allow the flight inertia canceling toggle.")
                .define("allow_inertia_canceling", true);
        b.pop();
        SPEC = b.build();
    }

    private TBConfig() {
    }
}
