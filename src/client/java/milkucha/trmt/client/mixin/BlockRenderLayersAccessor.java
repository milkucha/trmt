package milkucha.trmt.client.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(BlockRenderLayers.class)
public interface BlockRenderLayersAccessor {
    @Accessor("BLOCKS")
    static Map<Block, BlockRenderLayer> trmt$getBlocks() {
        throw new AssertionError();
    }
}
