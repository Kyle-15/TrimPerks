# TrimPerks — design spec

Fabric mod: armor and tool trims give gameplay effects. **Pattern = what it does. Material = how.**
Server-side logic; client needed only to see tool trims (models) and tooltips/HUD.

Target: **Fabric 26.3 only** (the server's version). Used on **both PvP and survival** servers.
Status: all effects, retrimming, the settle timer, the guide windows and the commands are written (0.1.0). It boots on a
26.3 dev server with every mixin target loaded cleanly. **Not yet played in a client**, so gameplay still needs a
real playtest. Op docs: OP_GUIDE.md.

## Decisions (2026-11-14)
- PvP + survival: every effect stays on; effects flagged [PvP] are scaled by `pvpMultiplier` (default 0.75) when they act on or against a player.
- Retrim = **material-only swap** is the main way to change a trim. Flat **3 levels** every time, never compounding (unlike the anvil). The used-up material and the 60s settle timer already stop spamming.
- Trim removal: grindstone + flint.
- Every number lives in `config/trimperks.json` (keys like `armor.vex.chance`, defaults and descriptions in `Perks.java`).
  `/reload` re-reads it. Ops can use `/trimperks config get|set|reset <key>`, `disable|enable <name>` and `reload`.
- `/trimperks guide` (any player) opens a vanilla dialog window listing every effect with the current config values.
  Effect code must read numbers through `TrimPerks.config.get(key)` so the guide and the behavior always match.

## Core rules
1. Armor patterns need all 4 pieces with the **same pattern**. Materials may mix; each piece gives 25% of its material's modifier.
2. Tools: one trim each, full material modifier. Effect is active only while the tool is held in the main hand.
3. Rarer material is not stronger, just different (every material has a downside).
4. A trim never stacks with an enchantment that covers the same thing; the stronger one applies. The tooltip shows "(overridden by X)".
5. Damage-reduction cap: Protection + trims together can't exceed what Protection IV alone gives.
6. Nothing works while AFK (no passive farm/crop auras).
7. Every pattern and material can be turned off or tuned in the config (`config/trimperks.json`). PvP-relevant effects are flagged: Vex, Ward, Bolt, Flow.

## Materials (modify the pattern's strength / duration / cooldown / range)
| Material | Upside | Downside |
|---|---|---|
| Iron | baseline | none |
| Copper | +50% duration | −25% strength |
| Gold | allies within 8 blocks get 50% of the effect | −25% for you |
| Redstone | −40% cooldown | −40% duration |
| Lapis | +25% strength | each trigger costs 2 XP points (competes with Mending) |
| Amethyst | +50% range/radius | trigger plays a chime (alerts mobs, triggers sculk) |
| Emerald | 15% chance to skip a cooldown | −10% strength |
| Quartz | +50% in the Nether | −25% elsewhere |
| Diamond | +25% strength | +25% cooldown |
| Netherite | +25% strength and duration | armor: −2.5% move speed per piece; tools: −5% attack/mine speed |
| Resin | effect lingers 5s after unequipping or swapping away | −15% strength |

## Armor patterns (full-set bonus)
| Pattern | Effect |
|---|---|
| Sentry | 20% chance to catch arrows that hit you (the arrow goes to your inventory, no damage) |
| Dune | no suffocation from falling blocks; Speed I on sand/red sand |
| Coast | faster swimming (overlaps Depth Strider) |
| Wild | immune to cactus and berry bushes; leaves don't slow you |
| Ward | below 4 hearts: Resistance I for 3s, 90s cooldown |
| Eye | endermen don't aggro; sneak makes mobs within 16 blocks glow for 3s, 60s cooldown |
| Vex | 10% chance to dodge a melee hit, 20s cooldown [PvP] |
| Tide | +50% air (overlaps Respiration) |
| Snout | piglins neutral without gold |
| Rib | fire and Wither last half as long (overlaps Fire Protection) |
| Spire | first fall every 30s takes no damage, up to 10 blocks (overlaps Feather Falling) |
| Wayfinder | HUD arrow pointing to your last death point |
| Shaper | +1 block-placing reach |
| Silence | quieter footsteps; sculk detects you at half range |
| Raiser | Jump Boost I while sprinting |
| Host | your tamed pets within 16 blocks get slow regeneration |
| Flow | dash on double-tapping jump in midair, 10s cooldown [PvP] |
| Bolt | knocks melee attackers back, 15s cooldown [PvP] |

## Tool patterns (weapon = sword/axe/mace/trident/bow/crossbow; mining tool = pickaxe/shovel/hoe/axe)
| Pattern | Weapon | Mining tool |
|---|---|---|
| Sentry | +20% damage vs illagers | ores drop +25% XP |
| Dune | sweep hits give Slowness I for 1s | faster on sand/gravel |
| Coast | +20% damage vs aquatic mobs (overlaps Impaling) | fishing rod: faster lure |
| Wild | crits give Poison I for 2s | hoe: right-click harvests and replants |
| Ward | kills give 1 heart of Absorption, 30s cooldown | stops at 1 durability instead of breaking |
| Eye | crits make the target glow for 3s | sneak + use: shows the ore count within 5 blocks (count only), 120s cooldown |
| Vex | +10% attack speed [PvP] | +10% mining speed at light level ≤7 |
| Tide | extra knockback in water | full mining speed while swimming |
| Snout | piglins you hit don't alert others | more gold from Nether gold ore and gilded blackstone |
| Rib | crits set the target on fire for 2s (overlaps Fire Aspect) | faster on Nether blocks |
| Spire | +10% damage while falling (not maces) | no mining penalty while airborne |
| Wayfinder | mob drops fly to you | block drops fly to you |
| Shaper | +0.5 attack reach | +1 mining reach |
| Silence | +20% damage from behind on mobs not targeting you | mining makes no sculk vibrations |
| Raiser | hits pop light mobs upward | hoe: bonemeal one crop, 30s cooldown |
| Host | your pets deal +20% to what you hit | your kills heal nearby pets 2 hearts |
| Flow | +15% attack speed for 3s after a sprint hit | chain-mining builds +5% speed per block (max +25%, resets after 3s) |
| Bolt | every 5th hit sparks 2 damage to a nearby mob | axe: fells the whole tree, 1 durability per log, max 64 logs |

## Retrimming
| Action | Where | Cost | Lost |
|---|---|---|---|
| New pattern | smithing: item + template + material | template and material | old trim |
| Material only | smithing: item + material, empty template slot | material and 3 XP levels | old material |
| Remove trim | grindstone: item + **flint** in the 2nd slot | free, small XP back | template and material (no refund) |

- Grindstone with the item alone works like vanilla: removes enchantments, keeps the trim.
- A new or changed trim stays inactive for **60s** while it "settles" (countdown in the tooltip), so nobody can swap builds mid-fight.

## Interaction with enchantments
- Trimming or retrimming keeps enchantments. Enchanting keeps trims.
- Trims don't add to the anvil's prior-work penalty.
- Anvil combine: the left item's trim is kept and the right item's trim is lost (show a warning).
- Curse of Vanishing destroys the trim with the item.

## Technical notes
- Trim is stored in the `minecraft:trim` data component. Allow it on tools by widening the smithing-trim recipe base (tag `trimperks:trimmable_tools` + vanilla trimmable armor). Verify the component survives on non-equippable items.
- Tool trim visuals: client item models using the `trim_material` select property, overlaying pattern textures per tool type (needs textures: 18 patterns × tool types, or one generic overlay per pattern to start).
- Per-player state: cooldowns, settle timers and last death point, stored in player attachments (Fabric Data Attachment API).
- Check effects each tick only on equipment change, with cached set state; cooldowns are tick counters.

## Build order (MVP first)
1. ~~Project skeleton, config loader~~ (done). Check in-game that the `data/trimperks/recipe/*_tool_trim.json` recipes actually trim a sword (unverified).
2. Material modifier framework, armor set detection, tooltip lines.
3. Tool trims through smithing, plus a generic tool overlay model.
4. Retrim: empty-template material swap, grindstone + flint removal, 60s settle timer.
5. Two patterns end-to-end to test feel: **Spire** (armor) and **Wayfinder** (tool).
6. Remaining patterns in batches; overlap/override rules; damage cap.
7. Release workflow (GitHub → Modrinth/CurseForge) like TweakSwitch.
