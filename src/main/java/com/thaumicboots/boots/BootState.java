package com.thaumicboots.boots;

/**
 * Per-player, per-side, unsaved state of the worn boots: time running, time in the air, the slam in progress. Kept as
 * a player attachment, so it goes with the player entity; {@link BootEvents} also clears it on logout and on a
 * dimension change. The slam is the one machine here: {@link #slam(Slam)} is the only place it changes.
 */
public final class BootState {
    /** The meteor slam: none, or diving after crouching in midair long enough. */
    public enum Slam {
        NONE, DIVING
    }

    public int runTicks;
    /** Ticks in the air since the ground (any slam state); cleared whenever the slam ends. */
    public int airTicks;
    /** Ticks of the dive; only means something while {@link Slam#DIVING}. */
    public int smashTicks;
    private Slam slam = Slam.NONE;
    // The new boots' powers (BootPowers): air jumps used since the ground, the jump key last tick, the phase step's
    // rest, ticks spent sneaking, and where the feet were last tick (to tell walking from standing).
    public int airJumpsUsed;
    public boolean jumpHeld;
    public int phaseCooldown;
    public int sneakTicks;
    public double lastX = Double.NaN;
    public double lastZ = Double.NaN;

    public Slam slam() {
        return slam;
    }

    public boolean smashing() {
        return slam == Slam.DIVING;
    }

    /**
     * The one place the slam changes. Starting a dive starts its tick count (staying in one keeps it); ending the slam
     * (landing, flying, taking the boots off) clears the dive and the air time together.
     */
    void slam(Slam next) {
        if (next == slam && next != Slam.NONE) {
            return;
        }
        slam = next;
        smashTicks = 0;
        if (next == Slam.NONE) {
            airTicks = 0;
        }
    }

    void resetSmash() {
        slam(Slam.NONE);
    }

    void reset() {
        resetSmash();
        runTicks = 0;
        airJumpsUsed = 0;
        sneakTicks = 0;
        lastX = Double.NaN;
        lastZ = Double.NaN;
    }
}
