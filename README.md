<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/177c11b808636328e9360ff476341a49e7af3167.png" alt="TRMT cover">
</p>

# The Roads More Travelled [TRMT]

A dynamic terrain mod that adds a gradual erosion system to the game, slowly transforming the routes you travel more often through into beautiful and realistic (yet still vanilla-friendly!) looking paths. Make your world feel more immersive by etching the story of your explorations onto the landscape.

## Logic
- Grass blocks, dirt blocks, sand blocks, leaves blocks and vegetation accumulate an erosion index everytime they're are stepped on. Neighbouring blocks accumulate a fraction of this index as well.
- Upon reaching a threshold, each one of these blocks transforms into their eroded variant progressing through different stages of erosion. Leaves blocks and vegeation simply become destroyed upon reaching their threshold.
- Erosion thresholds are randomly determined by configurable ranges assigned to each block type, making erosion happen in a more organic, less determnistic way.
- Erosion can be triggered by both players and mounts. The strength of the erosion is controlled by a configurable multiplier. By default, mounts erode terrain three times faster than players on foot.
- Eroded blocks that have not been walked on for a long time gradually revert to previous stages of erosion, simulating terrain recovery. The amount of time is controlled by a configurable de-erosion timeout window (measured in Minecraft days).
- Toggles for erodable blocks, erosion thresholds, multipliers and de-erosion timeout windows can all be modified via the config file*

*Default values might feel a little **too** gradual for some! Feel free to experiment with them and share feedback if you're keen. Increasing the player multiplier will make the erosion much faster to appear.

## Features

- 5 **Eroded Grass** and 3 **Eroded Dirt** Variants
- 5 **Eroded Sand** Variants
- **Bonemeal Recovery**. Selectively de-erode blocks by applying bonemeal to them.
- **Potion of Lightness**. Want to leave no trace? Mix yourself a potion with some nether wart and feathers to temporarily suspend erosion!
- **Multiplayer supported**

## Showcase

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

## Roadmap
- Planned compatibility for Dynmap, Pl3XMap and BlueMap if enough people are interested
- Planned port to Forge if enough people are interested as well!

<br></br>
_<center>Traveller, there is no road. The road is made by walking.</center>_
<center>Antonio Machado</center>
<br></br>
