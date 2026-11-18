package com.thaumicboots;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.thaumicboots.boots.BootItem;
import com.thaumicboots.boots.BootModes;
import com.thaumicboots.boots.BootState;
import com.thaumicboots.boots.Energy;
import com.thaumicboots.boots.SeasonCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Items, components, the per-player boot state and the season recipe condition. Every boot is registered whether or
 * not the mods it combines are present (so worlds keep their items); the creative tab only lists the boots whose
 * mods are loaded, and their recipes and research carry {@code neoforge:mod_loaded} conditions.
 */
public final class TBRegistry {
    private static final String MODID = ThaumicBoots.MODID;
    public static final String EMT = "electromagictools";
    public static final String TM = "taintedmagic";
    public static final String TX = "thaumicexploration";
    // Mods of the new boots (docs/COVERAGE.md, "New content"): only ids, never their classes.
    public static final String FF = "fieldframe";
    public static final String FF_ARCANA = "fieldframe_arcana";
    public static final String FF_FRAMES = "fieldframe_frameworks";
    public static final String FLORA = "floraferox";
    public static final String WG = "witchinggadgets";
    public static final String FORBIDDEN = "forsakenmagic";
    public static final String OCCULTISM = "occultism";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS = DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);

    /** Speed %, jump %, omni, step assist and inertia canceling, per pair of boots (the original's NBT modes). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BootModes>> MODES = COMPONENTS.registerComponentType("modes",
            b -> b.persistent(BootModes.CODEC).networkSynchronized(BootModes.STREAM_CODEC));
    /** Stored FE on electric boots; capacity and rates come from the boot_stats data map. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType("energy",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Run, air and slam ticks of the boots being worn; per side, not saved (the original kept them in the boots' NBT). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BootState>> STATE = ATTACHMENTS.register("boot_state",
            () -> AttachmentType.builder(BootState::new).build());

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<SeasonCondition>> SEASON = CONDITIONS.register("season",
            () -> SeasonCondition.CODEC);

    /** Mends the leather-and-cloth boots (the original repaired them with leather). */
    public static final TagKey<Item> REPAIRS_BOOTS = TagKey.create(Registries.ITEM, ThaumicBoots.id("repairs_boots"));

    /** One entry per item: the mods it needs to show in the creative tab. */
    private static final List<TabEntry> TAB_ITEMS = new ArrayList<>();
    private static final List<DeferredItem<BootItem>> ELECTRIC = new ArrayList<>();

    private record TabEntry(DeferredItem<? extends Item> item, List<String> mods, boolean electric) {
    }

    /**
     * Thaumcraft 4's special armour material, which every original boot used: durability factor 25 (325 for boots),
     * one point of armour on the boots, enchantability 25. One material per worn look.
     */
    private static ArmorMaterial material(String asset) {
        return new ArmorMaterial(25, Map.of(ArmorType.BOOTS, 1), 25, SoundEvents.ARMOR_EQUIP_LEATHER, 0, 0, REPAIRS_BOOTS, asset(asset));
    }

    public static ResourceKey<EquipmentAsset> asset(String name) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, ThaumicBoots.id(name));
    }

    /** Leather-and-cloth boots: durability, one armour point, repaired with leather. */
    private static DeferredItem<BootItem> boots(String name, String look, Rarity rarity, String... mods) {
        DeferredItem<BootItem> item = ITEMS.registerItem(name, BootItem::new,
                p -> p.humanoidArmor(material(look), ArmorType.BOOTS).rarity(rarity));
        TAB_ITEMS.add(new TabEntry(item, List.of(mods), false));
        return item;
    }

    /** Electric boots: no durability (hits cost FE instead, see the energy entry of boot_stats). */
    private static DeferredItem<BootItem> electric(String name, String look, Rarity rarity, String... mods) {
        DeferredItem<BootItem> item = ITEMS.registerItem(name, BootItem::new, p -> p.stacksTo(1).enchantable(25).rarity(rarity)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).setEquipSound(SoundEvents.ARMOR_EQUIP_IRON)
                        .setAsset(asset(look)).build()));
        TAB_ITEMS.add(new TabEntry(item, List.of(mods), true));
        ELECTRIC.add(item);
        return item;
    }

    // The Arcanium Lens (the original's "Thaumic Interfacer"): the catalyst of every combination infusion.
    public static final DeferredItem<Item> ARCANIUM_LENS = registerLens();

    // Always available: Thaumaturge's Boots of the Traveller is the only base they need.
    public static final DeferredItem<BootItem> SEASON_BOOTS = boots("season_boots", "season", Rarity.RARE);
    public static final DeferredItem<BootItem> CHRISTMAS_BOOTS = boots("christmas_boots", "christmas", Rarity.RARE);
    public static final DeferredItem<BootItem> SLOW_BOOTS = boots("slow_boots", "slow", Rarity.RARE);

    // Thaumic Exploration: Comet and Meteor blended.
    public static final DeferredItem<BootItem> COMET_METEOR_BOOTS = boots("comet_meteor_boots", "comet_meteor", Rarity.RARE, TX);
    public static final DeferredItem<BootItem> METEORIC_COMET_BOOTS = boots("meteoric_comet_boots", "meteoric_comet", Rarity.RARE, TX);

    // Thaumic Exploration + Tainted Magic.
    public static final DeferredItem<BootItem> VOID_COMET_BOOTS = boots("void_comet_boots", "void_comet", Rarity.EPIC, TX, TM);
    public static final DeferredItem<BootItem> VOID_METEOR_BOOTS = boots("void_meteor_boots", "void_meteor", Rarity.EPIC, TX, TM);

    // Electro-Magic Tools + Tainted Magic.
    public static final DeferredItem<BootItem> ELECTRIC_VOID_BOOTS = electric("electric_void_boots", "electric_void", Rarity.EPIC, EMT, TM);
    public static final DeferredItem<BootItem> NANO_VOID_BOOTS = electric("nano_void_boots", "nano_void", Rarity.EPIC, EMT, TM);
    public static final DeferredItem<BootItem> QUANTUM_VOID_BOOTS = electric("quantum_void_boots", "quantum_void", Rarity.EPIC, EMT, TM);

    // Electro-Magic Tools + Thaumic Exploration.
    public static final DeferredItem<BootItem> ELECTRIC_METEOR_BOOTS = electric("electric_meteor_boots", "electric_meteor", Rarity.RARE, EMT, TX);
    public static final DeferredItem<BootItem> NANO_METEOR_BOOTS = electric("nano_meteor_boots", "nano_meteor", Rarity.RARE, EMT, TX);
    public static final DeferredItem<BootItem> QUANTUM_METEOR_BOOTS = electric("quantum_meteor_boots", "quantum_meteor", Rarity.RARE, EMT, TX);
    public static final DeferredItem<BootItem> ELECTRIC_COMET_BOOTS = electric("electric_comet_boots", "electric_comet", Rarity.RARE, EMT, TX);
    public static final DeferredItem<BootItem> NANO_COMET_BOOTS = electric("nano_comet_boots", "nano_comet", Rarity.RARE, EMT, TX);
    public static final DeferredItem<BootItem> QUANTUM_COMET_BOOTS = electric("quantum_comet_boots", "quantum_comet", Rarity.RARE, EMT, TX);

    // New boots (2026-11-01). Fieldframe (+ Arcana, Frameworks).
    public static final DeferredItem<BootItem> GROUNDLINE_BOOTS = electric("groundline_boots", "groundline", Rarity.EPIC, FF, FF_ARCANA);
    public static final DeferredItem<BootItem> BLASTCATCH_BOOTS = electric("blastcatch_boots", "blastcatch", Rarity.EPIC, FF, FF_ARCANA, TX);
    public static final DeferredItem<BootItem> FRAMEWALKER_BOOTS = boots("framewalker_boots", "framewalker", Rarity.EPIC, FF_FRAMES, TM);
    // Flora Ferox.
    public static final DeferredItem<BootItem> VERDANT_BOOTS = boots("verdant_boots", "verdant", Rarity.RARE, FLORA);
    public static final DeferredItem<BootItem> BRIAR_BOOTS = boots("briar_boots", "briar", Rarity.RARE, FLORA);
    // Thaumaturge's fluids (no other mod needed).
    public static final DeferredItem<BootItem> GRIM_BOOTS = boots("grim_boots", "grim", Rarity.RARE);
    public static final DeferredItem<BootItem> LUSTRAL_BOOTS = boots("lustral_boots", "lustral", Rarity.RARE);
    // Other addons.
    public static final DeferredItem<BootItem> RIFT_BOOTS = boots("rift_boots", "rift", Rarity.EPIC, WG, TM, TX);
    public static final DeferredItem<BootItem> SLOTH_SIN_BOOTS = boots("sloth_sin_boots", "sloth_sin", Rarity.EPIC, FORBIDDEN);
    public static final DeferredItem<BootItem> SPIRITSTEP_BOOTS = boots("spiritstep_boots", "spiritstep", Rarity.RARE, OCCULTISM);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thaumicboots"))
            .icon(() -> new ItemStack(COMET_METEOR_BOOTS.get()))
            .displayItems((params, out) -> TAB_ITEMS.forEach(e -> {
                if (e.mods().stream().allMatch(ModList.get()::isLoaded)) {
                    out.accept(e.item().get());
                    if (e.electric()) {
                        out.accept(Energy.charged(e.item().get()));
                    }
                }
            }))
            .build());

    private static DeferredItem<Item> registerLens() {
        DeferredItem<Item> lens = ITEMS.registerItem("arcanium_lens", Item::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
        TAB_ITEMS.add(new TabEntry(lens, List.of(), false));
        return lens;
    }

    private TBRegistry() {
    }

    static void register(IEventBus bus) {
        ITEMS.register(bus);
        COMPONENTS.register(bus);
        ATTACHMENTS.register(bus);
        TABS.register(bus);
        CONDITIONS.register(bus);
        bus.addListener(TBRegistry::registerCapabilities);
    }

    /** FE in from any charger; the boots never give energy back (the original's provideEnergy was false). */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (DeferredItem<BootItem> item : ELECTRIC) {
            event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> Energy.handler(stack, access), item.get());
        }
    }
}
