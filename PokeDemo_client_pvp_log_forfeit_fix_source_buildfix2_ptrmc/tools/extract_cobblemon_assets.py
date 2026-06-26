#!/usr/bin/env python3
"""
Extract a minimal subset of Cobblemon assets into a client-side ArcartX-like resource folder
or into this Fabric mod project's run/resources directory.

Example:
  python tools/extract_cobblemon_assets.py \
    --cobblemon "/path/to/Cobblemon-fabric-1.7.3+1.21.1.jar" \
    --species pikachu \
    --out ./extracted
"""
import argparse
import json
import os
import pathlib
import re
import zipfile

SPECIES_TO_DEX = {
    "pikachu": "0025_pikachu",
}


def copy_if_exists(zf: zipfile.ZipFile, member: str, out_root: pathlib.Path):
    try:
        data = zf.read(member)
    except KeyError:
        print(f"[skip] {member}")
        return False
    dest = out_root / member
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    print(f"[ok]   {member}")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--cobblemon', required=True)
    ap.add_argument('--species', required=True)
    ap.add_argument('--out', required=True)
    args = ap.parse_args()

    species = args.species.lower()
    dex = SPECIES_TO_DEX.get(species)
    if not dex:
        raise SystemExit(f"Unsupported demo species: {species}")

    out_root = pathlib.Path(args.out)
    with zipfile.ZipFile(args.cobblemon) as zf:
        paths = [
            f"assets/cobblemon/bedrock/pokemon/models/{dex}/{species}_male.geo.json",
            f"assets/cobblemon/bedrock/pokemon/models/{dex}/{species}_female.geo.json",
            f"assets/cobblemon/bedrock/pokemon/animations/{dex}/{species}.animation.json",
            f"assets/cobblemon/textures/pokemon/{dex}/{species}.png",
            f"assets/cobblemon/textures/pokemon/{dex}/{species}_shiny.png",
            f"assets/cobblemon/bedrock/pokemon/posers/{dex}/{species}.json",
            f"assets/cobblemon/bedrock/pokemon/resolvers/{dex}/0_{species}_base.json",
            f"data/cobblemon/species/generation1/{species}.json",
        ]
        for p in paths:
            copy_if_exists(zf, p, out_root)

    print("Done.")

if __name__ == '__main__':
    main()
