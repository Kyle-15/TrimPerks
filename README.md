# TrimPerks

A Fabric mod for Minecraft **26.3** that makes armor and tool trims do things.
**The pattern decides what a trim does; the material decides how.** Every material has a downside, so there's no
single best trim, and everything is tunable in-game.

- **Armor:** a full set of 4 pieces with the same pattern gives a set bonus (safe falls, arrow catching, dodging, dashes...).
- **Tools and weapons can be trimmed too.** Each of the 18 patterns has a weapon effect and a mining-tool effect, plus its own texture overlay.
- **Materials** change the numbers: Redstone shortens cooldowns but also durations, Gold shares buffs with allies, Lapis is stronger but costs XP, and so on.
- **Retrimming:** swap just the material at a smithing table with the template slot empty (flat 3 levels), or remove a trim with **flint** at a grindstone. Enchantments are never touched.
- **Balanced for PvP and survival:** new trims settle for 60s, PvP effects are scaled separately, and every material is a real trade-off.
- **Server-side:** players don't need the mod. `/trimperks guide` opens a window listing every effect with the live numbers.

## Install
1. Put the TrimPerks jar and [Fabric API](https://modrinth.com/mod/fabric-api) in your server's `mods` folder.
2. That's it: players without the mod are offered the tool-trim resource pack automatically when they join.

## Docs
- [OP_GUIDE.md](OP_GUIDE.md): commands, every setting, balancing tips, resource pack setup
- [DESIGN.md](DESIGN.md): the full design and the reasoning behind each rule

## Building
```
./gradlew build          # jar in build/libs
./gradlew checkAssets    # checks the tool trim item models with the game's own parser
python3 tools/gen_tool_trims.py <path to the 26.3 minecraft-client.jar>   # regenerate overlays + resource pack
```

MIT licensed.
