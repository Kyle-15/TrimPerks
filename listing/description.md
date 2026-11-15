# TrimPerks

**Armor trims finally do something, and tools and weapons can be trimmed too.**
The **pattern** decides *what* a trim does; the **material** decides *how*. Every material is a trade-off, so there's
no single best trim, and server owners can tune every number in-game.

## Highlights
- **18 armor set bonuses:** wear 4 pieces with the same pattern. Dash with Flow, dodge hits with Vex, catch arrows with Sentry, shrug off a fall with Spire, and more.
- **Trimmed tools and weapons:** every pattern has a weapon effect and a mining-tool effect: longer reach, faster attacks, tree felling, ore sensing, drops that fly to you...
- **11 materials, each a real trade-off:** Redstone shortens cooldowns but also durations, Diamond is stronger with longer cooldowns, Gold shares Ward's Absorption with your team. A material only counts where both its upside and downside mean something.
- **Easy retrimming:** swap just the material at a smithing table (template slot empty, flat 3 levels), or remove a trim with **flint** at a grindstone. Enchantments are never touched.
- **Cooldown bars** at the top of the screen, which turn green with a ding when an effect is ready.
- **`/trimperks guide`** opens a window listing every effect with the server's live numbers.

## Built for PvP *and* survival
- New or changed trims take 60 seconds to settle, so nobody can swap builds mid-fight.
- PvP effects are scaled separately (`general.pvp_multiplier`), including reach, attack speed and dashes while another player is nearby.
- Trims never stack with an enchantment that does the same thing, and kill effects only count hostile mobs and players.
- Claim and protection mods are respected: tree felling and crop harvesting break each block the normal way.

## Server-side
Players **don't need the mod**: the windows, cooldown bars and effects all run on the server.
For the tool and weapon trim textures, players without the mod can use the included **resource pack**, which you
can set as the server resource pack. Players with the mod installed see the textures automatically.

## For server owners
- `/trimperks config` opens a settings window for ops (click any setting to edit it).
- `/trimperks config set <key> <value>`, `disable <name>`, `enable <name>`, `reload`; `/reload` also re-reads the config.
- Full guide: [OP_GUIDE.md](https://github.com/Kyle-15/TrimPerks/blob/main/OP_GUIDE.md)

Requires **Fabric API**. Minecraft **26.3**.
