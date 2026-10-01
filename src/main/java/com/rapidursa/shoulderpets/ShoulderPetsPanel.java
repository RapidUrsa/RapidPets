package com.rapidursa.shoulderpets;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class ShoulderPetsPanel extends PluginPanel
{
    private final ShoulderPetsPlugin plugin;
    private final JPanel content = new JPanel();
    private static final int PAGE_SIZE = 24;
    private final JTextField search = new JTextField();
    private final JComboBox<String> filter = new JComboBox<>(new String[] { "All pets", "Locked", "Hatched" });
    private final Map<PetTier, Integer> pages = new EnumMap<>(PetTier.class);
    private final Map<PetTier, Boolean> expanded = new EnumMap<>(PetTier.class);

    ShoulderPetsPanel(ShoulderPetsPlugin plugin)
    {
        super(true);
        this.plugin = plugin;
        setLayout(new java.awt.BorderLayout());
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel controls = new JPanel(new java.awt.BorderLayout(0, 4));
        search.setToolTipText("Search animal or monster names");
        controls.add(new JLabel("Search pets"), java.awt.BorderLayout.NORTH);
        controls.add(search, java.awt.BorderLayout.CENTER);
        controls.add(filter, java.awt.BorderLayout.SOUTH);
        add(controls, java.awt.BorderLayout.NORTH);
        add(content, java.awt.BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener()
        {
            private void changed() { pages.clear(); refresh(); }
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
        filter.addActionListener(e -> { pages.clear(); refresh(); });
        for (PetTier tier : PetTier.values()) expanded.put(tier, tier == PetTier.COMMON);
        refresh();
    }

    void refresh()
    {
        content.removeAll();
        JLabel title = new JLabel("Rapid Pets");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createRigidArea(new Dimension(0, 12)));

        JPanel incubator = new JPanel();
        incubator.setLayout(new BoxLayout(incubator, BoxLayout.Y_AXIS));
        incubator.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        incubator.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        incubator.setAlignmentX(LEFT_ALIGNMENT);
        JLabel heading = new JLabel("INCUBATOR");
        heading.setForeground(Color.WHITE);
        incubator.add(heading);
        EggState pending = plugin.getPending();
        EggState egg = plugin.getEgg();
        EggState active = pending != null ? pending : egg;
        if (active == null) incubator.add(label("No egg incubating"));
        else
        {
            PetTier tier = PetTier.forLevel(active.combatLevel);
            incubator.add(new JLabel(new ImageIcon(ShoulderPetsOverlay.createEggIcon(tier.color))));
            incubator.add(label(active.speciesName + " · " + tier.label()));
            if (pending != null)
            {
                JButton claim = new JButton("Claim egg");
                claim.addActionListener(e -> plugin.requestClaim());
                incubator.add(claim);
            }
            else
            {
                long total = Math.max(1, tier.hatchTime.toMillis());
                JProgressBar progress = new JProgressBar(0, 1000);
                progress.setValue((int) (1000L * (total - Math.min(total, egg.remainingMillis)) / total));
                incubator.add(progress);
                long seconds = Math.max(0, (egg.remainingMillis + 999) / 1000);
                incubator.add(label(String.format("%dh %02dm %02ds logged-in time remaining",
                    seconds / 3600, (seconds / 60) % 60, seconds % 60)));
                if (egg.remainingMillis == 0)
                {
                    JButton hatch = new JButton("Hatch egg");
                    hatch.addActionListener(e -> plugin.requestHatch());
                    incubator.add(hatch);
                }
            }
        }
        content.add(incubator);
        content.add(Box.createRigidArea(new Dimension(0, 15)));
        JLabel perched = new JLabel("ACTIVE PET · "
            + (plugin.getPerchedSpecies() == null ? "None" : plugin.getPerchedSpecies()));
        perched.setForeground(Color.WHITE);
        perched.setAlignmentX(LEFT_ALIGNMENT);
        content.add(perched);
        if (plugin.getPerchedSpecies() != null)
        {
            content.add(label("Settings edit this pet only"));
            JButton reset = new JButton("Reset placement");
            reset.setToolTipText("Restore position and size for the active pet");
            reset.setAlignmentX(LEFT_ALIGNMENT);
            reset.addActionListener(e -> plugin.requestResetPlacement());
            content.add(reset);
        }
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        JLabel collection = new JLabel("COLLECTION · " + plugin.getOwnedCount() + " hatched");
        collection.setForeground(Color.WHITE);
        collection.setAlignmentX(LEFT_ALIGNMENT);
        content.add(collection);
        if (!plugin.isCatalogueComplete()) content.add(label("Discovering monster catalogue…"));
        Map<String, Integer> levels = new HashMap<>();
        for (PetCatalogue.Entry entry : plugin.getCatalogue()) levels.put(entry.species, entry.level);
        levels.putAll(plugin.getOwnedLevels());
        Set<String> owned = plugin.getOwnedSpecies();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        int selectedFilter = filter.getSelectedIndex();
        for (PetTier tier : PetTier.values())
        {
            List<String> names = new ArrayList<>();
            int total = 0, hatched = 0;
            for (String name : levels.keySet())
            {
                if (PetTier.forLevel(levels.get(name)) != tier) continue;
                total++;
                boolean unlocked = owned.contains(name);
                if (unlocked) hatched++;
                if (!name.contains(query) || (selectedFilter == 1 && unlocked)
                    || (selectedFilter == 2 && !unlocked)) continue;
                names.add(name);
            }
            names.sort(Comparator.naturalOrder());
            boolean open = expanded.get(tier);
            JButton section = new JButton((open ? "▾ " : "▸ ") + tier.label() + "  " + hatched + "/" + total);
            section.setForeground(tier.color);
            section.setAlignmentX(LEFT_ALIGNMENT);
            section.addActionListener(e -> { expanded.put(tier, !expanded.get(tier)); refresh(); });
            content.add(section);
            if (open)
            {
                JPanel cards = new JPanel(new GridLayout(0, 3, 5, 5));
                cards.setBackground(ColorScheme.DARK_GRAY_COLOR);
                cards.setAlignmentX(LEFT_ALIGNMENT);
                if (names.isEmpty()) cards.add(label("No matching pets"));
                int pageCount = Math.max(1, (names.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                int page = Math.min(pages.getOrDefault(tier, 0), pageCount - 1);
                pages.put(tier, page);
                for (String name : names.subList(page * PAGE_SIZE, Math.min(names.size(), (page + 1) * PAGE_SIZE)))
                {
                    boolean unlocked = owned.contains(name);
                    boolean selected = name.equals(plugin.getPerchedSpecies());
                    String displayName = name.length() > 18 ? name.substring(0, 17) + "…" : name;
                    JButton card = new JButton("<html><center>" + displayName + "<br>"
                        + (!unlocked ? "Locked" : selected ? "Remove" : "Summon") + "</center></html>");
                    java.awt.image.BufferedImage preview = unlocked ? plugin.getPetPreview(name) : plugin.getSilhouette(name);
                    if (preview != null) card.setIcon(new ImageIcon(preview));
                    card.setHorizontalTextPosition(SwingConstants.CENTER);
                    card.setVerticalTextPosition(SwingConstants.BOTTOM);
                    card.setMargin(new Insets(3, 2, 3, 2));
                    card.setFont(card.getFont().deriveFont(10f));
                    card.setPreferredSize(new Dimension(70, 124));
                    card.setToolTipText(unlocked ? (selected ? "Remove " : "Summon ") + name
                        : "Locked: hatch a " + name + " egg · " + tier.label() + " · level " + levels.get(name));
                    if (unlocked) card.addActionListener(e -> plugin.requestPerch(name));
                    else card.setFocusable(false);
                    card.setForeground(!unlocked ? Color.GRAY : selected ? Color.WHITE : tier.color);
                    card.setBorder(BorderFactory.createLineBorder(unlocked ? tier.color : Color.DARK_GRAY, 1));
                    cards.add(card);
                }
                content.add(cards);
                if (pageCount > 1)
                {
                    JPanel pager = new JPanel();
                    pager.setAlignmentX(LEFT_ALIGNMENT);
                    JButton previous = new JButton("‹");
                    JButton next = new JButton("›");
                    previous.setEnabled(page > 0);
                    next.setEnabled(page + 1 < pageCount);
                    previous.addActionListener(e -> { pages.put(tier, page - 1); refresh(); });
                    next.addActionListener(e -> { pages.put(tier, page + 1); refresh(); });
                    pager.add(previous);
                    pager.add(label((page + 1) + " / " + pageCount));
                    pager.add(next);
                    content.add(pager);
                }
            }
        }
        revalidate();
        repaint();
    }

    private static JLabel label(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(Color.LIGHT_GRAY);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }
}
