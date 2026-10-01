package com.rapidursa.shoulderpets;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Duration;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.api.MenuAction;

public class ShoulderPetsOverlay extends Overlay
{
    private final ShoulderPetsPlugin plugin;
    private final ShoulderPetsConfig config;
    private final PanelComponent panel = new PanelComponent();

    @Inject ShoulderPetsOverlay(ShoulderPetsPlugin plugin, ShoulderPetsConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Hatch egg", "Rapid Pets"));
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        if (!config.showTimer()) return null;
        EggState egg = plugin.getEgg();
        if (egg == null) return null;
        panel.getChildren().clear();
        PetTier tier = PetTier.forLevel(egg.combatLevel);
        panel.getChildren().add(new ImageComponent(createEggIcon(tier.color)));
        long remaining = Math.max(0, egg.remainingMillis);
        Duration time = Duration.ofMillis(remaining);
        String label = remaining == 0 ? "Ready! Right click to hatch" :
            String.format("%dh %02dm %02ds", time.toHours(), time.toMinutes() % 60, time.getSeconds() % 60);
        panel.getChildren().add(LineComponent.builder().left(egg.speciesName + " egg").leftColor(tier.color).build());
        panel.getChildren().add(LineComponent.builder().left(label).build());
        return panel.render(graphics);
    }

    static BufferedImage createEggIcon(Color shell)
    {
        BufferedImage icon = new BufferedImage(32, 38, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(75, 46, 25));
            g.fillOval(4, 5, 24, 32);
            g.setColor(shell);
            g.fillOval(6, 6, 20, 28);
            g.setColor(new Color(255, 255, 255, 115));
            g.fillOval(9, 9, 8, 12);
            g.setColor(new Color(35, 20, 20, 100));
            g.fillOval(19, 24, 3, 3);
        }
        finally { g.dispose(); }
        return icon;
    }
}
