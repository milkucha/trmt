package milkucha.trmt;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Container class for all custom, eroded block types in the mod.
 * These blocks should ideally be registered via data components/JSON files
 * rather than programmatically, but this class maintains the necessary structure
 * and properties for the Java side to reference them correctly during runtime checks.
 */
public final class TRMTBlocks {

    // --- Block Definitions (Must match JSON definitions) ---

    /** The base block state used when a block is first placed or fully restored. */
    public static final ErodedGrassBlock ERODED_GRASS_BLOCK =
            register("eroded_grass_block", ErodedGrassBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK));
    public static final ErodedDirtBlock ERODED_DIRT =
            register("eroded_dirt", ErodedDirtBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT));
    // Coarse dirt shares the same behavior/logic as ERODED_DIRT (ErodedDirtBlock branches internally
    // on identity against TRMTBlocks.ERODED_COARSE_DIRT vs TRMTBlocks.ERODED_DIRT).
    public static final ErodedDirtBlock ERODED_COARSE_DIRT =
            register("eroded_coarse_dirt", ErodedDirtBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.COARSE_DIRT));
    public static final ErodedSandBlock ERODED_SAND =
            register("eroded_sand", ErodedSandBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SAND));

    // Placeholder for any other eroded blocks (e.g., Snow, Netherrack)
    // public static final ErodedSnowBlock ERODED_SNOW = new ErodedSnowBlock(Settings.copy(Blocks.SNOW_BLOCK));

    private TRMTBlocks() {}

    /** Registers a block and its corresponding {@link BlockItem} under the given path. */
    private static <T extends Block> T register(String path, java.util.function.Function<BlockBehaviour.Properties, T> factory,
                                                 BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(TRMT.MOD_ID, path);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        T block = factory.apply(properties.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        BlockItem blockItem = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);

        return block;
    }

    /** Called from the mod initializer to force this class (and thus all its registrations) to load. */
    public static void register() {
        // Intentionally empty: referencing this class's static fields (which happens implicitly
        // via the call site in TRMT.onInitialize) triggers class loading and, in turn, block registration.
    }
}
