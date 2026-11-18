package com.thaumicboots.boots;

import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import com.leclowndu93150.thaumaturge.api.items.IWarpingGear;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.thaumicboots.TBDataMaps;
import com.thaumicboots.TBRegistry;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Every Thaumic Boots boot. The item itself only carries what Thaumaturge asks gear for (vis discount, warp), the
 * energy bar and the stack upkeep (self repair, runic charge, the seasonal look); the rest is {@link BootEvents}
 * and the client's {@code BootMotion}, all reading the {@code boot_stats} data map.
 */
public class BootItem extends Item implements IVisDiscountGear, IWarpingGear {
    public BootItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getVisDiscount(ItemStack stack) {
        TBDataMaps.BootStats stats = TBDataMaps.stats(stack);
        return stats == null ? 0 : stats.visDiscount();
    }

    @Override
    public int warp(ItemStack stack, LivingEntity entity) {
        TBDataMaps.BootStats stats = TBDataMaps.stats(stack);
        return stats == null ? 0 : stats.gear().warp();
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        TBDataMaps.BootStats stats = TBDataMaps.stats(stack);
        if (stats == null) {
            return;
        }
        TBDataMaps.Gear gear = stats.gear();
        // Voidwalker boots mend themselves, worn or carried.
        if (gear.selfRepairTicks() > 0 && stack.isDamaged() && entity.tickCount % gear.selfRepairTicks() == 0) {
            stack.setDamageValue(stack.getDamageValue() - 1);
        }
        // The boots' own runic charge, under whatever runic augments added on top.
        if (gear.runicCharge() > 0 && stack.getOrDefault(TTDataComponents.RUNIC_CHARGE.get(), 0) < gear.runicCharge()) {
            stack.set(TTDataComponents.RUNIC_CHARGE.get(), gear.runicCharge());
        }
        stats.seasonal().flatMap(TBDataMaps.Seasonal::equipment).ifPresent(seasonLook -> seasonalLook(stack, stats, seasonLook));
    }

    /** In season the worn look switches to the season's equipment asset, and back afterwards. */
    private static void seasonalLook(ItemStack stack, TBDataMaps.BootStats stats, ResourceKey<EquipmentAsset> seasonLook) {
        Equippable base = stack.getPrototype().get(DataComponents.EQUIPPABLE);
        Equippable now = stack.get(DataComponents.EQUIPPABLE);
        if (base == null || now == null) {
            return;
        }
        Optional<ResourceKey<EquipmentAsset>> want = stats.season().isPresent() ? Optional.of(seasonLook) : base.assetId();
        if (!now.assetId().equals(want)) {
            stack.set(DataComponents.EQUIPPABLE, new Equippable(base.slot(), base.equipSound(), want, base.cameraOverlay(), base.allowedEntities(),
                    base.dispensable(), base.swappable(), base.damageOnHurt(), base.equipOnInteract(), base.canBeSheared(), base.shearingSound()));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return Energy.isElectric(stack) || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Energy.isElectric(stack) ? Energy.barWidth(stack) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Energy.isElectric(stack) ? Energy.barColor() : super.getBarColor(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        TBDataMaps.BootStats stats = TBDataMaps.stats(stack);
        if (stats == null) {
            return;
        }
        if (!stats.powers().equals(TBDataMaps.Powers.NONE)) {
            // One line on what the new boots' power does (tools/gen_data.py writes the text).
            out.accept(Component.translatable("tooltip.thaumicboots.power." + stack.typeHolder().getRegisteredName().replace(':', '.'))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        int discount = stats.visDiscount();
        if (discount > 0) {
            out.accept(Component.translatable("tooltip.thaumicboots.vis_discount", discount).withStyle(ChatFormatting.DARK_PURPLE));
        }
        TBDataMaps.Energy energy = stats.energy().orElse(null);
        if (energy != null) {
            out.accept(Component.translatable("tooltip.thaumicboots.energy", String.format("%,d", Energy.stored(stack)),
                    String.format("%,d", energy.capacity())).withStyle(ChatFormatting.GRAY));
        }
        if (stack.has(TBRegistry.MODES.get())) {
            BootModes modes = BootModes.of(stack);
            out.accept(Component.translatable("tooltip.thaumicboots.modes", Math.round(modes.speed() * 100), Math.round(modes.jump() * 100))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
