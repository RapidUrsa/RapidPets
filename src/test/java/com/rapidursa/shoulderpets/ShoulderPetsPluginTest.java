package com.rapidursa.shoulderpets;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ShoulderPetsPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ShoulderPetsPlugin.class);
        RuneLite.main(args);
    }
}
