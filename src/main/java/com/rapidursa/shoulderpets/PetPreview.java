package com.rapidursa.shoulderpets;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;

/** Small static model thumbnails rendered offscreen without changing RuneLite's rasterizer. */
final class PetPreview
{
    private PetPreview() { }

    static BufferedImage build(Client client, int npcId)
    {
        NPCComposition npc = client.getNpcDefinition(npcId);
        if (npc == null || npc.getModels() == null) return null;
        List<ModelData> parts = new ArrayList<>();
        for (int id : npc.getModels())
        {
            if (id < 0) continue;
            ModelData part = client.loadModelData(id);
            if (part != null) parts.add(part);
            else return null; // Wait until all model parts are in the cache.
        }
        if (parts.isEmpty()) return null;
        ModelData data = parts.size() == 1 ? parts.get(0) : client.mergeModels(parts.toArray(new ModelData[0]));
        if (data == null) return null;
        short[] from = npc.getColorToReplace(), to = npc.getColorToReplaceWith();
        if (from != null && to != null)
        {
            data = data.cloneColors();
            for (int i = 0; i < Math.min(from.length, to.length); i++) data.recolor(from[i], to[i]);
        }
        Model model = data.light(64, 768, -50, -10, -50);
        return draw(model);
    }

    static BufferedImage draw(Model model)
    {
        int count = model.getVerticesCount();
        if (count == 0 || model.getFaceCount() == 0) return null;
        float[] vx = model.getVerticesX(), vy = model.getVerticesY(), vz = model.getVerticesZ();
        double[] x = new double[count], y = new double[count], depth = new double[count];
        double yaw = Math.toRadians(-25), pitch = Math.toRadians(12);
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++)
        {
            x[i] = vx[i] * Math.cos(yaw) + vz[i] * Math.sin(yaw);
            double z = vz[i] * Math.cos(yaw) - vx[i] * Math.sin(yaw);
            y[i] = vy[i] * Math.cos(pitch) - z * Math.sin(pitch);
            depth[i] = z * Math.cos(pitch) + vy[i] * Math.sin(pitch);
            minX = Math.min(minX, x[i]); maxX = Math.max(maxX, x[i]);
            minY = Math.min(minY, y[i]); maxY = Math.max(maxY, y[i]);
        }
        int[] a = model.getFaceIndices1(), b = model.getFaceIndices2(), c = model.getFaceIndices3();
        int[] firstColors = model.getFaceColors1(), secondColors = model.getFaceColors2(), thirdColors = model.getFaceColors3();
        byte[] transparency = model.getFaceTransparencies();
        List<Integer> faces = new ArrayList<>();
        for (int f = 0; f < model.getFaceCount(); f++) faces.add(f);
        faces.sort(Comparator.comparingDouble((Integer f) ->
            (depth[a[f]] + depth[b[f]] + depth[c[f]]) / 3).reversed());
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double scale = Math.min(56 / Math.max(1, maxX - minX), 56 / Math.max(1, maxY - minY));
            double centerX = (minX + maxX) / 2, centerY = (minY + maxY) / 2;
            for (int f : faces)
            {
                if (thirdColors[f] == -2) continue;
                int alpha = transparency == null ? 255 : 255 - (transparency[f] & 255);
                if (alpha == 0) continue;
                Color color = hsl(firstColors[f]);
                if (thirdColors[f] >= 0)
                {
                    Color other = hsl(secondColors[f]), last = hsl(thirdColors[f]);
                    color = new Color((color.getRed() + other.getRed() + last.getRed()) / 3,
                        (color.getGreen() + other.getGreen() + last.getGreen()) / 3,
                        (color.getBlue() + other.getBlue() + last.getBlue()) / 3);
                }
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                Polygon polygon = new Polygon();
                for (int vertex : new int[] { a[f], b[f], c[f] })
                    polygon.addPoint((int) Math.round(32 + (x[vertex] - centerX) * scale),
                        (int) Math.round(32 + (y[vertex] - centerY) * scale));
                g.fillPolygon(polygon);
            }
        }
        finally { g.dispose(); }
        return image;
    }

    private static Color hsl(int packed)
    {
        double hue = ((packed >> 10) & 63) / 64.0 + 1.0 / 128;
        double saturation = ((packed >> 7) & 7) / 8.0 + 1.0 / 16;
        double lightness = (packed & 127) / 128.0;
        double chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
        double h = hue * 6, x = chroma * (1 - Math.abs(h % 2 - 1));
        double r = 0, g = 0, b = 0;
        if (h < 1) { r = chroma; g = x; }
        else if (h < 2) { r = x; g = chroma; }
        else if (h < 3) { g = chroma; b = x; }
        else if (h < 4) { g = x; b = chroma; }
        else if (h < 5) { r = x; b = chroma; }
        else { r = chroma; b = x; }
        double m = lightness - chroma / 2;
        return new Color(channel(r + m), channel(g + m), channel(b + m));
    }

    private static int channel(double value) { return Math.max(0, Math.min(255, (int) Math.round(value * 255))); }
}
