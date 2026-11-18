package com.thaumicboots.boots;

import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.thaumicboots.TBDataMaps;
import com.thaumicboots.TBDataMaps.BootStats;
import com.thaumicboots.TBDataMaps.Powers;
import com.thaumicboots.TBRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The powers of the new boots (docs/COVERAGE.md, "New content (Oculifelis 2026-11-01)"): ground charge, blast catch and
 * overcharge (Fieldframe), phase step (Fieldframe frames), growth and thorn catch (Flora Ferox), stomp and purify
 * (Thaumaturge's liquid death and purifying fluid), air jumps, the slow aura and the ghost walk. Every number comes
 * from the {@code powers} object of the boot's {@code boot_stats} entry; nothing here touches another mod's classes:
 * other mods' blocks are reached through tags, block ids and NeoForge's energy capability.
 */
public final class BootPowers {
    private BootPowers() {
    }

    /** Every tick the boots are worn, on both sides (each power picks its side). */
    static void tick(Player player, ItemStack boots, BootStats stats, BootState state) {
        Powers powers = stats.powers();
        if (state.phaseCooldown > 0) {
            state.phaseCooldown--;
        }
        if (player.onGround() || player.onClimbable() || player.isInWater()) {
            state.airJumpsUsed = 0;
        }
        state.sneakTicks = player.isShiftKeyDown() ? state.sneakTicks + 1 : 0;
        boolean walked = walked(player, state);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!Energy.powered(boots) && stats.energy().isPresent() && powers.groundCharge().isEmpty()) {
            return;
        }
        powers.groundCharge().ifPresent(charge -> groundCharge(player, boots, stats, charge));
        if (walked) {
            powers.growth().ifPresent(growth -> grow(level, player, growth));
            powers.purify().ifPresent(purify -> purifyBlocks(level, player, purify));
        }
        powers.purify().ifPresent(purify -> {
            if (player.tickCount % purify.fluxEvery() == 0 && purify.flux() > 0) {
                float drained = AuraHelper.drainFlux(level, player.blockPosition(), purify.flux(), false);
                if (drained > 0) {
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(), 3, 0.3, 0.1, 0.3, 0.01);
                }
            }
        });
        powers.aura().ifPresent(aura -> aura(level, player, aura));
        powers.ghost().ifPresent(ghost -> ghost(level, player, state, ghost));
    }

    /** True when the feet moved sideways since last tick (growth and purifying follow your steps, not your standing). */
    private static boolean walked(Player player, BootState state) {
        double dx = player.getX() - state.lastX;
        double dz = player.getZ() - state.lastZ;
        state.lastX = player.getX();
        state.lastZ = player.getZ();
        return dx * dx + dz * dz > 1.0E-4;
    }

    // ---- Fieldframe: ground charge, blast catch, overcharge, phase step ---------------------------------------------

    /** FE from the energy block under your feet, asked from its top side (a chassis' Power Terminal, a Frame Cell...). */
    private static void groundCharge(Player player, ItemStack boots, BootStats stats, TBDataMaps.GroundCharge charge) {
        TBDataMaps.Energy spec = stats.energy().orElse(null);
        if (spec == null) {
            return;
        }
        int stored = Energy.stored(boots);
        int room = spec.capacity() - stored;
        if (room <= 0) {
            return;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.1, player.getZ());
        EnergyHandler handler = player.level().getCapability(Capabilities.Energy.BLOCK, below, Direction.UP);
        if (handler == null) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int got = handler.extract(Math.min(room, charge.rate()), tx);
            if (got > 0) {
                tx.commit();
                boots.set(TBRegistry.ENERGY.get(), stored + got);
            }
        }
    }

    /** Explosion damage taken: the boots' share comes off the hit and goes into their charge. Returns the new amount. */
    static float blastCatch(ItemStack boots, BootStats stats, DamageSource source, float amount) {
        TBDataMaps.BlastCatch blast = stats.powers().blastCatch().orElse(null);
        TBDataMaps.Energy spec = stats.energy().orElse(null);
        if (blast == null || spec == null || !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            return amount;
        }
        float caught = amount * blast.share();
        long fe = (long) Math.ceil(caught * blast.fePerDamage());
        boots.set(TBRegistry.ENERGY.get(), (int) Math.min(spec.capacity(), Energy.stored(boots) + fe));
        return amount - caught;
    }

    /** Extra burst power a meteor landing buys with stored FE. */
    static int overcharge(ItemStack boots, BootStats stats) {
        TBDataMaps.Overcharge over = stats.powers().overcharge().orElse(null);
        if (over == null) {
            return 0;
        }
        int extra = Math.min(over.maxPower(), Energy.stored(boots) / over.fePerPower());
        if (extra > 0) {
            Energy.use(boots, extra * over.fePerPower());
        }
        return extra;
    }

    /**
     * Phase step (server): through the wall of phaseable blocks in front, to the first free two-high space at most
     * max_depth blocks in. Nothing happens unless the block right in front is phaseable and the way is all phaseable.
     */
    static void phase(Player player, BootState state, TBDataMaps.Phase phase) {
        if (state.phaseCooldown > 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Direction dir = player.getDirection();
        BlockPos feet = player.blockPosition();
        BlockPos first = feet.relative(dir);
        if (!phaseable(level, first, phase) && !phaseable(level, first.above(), phase)) {
            return;
        }
        for (int d = 1; d <= phase.maxDepth() + 1; d++) {
            BlockPos at = feet.relative(dir, d);
            boolean low = free(level, at);
            boolean high = free(level, at.above());
            if (low && high) {
                level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 24, 0.3, 0.6, 0.3, 0.2);
                player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                player.resetFallDistance();
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 24, 0.3, 0.6, 0.3, 0.05);
                level.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.4f);
                state.phaseCooldown = phase.cooldown();
                return;
            }
            if ((!low && !phaseable(level, at, phase)) || (!high && !phaseable(level, at.above(), phase))) {
                return;
            }
        }
    }

    private static boolean phaseable(Level level, BlockPos pos, TBDataMaps.Phase phase) {
        return level.getBlockState(pos).is(phase.blocks());
    }

    private static boolean free(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    // ---- Flora Ferox: growth, thorn catch, thorns -------------------------------------------------------------------

    private static BlockPos around(Player player, int radius, RandomSource rand) {
        return player.blockPosition().offset(rand.nextInt(2 * radius + 1) - radius, rand.nextInt(3) - 1, rand.nextInt(2 * radius + 1) - radius);
    }

    /** Bone-meal pulses on the tagged plants around your feet (crops, saplings: every Flora Ferox herb is a crop). */
    private static void grow(ServerLevel level, Player player, TBDataMaps.Growth growth) {
        RandomSource rand = player.getRandom();
        for (int i = 0; i < growth.attempts(); i++) {
            BlockPos pos = around(player, growth.radius(), rand);
            BlockState here = level.getBlockState(pos);
            if (here.is(growth.blocks()) && here.getBlock() instanceof BonemealableBlock plant && plant.isValidBonemealTarget(level, pos, here)
                    && rand.nextFloat() < growth.chance()) {
                plant.performBonemeal(level, rand, pos, here);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
            }
        }
    }

    /** A fall the briars catch: no damage, some durability, and everything nearby rooted. Returns true if caught. */
    static boolean thornCatch(Player player, ItemStack boots, BootStats stats, float distance) {
        TBDataMaps.ThornCatch thorn = stats.powers().thornCatch().orElse(null);
        if (thorn == null || distance <= thorn.minFall()) {
            return false;
        }
        if (player.level() instanceof ServerLevel level) {
            if (thorn.durability() > 0 && boots.isDamageableItem()) {
                boots.hurtAndBreak(thorn.durability(), player, EquipmentSlot.FEET);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(thorn.radius()), e -> e != player)) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, thorn.ticks(), thorn.amplifier()), player);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, e.getX(), e.getY() + 0.1, e.getZ(), 6, 0.3, 0.05, 0.3, 0);
            }
            level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, player.getX(), player.getY() + 0.2, player.getZ(), 16, 1.2, 0.1, 1.2, 0);
            level.playSound(null, player.blockPosition(), SoundEvents.SWEET_BERRY_BUSH_PLACE, SoundSource.PLAYERS, 1.0f, 0.7f);
        }
        return true;
    }

    /** Melee attackers take thorns damage. */
    static void thorns(ServerLevel level, Player player, BootStats stats, DamageSource source) {
        TBDataMaps.Thorns thorns = stats.powers().thorns().orElse(null);
        if (thorns == null || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || attacker == player) {
            return;
        }
        if (player.getRandom().nextFloat() < thorns.chance()) {
            attacker.hurtServer(level, level.damageSources().thorns(player), thorns.damage());
        }
    }

    // ---- Thaumaturge fluids: stomp (liquid death), purify (purifying fluid) -----------------------------------------

    /** Landing on mobs: they take the fall, withered; you bounce and the boots pay durability. Returns true if handled. */
    static boolean stomp(Player player, ItemStack boots, BootStats stats, float distance) {
        TBDataMaps.Stomp stomp = stats.powers().stomp().orElse(null);
        if (stomp == null || distance <= stomp.minFall()) {
            return false;
        }
        if (player.level() instanceof ServerLevel level) {
            AABB feet = player.getBoundingBox();
            AABB box = new AABB(feet.minX - stomp.radius(), feet.minY - 0.75, feet.minZ - stomp.radius(),
                    feet.maxX + stomp.radius(), feet.minY + 1.0, feet.maxZ + stomp.radius());
            float damage = Math.min(stomp.maxDamage(), distance * stomp.damagePerBlock());
            boolean hit = false;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
                e.hurtServer(level, level.damageSources().playerAttack(player), damage);
                if (stomp.witherTicks() > 0) {
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, stomp.witherTicks(), stomp.witherAmplifier()), player);
                }
                level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                hit = true;
            }
            if (hit) {
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x, stomp.bounce(), v.z);
                player.hurtMarked = true;
                level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5f, 1.6f);
            }
            if (stomp.durability() > 0 && boots.isDamageableItem()) {
                boots.hurtAndBreak(stomp.durability(), player, EquipmentSlot.FEET);
            }
        }
        return true;
    }

    /** Taint around your feet turns back, by the conversion list (block ids; blocks of absent mods are skipped). */
    private static void purifyBlocks(ServerLevel level, Player player, TBDataMaps.Purify purify) {
        RandomSource rand = player.getRandom();
        for (int i = 0; i < purify.attempts(); i++) {
            BlockPos pos = around(player, purify.radius(), rand);
            BlockState here = level.getBlockState(pos);
            if (here.isAir()) {
                continue;
            }
            for (TBDataMaps.Conversion c : purify.conversions()) {
                if (!BuiltInRegistries.BLOCK.containsKey(c.from()) || !BuiltInRegistries.BLOCK.containsKey(c.to())) {
                    continue;
                }
                Block from = BuiltInRegistries.BLOCK.getValue(c.from());
                if (here.is(from)) {
                    level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.getValue(c.to()).defaultBlockState());
                    level.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0.02);
                    break;
                }
            }
        }
    }

    // ---- other addons: air jumps, slow aura, ghost walk ---------------------------------------------------------------

    /** Hook set by the client: sends the air-jump notice to the server (this class never touches client classes). */
    public static Runnable airJumpNotice = () -> { };

    /** Client, before the local player moves: a fresh press of jump in midair spends an air jump. */
    public static void airJump(Player player, ItemStack boots, BootStats stats, BootState state, boolean jumpKey) {
        TBDataMaps.AirJumps jumps = stats.powers().airJumps().orElse(null);
        boolean pressed = jumpKey && !state.jumpHeld;
        state.jumpHeld = jumpKey;
        if (jumps == null || !pressed || player.onGround() || player.onClimbable() || player.isInWater() || player.getAbilities().flying
                || state.airJumpsUsed >= jumps.count() || !Energy.powered(boots)) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, jumps.power() * BootModes.of(boots).jump(), v.z);
        state.airJumpsUsed++;
        RandomSource rand = player.getRandom();
        for (int i = 0; i < 8; i++) {
            player.level().addParticle(ParticleTypes.REVERSE_PORTAL, player.getX() + rand.nextDouble() - 0.5, player.getY(), player.getZ() + rand.nextDouble() - 0.5, 0, -0.05, 0);
        }
        airJumpNotice.run();
    }

    /** Server, on the client's notice: count the jump and forget the fall so far. */
    public static void airJumpServer(Player player) {
        ItemStack boots = BootEvents.worn(player);
        BootStats stats = TBDataMaps.stats(boots);
        TBDataMaps.AirJumps jumps = stats == null ? null : stats.powers().airJumps().orElse(null);
        BootState state = player.getData(TBRegistry.STATE);
        if (jumps == null || state.airJumpsUsed >= jumps.count()) {
            return;
        }
        state.airJumpsUsed++;
        player.resetFallDistance();
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(), player.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
        }
    }

    private static void aura(ServerLevel level, Player player, TBDataMaps.Aura aura) {
        if (player.tickCount % aura.every() != 0) {
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(aura.radius()),
                e -> e != player && e.isAlive() && (!aura.hostileOnly() || e instanceof Enemy))) {
            for (TBDataMaps.EffectSpec spec : aura.effects()) {
                e.addEffect(new MobEffectInstance(spec.effect(), spec.duration(), spec.amplifier(), true, true), player);
            }
        }
    }

    private static void ghost(ServerLevel level, Player player, BootState state, TBDataMaps.Ghost ghost) {
        if (state.sneakTicks < ghost.sneakTicks() || player.tickCount % ghost.every() != 0) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ghost.every() + 10, 0, true, false));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ghost.radius()), m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.3, 0.05, 0.3, 0.01);
    }
}
