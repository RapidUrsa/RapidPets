package com.rapidursa.shoulderpets;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PetTierTest
{
    @Test public void boundariesBelongToHigherTier()
    {
        assertEquals(PetTier.COMMON, PetTier.forLevel(49));
        assertEquals(PetTier.UNCOMMON, PetTier.forLevel(50));
        assertEquals(PetTier.RARE, PetTier.forLevel(100));
        assertEquals(PetTier.EPIC, PetTier.forLevel(200));
        assertEquals(PetTier.LEGENDARY, PetTier.forLevel(500));
    }

    @Test public void normalRatesAndLoggedInTimers()
    {
        int[] rates = { 150, 125, 100, 50, 25 };
        long[] hours = { 1, 3, 10, 15, 24 };
        PetTier[] tiers = PetTier.values();
        for (int i = 0; i < tiers.length; i++)
        {
            assertEquals(rates[i], tiers[i].denominator);
            assertEquals(hours[i], tiers[i].hatchTime.toHours());
        }
    }

    @Test public void savedRemainingTimeIsNotRecomputedFromWallClock()
    {
        EggState saved = new EggState(1, "Goblin", 2, 123456L);
        assertEquals(123456L, EggState.decode(saved.encode()).remainingMillis);
    }
}
