<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/177c11b808636328e9360ff476341a49e7af3167.png" alt="TRMT cover">
</p>

# The Roads More Travelled [TRMT]

A dynamic terrain mod that adds a gradual erosion system to the game, slowly transforming the routes you travel more often through into beautiful and realistic (yet still vanilla-friendly!) looking paths. Make your world feel more immersive by etching the story of your explorations onto the landscape.

## Features

- 5 **Eroded Grass** and 4 **Eroded Dirt** Variants
- 5 **Eroded Sand** Variants
- **Snow Erosion**
- **Speed Buff on Eroded Paths**. Walking on a 2-block wide eroded path gives +8 / +16 / +25% speed, depending on how worn the path is.
- **Bonemeal and Brush Recovery**. Selectively de-erode blocks by applying bonemeal to or using the brush on them. Bonemeal recovers grass and dirt, brush recovers sand. Covers a 3x3 area.
- **Potion of Lightness**. Want to leave no trace? Mix yourself a potion with some nether wart and feathers to temporarily suspend erosion!
- **Multiplayer supported**. Needs the mod in both server and client.

## Logic
- Grass blocks, dirt blocks, sand blocks, snow layers and blocks, leaves blocks and vegetation accumulate an erosion index every time they are stepped on. Neighbouring blocks accumulate a fraction of this index as well.
- Upon reaching a threshold, each one of these blocks transforms into their eroded variant progressing through different stages of erosion until reaching a final stage. Snow layers, leaves blocks and vegetation are trampled when reaching their threshold.
- Erosion thresholds are randomly determined by configurable ranges assigned to each block type, making erosion happen in a more organic, less deterministic way.
- Erosion can be triggered by players, players on mounts, mobs on leashes or whitelisted freeroaming (adult) mobs. The strength of the erosion is controlled by a configurable multiplier. By default, players on mounts erode terrain 2.0 times faster than players on foot.
- Eroded blocks that have not been walked on for a long time gradually revert to previous stages of erosion, simulating terrain recovery. The amount of time that has to pass before an unstepped block reverts to a previous erosion stage is controlled by a configurable de-erosion timeout window (measured in Minecraft days). Snow, leaves and vegetation do not recover in this way.
- Toggles for erosion and de-erosion of blocks, erosion multipliers and de-erosion timeout windows, erosion thresholds, erodable vegetation list, and whitelisted freeroaming mobs can all be modified via the config file.

## Parameters
These are the parameters that govern the mod's logic and which can be tweaked to taste in the config file. 

- _**erosion and deErosion**_. Toggles to enable/disable erosion and de-erosion for different block types. Disabling erosion for block types that have been eroded doesn't make them disappear, it simply makes them stop eroding/de-eroding.
- _**erosionMultipliers**_. Amount of erosion that accumulates when players step on blocks. *mounted*, *leash* and *freeRoaming* multiply *player* baseline. 
- _**pathSpeedBuff**_. Toggles speed buff and *tierPercent* determines speed increase percentage for different tiers of path wear. *tierMinDepth* controls how deep a path needs to be to jump a tier. *pathBlocks* toggles speed buff for specific eroded blocks.
- _**erosionThresholds**_. Thresholds at which different block types transform into their next stage of erosion, determined at random for each block within a min-max range.
- _**erodableVegetation**_. Whitelist including all blocks that are considered vegetation and thus subjected to trampling.
- _**erosionMobWhitelist**_. Mobs whitelisted cause erosion, they need to be written as "namespace:name". Modded mobs should work so long as they're built on Minecraft's mob class. Use sparingly: lots of wandering mobs means lots of erosion data to track.
- _**revertAlwaysBlocks and revertNeverBlocks**_. List for modded blocks that revert (or not) eroded blocks to their vanilla variants when placed on top of them. Does not apply to eroded sand.
- _**deErosionTimeoutDays**_. Length of time (in Minecraft days) it takes for each block type since the last time they were stepped on to start reverting to a previous erosion stage.
- _**permanentStages**_. Toggle for locking in erosion stages. Stages marked permanent will not erode or de-erode on their own any further.
- _**entryExpiryDays**_. Amount of time before a vanilla block that's been stepped on but hasn't eroded yet clears its erosion entry.
- _**debugHud**_. Toggles debug HUD, mainly useful for devs. Client side config.
- _**mipmapFix**_. Toggles experimental fix to remove shimmering in distant eroded grass blocks. Off by default. Client side config.

## Config Defaults
The current default values are the ones that felt the best for me so far, but of course you may find some that work best for your own experience. With these, walking the same path 2 to 4 times is enough to wear it down lightly, but it will take continuous repetition to consolidate it. Left alone, lightly worn spots fade within 2 to 5 Minecraft days, while a fully worn coarse dirt path can take more than 2 Minecraft months to grow back to grass.

#### Sample trmt.json 1.20.1
```json
{
  "erosion": {
    "grassEnabled": true,
    "dirtEnabled": true,
    "sandEnabled": true,
    "leavesEnabled": true,
    "vegetationEnabled": true,
    "snowEnabled": true
  },
  "deErosion": {
    "grassEnabled": true,
    "dirtEnabled": true,
    "sandEnabled": true
  },
  "erosionMultipliers": {
    "player": 0.5,
    "mounted": 2.0,
    "leash": 1.5,
    "freeRoaming": 1.0
  },
  "pathSpeedBuff": {
    "enabled": true,
    "tier1Percent": 8.0,
    "tier2Percent": 16.0,
    "tier3Percent": 25.0,
    "tier2MinDepth": 1.75,
    "tier3MinDepth": 2.5,
    "pathBlocks": {
      "erodedGrass": true,
      "erodedDirt": true,
      "erodedCoarseDirt": true,
      "erodedSand": true
    }
  },
  "erosionThresholds": {
    "grass": {
      "min": 2.0,
      "max": 4.0
    },
    "dirt": {
      "min": 8.0,
      "max": 12.0
    },
    "coarseDirt": {
      "min": 12.0,
      "max": 20.0
    },
    "sand": {
      "min": 1.5,
      "max": 3.0
    },
    "leaves": {
      "dropChance": 0.1,
      "min": 2.0,
      "max": 3.0
    },
    "vegetation": {
      "dropChance": 0.2,
      "min": 2.0,
      "max": 3.0
    },
    "snow": {
      "dropChance": 0.0,
      "min": 0.4,
      "max": 0.9
    },
    "snowBlock": {
      "min": 0.5,
      "max": 1.0
    }
  },
  "erodableVegetation": [
    "minecraft:grass",
    "minecraft:tall_grass",
    "minecraft:dandelion",
    "minecraft:poppy",
    "minecraft:blue_orchid",
    "minecraft:allium",
    "minecraft:azure_bluet",
    "minecraft:red_tulip",
    "minecraft:orange_tulip",
    "minecraft:white_tulip",
    "minecraft:pink_tulip",
    "minecraft:oxeye_daisy",
    "minecraft:cornflower",
    "minecraft:lily_of_the_valley",
    "minecraft:sunflower",
    "minecraft:lilac",
    "minecraft:rose_bush",
    "minecraft:peony"
  ],
  "revertAlwaysBlocks": [],
  "revertNeverBlocks": [],
  "erosionMobWhitelist": [],
  "deErosionTimeoutDays": {
    "grass": {
      "stage1": 5.0,
      "stage2": 5.0,
      "stage3": 5.0,
      "stage4": 5.0,
      "stage5": 5.0
    },
    "dirt": {
      "erodedDirt": 8.0,
      "erodedCoarseDirt": 13.0
    },
    "sand": {
      "stage1": 3.0,
      "stage2": 5.0,
      "stage3": 8.0,
      "stage4": 13.0,
      "stage5": 13.0
    }
  },
  "permanentStages": {
    "grass": {
      "stage1": false,
      "stage2": false,
      "stage3": false,
      "stage4": false,
      "stage5": false
    },
    "dirt": {
      "erodedDirt": false,
      "erodedCoarseDirt": false
    },
    "sand": {
      "stage1": false,
      "stage2": false,
      "stage3": false,
      "stage4": false,
      "stage5": false
    }
  },
  "entryExpiryDays": 5.0
}
```

## Commands

All `/trmt` commands need operator rights (in singleplayer, cheats on or Open to LAN with Allow Cheats).

- **/trmt reload-config**: reloads `trmt.json` without restarting. If the file is broken, the previous settings stay in use.
- **/trmt convert-to-vanilla**: turns every eroded block in the world back into its vanilla variant. Shows a preview first. Back up first, it can't be undone.
  - **confirm**: starts the conversion.
  - **cancel**: stops it; running it again resumes.
  - **quick**: only visits chunks with erosion data (faster, but can miss some blocks).
- **/trmt eroded-chunks**: lists how many chunks have erosion data in each dimension, and where the unloaded ones are.
- **/trmt-hud**: toggles the erosion debug HUD (client-side, no operator rights needed).


## Showcase
[![Watch the video](https://img.youtube.com/vi/OvsNVaFiK1Y/maxresdefault.jpg)](https://www.youtube.com/watch?v=OvsNVaFiK1Y)

<div align="center">
<img src="https://i.imgur.com/yYnk0Yw.png" alt="eroded grass 1" style="display: block; margin: 0 auto;">
</div>

<div align="center">
<img src="https://i.imgur.com/daRhImr.png" alt="eroded grass 2" style="display: block; margin: 0 auto;">
</div>

<div align="center">
<img src="https://i.imgur.com/pI4osIF.png" alt="eroded sand 1" style="display: block; margin: 0 auto;">
</div>

<div align="center">
<img src="https://i.imgur.com/EKwLRKb.png" alt="eroded sand 2" style="display: block; margin: 0 auto;">
</div>

<div align="center">
<img src="https://i.imgur.com/di2rozD.png" alt="eroded snow 1" style="display: block; margin: 0 auto;">
</div>

<div align="center">
<img src="https://i.imgur.com/QFuekg6.png" alt="eroded snow 2" style="display: block; margin: 0 auto;">
</div>

##
## Recommended Companion Mods
Due to the erosion logic being essentially subtle but progressively noticeable over time, TRMT works as a nice world building add-on to other terrain generation (Terralith, Tectonic, Biomes O'Plenty, Oh The Biomes You'll Go), exploration (Distant Horizons, Voxy) and nature focused (Serene Seasons) mods.

## Compatibility
- Xaero's World Map, JourneyMap and VoxelMap. The roads more travelled are also visible from a bird's eye view!
- Via Romana. The roads more travelled can also be used to establish fast travel routes.

## FAQ
_**What versions and mod loaders are you maintaining?**_

| Mod Loader | Version |
|---|---|
| Fabric | 1.20–1.20.1, 1.21–1.21.1, 1.21.11, 26.1–26.1.2, 26.3 |
| NeoForge | 1.21.1, 1.21.11, 26.1–26.1.2, 26.3 |
| Forge | 1.20.1 |

_**Any plans on porting to other platforms?**_

Yes. I'm planning to do a rewrite for Paper and Bedrock, hopefully ready by 2027-Q2 at the latest.

_**What about other versions?**_

Not for now, but may still do some backporting if I have the capacities.

_**Is it compatible with other mods that add mounts and vehicles?**_

In principle yes, as long as they are registered by the game as mounts.

_**What other mod compatibilities are planned?**_

For the time being, Create Aeronautics, Countered Terrain Slabs, Countered/Terrain Slabs and TerraFirmaCraft. I might add more to this list. Will post updates on this regard further down the road.

_**How is this performance-wise?**_

Fairly cheap. Erosion data is stored sparsely and in a self-cleaning way with reference to the block position, meaning that only blocks that have actually been stepped on are tracked: blocks on their way to eroding, and eroded blocks waiting to de-erode. Progress on a block that never transformed is forgotten after `entryExpiryDays` (5 MC days by default), so stray footsteps don't pile up. The data is saved per dimension in region files, and only the regions that changed get written on autosave. As for how de-erosion works, each eroded block stores a timestamp of when it was last stepped on, which gets compared with the current game time on random ticks; once enough time has passed, the block recovers a stage. This only happens while a chunk is loaded, so it doesn't pose any more of an additional burden on the system than saplings becoming trees normally do (which is negligible).

_**Can erosion and/or de-erosion be turned-off?**_

Yes. You can use the erosion and deErosion toggles as well as the permanentStages list to freeze erosion stages on the config.

_**Can I use this in an already generated world?**_

Absolutely.

_**What happens to the eroded paths if I uninstall the mod?**_

Eroded blocks can be safely turned into their vanilla variants by running **/trmt convert-to-vanilla**. Make sure to run this before uninstalling the mod, otherwise the eroded blocks will simply turn into air.

**WARNING**. Once updating the mod to 0.6, it is not possible to downgrade back to older versions without losing all paths. 

## Contributors
Thanks to everyone who's dedicated time to making this mod better, whether by using it, sharing it, commenting on it or taking part in the Discord community. And last but not least:

- @DrDukeLear: Discord server manager
- @kaiserproger: Forge 1.20.1 login-sync fix
- @EldritchMoon: NeoForge 26.1.2 crash fix
- @megatocha: localization
- @kaiguy71: 26.3 porting hints
- Via Romana (@RasaNovum): path-rule credit.

## Roadmap
**2027-Q1**
- Create: Aeronautics compat
- Countered Terrain Slabs compat
- Continuity/Fusion compat
- TerraFirmaCraft compat
- Compat for other mods
- Quartz Dust Powder: erosion stage lock item
- More eroded blocks: red sand, podzol, nether blocks.


**2027-Q2** (tentative)
- Paper rewrite
- Bedrock rewrite
- Backports

<br></br>
_<center>Traveller, there is no road. The road is made by walking.</center>_
<center>Antonio Machado</center>
<br></br>
