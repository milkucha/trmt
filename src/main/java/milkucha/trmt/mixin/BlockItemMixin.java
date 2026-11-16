package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Refuses placing a block that touches the ground but is not a full cube (and not a sand plant) on top of
 * eroded sand. Runs on the client and the server, so the prediction matches, and for dispensers too
 * (they call {@code BlockItem.place(ItemPlacementContext)}). See {@link BlockThresholds#refusesPlacementOnErodedSand}.
 *
 * <p>The hook is the single call to {@code getPlacementState} inside {@code place(ItemPlacementContext)}: it is
 * virtual, and subclasses (torches, signs, skulls, banners: {@code VerticallyAttachableBlockItem}) override it
 * without ever reaching {@code BlockItem.canPlace}, so a {@code canPlace} hook would miss them. Returning
 * {@code null} makes {@code place} return {@code ActionResult.FAIL}. The state judged is the one the item
 * actually chose (a wall torch placed on a block's side is a different state from the floor torch).
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Shadow
    protected abstract BlockState getPlacementState(ItemPlacementContext context);

    @Redirect(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/BlockItem;getPlacementState(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/block/BlockState;"))
    private BlockState trmt$refusePlacementOnErodedSand(BlockItem self, ItemPlacementContext context) {
        BlockState state = this.getPlacementState(context);
        if (state == null) return null;
        var world = context.getWorld();
        var pos = context.getBlockPos();
        if (world.getBlockState(pos.down()).isOf(TRMTBlocks.ERODED_SAND)
                && BlockThresholds.refusesPlacementOnErodedSand(state, world, pos)) {
            return null;
        }
        return state;
    }
}
