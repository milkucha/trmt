<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/177c11b808636328e9360ff476341a49e7af3167.png" alt="TRMT cover">
</p>

# The Roads More Travelled [TRMT]

A dynamic terrain mod that adds a gradual erosion system to the game, slowly transforming the routes you travel more often through into beautiful and realistic (yet still vanilla-friendly!) looking paths. Make your world feel more immersive by etching the story of your explorations onto the landscape.

## Logic
- Grass blocks, dirt blocks, sand blocks, leaves blocks and vegetation accumulate an erosion index everytime they're are stepped on. Neighbouring blocks accumulate a fraction of this index as well.
- Upon reaching a threshold, each one of these blocks transforms into their eroded variant progressing through different stages of erosion until reaching a final stage. Leaves blocks and vegeation simply become destroyed upon reaching their threshold.
- Erosion thresholds are randomly determined by configurable ranges assigned to each block type, making erosion happen in a more organic, less determnistic way.
- Erosion can be triggered by both players and players on mounts. The strength of the erosion is controlled by a configurable multiplier. By default, players on mounts erode terrain 1.5 times faster than players on foot.
- Eroded blocks that have not been walked on for a long time gradually revert to previous stages of erosion, simulating terrain recovery. The amount of time that has to pass before an unstepped block reverts to a previous erosion stage is controlled by a configurable de-erosion timeout window (measured in Minecraft days).
- Toggles for erodable blocks, erosion thresholds, multipliers and de-erosion timeout windows can all be modified via the config file*

*Default values might feel a little too gradual for some! Feel free to experiment with them and share feedback if you're keen. Increasing the erosionMultiplier will make the erosion much faster to appear, 

## Parameters
These are the parameters that govern the mod's logic and which can be tweaked to taste in the config file. The current default values are the ones that felt the best for me so far, but of course you may find some that work best for your own experience so please feel free to experiment with them (and share feedback if you're keen!).

- _**erosion**_. Toggles to enable/disable erosion for different block types. Disabling erosion for block types that have been eroded doesn't make them dissappear, it simply makes them stop eroding/de-eroding.
- _**erosionMultipliers**_. Amount of erosion that accumulates when players step on blocks.
- _**erosionThresholds**_. Thresholds at which different block types transform into their next stage of erosion, determined at random for each block within a min-max range.
- _**deErosionTimeoutDays**_. Length of time (in Minecraft days, each one being 24000 ticks) it takes for each block type since the last time they were stepped on to start reverting to a previous erosion stage.

## Features

- 5 **Eroded Grass** and 3 **Eroded Dirt** Variants
- 5 **Eroded Sand** Variants
- **Bonemeal Recovery**. Selectively de-erode blocks by applying bonemeal to them.
- **Potion of Lightness**. Want to leave no trace? Mix yourself a potion with some nether wart and feathers to temporarily suspend erosion!
- **Multiplayer supported**

## Showcase
<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/OvsNVaFiK1Y" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

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
_**Any plans on porting to other mod loaders?**_

Yes, definitely. NeoForge and Forge support will be included in the next update. In the meantime, it seems like Sinytra Connector works well as a workaround but I haven't tested it myself yet so can't say with 100% certainty.

_**What about other versions?**_

Also yes. I'll be adding support to **1.21.4**, **1.21.11**, **26.1+26.1.1+26.1.2** for the next update (for both Fabric and NeoForge). 
As for earlier versions (i.e. 1.12.2) though, this will be a bit more challenging but I'll give it a try if there's enough interest.

_**Is it compatible with other mods that add mounts and vehicles?**_

In principle yes, as long as they are registered by the game as mounts. As for more complex mods like Create Aeronautics (been asked about this one a lot!), I will have to look into it but I'm hoping it will be possible. However, I can only do this after the NeoForge port is ready.

_**How is this performance-wise?**_

Fairly cheap. Erosion data is stored sparsely and in a self-cleaning way with reference to the block position, meaning that only blocks that have been stepped on but haven't transformed yet are tracked. As for how de-erosion works, each erosion entry also stores a timestamp which gets compared with the current gametime when a block has not been stepped on for a determinate amount of time. This is done using random ticks and only when a chunk is loaded, so it doesn't pose any more of an additional burden on the system than saplings becoming trees normally do (which is negligible). Of course, player testing will reveal any issues not covered by the design, but so far I have not experienced any problems with it testing in a big and heavily modded world.

_**Can de-erosion be turned-off?**_

Not completely right now, but I'll be sure to add a toggle for it in the next update. However, it can be certainly be slowed down and pretty much effectively stopped by setting the deErosionTimeOut days to a high value like 99. In any case, de-erosion is designed to work quite slowly, so paths will still remain for a long time, unless you abandon them for months and continuously visit the area without stepping on the blocks (which is the behaviour I intended).


_**Can I use this in an already generated world?**_

Absolutely.

## Roadmap
- Port to NeoForge and Forge
- Port to 1.21.4, 1.21.11 and 26.1+26.1.1+26.1.2
- Added features: snow erosion, de-erosion toggle, path persistence mechanic
- Bug fixes

<br></br>
_<center>Traveller, there is no road. The road is made by walking.</center>_
<center>Antonio Machado</center>
<br></br>
