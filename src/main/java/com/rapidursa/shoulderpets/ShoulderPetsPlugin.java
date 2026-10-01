package com.rapidursa.shoulderpets;

import com.google.inject.Provides;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.MenuAction;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ColorUtil;
import java.awt.image.BufferedImage;

@PluginDescriptor(name = "Rapid Pets", description = "Find and hatch virtual creature eggs")
public class ShoulderPetsPlugin extends Plugin
{
    private static final String GROUP = "rapidShoulderPets";
    private static final String OWNED_KEY = "owned";
    private static final String OWNED_SPECIES_KEY = "ownedSpecies";
    private static final String OWNED_LEVELS_KEY = "ownedLevels";
    private static final String OWNED_NPC_IDS_KEY = "ownedNpcIds";
    private static final String PERCHED_KEY = "perchedSpecies";
    private static final String EGG_KEY = "egg";
    private static final String PENDING_KEY = "pending";
    private static final String PENDING_LOCATION_KEY = "pendingLocation";

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private OverlayManager overlayManager;
    @Inject private ShoulderPetsOverlay overlay;
    @Inject private ShoulderPetsConfig config;
    @Inject private ClientToolbar toolbar;
    @Inject private ClientThread clientThread;

    // Keep old NPC IDs for migration; names make variants of one creature one species.
    private final Set<Integer> legacyOwnedIds = new HashSet<>();
    private final Set<String> ownedSpecies = new HashSet<>();
    private final Map<String, Integer> ownedLevels = new HashMap<>();
    private final Map<String, Integer> ownedNpcIds = new HashMap<>();
    private final Map<String, int[]> speciesAnimations = new HashMap<>();
    private final Map<String, BufferedImage> petPreviews = new ConcurrentHashMap<>();
    private PetCatalogue catalogue = new PetCatalogue();
    private boolean applyingPlacement;
    private int previewTicks;
    private EggState egg;
    private EggState pending;
    private String account;
    private long lastTickMillis;
    private ShoulderPetsPanel sidePanel;
    private NavigationButton navigation;
    private int sidebarRefreshTick;
    private GroundEgg groundEgg;
    private WorldPoint pendingLocation;
    private ShoulderPetRenderer shoulderRenderer;
    private String perchedSpecies;

    @Provides ShoulderPetsConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(ShoulderPetsConfig.class);
    }

    @Override protected void startUp()
    {
        catalogue.restore(configManager.getConfiguration(GROUP, "monsterCatalogue"));
        overlayManager.add(overlay);
        groundEgg = new GroundEgg(client);
        shoulderRenderer = new ShoulderPetRenderer(client, config);
        SwingUtilities.invokeLater(() ->
        {
            sidePanel = new ShoulderPetsPanel(this);
            BufferedImage icon = ShoulderPetsOverlay.createEggIcon(PetTier.LEGENDARY.color);
            navigation = NavigationButton.builder().tooltip("Rapid Pets").icon(icon)
                .priority(6).panel(sidePanel).build();
            toolbar.addNavigation(navigation);
            refreshPanel();
        });
        loadAccount();
    }

    @Override protected void shutDown()
    {
        saveCurrentPlacement();
        catalogue = new PetCatalogue();
        overlayManager.remove(overlay);
        if (groundEgg != null) groundEgg.clear();
        if (shoulderRenderer != null) shoulderRenderer.clear();
        groundEgg = null;
        shoulderRenderer = null;
        pendingLocation = null;
        perchedSpecies = null;
        NavigationButton oldNavigation = navigation;
        navigation = null;
        SwingUtilities.invokeLater(() ->
        {
            if (oldNavigation != null) toolbar.removeNavigation(oldNavigation);
            sidePanel = null;
        });
        account = null;
        lastTickMillis = 0;
        legacyOwnedIds.clear();
        ownedSpecies.clear();
        ownedLevels.clear();
        ownedNpcIds.clear();
        speciesAnimations.clear();
        petPreviews.clear();
        egg = null;
        pending = null;
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN) loadAccount();
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            if (groundEgg != null) groundEgg.clear();
            if (shoulderRenderer != null) shoulderRenderer.clear();
            lastTickMillis = 0;
            save(EGG_KEY, egg);
        }
        if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            saveCurrentPlacement();
            account = null;
            legacyOwnedIds.clear();
            ownedSpecies.clear();
            ownedLevels.clear();
            ownedNpcIds.clear();
            speciesAnimations.clear();
            petPreviews.clear();
            perchedSpecies = null;
            egg = null;
            pending = null;
            pendingLocation = null;
            refreshPanel();
        }
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        // LOGGED_IN can arrive before getLocalPlayer() is available. Keep trying
        // until the character name is populated, including after world hops.
        if (client.getGameState() == GameState.LOGGED_IN && account == null)
        {
            loadAccount();
        }
        if (client.getGameState() == GameState.LOGGED_IN && account != null)
        {
            if (catalogue.tick(client)) refreshPanel();
            String catalogueSave = catalogue.consumeSave();
            if (catalogueSave != null) configManager.setConfiguration(GROUP, "monsterCatalogue", catalogueSave);
            if (++previewTicks % 4 == 0) preparePreviews();
            for (NPC npc : client.getNpcs())
            {
                String key = speciesKey(npc.getName());
                if (ownedSpecies.contains(key) || (pending != null && key.equals(speciesKey(pending.speciesName)))
                    || (egg != null && key.equals(speciesKey(egg.speciesName)))) captureAnimations(npc);
            }
        }
        if (client.getGameState() == GameState.LOGGED_IN && pending != null && pendingLocation != null && groundEgg != null)
        {
            groundEgg.show(pendingLocation, PetTier.forLevel(pending.combatLevel));
        }
        if (client.getGameState() != GameState.LOGGED_IN || account == null || egg == null) return;
        long now = System.currentTimeMillis();
        if (lastTickMillis > 0 && egg.remainingMillis > 0)
        {
            // A long gap means the client disconnected or paused: never count offline time.
            long elapsed = now - lastTickMillis;
            if (elapsed > 0 && elapsed < 3000)
            {
                egg.remainingMillis = Math.max(0, egg.remainingMillis - elapsed);
                save(EGG_KEY, egg);
            }
        }
        lastTickMillis = now;
        if (++sidebarRefreshTick % 2 == 0) refreshPanel();
    }

    @Subscribe public void onBeforeRender(BeforeRender event)
    {
        if (shoulderRenderer == null) return;
        int id = perchedSpecies == null ? -1 : ownedNpcIds.getOrDefault(perchedSpecies, -1);
        int[] animations = speciesAnimations.get(perchedSpecies);
        if ("vorkath".equals(perchedSpecies))
        {
            // The rat shortcut cannot learn boss animations. Use the verified Rapid Mounts defaults.
            animations = new int[] { 7948, 7947 };
        }
        shoulderRenderer.update(client.getGameState() == GameState.LOGGED_IN ? client.getLocalPlayer() : null, id,
            animations == null ? -1 : animations[0], animations == null ? -1 : animations[1]);
    }

    private void loadAccount()
    {
        Player player = client.getLocalPlayer();
        if (player == null || player.getName() == null) return;
        String name = player.getName().toLowerCase(Locale.ROOT);
        if (name.equals(account)) return;
        account = name;
        legacyOwnedIds.clear();
        ownedSpecies.clear();
        ownedLevels.clear();
        ownedNpcIds.clear();
        speciesAnimations.clear();
        petPreviews.clear();
        String saved = configManager.getConfiguration(GROUP, account + "." + OWNED_KEY);
        if (saved != null) for (String id : saved.split(","))
        {
            try { legacyOwnedIds.add(Integer.parseInt(id)); } catch (NumberFormatException ignored) { }
        }
        String savedSpecies = configManager.getConfiguration(GROUP, account + "." + OWNED_SPECIES_KEY);
        if (savedSpecies != null) for (String species : savedSpecies.split(";"))
        {
            if (!species.isEmpty()) ownedSpecies.add(species);
        }
        String levels = configManager.getConfiguration(GROUP, account + "." + OWNED_LEVELS_KEY);
        if (levels != null) for (String entry : levels.split(";"))
        {
            int separator = entry.lastIndexOf('=');
            if (separator > 0) try
            {
                ownedLevels.put(entry.substring(0, separator), Integer.parseInt(entry.substring(separator + 1)));
            }
            catch (NumberFormatException ignored) { }
        }
        String savedNpcIds = configManager.getConfiguration(GROUP, account + "." + OWNED_NPC_IDS_KEY);
        if (savedNpcIds != null) for (String entry : savedNpcIds.split(";"))
        {
            int separator = entry.lastIndexOf('=');
            if (separator > 0) try
            {
                ownedNpcIds.put(entry.substring(0, separator), Integer.parseInt(entry.substring(separator + 1)));
            }
            catch (NumberFormatException ignored) { }
        }
        migrateLegacySpecies();
        String animationData = configManager.getConfiguration(GROUP, account + ".animations");
        if (animationData != null) for (String entry : animationData.split(";"))
        {
            int separator = entry.lastIndexOf('=');
            if (separator <= 0) continue;
            String[] values = entry.substring(separator + 1).split(",");
            if (values.length != 2) continue;
            try { speciesAnimations.put(entry.substring(0, separator),
                new int[] { Integer.parseInt(values[0]), Integer.parseInt(values[1]) }); }
            catch (NumberFormatException ignored) { }
        }
        perchedSpecies = configManager.getConfiguration(GROUP, account + "." + PERCHED_KEY);
        if (!ownedSpecies.contains(perchedSpecies)) perchedSpecies = null;
        if (perchedSpecies == null && ownedSpecies.size() == 1)
        {
            String previousPet = ownedSpecies.iterator().next();
            String key = placementKey(previousPet);
            if (configManager.getConfiguration(GROUP, key) == null)
                configManager.setConfiguration(GROUP, key, currentPlacement().encode());
        }
        if (perchedSpecies != null) applyPlacement(perchedSpecies, true);
        egg = read(EGG_KEY);
        pending = read(PENDING_KEY);
        pendingLocation = readPendingLocation();
        lastTickMillis = 0;
        // Persist migration immediately, so offline time does not count on the next login.
        if (egg != null) save(EGG_KEY, egg);
        // An egg rolled from another NPC variant before this fix must not undo an earlier hatch.
        if (egg != null && ownedSpecies.contains(speciesKey(egg.speciesName)))
        {
            egg = null;
            save(EGG_KEY, null);
        }
        if (pending != null && ownedSpecies.contains(speciesKey(pending.speciesName)))
        {
            pending = null;
            save(PENDING_KEY, null);
        }
        if (pending == null) clearPendingLocation();
        if (pending != null) eggMessage(pending, "Your virtual " + pending.speciesName + " egg is waiting. Pick it up on the ground or claim it from the sidebar.");
        else if (egg != null) eggMessage(egg, "Your " + egg.speciesName + " egg is incubating. Check the timer panel.");
        refreshPanel();
        preparePreviews();
    }

    private EggState read(String key)
    {
        String saved = configManager.getConfiguration(GROUP, account + "." + key);
        if (saved == null) return null;
        try { return EggState.decode(saved); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private void save(String key, EggState value)
    {
        if (account == null) return;
        if (value == null) configManager.unsetConfiguration(GROUP, account + "." + key);
        else configManager.setConfiguration(GROUP, account + "." + key, value.encode());
    }

    @Subscribe public void onActorDeath(ActorDeath event)
    {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC) || account == null || pending != null || egg != null) return;
        NPC npc = (NPC) actor;
        Player player = client.getLocalPlayer();
        // Only award a roll for the player's own target. Group kills need a later attribution pass.
        if (player == null || player.getInteracting() != npc || npc.getCombatLevel() < 1) return;
        int species = npc.getId();
        String name = npc.getName();
        int combatLevel = npc.getCombatLevel();
        PetTier tier = PetTier.forLevel(combatLevel);
        int denominator = tier.denominator;
        if (legacyOwnedIds.contains(species) || ownedSpecies.contains(speciesKey(name))
            || ThreadLocalRandom.current().nextInt(denominator) != 0) return;
        pending = new EggState(species, name == null ? "Creature" : name, combatLevel, 0);
        captureAnimations(npc);
        pendingLocation = npc.getWorldLocation();
        savePendingLocation();
        if (groundEgg != null) groundEgg.show(pendingLocation, tier);
        save(PENDING_KEY, pending);
        eggMessage(pending, "A virtual " + pending.speciesName + " egg appeared on the ground! Pick it up to start incubation.");
        refreshPanel();
    }

    void claim()
    {
        if (pending == null || egg != null) return;
        egg = new EggState(pending.speciesId, pending.speciesName, pending.combatLevel,
            PetTier.forLevel(pending.combatLevel).hatchTime.toMillis());
        lastTickMillis = System.currentTimeMillis();
        pending = null;
        clearPendingLocation();
        if (groundEgg != null) groundEgg.clear();
        save(PENDING_KEY, null);
        save(EGG_KEY, egg);
        eggMessage(egg, "You picked up the virtual " + egg.speciesName + " egg. The hatch timer has started.");
        refreshPanel();
    }

    void hatch()
    {
        if (egg == null || egg.remainingMillis > 0) return;
        eggMessage(egg, "Your " + egg.speciesName + " egg hatched! Added to your micro pet collection.");
        legacyOwnedIds.add(egg.speciesId);
        configManager.setConfiguration(GROUP, account + "." + OWNED_KEY,
            legacyOwnedIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
        ownedSpecies.add(speciesKey(egg.speciesName));
        ownedLevels.put(speciesKey(egg.speciesName), egg.combatLevel);
        ownedNpcIds.put(speciesKey(egg.speciesName), egg.speciesId);
        saveOwnedSpecies();
        saveOwnedLevels();
        saveOwnedNpcIds();
        preparePreviews();
        egg = null;
        save(EGG_KEY, null);
        lastTickMillis = 0;
        refreshPanel();
    }

    EggState getEgg() { return egg; }
    EggState getPending() { return pending; }
    int getOwnedCount() { return ownedSpecies.size(); }
    Set<String> getOwnedSpecies() { return Collections.unmodifiableSet(new HashSet<>(ownedSpecies)); }
    Map<String, Integer> getOwnedLevels() { return Collections.unmodifiableMap(new HashMap<>(ownedLevels)); }
    String getPerchedSpecies() { return perchedSpecies; }
    BufferedImage getPetPreview(String species) { return petPreviews.get(species); }

    java.util.List<PetCatalogue.Entry> getCatalogue() { return catalogue.snapshot(); }
    boolean isCatalogueComplete() { return catalogue.isComplete(); }
    BufferedImage getSilhouette(String species)
    {
        BufferedImage image = catalogue.silhouette(species);
        if (image == null) clientThread.invokeLater(() -> catalogue.request(species));
        return image;
    }

    void requestResetPlacement()
    {
        clientThread.invokeLater(() ->
        {
            if (perchedSpecies == null || account == null) return;
            configManager.setConfiguration(GROUP, placementKey(perchedSpecies), PetPlacement.defaults().encode());
            applyPlacement(perchedSpecies, false);
            if (shoulderRenderer != null) shoulderRenderer.clear();
            refreshPanel();
        });
    }

    private void preparePreviews()
    {
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : ownedNpcIds.entrySet())
        {
            if (petPreviews.containsKey(entry.getKey())) continue;
            BufferedImage preview = PetPreview.build(client, entry.getValue());
            if (preview != null)
            {
                petPreviews.put(entry.getKey(), preview);
                changed = true;
            }
        }
        if (changed) refreshPanel();
    }

    private String placementKey(String species)
    {
        return account + ".placement." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(species.getBytes(StandardCharsets.UTF_8));
    }

    private PetPlacement currentPlacement()
    {
        return new PetPlacement(config.shoulderSide(), config.shoulderHeight(), config.shoulderForward(), config.shoulderSize());
    }

    private void saveCurrentPlacement()
    {
        if (account != null && perchedSpecies != null)
            configManager.setConfiguration(GROUP, placementKey(perchedSpecies), currentPlacement().encode());
    }

    private void applyPlacement(String species, boolean migrateOldSettings)
    {
        PetPlacement saved = PetPlacement.decode(configManager.getConfiguration(GROUP, placementKey(species)));
        if (saved == null) saved = migrateOldSettings ? currentPlacement() : PetPlacement.defaults();
        configManager.setConfiguration(GROUP, placementKey(species), saved.encode());
        applyingPlacement = true;
        try
        {
            configManager.setConfiguration(GROUP, "shoulderSide", saved.sideways);
            configManager.setConfiguration(GROUP, "shoulderHeight", saved.height);
            configManager.setConfiguration(GROUP, "shoulderForward", saved.forward);
            configManager.setConfiguration(GROUP, "shoulderSize", saved.size);
        }
        finally { applyingPlacement = false; }
    }

    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if (!GROUP.equals(event.getGroup()) || applyingPlacement) return;
        String key = event.getKey();
        if (!"shoulderSide".equals(key) && !"shoulderHeight".equals(key)
            && !"shoulderForward".equals(key) && !"shoulderSize".equals(key)) return;
        String editedSpecies = perchedSpecies;
        PetPlacement editedPlacement = currentPlacement();
        String editedAccount = account;
        if (editedSpecies == null || editedAccount == null) return;
        String editedKey = placementKey(editedSpecies);
        clientThread.invokeLater(() ->
        {
            if (editedAccount.equals(account))
                configManager.setConfiguration(GROUP, editedKey, editedPlacement.encode());
        });
    }

    private void captureAnimations(NPC npc)
    {
        if (account == null || npc.getName() == null) return;
        String key = speciesKey(npc.getName());
        int idle = npc.getIdlePoseAnimation();
        int walk = npc.getWalkAnimation();
        int[] previous = speciesAnimations.get(key);
        // Keep the first useful definition; similarly named variants may have another skeleton.
        if (previous != null && previous[0] >= 0 && previous[1] >= 0) return;
        if (idle < 0 && walk < 0) return;
        if (previous != null)
        {
            if (idle < 0) idle = previous[0];
            if (walk < 0) walk = previous[1];
            if (idle == previous[0] && walk == previous[1]) return;
        }
        speciesAnimations.put(key, new int[] { idle, walk });
        configManager.setConfiguration(GROUP, account + ".animations", speciesAnimations.entrySet().stream()
            .sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + "=" + e.getValue()[0] + "," + e.getValue()[1])
            .collect(Collectors.joining(";")));
    }

    void requestPerch(String species) { clientThread.invokeLater(() -> perch(species)); }

    private void perch(String species)
    {
        if (!ownedSpecies.contains(species) || !ownedNpcIds.containsKey(species)) return;
        saveCurrentPlacement();
        perchedSpecies = species.equals(perchedSpecies) ? null : species;
        if (shoulderRenderer != null) shoulderRenderer.clear();
        if (perchedSpecies == null) configManager.unsetConfiguration(GROUP, account + "." + PERCHED_KEY);
        else
        {
            configManager.setConfiguration(GROUP, account + "." + PERCHED_KEY, perchedSpecies);
            applyPlacement(perchedSpecies, false);
        }
        refreshPanel();
    }

    void requestClaim() { clientThread.invokeLater(this::claim); }
    void requestHatch() { clientThread.invokeLater(this::hatch); }

    private void refreshPanel()
    {
        ShoulderPetsPanel current = sidePanel;
        if (current != null) SwingUtilities.invokeLater(current::refresh);
    }

    @Subscribe public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (pending == null || pendingLocation == null || event.getMenuEntry().getType() != MenuAction.WALK) return;
        Tile tile = client.getSelectedSceneTile();
        if (tile == null || !pendingLocation.equals(tile.getWorldLocation())) return;
        client.createMenuEntry(-1)
            .setOption("Pick up")
            .setTarget(ColorUtil.wrapWithColorTag(
                pending.speciesName + " egg (virtual)", PetTier.forLevel(pending.combatLevel).color))
            .setType(MenuAction.RUNELITE)
            .onClick(e -> claim());
    }

    private WorldPoint readPendingLocation()
    {
        String value = configManager.getConfiguration(GROUP, account + "." + PENDING_LOCATION_KEY);
        if (value == null) return null;
        String[] parts = value.split(":");
        if (parts.length != 3) return null;
        try { return new WorldPoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); }
        catch (NumberFormatException ex) { return null; }
    }

    private void savePendingLocation()
    {
        if (account != null && pendingLocation != null)
        {
            configManager.setConfiguration(GROUP, account + "." + PENDING_LOCATION_KEY,
                pendingLocation.getX() + ":" + pendingLocation.getY() + ":" + pendingLocation.getPlane());
        }
    }

    private void clearPendingLocation()
    {
        pendingLocation = null;
        if (account != null) configManager.unsetConfiguration(GROUP, account + "." + PENDING_LOCATION_KEY);
    }

    private static String speciesKey(String name)
    {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(';', ' ');
    }

    private void saveOwnedSpecies()
    {
        configManager.setConfiguration(GROUP, account + "." + OWNED_SPECIES_KEY,
            ownedSpecies.stream().sorted().collect(Collectors.joining(";")));
    }

    private void saveOwnedLevels()
    {
        configManager.setConfiguration(GROUP, account + "." + OWNED_LEVELS_KEY,
            ownedLevels.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(";")));
    }

    private void saveOwnedNpcIds()
    {
        configManager.setConfiguration(GROUP, account + "." + OWNED_NPC_IDS_KEY,
            ownedNpcIds.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(";")));
    }

    private void migrateLegacySpecies()
    {
        boolean changed = false;
        boolean levelsChanged = false;
        boolean idsChanged = false;
        for (int npcId : legacyOwnedIds)
        {
            NPCComposition composition = client.getNpcDefinition(npcId);
            if (composition != null && composition.getName() != null)
            {
                String key = speciesKey(composition.getName());
                changed |= ownedSpecies.add(key);
                if (!ownedLevels.containsKey(key))
                {
                    ownedLevels.put(key, composition.getCombatLevel());
                    levelsChanged = true;
                }
                if (!ownedNpcIds.containsKey(key))
                {
                    ownedNpcIds.put(key, npcId);
                    idsChanged = true;
                }
            }
        }
        if (changed) saveOwnedSpecies();
        if (levelsChanged) saveOwnedLevels();
        if (idsChanged) saveOwnedNpcIds();
    }

    @Subscribe public void onOverlayMenuClicked(OverlayMenuClicked event)
    {
        if (event.getOverlay() != overlay) return;
        String option = event.getEntry().getOption();
        if ("Claim egg".equals(option)) claim();
        else if ("Hatch egg".equals(option)) hatch();
    }

    private void eggMessage(EggState state, String text)
    {
        message(ColorUtil.wrapWithColorTag(text, PetTier.forLevel(state.combatLevel).color));
    }

    private void message(String text)
    {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "Rapid Pets", text, null);
    }
}
