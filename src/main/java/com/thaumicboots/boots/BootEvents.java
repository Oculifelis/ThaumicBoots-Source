package com.thaumicboots.boots;

import com.thaumicboots.TBDataMaps;
import com.thaumicboots.TBDataMaps.BootStats;
import com.thaumicboots.TBRegistry;
import com.thaumicboots.ThaumicBoots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * The boots' worn effects on both sides (the original's onArmorTick, BootsEventHandler and the IComet / IMeteor
 * special effects): fall negation, step assist, the meteor slam and dash, comet ice, the boosted jump, electric
 * comet falls and electric absorption, the voidwalker ward. Ground speed, omni-movement, air control and inertia
 * canceling are client movement, in {@link BootMotion}.
 */
public final class BootEvents {
    private static final Identifier STEP_ID = ThaumicBoots.id("step_assist");

    private BootEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BootEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(BootEvents::onJump);
        NeoForge.EVENT_BUS.addListener(BootEvents::onFall);
        NeoForge.EVENT_BUS.addListener(BootEvents::onIncomingDamage);
        // the boot state is per player and unsaved: start it fresh on logout and in a new dimension
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> e.getEntity().getData(TBRegistry.STATE).reset());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> e.getEntity().getData(TBRegistry.STATE).reset());
    }

    public static ItemStack worn(Player player) {
        return player.getItemBySlot(EquipmentSlot.FEET);
    }

    public static @Nullable BootStats wornStats(Player player) {
        return TBDataMaps.stats(worn(player));
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        ItemStack boots = worn(player);
        BootStats stats = TBDataMaps.stats(boots);
        BootState state = player.getData(TBRegistry.STATE);
        if (stats == null) {
            state.reset();
            step(player, 0);
            return;
        }
        BootModes modes = BootModes.of(boots);
        if (stats.movement().negateFall()) {
            player.fallDistance = 0;
        }
        step(player, modes.step() && !player.isShiftKeyDown() ? stats.movement().stepHeight() : 0);
        stats.meteor().ifPresent(meteor -> smash(player, boots, stats, state, meteor));
        BootPowers.tick(player, boots, stats, state);
        if (!player.level().isClientSide()) {
            stats.frost().ifPresent(frost -> freeze(player, frost));
        }
    }

    /** Step assist: raise the step height to the boots' value while not sneaking (a transient modifier, so it never sticks). */
    private static void step(Player player, float height) {
        AttributeInstance attr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr == null) {
            return;
        }
        double extra = height - attr.getBaseValue();
        if (extra <= 0) {
            attr.removeModifier(STEP_ID);
        } else {
            AttributeModifier current = attr.getModifier(STEP_ID);
            if (current == null || current.amount() != extra) {
                attr.addOrUpdateTransientModifier(new AttributeModifier(STEP_ID, extra, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /** The original's slam: crouch in midair to dive; landing after a long enough dive bursts without breaking blocks. */
    private static void smash(Player player, ItemStack boots, BootStats stats, BootState state, TBDataMaps.Meteor meteor) {
        if (player.onGround() || player.onClimbable()) {
            int size = 0;
            for (int threshold : meteor.steps()) {
                if (state.smashTicks > threshold) {
                    size++;
                }
            }
            if (size > 0 && player.level() instanceof ServerLevel level) {
                size += BootPowers.overcharge(boots, stats);
                level.explode(player, player.getX(), player.getY(), player.getZ(), size, Level.ExplosionInteraction.NONE);
            }
            state.resetSmash();
            return;
        }
        if (!player.isInWater()) {
            if (!player.isShiftKeyDown()) {
                state.airTicks++;
            } else if (state.airTicks > meteor.airTicks()) {
                state.slam(BootState.Slam.DIVING);
            }
        }
        if (player.getAbilities().flying) {
            state.resetSmash();
        }
        if (state.smashing()) {
            state.smashTicks++;
            player.setDeltaMovement(player.getDeltaMovement().add(0, -meteor.pull(), 0));
        }
    }

    /** Comet ice: still water one block down turns to frosted ice, which melts back on its own (the original's melting ice). */
    private static void freeze(Player player, TBDataMaps.Frost frost) {
        if (player.isInWater()) {
            return;
        }
        Level level = player.level();
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() - 1, player.getZ());
        int r = frost.radius();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (Math.abs(x) + Math.abs(z) >= frost.reach()) {
                    continue;
                }
                BlockPos p = feet.offset(x, 0, z);
                BlockState here = level.getBlockState(p);
                if (here.getBlock() instanceof LiquidBlock && here.getFluidState().is(Fluids.WATER) && here.getFluidState().isSource()
                        && level.getBlockState(p.above()).isAir() && level.isUnobstructed(ice, p, CollisionContext.empty())) {
                    level.setBlockAndUpdate(p, ice);
                    level.scheduleTick(p, Blocks.FROSTED_ICE, Mth.nextInt(player.getRandom(), 60, 120));
                    if (level instanceof ServerLevel server) {
                        server.sendParticles(ParticleTypes.ITEM_SNOWBALL, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 1, 0, 0.025, 0, 0);
                    }
                }
            }
        }
    }

    /** The boosted jump; with meteor boots a crouch-jump is the dash instead. Uncharged electric boots do neither. */
    private static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack boots = worn(player);
        BootStats stats = TBDataMaps.stats(boots);
        if (stats == null || !Energy.powered(boots)) {
            return;
        }
        TBDataMaps.Phase phase = stats.powers().phase().orElse(null);
        if (phase != null && player.isShiftKeyDown()) {
            // Framewalker: a crouch-jump is the phase step, not a jump.
            BootPowers.phase(player, player.getData(TBRegistry.STATE), phase);
            return;
        }
        TBDataMaps.Meteor meteor = stats.meteor().orElse(null);
        if (meteor != null && player.isShiftKeyDown()) {
            Vec3 look = player.getLookAngle();
            double up = Math.max(look.y, Math.abs(look.z + look.x));
            player.setDeltaMovement(player.getDeltaMovement().add(look.x * meteor.dash() * meteor.dashForward(), up * meteor.dash() / meteor.dashUp(),
                    look.z * meteor.dash() * meteor.dashForward()));
        } else {
            player.setDeltaMovement(player.getDeltaMovement().add(0, stats.movement().jump() * BootModes.of(boots).jump(), 0));
        }
        player.hurtMarked = true;
    }

    /** Electric comet boots: short falls are free, longer ones cost FE (more past min_distance), or hurt if it runs out. */
    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack boots = worn(player);
        BootStats stats = TBDataMaps.stats(boots);
        if (stats == null) {
            return;
        }
        float distance = (float) event.getDistance();
        // The new boots' landings: briars catch the fall, or the stomp lands on whatever is below.
        if (BootPowers.thornCatch(player, boots, stats, distance) || BootPowers.stomp(player, boots, stats, distance)) {
            event.setCanceled(true);
            return;
        }
        if (stats.cometFall().isEmpty()) {
            return;
        }
        TBDataMaps.CometFall fall = stats.cometFall().get();
        if (distance <= fall.minHeight()) {
            event.setCanceled(true);
            return;
        }
        TBDataMaps.Energy energy = stats.energy().orElse(null);
        if (energy == null) {
            return;
        }
        float multiplier = distance > fall.minDistance() ? distance * fall.distanceMultiplier() : distance;
        int demand = Math.round(energy.perDamage() * (multiplier - fall.minHeight()));
        if (Energy.use(boots, demand)) {
            event.setCanceled(true);
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        ItemStack boots = worn(player);
        BootStats stats = TBDataMaps.stats(boots);
        if (stats == null) {
            return;
        }
        DamageSource source = event.getSource();
        float amount = event.getAmount();
        TBDataMaps.Ward ward = stats.ward().orElse(null);
        if (ward != null) {
            float cut = Math.max(source.is(DamageTypeTags.WITCH_RESISTANT_TO) ? ward.magic() : 0,
                    Math.max(source.is(DamageTypeTags.IS_FIRE) ? ward.fire() : 0, source.is(DamageTypeTags.IS_EXPLOSION) ? ward.explosion() : 0));
            amount *= 1 - cut;
        }
        amount = BootPowers.blastCatch(boots, stats, source, amount);
        if (player.level() instanceof ServerLevel level) {
            BootPowers.thorns(level, player, stats, source);
        }
        TBDataMaps.Energy energy = stats.energy().orElse(null);
        if (energy != null && energy.perDamage() > 0 && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            int stored = Energy.stored(boots);
            if (stored >= energy.perDamage()) {
                // The original's ISpecialArmor: absorb a share of the hit, at most what the charge covers, and pay per point.
                float absorbed = (float) Math.min(amount * energy.baseAbsorption() * energy.absorption(), stored / (double) energy.perDamage());
                int cost = Math.max(1, (int) absorbed) * energy.perDamage();
                if (Energy.use(boots, Math.min(cost, stored))) {
                    amount -= absorbed;
                }
            }
        }
        if (amount != event.getAmount()) {
            event.setAmount(Math.max(0, amount));
        }
    }
}
