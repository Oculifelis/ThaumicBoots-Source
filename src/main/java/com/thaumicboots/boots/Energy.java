package com.thaumicboots.boots;

import com.thaumicboots.TBDataMaps;
import com.thaumicboots.TBRegistry;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import org.jspecify.annotations.Nullable;

/** FE bookkeeping for electric boots: the stored amount is the {@code thaumicboots:energy} component. */
public final class Energy {
    private static final int BAR_COLOR = 0x3FA9F5;

    private Energy() {
    }

    public static TBDataMaps.@Nullable Energy spec(ItemStack stack) {
        TBDataMaps.BootStats stats = TBDataMaps.stats(stack);
        return stats == null ? null : stats.energy().orElse(null);
    }

    public static boolean isElectric(ItemStack stack) {
        return spec(stack) != null;
    }

    public static int stored(ItemStack stack) {
        return stack.getOrDefault(TBRegistry.ENERGY.get(), 0);
    }

    /** Electric boots with no charge left do nothing; boots that are not electric always work. */
    public static boolean powered(ItemStack stack) {
        return !isElectric(stack) || stored(stack) > 0;
    }

    /** Spends exactly {@code amount} FE if the boots have it. */
    public static boolean use(ItemStack stack, int amount) {
        int have = stored(stack);
        if (have < amount) {
            return false;
        }
        stack.set(TBRegistry.ENERGY.get(), have - amount);
        return true;
    }

    public static ItemStack charged(Item item) {
        ItemStack stack = new ItemStack(item);
        TBDataMaps.Energy spec = spec(stack);
        if (spec != null) {
            stack.set(TBRegistry.ENERGY.get(), spec.capacity());
        }
        return stack;
    }

    public static @Nullable EnergyHandler handler(ItemStack stack, ItemAccess access) {
        TBDataMaps.Energy spec = spec(stack);
        return spec == null ? null : new ItemAccessEnergyHandler(access, TBRegistry.ENERGY.get(), spec.capacity(), spec.transfer(), 0);
    }

    public static int barWidth(ItemStack stack) {
        TBDataMaps.Energy spec = spec(stack);
        return spec == null ? 0 : Math.round(13.0f * Mth.clamp(stored(stack) / (float) spec.capacity(), 0.0f, 1.0f));
    }

    public static int barColor() {
        return BAR_COLOR;
    }
}
