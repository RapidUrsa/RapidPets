# Rapid Pets v1.0.0

Collect client-side creature eggs and hatch micro pets that run beside your player. Egg chat messages, ground labels and collection sections match the rarity colour. The screen timer appears only after pickup and counts only logged-in time.

| Combat level | Tier | Colour | Egg chance per eligible kill | Logged-in hatch time |
| --- | --- | --- | --- | --- |
| 1–49 | Common | Green | 1/150 | 1 hour |
| 50–99 | Uncommon | Blue | 1/125 | 3 hours |
| 100–199 | Rare | Purple | 1/100 | 10 hours |
| 200–499 | Epic | Red | 1/50 | 15 hours |
| 500+ | Legendary | Yellow | 1/25 | 24 hours |

The sidebar uses the yellow Legendary egg icon. Matching `icon.png` artwork is included at the repository root for the Plugin Hub listing.

## Collection

The sidebar builds a catalogue from the current game cache, using named combat NPC definitions with an Attack action and a model. The first scan runs in small batches while logged in. A completed catalogue is saved locally and shown immediately on subsequent starts. The game revision and NPC definition index are checked on login; changes trigger a fresh scan. A damaged saved catalogue is rebuilt. The saved list is shared across your accounts, while unlocks remain per account. Silhouettes still load as you browse. The catalogue includes quest and event variants; an entry does not guarantee that a monster is available to fight at your current progress. NPCs sharing the same normalized name count as one species, matching the existing unlock rules. Locked species use the lowest-level attackable variant for their catalogue tier; an unlocked pet uses the combat level of the actual egg you hatched.

Unhatched species show grey model silhouettes and a Locked label. Hatched species show colour model previews with Summon/Remove controls. Search by name, filter All/Locked/Hatched, expand rarity sections, and browse 24 cards per page. Tier headers show hatched/total counts. The catalogue and visible thumbnails populate gradually; models unavailable in the cache retry while their cards are visible. Only visible locked cards request models.

## Eggs and pets

Kill your current NPC target to roll. A pending or incubating egg blocks further rolls; once a species is hatched it stops dropping. Pick up the virtual ground egg through its client-side menu, or use Claim in the sidebar. After the timer expires, hatch through the sidebar or the screen timer menu. Rare eggs use a purple recoloured model; the other tiers use the corresponding green, blue, red and yellow Barbarian Assault egg models.

Placement and size are saved separately for each species and account. Position controls edit the active pet. Reset active pet placement restores sideways 42, height 0, forward 0 and size 20%. Position values allow -256 to 256; negative height lifts the pet. Idle/walk animations are learned from nearby NPCs and saved. Vorkath uses idle 7948 and walk 7947, matching the Rapid Mounts defaults.

All temporary drop-rate and hatch-time overrides, including the Lumbridge rat-to-Vorkath shortcut, have been removed. Existing unlocks, species settings and saved incubations carry over through the original configuration group. An incubation already started with a short test timer keeps its saved remaining time; new eggs use the table above.

## Testing

Check that the catalogue populates while logged in, locked cards show silhouettes and cannot summon, and search/filter/paging work. Confirm existing pets still summon with their saved positions. Tune one active pet, reset it, switch species and relog to check saved placement. Verify a new egg follows the rates and timers above.

Current limits: one pending/incubating egg at a time, local per-account collection storage, and kill attribution based on your current target. Group kills and targets changed before death may not roll. Not every cached monster model or animation has been verified in-game. This plugin adds client-only visuals and no server items, inventory items or rewards. Plugin Hub review remains necessary before release.

From inside the `rapid-pets` folder on Windows, launch with:

```bat
gradlew.bat runClient
```

To run tests and build the plugin JAR:

```bat
gradlew.bat clean test build
```

The JAR is written to `build/libs/`. Use Java 17 or newer. The included wrapper downloads Gradle when required.
