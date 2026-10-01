package com.rapidursa.shoulderpets;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** A purely local scene model. It never enters the server's ground-item list. */
final class GroundEgg
{
    private final Client client;
    private RuneLiteObject object;
    private WorldPoint location;

    GroundEgg(Client client) { this.client = client; }

    void show(WorldPoint where, PetTier tier)
    {
        if (where == null) return;
        if (object != null && where.equals(location)) return;
        clear();
        LocalPoint local = LocalPoint.fromWorld(client, where);
        if (local == null || where.getPlane() != client.getPlane()) return;
        ItemComposition item = client.getItemDefinition(itemId(tier));
        if (item == null) return;
        Model model;
        if (tier == PetTier.RARE)
        {
            ModelData data = client.loadModelData(item.getInventoryModel());
            if (data == null) return;
            data = data.cloneColors();
            short[] colors = data.getFaceColors();
            for (int i = 0; i < colors.length; i++)
            {
                int color = colors[i] & 0xffff;
                // Preserve shading and neutral details; tint coloured shell faces purple.
                if (((color >> 7) & 7) > 0)
                    colors[i] = (short) ((49 << 10) | (color & 1023));
            }
            model = data.light();
        }
        else model = client.loadModel(item.getInventoryModel());
        if (model == null) return;
        RuneLiteObject egg = client.createRuneLiteObject();
        egg.setModel(model);
        egg.setLocation(local, where.getPlane());
        egg.setActive(true);
        object = egg;
        location = where;
    }

    void clear()
    {
        if (object != null) object.setActive(false);
        object = null;
        location = null;
    }

    private static int itemId(PetTier tier)
    {
        switch (tier)
        {
            case UNCOMMON: return ItemID.BLUE_EGG;
            case RARE: return ItemID.BLUE_EGG;
            case EPIC: return ItemID.RED_EGG;
            case LEGENDARY: return ItemID.YELLOW_EGG;
            default: return ItemID.GREEN_EGG;
        }
    }
}
