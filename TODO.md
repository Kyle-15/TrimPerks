# TrimPerks: things to fix

Checked items were done in 0.2.0 (balance pass, matching-tier trim shade, CI and Modrinth/CurseForge publishing).

Found while answering balance questions on 2026-11-14 (after 0.1.4). Do these as one balance pass.

## Exploits
- [x] **Snout + Silk Touch gives double gold.** Mining Nether gold ore or gilded blackstone with a Snout pickaxe and
      Silk Touch drops the ore block *and* the bonus nuggets; the block can then be smelted or Fortuned later.
      Fix: no bonus nuggets when the tool has Silk Touch (`Effects.afterBreak`, case "snout").
- [x] **Wayfinder can pull in other players' items.** The magnet takes any item under 3 ticks old within 1.5 blocks
      of the broken block or killed mob, so someone else's drops landing there at the same moment come to you.
      Minor. Fix: only items from that block/mob (e.g. capture drops instead of scanning).

## PvP: effects that get around the PvP multiplier (general.pvp_multiplier)
- [x] **Shaper weapon reach** (`entity_interaction_range`) and **Vex weapon attack speed** (`attack_speed`) are
      attribute changes made with `power(name, null)`, so the 0.75 never applies. Shaper + Diamond gives +0.625 reach
      in PvP, probably the strongest PvP trim right now.
- [x] **Spire weapon** (+damage while falling = every crit) and **Dune weapon** (Slowness on every hit) aren't in
      `Perks.PVP`, so they're full strength against players.
- [x] **Flow armor dash** distance isn't PvP-scaled (it's movement, not a hit). Decide whether it should be.
- [x] Decide how to scale attribute effects for PvP when the target isn't known (always scale PvP patterns on
      servers with pvp=true? separate config value?).

## Materials: every material should be a real trade-off
- [x] **Diamond and Lapis are free upgrades** on any effect without a cooldown (their downsides, longer cooldown and
      XP cost, only apply to cooldown effects). Examples: Flow/Vex/Sentry pickaxes, every sword pick for PvP.
- [x] **Copper and Emerald are free on Ward weapon**: the Absorption is a fixed 2 hearts, so their −strength costs
      nothing.
- [x] **Netherite's +strength is wasted** on patterns with no strength number (Ward weapon, Wayfinder, Wild hoe,
      Tide/Spire/Silence tools, Wild/Snout/Silence armor, ...), so you only get the slowdown.
- [x] **Gold is only a downside** unless the effect gives a status effect (it only shares `Trims.buff`), e.g. Rib
      pickaxe + Gold = −25% and nothing back.
- [x] Resin's linger does nothing for patterns that only act at a single moment.
- [x] Idea: let strength change effect levels (Absorption hearts, Resistance level), give number-less patterns a
      small strength-scaled bonus, and give Diamond/Lapis a downside that applies to every effect.
- [x] After the pass, update the guide wording and redo the "best trim" advice (Flow + Diamond tops nearly every list now).

## Balance questions
- [x] **Ward armor is useless with Protection IV.** Prot IV on 4 pieces already blocks 64%, which equals
      `general.max_damage_reduction`, so Ward's Resistance adds nothing in Nethpot-style PvP. Rethink Ward (Absorption
      instead of Resistance?) or the cap.
- [x] Silence weapon backstab and Raiser weapon pop don't affect players. Fine, but say so in the guide.
- [x] Sentry on a Silk Touch pickaxe does nothing (Silk Touch ores drop no XP). Say so in the guide.

## Resource pack / visuals
- [x] Iron trim on iron tools and netherite trim on netherite tools are hard to see. Vanilla armor uses a darker
      shade when the trim matches the armor; do the same per tool tier (needs a select on the item, or per-tier models).
- [ ] Some 16×16 overlays look alike, especially on shovels and hoes. Hand-tune the weak ones.
- [x] Only gold has been previewed. Make a preview with every material on a few tools.
- [ ] Pack format (`min_format [97,0]`, `max_format [97,1]`) is unverified. Check that 26.3 doesn't call it "made for a
      different version".
- [ ] Trident has no overlay (3D in hand). Maybe overlay only in the GUI view.

## Not checked in-game yet
- [ ] Flint in a grindstone on unmodded clients (may flicker before landing in the slot).
- [ ] Whether breaking an armor stand counts as a kill.
- [ ] Cooldown bars, Eye double-tap sneak, Spire's small-fall rule, Bolt felling with a claim mod.

## Versions
- [ ] Minecraft 1.21 to 1.21.8. Needs cut-backs: no guide windows before 1.21.6 (dialogs), no tool-trim textures before
      1.21.4 (item model definitions), no jump-key input for Flow before 1.21.2.

## Smaller
- [ ] Cooldowns reset on a server restart (kept in memory only). Fine for now; could save them as player data.
- [x] No GitHub Actions build or Modrinth/CurseForge listing yet (TweakSwitch has both to copy from).
