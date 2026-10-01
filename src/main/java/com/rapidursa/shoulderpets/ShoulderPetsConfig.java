package com.rapidursa.shoulderpets;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("rapidShoulderPets")
public interface ShoulderPetsConfig extends Config
{
    @ConfigItem(keyName = "showTimer", name = "Show egg timer", description = "Show the current egg and hatch countdown")
    default boolean showTimer() { return true; }

    @Range(min = -256, max = 256)
    @ConfigItem(keyName = "shoulderSide", name = "Pet sideways", description = "Move the active pet left/right; saved separately for this species")
    default int shoulderSide() { return 42; }

    @Range(min = -256, max = 256)
    @ConfigItem(keyName = "shoulderHeight", name = "Pet height", description = "Active pet height; negative is up; saved separately for this species")
    default int shoulderHeight() { return 0; }

    @Range(min = -256, max = 256)
    @ConfigItem(keyName = "shoulderForward", name = "Pet forward", description = "Move active pet forward/back; saved separately for this species")
    default int shoulderForward() { return 0; }

    @Range(min = 10, max = 100)
    @ConfigItem(keyName = "shoulderSize", name = "Pet size %", description = "Scale active pet, 10 to 100 percent; saved separately for this species")
    default int shoulderSize() { return 20; }
}
