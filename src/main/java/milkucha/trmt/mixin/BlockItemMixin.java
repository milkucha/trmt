package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Refuses placing a block that touches the ground but is not a full cube (and not a sand plant) on top of
 * eroded sand. Runs on the client and the server, so the prediction matches, and for dispensers too
 * (they call {@code BlockItem.place(BlockPlaceContext)}). See {@link BlockThresholds#refusesPlacementOnErodedSand}.
 *
 * <p>The hook is the single call to {@code getPlacementState} inside {@code place(BlockPlaceContext)}: it is
 * virtual, and subclasses (torches, signs, skulls, banners: {@code StandingAndWallBlockItem}) override it
 * without ever reaching {@code BlockItem.canPlace}, so a {@code canPlace} hook would miss them. Returning
 * {@code null} makes {@code place} return {@code InteractionResult.FAIL}. The state judged is the one the
 * item actually chose (a wall torch placed on the side of a block is a different state from the floor torch).
 *
 * <p>This replaces the old PlayerInteractEvent.RightClickBlock handler: a NeoForge event would only cover
 * players, not dispensers, and could not judge the state the item actually picks.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Shadow
    protected abstract BlockState getPlacementState(BlockPlaceContext context);

    @Redirect(
            method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState trmt$refusePlacementOnErodedSand(BlockItem self, BlockPlaceContext context) {
        BlockState state = this.getPlacementState(context);
        if (state == null) return null;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (level.getBlockState(pos.below()).is(TRMTBlocks.ERODED_SAND.get())
                && BlockThresholds.refusesPlacementOnErodedSand(state, level, pos)) {
            return null;
        }
        return state;
    }
}
