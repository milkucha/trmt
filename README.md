<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/177c11b808636328e9360ff476341a49e7af3167.png" alt="TRMT cover">
</p>

# The Roads More Travelled [TRMT]

A dynamic terrain mod that adds a gradual erosion system to the game, slowly transforming the routes you travel more often through into beautiful and realistic (yet still vanilla-friendly!) looking paths. Make your world feel more immersive by etching the story of your explorations onto the landscape.

## Logic
- Grass blocks, dirt blocks, sand blocks, leaves blocks and vegetation accumulate an erosion index everytime they're are stepped on. Neighbouring blocks accumulate a fraction of this index as well.
- Upon reaching a threshold, each one of these blocks transforms into their eroded variant progressing through different stages of erosion until reaching a final stage. Leaves blocks and vegeation are trampled when reaching their threshold.
- Erosion thresholds are randomly determined by configurable ranges assigned to each block type, making erosion happen in a more organic, less determnistic way.
- Erosion can be triggered by players, players on mounts or mobs on leashes. The strength of the erosion is controlled by a configurable multiplier. By default, players on mounts erode terrain 2.0 times faster than players on foot.
- Eroded blocks that have not been walked on for a long time gradually revert to previous stages of erosion, simulating terrain recovery. The amount of time that has to pass before an unstepped block reverts to a previous erosion stage is controlled by a configurable de-erosion timeout window (measured in Minecraft days).
- Toggles for erosion and de-erosion of blocks, erodable vegetation list, erosion thresholds, multipliers and de-erosion timeout windows can all be modified via the config file*

## Parameters
These are the parameters that govern the mod's logic and which can be tweaked to taste in the config file. The current default values are the ones that felt the best for me so far, but of course you may find some that work best for your own experience so please feel free to experiment with them.

- _**erosion and deErosion**_. Toggles to enable/disable erosion and de-erosion for different block types. Disabling erosion for block types that have been eroded doesn't make them dissappear, it simply makes them stop eroding/de-eroding.
- _**erosionMultipliers**_. Amount of erosion that accumulates when players step on blocks. *mounted* and *leash* multiply *player* baseline. 
- _**erosionThresholds**_. Thresholds at which different block types transform into their next stage of erosion, determined at random for each block within a min-max range.
- _**erodableVegetation**_. Whitelist including all blocks that are considered vegetation and thus subjected to trampling.
- _**deErosionTimeoutDays**_. Length of time (in Minecraft days) it takes for each block type since the last time they were stepped on to start reverting to a previous erosion stage.

## Features

- 5 **Eroded Grass** and 3 **Eroded Dirt** Variants
- 5 **Eroded Sand** Variants
- **Bonemeal and Brush Recovery**. Selectively de-erode blocks by applying bonemeal to or using the brush on them. Bonemeal recovers grass and dirt, brush recovers sand.
- **Potion of Lightness**. Want to leave no trace? Mix yourself a potion with some nether wart and feathers to temporarily suspend erosion!
- **Multiplayer supported**

## Showcase
[![Watch the video](https://img.youtube.com/vi/OvsNVaFiK1Y/maxresdefault.jpg)](https://www.youtube.com/watch?v=OvsNVaFiK1Y)

<div align="center">
<img src="https://i.imgur.com/LOvPlVV.png" alt="map view" style="display: block; margin: 0 auto;">
</div>

<div align="center">
<img src="https://i.imgur.com/PHJaK3E.png" alt="map view" style="display: block; margin: 0 auto;">
</div>

<div align="center">
<img src="https://i.imgur.com/Uqg119h.png" alt="map view" style="display: block; margin: 0 auto;">
</div>

<div align="center">
<img src="https://i.imgur.com/zOpgx3F.png" alt="map view" style="display: block; margin: 0 auto;">
</div>

##
## Recommended Companion Mods
Due to the erosion logic being essentially subtle but progressively noticeable overtime, TRMT works as a nice world building add-on to other terrain generation (Terralith, Tectonic, Biomes O'Plenty, Oh The Biomes You'll Go), exploration (Distant Horizons, Voxy) and nature focused (Serene Seasons) mods.

## Compatibility
Compatible with Xaero's World Map, JourneyMap and VoxelMap. The roads more travelled are also visible from a bird's eye view!

<table align="center">
  <tr>
    <td><img src="https://i.imgur.com/S3GuWsP.png" alt="map view 1" width="400"></td>
    <td><img src="https://i.imgur.com/fFAmORw.png" alt="map view 2" width="400"></td>
  </tr>
</table>

## FAQ
_**Any plans on porting to other mod loaders and platforms?**_

Yes, definitely. NeoForge and Forge support will be included in the next update. When I'm done with those ports I will work on a Paper port and then a Bedrock port.

_**What about other versions?**_

I will be attempting a port to 1.16.5 and 1.12.2 soon.

_**Is it compatible with other mods that add mounts and vehicles?**_

In principle yes, as long as they are registered by the game as mounts. As for more complex mods like Create Aeronautics (been asked about this one a lot!), I will work on making it compatible soon as I'm done with the NeoForge port.

_**What other mod compatibilities are planned?**__

For the time being, I will be working on adding compatibility with Create Aeronautics, Via Romana and Countered Terrain Salbs. For other terrain generation mods that add their own custom blocks I also wish to add compatibility, but this will require a little bit more work. Will post updates on this regard further down the road.

_**How is this performance-wise?**_

Fairly cheap. Erosion data is stored sparsely and in a self-cleaning way with reference to the block position, meaning that only blocks that have been stepped on but haven't transformed yet are tracked. As for how de-erosion works, each erosion entry also stores a timestamp which gets compared with the current gametime when a block has not been stepped on for a determinate amount of time. This is done using random ticks and only when a chunk is loaded, so it doesn't pose any more of an additional burden on the system than saplings becoming trees normally do (which is negligible).

_**Can de-erosion be turned-off?**_

Yes. You can use the deErosion toggle on the config.

_**Can I use this in an already generated world?**_

Absolutely.

_**What happens to the eroded paths if I uninstall the mod?**_

Eroded blocks can be turned into their vanilla variants by running **/trmt convert-to-vanilla** on loaded chunks. Otherwise they will simply turn into air.

## Roadmap
- Port to NeoForge and Forge; Paper; Bedrock
- Mod compat support: Create Aeronautics, Via Romana, Countered Terrain Slabs
- Features to be added: more eroded blocks (snow and nether blocks), speed buff on eroded paths, new item to lock eroded/uneroded state
- Bug fixes

<br></br>
_<center>Traveller, there is no road. The road is made by walking.</center>_
<center>Antonio Machado</center>
<br></br>
