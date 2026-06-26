# PokeDemo Bridge Client (1.21.11)

This is a **stage-1 scaffold** for a dedicated client mod that bridges your Paper/Purpur `PokeDemo` plugin and Cobblemon assets.

What is implemented:

- Fabric **1.21.11** project scaffold.
- Custom payload protocol for a Bukkit/Paper plugin to talk to the Fabric client.
- Left-side **party HUD** bound to packet data from the server.
- Per-entity render state cache for carrier entities (wolves in your current design).
- Asset resolver that reads a per-species manifest and points at locally extracted Cobblemon assets.
- A server-side `plugin_patch` helper class that shows the exact payload JSON expected by the client.
- A local `tools/extract_cobblemon_assets.py` helper to copy the first demo species from the Cobblemon jar.

What is **not** finished yet:

- Actual Bedrock/Cobblemon geo model rendering over the wolf entity.
- Animation playback.
- Form/shiny/gender switching beyond manifest selection.
- Asset baking and renderer hijacking.

That next step is much larger, so this project keeps the difficult part honest instead of pretending it is done.

## Recommended path

1. Build this mod and get the HUD + payloads working first.
2. Patch PokeDemo using `plugin_patch/PokeDemoClientBridgeService.java`.
3. Verify that party state updates correctly on the client.
4. Then implement a dedicated renderer for wolf carrier entities that reads the resolved Bedrock assets.

## Version notes

- Fabric officially supports Minecraft **1.21.11**. The Fabric team recommends Loom **1.14** and a current stable Fabric Loader for 1.21.11. citeturn242743search0turn242743search12
- The project is configured for Yarn mappings `1.21.11+build.4`, which exist in Fabric's Maven docs. citeturn213562search4turn213562search2
- Fabric API `0.141.3+1.21.11` is a released build for Minecraft 1.21.11. citeturn213562search9

## Asset workflow

Do **not** redistribute Cobblemon wholesale inside this scaffold.
Instead, use the helper script to extract only the assets you need from your own local Cobblemon jar.

Example:

```bash
python tools/extract_cobblemon_assets.py \
  --cobblemon "/path/to/Cobblemon-fabric-1.7.3+1.21.1.jar" \
  --species pikachu \
  --out ./run/cobblemon_assets
```

The included `data/pokedemo_bridge/species/pikachu.json` shows how the bridge resolves male/female asset paths.


## HUD test v2
- Left-side party HUD now uses a compact Cobblemon-like card layout.
- Press `O` to toggle the HUD.
- Real server data will override debug slots automatically when the PokeDemo plugin sends sync payloads.
