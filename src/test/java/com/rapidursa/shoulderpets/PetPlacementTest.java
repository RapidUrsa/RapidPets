package com.rapidursa.shoulderpets;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PetPlacementTest
{
    @Test public void signedPlacementSurvivesSaving()
    {
        PetPlacement placement = PetPlacement.decode(new PetPlacement(-42, -12, -30, 24).encode());
        assertEquals(-42, placement.sideways);
        assertEquals(-12, placement.height);
        assertEquals(-30, placement.forward);
        assertEquals(24, placement.size);
    }

    @Test public void damagedSavedPlacementFallsBackSafely()
    {
        assertNull(PetPlacement.decode("42,broken,0,20"));
        assertNull(PetPlacement.decode("42,0,20"));
    }

    @Test public void savedValuesCannotExceedSupportedRanges()
    {
        PetPlacement placement = PetPlacement.decode("-1000,1000,1000,0");
        assertEquals(-256, placement.sideways);
        assertEquals(256, placement.height);
        assertEquals(256, placement.forward);
        assertEquals(10, placement.size);
    }
}
