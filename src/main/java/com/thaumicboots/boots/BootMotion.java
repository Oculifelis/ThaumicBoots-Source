package com.thaumicboots.boots;

import com.leclowndu93150.thaumaturge.content.equipment.hover.HoverManager;
import com.thaumicboots.TBConfig;
import com.thaumicboots.TBDataMaps;
import com.thaumicboots.TBDataMaps.BootStats;
import com.thaumicboots.TBRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The movement half of the original's onArmorTick, run for the local player every tick before it moves (player
 * movement is the client's): run ticks for the long-running ramp, inertia canceling, the ground speed boost along
 * the input (strafing and vertical too in omni mode) and air control from the jump bonus. Then the particle trails.
 */
public final class BootMotion {
    private BootMotion() {
    }

    public static void tick(Player player, boolean jumpKey) {
        ItemStack boots = BootEvents.worn(player);
        BootStats stats = TBDataMaps.stats(boots);
        if (stats == null) {
            return;
        }
        BootModes modes = BootModes.of(boots);
        BootState state = player.getData(TBRegistry.STATE);
        TBDataMaps.Movement mv = stats.movement();
        TBDataMaps.Tuning t = stats.tuning();
        Vec3 v = player.getDeltaMovement();
        boolean flying = player.getAbilities().flying;
        boolean hover = HoverManager.isHovering(player);

        if (mv.steady()) {
            double motion = Math.abs(v.x) + Math.abs(v.z) + Math.abs(v.y);
            if (motion > t.runMotion() || !player.onGround() || player.onClimbable()) {
                if (state.runTicks < t.runTicksMax()) {
                    state.runTicks++;
                }
            } else {
                state.runTicks = 0;
            }
        }
        trails(player, stats, state, v);
        BootPowers.airJump(player, boots, stats, state, jumpKey);
        v = player.getDeltaMovement();

        float forward = player.zza;
        float strafe = player.xxa;
        if (TBConfig.ALLOW_INERTIA_CANCELING.get() && modes.inertia() && forward == 0 && strafe == 0 && (flying || hover)) {
            player.setDeltaMovement(v.x * t.inertiaDamping(), v.y, v.z * t.inertiaDamping());
            v = player.getDeltaMovement();
        }
        boolean omni = mv.omni() && modes.omni();
        if (forward <= 0 && !omni) {
            return;
        }
        if (forward == 0 && strafe == 0 && v.y == 0) {
            return;
        }

        float bonus = mv.steady() ? mv.speed() + state.runTicks * t.rampPerTick() * stats.longRunning() : mv.speed();
        if (flying) {
            bonus *= t.flyingMultiplier();
        }
        if (!Energy.powered(boots)) {
            bonus = 0;
        }
        float speedMod = (float) modes.speed();
        bonus *= speedMod;
        if (mv.waterPenalty() && player.isInWater()) {
            bonus *= t.waterMultiplier();
        }

        if (player.onGround() || player.onClimbable() || flying) {
            if (forward != 0) {
                player.moveRelative(bonus, new Vec3(0, 0, forward));
            }
            if (omni) {
                if (strafe != 0) {
                    player.moveRelative(bonus, new Vec3(strafe, 0, 0));
                }
                if (v.y != 0) {
                    boolean sneaking = player.isShiftKeyDown();
                    if (sneaking && !jumpKey && !player.onGround()) {
                        player.setDeltaMovement(player.getDeltaMovement().add(0, -bonus, 0));
                    } else if (jumpKey && !sneaking) {
                        player.setDeltaMovement(player.getDeltaMovement().add(0, bonus, 0));
                    }
                }
            }
        } else {
            // The original replaced the air-control factor; vanilla already applies air_floor, so add the difference.
            float factor = hover ? t.airFactorHover() : t.airFactor();
            float air = (factor / t.airBaseJump() * (float) mv.jump() - t.airFloor()) * speedMod + t.airFloor();
            float extra = air - t.airFloor();
            if (extra != 0 && (forward != 0 || strafe != 0)) {
                player.moveRelative(extra, new Vec3(strafe, 0, forward));
            }
        }
    }

    private static void trails(Player player, BootStats stats, BootState state, Vec3 v) {
        RandomSource rand = player.getRandom();
        for (TBDataMaps.Trail trail : stats.trails()) {
            if (trail.whileSmashing()) {
                if (state.smashing()) {
                    player.level().addParticle(trail.particle(), player.getX() + rand.nextDouble() - 0.5, player.getY() + rand.nextDouble() - 0.5,
                            player.getZ() + rand.nextDouble() - 0.5, 0, 0, 0);
                }
                continue;
            }
            if (!trail.inWater() && player.isInWaterOrRain()) {
                continue;
            }
            double motion = Math.abs(v.x) + Math.abs(v.z) + Math.abs(v.y * trail.verticalWeight());
            boolean moving = motion > trail.minMotion() || (trail.airborne() && !player.onGround());
            if (moving && rand.nextInt(trail.every()) == 0) {
                double y = player.getBoundingBox().minY + (trail.airborne() ? 0.05 + (rand.nextDouble() - rand.nextDouble()) * 0.1
                        : 0.25 + (rand.nextDouble() - 0.5) * 0.25);
                player.level().addParticle(trail.particle(), player.getX() + rand.nextDouble() - 0.5, y, player.getZ() + rand.nextDouble() - 0.5,
                        0, 0.025, 0);
            }
        }
    }
}
