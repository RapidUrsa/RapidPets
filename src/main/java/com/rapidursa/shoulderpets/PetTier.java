package com.rapidursa.shoulderpets;

import java.awt.Color;
import java.time.Duration;

enum PetTier
{
    COMMON(1, 50, 150, 1, new Color(74, 172, 79)),
    UNCOMMON(50, 100, 125, 3, new Color(70, 145, 233)),
    RARE(100, 200, 100, 10, new Color(180, 95, 228)),
    EPIC(200, 500, 50, 15, new Color(217, 72, 64)),
    LEGENDARY(500, Integer.MAX_VALUE, 25, 24, new Color(244, 203, 76));

    final int minimum;
    final int maximumExclusive;
    final int denominator;
    final Duration hatchTime;
    final Color color;

    PetTier(int minimum, int maximumExclusive, int denominator, int hours, Color color)
    {
        this.minimum = minimum;
        this.maximumExclusive = maximumExclusive;
        this.denominator = denominator;
        this.hatchTime = Duration.ofHours(hours);
        this.color = color;
    }

    static PetTier forLevel(int combatLevel)
    {
        for (PetTier tier : values())
        {
            if (combatLevel >= tier.minimum && combatLevel < tier.maximumExclusive) return tier;
        }
        return COMMON;
    }

    String label()
    {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
