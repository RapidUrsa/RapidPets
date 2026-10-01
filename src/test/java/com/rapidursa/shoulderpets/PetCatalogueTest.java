package com.rapidursa.shoulderpets;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.NPCComposition;
import org.junit.Test;
import static org.junit.Assert.*;

public class PetCatalogueTest
{
    @Test public void catalogueGroupsVariantsAndExcludesNonCombatDefinitions()
    {
        Map<Integer, NPCComposition> definitions = new HashMap<>();
        definitions.put(0, npc(0, "Cow", 2, "Attack"));
        definitions.put(1, npc(1, "Cow", 5, "Attack"));
        definitions.put(2, npc(2, "Banker", 100, "Bank"));
        definitions.put(3, npc(3, "null", 2, "Attack"));
        definitions.put(4, npc(4, "Vorkath", 732, "Attack"));
        IndexDataBase cache = (IndexDataBase) Proxy.newProxyInstance(IndexDataBase.class.getClassLoader(),
            new Class<?>[] { IndexDataBase.class }, (object, method, args) ->
                method.getName().equals("getFileIds") ? new int[] { 0, 1, 2, 3, 4 } : null);
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] { Client.class },
            (object, method, args) -> method.getName().equals("getIndexConfig") ? cache
                : method.getName().equals("getNpcDefinition") ? definitions.get((Integer) args[0])
                : method.getName().equals("getRevision") ? 1 : null);
        PetCatalogue catalogue = new PetCatalogue();
        for (int i = 0; i < 10 && !catalogue.isComplete(); i++) catalogue.tick(client);
        assertTrue(catalogue.isComplete());
        assertEquals(2, catalogue.snapshot().size());
        PetCatalogue.Entry cow = catalogue.snapshot().stream().filter(e -> e.species.equals("cow")).findFirst().get();
        assertEquals(2, cow.level);
        assertEquals(0, cow.npcId);
    }

    @Test public void savedCatalogueRestoresWithoutScanning()
    {
        PetCatalogue catalogue = new PetCatalogue();
        String species = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("vorkath".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        catalogue.restore("1|1|32\n8061:732:" + species);
        assertTrue(catalogue.isComplete());
        assertEquals("vorkath", catalogue.snapshot().get(0).species);
        PetCatalogue damaged = new PetCatalogue();
        damaged.restore("1|bad|data");
        assertFalse(damaged.isComplete());
        assertTrue(damaged.snapshot().isEmpty());
    }

    @Test public void silhouetteKeepsTransparencyAndDoesNotAlterPreview()
    {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x80ff1122);
        BufferedImage silhouette = PetCatalogue.toSilhouette(source);
        assertEquals(0x80666666, silhouette.getRGB(0, 0));
        assertEquals(0, silhouette.getRGB(1, 0) >>> 24);
        assertEquals(0x80ff1122, source.getRGB(0, 0));
    }

    private static NPCComposition npc(int id, String name, int level, String action)
    {
        return (NPCComposition) Proxy.newProxyInstance(NPCComposition.class.getClassLoader(),
            new Class<?>[] { NPCComposition.class }, (object, method, args) ->
            {
                switch (method.getName())
                {
                    case "getId": return id;
                    case "getName": return name;
                    case "getCombatLevel": return level;
                    case "getModels": return new int[] { 1 };
                    case "getActions": return new String[] { action };
                    default: return null;
                }
            });
    }
}
