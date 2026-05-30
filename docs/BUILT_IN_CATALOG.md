# Built-In Catalog

Current built-in ROM pack is intentionally small and audit-friendly.

## Included ROM

1. `Big City Sliding Blaster`
Source: `https://github.com/NovaSquirrel/SlidingBlaster`
License: `zlib`

2. `Double Action Blaster Guys`
Source: `https://github.com/NovaSquirrel/DABG`
License: `zlib`

3. `robotfindskitten`
Source: `https://github.com/pinobatch/rfk-nes`
License: `zlib`

## Packaging Rules

- Only titles with explicit redistribution-friendly terms should go into `app/src/main/assets/roms/`.
- Each ROM must have a matching entry in `app/src/main/assets/built_in_catalog.json`.
- Each ROM must have a matching license file in `app/src/main/assets/licenses/`.
- If a game is GPL or has special binary/source conditions, bundle strategy must be reviewed before adding it to the built-in catalog.

## Growth Path

- Add cover art when redistribution for the artwork is equally clear.
- Add SPDX-like metadata fields if the catalog grows beyond a small audited pack.
- Keep user-imported ROM separate from built-in legal-safe ROM.

