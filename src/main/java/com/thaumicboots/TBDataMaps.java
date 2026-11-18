package com.thaumicboots;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jspecify.annotations.Nullable;

/**
 * {@code data/<ns>/data_maps/item/boot_stats.json}: everything a boot does, one entry per item. The original kept
 * these numbers in each boot class's {@code setBootsData()}; tools/gen_data.py writes them out unchanged (energy is
 * FE: the original's EU times 8, the rate our Electro-Magic Tools port uses). Every field is written explicitly for
 * every boot, so the JSON, not the codec defaults, is the source of truth.
 */
public final class TBDataMaps {
    /** How the boot moves you. {@code speed} is added along your input each tick on the ground (negative slows). */
    public record Movement(float speed, double jump, float longRunning, boolean steady, float stepHeight, boolean omni,
                           boolean waterPenalty, boolean negateFall) {
        public static final Codec<Movement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("speed").forGetter(Movement::speed),
                Codec.DOUBLE.optionalFieldOf("jump", 0.0).forGetter(Movement::jump),
                Codec.FLOAT.optionalFieldOf("long_running", 0f).forGetter(Movement::longRunning),
                Codec.BOOL.optionalFieldOf("steady", false).forGetter(Movement::steady),
                Codec.FLOAT.optionalFieldOf("step_height", 1f).forGetter(Movement::stepHeight),
                Codec.BOOL.optionalFieldOf("omni", true).forGetter(Movement::omni),
                Codec.BOOL.optionalFieldOf("water_penalty", false).forGetter(Movement::waterPenalty),
                Codec.BOOL.optionalFieldOf("negate_fall", true).forGetter(Movement::negateFall)
        ).apply(i, Movement::new));
    }

    /**
     * The shared constants of the original's movement code (ItemBoots): the speed multiplier while flying or
     * hovering, in water, the air-control formula ({@code (air_factor / air_base_jump * jump - air_floor) * speed% +
     * air_floor}), the inertia damping, and the long-running ramp ({@code speed + run_ticks * ramp_per_tick *
     * long_running}, run ticks counting up to {@code run_ticks_max} while moving faster than {@code run_motion}).
     */
    public record Tuning(float flyingMultiplier, float waterMultiplier, float airBaseJump, float airFactor, float airFactorHover,
                         float airFloor, double inertiaDamping, float rampPerTick, int runTicksMax, double runMotion) {
        public static final Codec<Tuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("flying_multiplier").forGetter(Tuning::flyingMultiplier),
                Codec.FLOAT.fieldOf("water_multiplier").forGetter(Tuning::waterMultiplier),
                Codec.FLOAT.fieldOf("air_base_jump").forGetter(Tuning::airBaseJump),
                Codec.FLOAT.fieldOf("air_factor").forGetter(Tuning::airFactor),
                Codec.FLOAT.fieldOf("air_factor_hover").forGetter(Tuning::airFactorHover),
                Codec.FLOAT.fieldOf("air_floor").forGetter(Tuning::airFloor),
                Codec.DOUBLE.fieldOf("inertia_damping").forGetter(Tuning::inertiaDamping),
                Codec.FLOAT.fieldOf("ramp_per_tick").forGetter(Tuning::rampPerTick),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("run_ticks_max").forGetter(Tuning::runTicksMax),
                Codec.DOUBLE.fieldOf("run_motion").forGetter(Tuning::runMotion)
        ).apply(i, Tuning::new));
    }

    /** Thaumaturge gear numbers: vis discount (%), runic charge, warp, the original's tier, self repair interval. */
    public record Gear(int visDiscount, int runicCharge, int warp, int tier, int selfRepairTicks) {
        public static final Codec<Gear> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("vis_discount", 0).forGetter(Gear::visDiscount),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("runic_charge", 0).forGetter(Gear::runicCharge),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("warp", 0).forGetter(Gear::warp),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("tier", 0).forGetter(Gear::tier),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("self_repair_ticks", 0).forGetter(Gear::selfRepairTicks)
        ).apply(i, Gear::new));
    }

    /**
     * Electric boots (the original's IC2 side): FE capacity and charge rate, FE per point of damage absorbed, and the
     * absorption: {@code base_absorption * absorption} of each hit, as long as the charge lasts. Uncharged boots
     * give no speed and no jump.
     */
    public record Energy(int capacity, int transfer, int perDamage, double absorption, double baseAbsorption) {
        public static final Codec<Energy> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("capacity").forGetter(Energy::capacity),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("transfer").forGetter(Energy::transfer),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("per_damage").forGetter(Energy::perDamage),
                Codec.DOUBLE.fieldOf("absorption").forGetter(Energy::absorption),
                Codec.DOUBLE.fieldOf("base_absorption").forGetter(Energy::baseAbsorption)
        ).apply(i, Energy::new));
    }

    /**
     * Electric comet falls: no damage up to {@code min_height}; past it the fall is cancelled if the boots can pay
     * {@code per_damage * (d - min_height)} FE, where d is the distance, times {@code distance_multiplier} beyond
     * {@code min_distance}.
     */
    public record CometFall(float minHeight, double minDistance, float distanceMultiplier) {
        public static final Codec<CometFall> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("min_height").forGetter(CometFall::minHeight),
                Codec.DOUBLE.fieldOf("min_distance").forGetter(CometFall::minDistance),
                Codec.FLOAT.fieldOf("distance_multiplier").forGetter(CometFall::distanceMultiplier)
        ).apply(i, CometFall::new));
    }

    /** Comet ice: still water one block below, in a square of {@code radius}, nearer than {@code reach} (x + z). */
    public record Frost(int radius, int reach) {
        public static final Codec<Frost> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("radius").forGetter(Frost::radius),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("reach").forGetter(Frost::reach)
        ).apply(i, Frost::new));
    }

    /**
     * Meteor: crouch in midair after {@code air_ticks} to slam down ({@code pull} per tick); landing bursts with power
     * 1, 2, 3 after more than {@code steps} ticks of slamming. A crouch-jump dashes the way you look:
     * {@code look * dash * dash_forward} sideways and {@code max(look.y, |x + z|) * dash / dash_up} up.
     */
    public record Meteor(int airTicks, List<Integer> steps, double pull, double dash, double dashForward, double dashUp) {
        public static final Codec<Meteor> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("air_ticks").forGetter(Meteor::airTicks),
                ExtraCodecs.NON_NEGATIVE_INT.listOf().fieldOf("steps").forGetter(Meteor::steps),
                Codec.DOUBLE.fieldOf("pull").forGetter(Meteor::pull),
                Codec.DOUBLE.fieldOf("dash").forGetter(Meteor::dash),
                Codec.DOUBLE.fieldOf("dash_forward").forGetter(Meteor::dashForward),
                Codec.DOUBLE.fieldOf("dash_up").forGetter(Meteor::dashUp)
        ).apply(i, Meteor::new));
    }

    /** Voidwalker ward: the fraction of magic, fire and explosion damage the boots take off (the original's armor/80). */
    public record Ward(float magic, float fire, float explosion) {
        public static final Codec<Ward> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("magic").forGetter(Ward::magic),
                Codec.FLOAT.fieldOf("fire").forGetter(Ward::fire),
                Codec.FLOAT.fieldOf("explosion").forGetter(Ward::explosion)
        ).apply(i, Ward::new));
    }

    private static final Codec<SimpleParticleType> SIMPLE_PARTICLE = BuiltInRegistries.PARTICLE_TYPE.byNameCodec().comapFlatMap(
            t -> t instanceof SimpleParticleType s ? com.mojang.serialization.DataResult.success(s)
                    : com.mojang.serialization.DataResult.error(() -> "not a simple particle: " + BuiltInRegistries.PARTICLE_TYPE.getKey(t)),
            s -> (ParticleType<?>) s);

    /**
     * A particle trail: one of {@code particle}, on average every {@code every} ticks, while moving faster than
     * {@code min_motion} (vertical motion counted at {@code vertical_weight}), or always while in the air if
     * {@code airborne}; never while wet unless {@code in_water}. {@code while_smashing} trails show only during a
     * meteor slam, around the whole body.
     */
    public record Trail(SimpleParticleType particle, int every, double minMotion, double verticalWeight, boolean airborne, boolean inWater,
                        boolean whileSmashing) {
        public static final Codec<Trail> CODEC = RecordCodecBuilder.create(i -> i.group(
                SIMPLE_PARTICLE.fieldOf("particle").forGetter(Trail::particle),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("every", 1).forGetter(Trail::every),
                Codec.DOUBLE.optionalFieldOf("min_motion", 0.1).forGetter(Trail::minMotion),
                Codec.DOUBLE.optionalFieldOf("vertical_weight", 1.0).forGetter(Trail::verticalWeight),
                Codec.BOOL.optionalFieldOf("airborne", false).forGetter(Trail::airborne),
                Codec.BOOL.optionalFieldOf("in_water", false).forGetter(Trail::inWater),
                Codec.BOOL.optionalFieldOf("while_smashing", false).forGetter(Trail::whileSmashing)
        ).apply(i, Trail::new));
    }

    /**
     * A seasonal boost (the original checked for December at start-up): during {@code months}, the vis discount and
     * long-running bonus become these, and the worn look switches to {@code equipment} if given.
     */
    public record Seasonal(List<Integer> months, int visDiscount, float longRunning, Optional<ResourceKey<EquipmentAsset>> equipment) {
        public static final Codec<Seasonal> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 12).listOf().fieldOf("months").forGetter(Seasonal::months),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("vis_discount").forGetter(Seasonal::visDiscount),
                Codec.FLOAT.fieldOf("long_running").forGetter(Seasonal::longRunning),
                ResourceKey.codec(EquipmentAssets.ROOT_ID).optionalFieldOf("equipment").forGetter(Seasonal::equipment)
        ).apply(i, Seasonal::new));

        public boolean active() {
            return months.contains(java.time.LocalDate.now().getMonthValue());
        }
    }

    // ---- powers of the new boots (not in the original; docs/COVERAGE.md "New content") ----------------------------

    /** Ground charge: up to {@code rate} FE a tick from the energy block under your feet (asked from its top side). */
    public record GroundCharge(int rate) {
        public static final Codec<GroundCharge> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("rate").forGetter(GroundCharge::rate)
        ).apply(i, GroundCharge::new));
    }

    /** Blast catch: {@code share} of each explosion's damage is taken off and stored as {@code fe_per_damage} FE a point. */
    public record BlastCatch(float share, int fePerDamage) {
        public static final Codec<BlastCatch> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("share").forGetter(BlastCatch::share),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("fe_per_damage").forGetter(BlastCatch::fePerDamage)
        ).apply(i, BlastCatch::new));
    }

    /** Overcharge: a meteor landing burst spends {@code fe_per_power} FE for each extra point of power, at most {@code max_power}. */
    public record Overcharge(int fePerPower, int maxPower) {
        public static final Codec<Overcharge> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("fe_per_power").forGetter(Overcharge::fePerPower),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("max_power").forGetter(Overcharge::maxPower)
        ).apply(i, Overcharge::new));
    }

    /**
     * Phase step: a crouch-jump facing {@code blocks} steps you through up to {@code max_depth} blocks of them to the
     * first free two-high space; then {@code cooldown} ticks of rest.
     */
    public record Phase(TagKey<Block> blocks, int maxDepth, int cooldown) {
        public static final Codec<Phase> CODEC = RecordCodecBuilder.create(i -> i.group(
                TagKey.codec(Registries.BLOCK).fieldOf("blocks").forGetter(Phase::blocks),
                ExtraCodecs.POSITIVE_INT.fieldOf("max_depth").forGetter(Phase::maxDepth),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("cooldown").forGetter(Phase::cooldown)
        ).apply(i, Phase::new));
    }

    /** Growth: while you walk, {@code attempts} random spots a tick within {@code radius}; {@code blocks} there get a bone-meal pulse at {@code chance}. */
    public record Growth(TagKey<Block> blocks, int radius, int attempts, float chance) {
        public static final Codec<Growth> CODEC = RecordCodecBuilder.create(i -> i.group(
                TagKey.codec(Registries.BLOCK).fieldOf("blocks").forGetter(Growth::blocks),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("radius").forGetter(Growth::radius),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("attempts").forGetter(Growth::attempts),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Growth::chance)
        ).apply(i, Growth::new));
    }

    /**
     * Thorn catch: a fall longer than {@code min_fall} does no damage and costs {@code durability}; every other living
     * thing within {@code radius} gets Slowness ({@code amplifier}) for {@code ticks}.
     */
    public record ThornCatch(float minFall, int durability, double radius, int ticks, int amplifier) {
        public static final Codec<ThornCatch> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("min_fall").forGetter(ThornCatch::minFall),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("durability").forGetter(ThornCatch::durability),
                Codec.DOUBLE.fieldOf("radius").forGetter(ThornCatch::radius),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ticks").forGetter(ThornCatch::ticks),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("amplifier").forGetter(ThornCatch::amplifier)
        ).apply(i, ThornCatch::new));
    }

    /** Thorns: a melee attacker takes {@code damage} (thorns damage) at {@code chance}. */
    public record Thorns(float damage, float chance) {
        public static final Codec<Thorns> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("damage").forGetter(Thorns::damage),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Thorns::chance)
        ).apply(i, Thorns::new));
    }

    /**
     * Stomp: landing after more than {@code min_fall} blocks on living things (within {@code radius} sideways) hurts
     * them {@code damage_per_block} a block, at most {@code max_damage}, withers them ({@code wither_amplifier},
     * {@code wither_ticks}) and bounces you {@code bounce} up; your own fall costs {@code durability} instead of health.
     */
    public record Stomp(float minFall, float damagePerBlock, float maxDamage, double radius, int witherTicks, int witherAmplifier,
                        double bounce, int durability) {
        public static final Codec<Stomp> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("min_fall").forGetter(Stomp::minFall),
                Codec.FLOAT.fieldOf("damage_per_block").forGetter(Stomp::damagePerBlock),
                Codec.FLOAT.fieldOf("max_damage").forGetter(Stomp::maxDamage),
                Codec.DOUBLE.fieldOf("radius").forGetter(Stomp::radius),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("wither_ticks").forGetter(Stomp::witherTicks),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("wither_amplifier").forGetter(Stomp::witherAmplifier),
                Codec.DOUBLE.fieldOf("bounce").forGetter(Stomp::bounce),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("durability").forGetter(Stomp::durability)
        ).apply(i, Stomp::new));
    }

    /** One purification: block {@code from} becomes block {@code to}. Ids, so blocks of absent mods are simply skipped. */
    public record Conversion(Identifier from, Identifier to) {
        public static final Codec<Conversion> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("from").forGetter(Conversion::from),
                Identifier.CODEC.fieldOf("to").forGetter(Conversion::to)
        ).apply(i, Conversion::new));
    }

    /**
     * Purify: while you walk, {@code attempts} random spots a tick within {@code radius} go through {@code conversions};
     * every {@code flux_every} ticks worn, {@code flux} is drained from the aura where you stand.
     */
    public record Purify(int radius, int attempts, List<Conversion> conversions, int fluxEvery, float flux) {
        public static final Codec<Purify> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("radius").forGetter(Purify::radius),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("attempts").forGetter(Purify::attempts),
                Conversion.CODEC.listOf().fieldOf("conversions").forGetter(Purify::conversions),
                ExtraCodecs.POSITIVE_INT.fieldOf("flux_every").forGetter(Purify::fluxEvery),
                Codec.FLOAT.fieldOf("flux").forGetter(Purify::flux)
        ).apply(i, Purify::new));
    }

    /** Air jumps: {@code count} extra jumps in midair, each setting your upward speed to {@code power}. */
    public record AirJumps(int count, double power) {
        public static final Codec<AirJumps> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("count").forGetter(AirJumps::count),
                Codec.DOUBLE.fieldOf("power").forGetter(AirJumps::power)
        ).apply(i, AirJumps::new));
    }

    /** A status effect handed out by an aura. */
    public record EffectSpec(Holder<MobEffect> effect, int duration, int amplifier) {
        public static final Codec<EffectSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(EffectSpec::effect),
                ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(EffectSpec::duration),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("amplifier").forGetter(EffectSpec::amplifier)
        ).apply(i, EffectSpec::new));
    }

    /** Aura: every {@code every} ticks, the effects go to living things within {@code radius} (only monsters if {@code hostile_only}). */
    public record Aura(double radius, int every, boolean hostileOnly, List<EffectSpec> effects) {
        public static final Codec<Aura> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("radius").forGetter(Aura::radius),
                ExtraCodecs.POSITIVE_INT.fieldOf("every").forGetter(Aura::every),
                Codec.BOOL.fieldOf("hostile_only").forGetter(Aura::hostileOnly),
                EffectSpec.CODEC.listOf().fieldOf("effects").forGetter(Aura::effects)
        ).apply(i, Aura::new));
    }

    /** Ghost walk: after {@code sneak_ticks} of sneaking, invisible, and mobs within {@code radius} drop you as a target (checked every {@code every} ticks). */
    public record Ghost(int sneakTicks, double radius, int every) {
        public static final Codec<Ghost> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("sneak_ticks").forGetter(Ghost::sneakTicks),
                Codec.DOUBLE.fieldOf("radius").forGetter(Ghost::radius),
                ExtraCodecs.POSITIVE_INT.fieldOf("every").forGetter(Ghost::every)
        ).apply(i, Ghost::new));
    }

    /** The new boots' powers, one optional object per power (kept apart so BootStats stays under the codec's field limit). */
    public record Powers(Optional<GroundCharge> groundCharge, Optional<BlastCatch> blastCatch, Optional<Overcharge> overcharge,
                         Optional<Phase> phase, Optional<Growth> growth, Optional<ThornCatch> thornCatch, Optional<Thorns> thorns,
                         Optional<Stomp> stomp, Optional<Purify> purify, Optional<AirJumps> airJumps, Optional<Aura> aura,
                         Optional<Ghost> ghost) {
        public static final Powers NONE = new Powers(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        public static final Codec<Powers> CODEC = RecordCodecBuilder.create(i -> i.group(
                GroundCharge.CODEC.optionalFieldOf("ground_charge").forGetter(Powers::groundCharge),
                BlastCatch.CODEC.optionalFieldOf("blast_catch").forGetter(Powers::blastCatch),
                Overcharge.CODEC.optionalFieldOf("overcharge").forGetter(Powers::overcharge),
                Phase.CODEC.optionalFieldOf("phase").forGetter(Powers::phase),
                Growth.CODEC.optionalFieldOf("growth").forGetter(Powers::growth),
                ThornCatch.CODEC.optionalFieldOf("thorn_catch").forGetter(Powers::thornCatch),
                Thorns.CODEC.optionalFieldOf("thorns").forGetter(Powers::thorns),
                Stomp.CODEC.optionalFieldOf("stomp").forGetter(Powers::stomp),
                Purify.CODEC.optionalFieldOf("purify").forGetter(Powers::purify),
                AirJumps.CODEC.optionalFieldOf("air_jumps").forGetter(Powers::airJumps),
                Aura.CODEC.optionalFieldOf("aura").forGetter(Powers::aura),
                Ghost.CODEC.optionalFieldOf("ghost").forGetter(Powers::ghost)
        ).apply(i, Powers::new));
    }

    public record BootStats(Movement movement, Tuning tuning, Gear gear, Optional<Energy> energy, Optional<CometFall> cometFall,
                            Optional<Frost> frost, Optional<Meteor> meteor, Optional<Ward> ward, List<Trail> trails,
                            Optional<Seasonal> seasonal, Powers powers) {
        public static final Codec<BootStats> CODEC = RecordCodecBuilder.create(i -> i.group(
                Movement.CODEC.fieldOf("movement").forGetter(BootStats::movement),
                Tuning.CODEC.fieldOf("tuning").forGetter(BootStats::tuning),
                Gear.CODEC.fieldOf("gear").forGetter(BootStats::gear),
                Energy.CODEC.optionalFieldOf("energy").forGetter(BootStats::energy),
                CometFall.CODEC.optionalFieldOf("comet_fall").forGetter(BootStats::cometFall),
                Frost.CODEC.optionalFieldOf("frost").forGetter(BootStats::frost),
                Meteor.CODEC.optionalFieldOf("meteor").forGetter(BootStats::meteor),
                Ward.CODEC.optionalFieldOf("ward").forGetter(BootStats::ward),
                Trail.CODEC.listOf().optionalFieldOf("trails", List.of()).forGetter(BootStats::trails),
                Seasonal.CODEC.optionalFieldOf("seasonal").forGetter(BootStats::seasonal),
                Powers.CODEC.optionalFieldOf("powers", Powers.NONE).forGetter(BootStats::powers)
        ).apply(i, BootStats::new));

        /** The season boost, when it is in season now. */
        public Optional<Seasonal> season() {
            return seasonal.filter(Seasonal::active);
        }

        public int visDiscount() {
            return season().map(Seasonal::visDiscount).orElse(gear.visDiscount());
        }

        public float longRunning() {
            return season().map(Seasonal::longRunning).orElse(movement.longRunning());
        }
    }

    public static final DataMapType<Item, BootStats> BOOT_STATS = DataMapType
            .builder(ThaumicBoots.id("boot_stats"), Registries.ITEM, BootStats.CODEC)
            .synced(BootStats.CODEC, false).build();

    public static @Nullable BootStats stats(Holder<Item> item) {
        return item.getData(BOOT_STATS);
    }

    public static @Nullable BootStats stats(ItemStack stack) {
        return stack.isEmpty() ? null : stats(stack.typeHolder());
    }

    static void register(RegisterDataMapTypesEvent event) {
        event.register(BOOT_STATS);
    }

    private TBDataMaps() {
    }
}
