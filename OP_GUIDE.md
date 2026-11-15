# TrimPerks: op guide

Running a server with TrimPerks (Fabric 26.3). Players don't need the mod installed: everything, including both
windows, runs on the server.

## Install
1. Put `trimperks-<version>+26.3.jar` and Fabric API in the server's `mods` folder.
2. Start the server once. It writes `config/trimperks.json` with every setting at its default.

## Tool and weapon trim textures
Vanilla has no trim textures for tools, so TrimPerks ships its own: 18 pattern overlays for swords, axes, pickaxes,
shovels, hoes (every tier), maces, bows, crossbows and fishing rods, tinted in the trim material's color.
Tridents get the effects but no overlay (in hand they're 3D models).

- **Players with the mod installed** see them automatically.
- **Players without the mod** need the resource pack. For 0.1.0, put this in `server.properties`:
  ```
  resource-pack=https://github.com/Kyle-15/TrimPerks/releases/download/v0.1.0/TrimPerks-Resources.zip
  resource-pack-sha1=340db6ff67b2b763bb24c4250eb34e52a052b008
  require-resource-pack=false
  ```
  For a newer release, use that release's `TrimPerks-Resources.zip` link and its sha1 (`shasum -a 1 TrimPerks-Resources.zip`).
- Without either, trimmed tools look normal. The effects still work, and the trim still shows in the tooltip.
- To change the art, edit `tools/gen_tool_trims.py` and run it with the path to the 26.3 `minecraft-client.jar`.
  Then run `./gradlew checkAssets` (checks every model with the game's own parser) and `./gradlew build`.

## Commands
Everything under `/trimperks config` needs op (permission level 2).

| Command | What it does |
|---|---|
| `/trimperks guide` | Player guide window: every effect with its current numbers. Anyone can use it. |
| `/trimperks bars on` / `off` | Shows or hides your own cooldown bars. Anyone can use it; it's remembered across restarts. |
| `/trimperks config` | Op settings window: every setting grouped by page; click one to put its `set` command in chat. Changed values show in yellow. |
| `/trimperks config set <key> <value>` | Changes one number right away and saves the file. Tab lists every key. |
| `/trimperks config get <key>` | Shows the current value and the default. |
| `/trimperks config reset <key>` | Puts one number back to its default. |
| `/trimperks config disable <name>` | Turns an effect off (see names below). |
| `/trimperks config enable <name>` | Turns it back on. |
| `/trimperks config reload` | Re-reads the file. Plain `/reload` does this too. |

Changes apply immediately. Players see the new numbers next time they open `/trimperks guide`.

## How settings are named
`<group>.<name>.<setting>`. For example:

- `general.pvp_multiplier`: server-wide rules
- `material.redstone.cooldown`: how a material changes a trim
- `armor.vex.chance`: an armor set bonus (all 4 pieces with the same pattern)
- `weapon.bolt.hits`: a pattern on a sword, axe, mace, trident, bow or crossbow
- `tool.eye.range`: a pattern on a pickaxe, shovel, hoe, axe or fishing rod (axes get both weapon and tool effects)

Units:
- Chances and percentages are fractions: `0.25` = 25%.
- Durations and cooldowns are seconds.
- Material `strength`, `duration`, `cooldown` and `range` are multipliers: `1` = no change, `1.25` = 25% more.

## Turning things off
- `disable vex` turns off all three Vex effects (armor, weapon, tool).
- `disable armor.vex` turns off only the armor set bonus.
- `disable netherite` turns off a material's modifiers; trims made with it then work like iron.

## General settings
| Key | Default | Meaning |
|---|---|---|
| `general.pvp_multiplier` | 0.75 | Strength of [PvP] effects when they hit or protect against a player. Survival vs mobs is unaffected. |
| `general.settle_seconds` | 60 | How long a new or changed trim stays inactive. Stops mid-fight swapping. |
| `general.material_swap_levels` | 3 | XP levels for a material-only retrim. Flat: it never grows with repeats. |
| `general.max_damage_reduction` | 0.64 | Protection + Ward's Resistance can never block more than this (0.64 = Protection IV). |
| `general.cooldown_bars` | 1 | Cooldown bars at the top of the screen. 0 turns them off for everyone. |

PvP-flagged patterns: Ward, Vex, Rib, Shaper, Silence, Flow, Bolt.

## Balancing recipes
- **Weaker in PvP only:** `set general.pvp_multiplier 0.5`
- **Vex dodges feel too lucky:** `set armor.vex.chance 0.05` or `set armor.vex.cooldown 30`
- **Tree felling too strong:** `set tool.bolt.max_logs 24`, or `disable tool.bolt`
- **Players swap materials too often:** `set general.material_swap_levels 5` or `set general.settle_seconds 120`
- **Ore counting feels like x-ray:** `set tool.eye.cooldown 300` or `set tool.eye.range 3`
- **Netherite too slow to be worth it:** `set material.netherite.armor_slow 0.015`

Change one thing at a time and give players a few days before changing it again.

## How it works (for answering player questions)
- **Applying a trim:** smithing table + trim template + trim material, as in vanilla. Tools work too.
- **Changing only the material:** smithing table with the trimmed item + a new material and an **empty template slot**.
  Costs `material_swap_levels` levels, and the old material is lost.
- **Removing a trim:** grindstone with the item + **flint**. One flint is used, the enchantments stay, no XP is given, and the template is lost.
- **Grindstone without flint:** removes enchantments as usual and keeps the trim.
- **Settling:** after any trim change the item shows "Trim settling" and does nothing for `settle_seconds`.
- **Enchantments:** never removed by trimming. A trim that overlaps an enchantment (Feather Falling, Depth Strider,
  Respiration, Fire Protection, Fire Aspect, Impaling) turns itself off while that enchantment is present.
- **Mixed materials on armor:** each piece gives 25% of its material's modifiers.
- **Cooldown bars:** when an effect with a cooldown fires, a boss bar fills up at the top of the screen (blue = armor,
  red = weapon, yellow = tool), then turns green with a ding when it's ready. Vanilla boss bars, so no mod needed.
- **Kill effects** (Ward weapon, Host tool) only count hostile mobs and players, not farm animals.
- **Resin:** keeps an effect working for a few seconds after the item is removed or swapped away.

## Working with other mods and packs
- **Claim and protection mods:** tree felling (Bolt axe) and crop harvesting (Wild hoe) break each block through the
  player's normal block breaking, so claims, spawn protection and adventure mode apply to every block.
  Bonemealing (Raiser hoe) respects spawn protection and adventure mode.
- **Other resource packs:** the TrimPerks pack replaces the item definition files (`assets/minecraft/items/`) of
  the tools it covers. Packs that only change tool *textures or models* work fine together with it. A pack that
  replaces those same item definitions (custom 3D tools, CIT-style packs) wins if it's loaded above TrimPerks, and
  then the overlays won't show on those tools. Put TrimPerks above it to keep the overlays.
- **Custom model data:** TrimPerks only sets custom model data on trimmed tools that have none, and never overwrites
  custom model data from another plugin or mod.
- **Players without the mod:** putting flint in a grindstone may flicker for a moment on their screen before it
  lands in the slot. The server decides, so it still works.

## Files
- `config/trimperks.json`: `values` holds every number, and `disabled` lists turned-off effects. Safe to edit by
  hand, then `/reload`. Unknown keys are dropped and missing ones are filled with defaults.
