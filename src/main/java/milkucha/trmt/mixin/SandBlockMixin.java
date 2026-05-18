package milkucha.trmt.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Full-cube collision for normal and red sand (1.21 uses {@code ColoredFallingBlock}, not {@code SandBlock}).
 * Implemented at {@link BlockBehaviour} because sand types do not override {@code getCollisionShape}.
 */
@Mixin(BlockBehaviour.class)
public class SandBlockMixin {

	private static final VoxelShape SAND_COLLISION_SHAPE = Shapes.block();

	@Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
	private void trmt$fullCubeSandCollision(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
			cir.setReturnValue(SAND_COLLISION_SHAPE);
		}
	}
}
