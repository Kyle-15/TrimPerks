# Making the CurseForge page (by hand: CurseForge has no API for creating projects)

1. Go to https://authors.curseforge.com and choose **Create project** → Minecraft → **Mods**.
2. Fill in:
   - **Name:** TrimPerks
   - **Summary:** the `summary` line from `listing.json`
   - **Description:** paste `description.md` (switch the editor to Markdown first)
   - **Main category:** Armor, Tools, and Weapons. **Other categories:** Server Utility, Adventure and RPG
   - **License:** MIT
   - **Logo:** `icon.png`
   - **Source:** https://github.com/Kyle-15/TrimPerks, **Issues:** https://github.com/Kyle-15/TrimPerks/issues
3. After it's created, copy the **Project ID** (right side of the project page) and save it as the repository
   variable `CURSEFORGE_PROJECT`, or just tell Claude the number.
