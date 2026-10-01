package com.rapidursa.shoulderpets;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;

/** A micro pet sharing the player's tile, with a small local offset beside them. */
final class ShoulderPetRenderer
{
    private final Client client;
    private final ShoulderPetsConfig config;
    private MicroObject object;
    private int npcId = -1;
    private int size;
    private int animationId = Integer.MIN_VALUE;
    private int lastX, lastY;
    private long movingUntil;

    ShoulderPetRenderer(Client client, ShoulderPetsConfig config)
    {
        this.client = client;
        this.config = config;
    }

    void update(Player player, int selectedNpcId, int idleAnimation, int walkAnimation)
    {
        if (player == null || selectedNpcId < 0 || player.getLocalLocation() == null)
        {
            clear();
            return;
        }
        int newSize = Math.max(10, Math.min(100, config.shoulderSize()));
        if (object == null || npcId != selectedNpcId || size != newSize)
        {
            clear();
            object = build(selectedNpcId, newSize);
            if (object == null) return;
            npcId = selectedNpcId;
            size = newSize;
            lastX = player.getLocalLocation().getX();
            lastY = player.getLocalLocation().getY();
        }
        LocalPoint playerPoint = player.getLocalLocation();
        long now = System.currentTimeMillis();
        if (playerPoint.getX() != lastX || playerPoint.getY() != lastY) movingUntil = now + 180;
        lastX = playerPoint.getX();
        lastY = playerPoint.getY();
        int nextAnimation = now < movingUntil && walkAnimation >= 0 ? walkAnimation : idleAnimation;
        if (nextAnimation != animationId)
        {
            object.setAnimation(nextAnimation >= 0 ? client.loadAnimation(nextAnimation) : null);
            object.setShouldLoop(true);
            animationId = nextAnimation;
        }
        // Rotate the sideways/forward offsets with the player's facing direction.
        int orientation = player.getCurrentOrientation();
        double angle = orientation * Math.PI / 1024.0;
        int side = Math.max(-256, Math.min(256, config.shoulderSide()));
        int forward = Math.max(-256, Math.min(256, config.shoulderForward()));
        int dx = (int) Math.round(side * Math.cos(angle) + forward * Math.sin(angle));
        int dy = (int) Math.round(forward * Math.cos(angle) - side * Math.sin(angle));
        LocalPoint point = new LocalPoint(playerPoint.getX() + dx, playerPoint.getY() + dy, playerPoint.getWorldView());
        int plane = player.getWorldLocation().getPlane();
        object.setLocation(point, plane);
        object.setZ(Perspective.getTileHeight(client, playerPoint, plane)
            + Math.max(-256, Math.min(256, config.shoulderHeight())));
        object.setOrientation(orientation);
        if (!object.isActive()) object.setActive(true);
    }

    void clear()
    {
        if (object != null) object.setActive(false);
        object = null;
        npcId = -1;
        animationId = Integer.MIN_VALUE;
        movingUntil = 0;
    }

    private MicroObject build(int id, int size)
    {
        NPCComposition npc = client.getNpcDefinition(id);
        if (npc == null || npc.getModels() == null) return null;
        List<ModelData> parts = new ArrayList<>();
        for (int modelId : npc.getModels())
        {
            if (modelId < 0) continue;
            ModelData part = client.loadModelData(modelId);
            if (part != null) parts.add(part);
        }
        if (parts.isEmpty()) return null;
        ModelData data = parts.size() == 1 ? parts.get(0) : client.mergeModels(parts.toArray(new ModelData[0]));
        if (data == null) return null;
        short[] oldColors = npc.getColorToReplace();
        short[] newColors = npc.getColorToReplaceWith();
        if (oldColors != null && newColors != null)
        {
            data = data.cloneColors();
            for (int i = 0; i < Math.min(oldColors.length, newColors.length); i++)
                data.recolor(oldColors[i], newColors[i]);
        }
        // Animate the full-sized model first, then shrink it, so animation translations scale too.
        Model base = data.light(64, 768, -50, -10, -50);
        Model staticModel = data.cloneVertices().scale(Math.round(128f * size / 100),
            Math.round(128f * size / 100), Math.round(128f * size / 100)).light(64, 768, -50, -10, -50);
        MicroObject pet = new MicroObject(client, size, staticModel);
        pet.setModel(base);
        return pet;
    }

    private static final class MicroObject extends RuneLiteObject
    {
        private final int scale;
        private final Model staticModel;

        MicroObject(Client client, int size, Model staticModel)
        {
            super(client);
            this.scale = Math.round(128f * size / 100);
            this.staticModel = staticModel;
        }

        @Override public Model getModel()
        {
            if (getAnimationController() == null) return staticModel;
            Model model = super.getModel();
            if (model == null) return null;
            model.scale(scale, scale, scale);
            model.calculateBoundsCylinder();
            return model;
        }
    }
}
