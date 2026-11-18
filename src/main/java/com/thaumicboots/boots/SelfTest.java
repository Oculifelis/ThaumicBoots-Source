package com.thaumicboots.boots;

import com.thaumicboots.ThaumicBoots;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Checks of the boots' slam machine, run once the dev server has started when the JVM has
 * {@code -Dthaumicboots.selftest=true} (only the Gradle server run sets it). The boot state is deliberately unsaved,
 * so instead of a save/load round trip these check that every exit from a state clears what belongs to it. Logs one
 * {@code SELFTEST PASS} or {@code SELFTEST FAIL} line each.
 */
public final class SelfTest {
    private SelfTest() {
    }

    public static void register() {
        if (Boolean.getBoolean("thaumicboots.selftest")) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> run());
        }
    }

    private static void check(String name, boolean ok, String detail) {
        ThaumicBoots.LOG.info("SELFTEST {} {}: {}", ok ? "PASS" : "FAIL", name, detail);
    }

    private static String dump(BootState s) {
        return "slam=" + s.slam() + " air=" + s.airTicks + " smash=" + s.smashTicks + " run=" + s.runTicks + " jumps=" + s.airJumpsUsed;
    }

    private static BootState diving() {
        BootState s = new BootState();
        s.airTicks = 30;
        s.slam(BootState.Slam.DIVING);
        s.smashTicks = 12;
        s.runTicks = 40;
        s.airJumpsUsed = 2;
        return s;
    }

    private static void run() {
        try {
            BootState s = diving();
            s.slam(BootState.Slam.DIVING);
            check("boots staying in a dive keeps its ticks", s.smashing() && s.smashTicks == 12 && s.airTicks == 30, dump(s));
            s.resetSmash();
            check("boots landing clears dive + air time", s.slam() == BootState.Slam.NONE && s.smashTicks == 0 && s.airTicks == 0
                    && s.runTicks == 40, dump(s));
            s = diving();
            s.reset();
            check("boots reset (logout / dimension / boots off) clears all", !s.smashing() && s.smashTicks == 0 && s.airTicks == 0
                    && s.runTicks == 0 && s.airJumpsUsed == 0 && Double.isNaN(s.lastX), dump(s));
            s = new BootState();
            s.smashTicks = 5;
            s.slam(BootState.Slam.DIVING);
            check("boots a new dive starts its count at 0", s.smashing() && s.smashTicks == 0, dump(s));
        } catch (Exception ex) {
            ThaumicBoots.LOG.error("SELFTEST FAIL crashed", ex);
        }
    }
}
