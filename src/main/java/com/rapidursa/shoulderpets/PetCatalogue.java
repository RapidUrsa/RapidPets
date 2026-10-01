package com.rapidursa.shoulderpets;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.NPCComposition;

/** Incremental cache catalogue. Only requested, visible cards load model thumbnails. */
final class PetCatalogue
{
    static final class Entry
    {
        final String species;
        final int npcId, level;
        Entry(String species, int npcId, int level)
        { this.species = species; this.npcId = npcId; this.level = level; }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> silhouettes = new ConcurrentHashMap<>();
    private final ArrayDeque<String> requests = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Map<String, Integer> retryAfter = new ConcurrentHashMap<>();
    private int[] ids;
    private int position, ticks;
    private volatile boolean complete;
    private int revision, indexFingerprint;
    private boolean needsSave;

    List<Entry> snapshot() { return new ArrayList<>(entries.values()); }
    boolean isComplete() { return complete; }
    BufferedImage silhouette(String species) { return silhouettes.get(species); }

    void restore(String saved)
    {
        if (saved == null || saved.length() > 5_000_000) return;
        try
        {
            String[] lines = saved.split("\n");
            String[] header = lines[0].split("\\|");
            if (header.length != 3 || !"1".equals(header[0])) return;
            int savedRevision = Integer.parseInt(header[1]);
            int savedFingerprint = Integer.parseInt(header[2]);
            Map<String, Entry> restored = new HashMap<>();
            for (int i = 1; i < lines.length; i++)
            {
                String[] fields = lines[i].split(":", 3);
                if (fields.length != 3) return;
                int id = Integer.parseInt(fields[0]), level = Integer.parseInt(fields[1]);
                String species = new String(Base64.getUrlDecoder().decode(fields[2]), StandardCharsets.UTF_8);
                if (id < 0 || level < 1 || species.isEmpty()) return;
                restored.put(species, new Entry(species, id, level));
            }
            if (restored.isEmpty()) return;
            entries.clear();
            entries.putAll(restored);
            revision = savedRevision;
            indexFingerprint = savedFingerprint;
            complete = true;
        }
        catch (IllegalArgumentException ex) { /* Rebuild a damaged or obsolete local catalogue. */ }
    }

    String consumeSave()
    {
        if (!needsSave) return null;
        needsSave = false;
        return "1|" + revision + "|" + indexFingerprint + "\n" + entries.values().stream()
            .sorted(java.util.Comparator.comparing(e -> e.species))
            .map(e -> e.npcId + ":" + e.level + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(e.species.getBytes(StandardCharsets.UTF_8)))
            .collect(Collectors.joining("\n"));
    }

    void request(String species)
    {
        if (!silhouettes.containsKey(species) && ticks >= retryAfter.getOrDefault(species, 0)
            && queued.add(species)) requests.add(species);
    }

    boolean tick(Client client)
    {
        ticks++;
        boolean changed = false;
        if (ids == null)
        {
            IndexDataBase cache = client.getIndexConfig();
            if (cache == null) return false;
            ids = cache.getFileIds(9); // NPC definitions archive.
            if (ids == null) return false;
            int currentRevision = client.getRevision();
            int fingerprint = Arrays.hashCode(ids);
            if (complete && revision == currentRevision && indexFingerprint == fingerprint)
                position = ids.length;
            else if (complete)
            {
                entries.clear();
                silhouettes.clear();
                requests.clear();
                queued.clear();
                retryAfter.clear();
                complete = false;
                changed = true;
            }
            revision = currentRevision;
            indexFingerprint = fingerprint;
        }
        long deadline = System.nanoTime() + 3_000_000;
        for (int batch = 0; position < ids.length && batch < 100; batch++)
        {
            NPCComposition npc = client.getNpcDefinition(ids[position++]);
            if (npc != null && eligible(npc))
            {
                String species = npc.getName().trim().toLowerCase(Locale.ROOT).replace(';', ' ');
                Entry previous = entries.get(species);
                // Match the existing one-unlock-per-name collection; use the lowest-level variant.
                if (previous == null || npc.getCombatLevel() < previous.level)
                {
                    entries.put(species, new Entry(species, npc.getId(), npc.getCombatLevel()));
                    silhouettes.remove(species);
                    changed = true;
                }
            }
            if (System.nanoTime() >= deadline) break;
        }
        if (!complete && position == ids.length) { complete = true; needsSave = true; changed = true; }
        for (int built = 0; built < 2 && !requests.isEmpty(); built++)
        {
            String species = requests.remove();
            queued.remove(species);
            Entry entry = entries.get(species);
            if (entry == null || silhouettes.containsKey(species)) continue;
            BufferedImage preview = PetPreview.build(client, entry.npcId);
            if (preview == null) { retryAfter.put(species, ticks + 10); continue; }
            silhouettes.put(species, toSilhouette(preview));
            changed = true;
        }
        return changed;
    }

    private static boolean eligible(NPCComposition npc)
    {
        String name = npc.getName();
        if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name)
            || npc.getCombatLevel() < 1 || npc.getModels() == null) return false;
        String[] actions = npc.getActions();
        if (actions != null) for (String action : actions)
            if ("Attack".equalsIgnoreCase(action)) return true;
        return false;
    }

    static BufferedImage toSilhouette(BufferedImage source)
    {
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            image.setRGB(x, y, (source.getRGB(x, y) & 0xff000000) | 0x666666);
        return image;
    }
}
