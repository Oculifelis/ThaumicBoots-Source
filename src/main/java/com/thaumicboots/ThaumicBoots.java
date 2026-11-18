package com.thaumicboots;

import com.mojang.logging.LogUtils;
import com.thaumicboots.boots.BootEvents;
import com.thaumicboots.net.BootKeyPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Thaumic Boots (Alastors, Thaumcraft 4, GTNH fork), ported to Thaumaturge on NeoForge 26.1.2. Every boot is one
 * {@link com.thaumicboots.boots.BootItem}; what it does comes from the {@code thaumicboots:boot_stats} item data map
 * (see docs/ORIGINAL-SPEC.md for the original numbers and docs/COVERAGE.md for what went where).
 */
@Mod(ThaumicBoots.MODID)
public final class ThaumicBoots {
    public static final String MODID = "thaumicboots";
    public static final Logger LOG = LogUtils.getLogger();

    public ThaumicBoots(IEventBus modBus, ModContainer container, Dist dist) {
        TBRegistry.register(modBus);
        modBus.addListener(TBDataMaps::register);
        modBus.addListener(BootKeyPayload::register);
        container.registerConfig(ModConfig.Type.SERVER, TBConfig.SPEC);
        BootEvents.register();
        com.thaumicboots.boots.SelfTest.register();
        if (dist == Dist.CLIENT) {
            com.thaumicboots.client.TBClient.init(modBus);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
