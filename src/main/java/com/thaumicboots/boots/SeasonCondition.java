package com.thaumicboots.boots;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.LocalDate;
import java.util.List;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * {@code {"type": "thaumicboots:season", "months": [12]}}: true while the server's calendar month is one of these.
 * Checked when datapacks load, as the original checked for December at start-up (its Christmas recipe only existed
 * in December).
 */
public record SeasonCondition(List<Integer> months) implements ICondition {
    public static final MapCodec<SeasonCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 12).listOf().fieldOf("months").forGetter(SeasonCondition::months)
    ).apply(i, SeasonCondition::new));

    @Override
    public boolean test(IContext context) {
        return months.contains(LocalDate.now().getMonthValue());
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
