# Changelog

## v0.5

### New Features

- **Leashed mob erosion** — mobs on a leash now cause erosion as they walk, consistent with ridden and general foot traffic.
- **Brush recovery for eroded sand** — eroded sand blocks can be restored to their original state using a brush.
- **Tall grass → short grass erosion** — tall grass now degrades to short grass before disappearing entirely when eroded.
- **Per-block de-erosion toggle** — individual block types can be excluded from de-eroding (recovering) via config.
- **Vegetation whitelist in config** — specific vegetation types can be whitelisted to prevent erosion effects.
- **Migration system on uninstall** — running the mod's uninstall command now converts all custom TRMT blocks back to their vanilla equivalents, leaving the world in a clean state.
- **Translation support** — all mod strings are now fully localizable via lang files.

### Bug Fixes

- Fixed erosion toggle settings in the config reverting to vanilla blocks unexpectedly.
- Fixed block drops not working correctly for eroded blocks.
- Fixed saplings not being placeable on eroded grass and dirt.
- Fixed sugarcane being unable to grow/place on stage-0 eroded sand.
- Fixed a texture whiteness (rendering) bug on eroded blocks.
- Fixed a visual glitch on eroded sand when a block was placed directly on top.
- Fixed a missing translation key for tipped arrows.
