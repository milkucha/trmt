package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hoe tilling (eroded grass/dirt to farmland) and shovel path-flattening (eroded grass/dirt to
 * dirt path) for trmt blocks.
 *
 * MC 26.3 removed {@code HoeItem}/{@code ShovelItem}: every hoe and shovel carries the
 * {@code minecraft:block_transformer} component ({@link BlockTransformers#HOE} /
 * {@link BlockTransformers#SHOVEL}) and {@code Item.useOn} calls
 * {@link BlockTransformer#transformBlock(UseOnContext, Holder)}. This injects at its HEAD, where the
 * old {@code HoeItemMixin}/{@code ShovelItemMixin} injected into {@code useOn}, with the same
 * bodies. The transformer is identified by its registry key, so it runs only for hoe/shovel use
 * on a trmt block (any item carrying the hoe/shovel transformer, as the old mixins covered
 * HoeItem/ShovelItem subclasses), and the erosion entry is removed only on that conversion, from
 * the clicked level's manager.
 *
 * Conditions (otherwise the use passes): both match vanilla's hoe and shovel transformers (air
 * above, not the bottom face).
 */
@Mixin(BlockTransformer.class)
public class BlockTransformerMixin {

    @Inject(method = "transformBlock(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void trmt$transformErodedBlocks(UseOnContext context, @Nullable Holder<BlockTransformer> transformer,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos pos = context.getClickedPos();
        Level world = context.getLevel();
        BlockState state = world.getBlockState(pos);

        if (!state.is(TRMTBlocks.ERODED_GRASS_BLOCK.get()) && !state.is(TRMTBlocks.ERODED_DIRT.get())) {
            return;
        }

        // Item.useOn passes the stack's holder: compare by key (registry-instance independent).
        // Only a direct call without a holder falls back to a lookup of this value.
        ResourceKey<BlockTransformer> key = transformer != null
                ? transformer.unwrapKey().orElse(null)
                : world.registryAccess().lookupOrThrow(Registries.BLOCK_TRANSFORMER)
                        .getResourceKey((BlockTransformer) (Object) this).orElse(null);
        if (BlockTransformers.HOE.equals(key)) {
            trmt$tillErodedBlocks(context, pos, world, cir);
        } else if (BlockTransformers.SHOVEL.equals(key)) {
            trmt$flattenErodedBlocks(context, pos, world, cir);
        }
    }

    @Unique
    private static void trmt$tillErodedBlocks(UseOnContext context, BlockPos pos, Level world,
                                              CallbackInfoReturnable<InteractionResult> cir) {
        // Hoes can only till if the block above is air — same rule as vanilla.
        if (!world.getBlockState(pos.above()).isAir()) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        // Don't till the bottom face (matches vanilla hoe behaviour).
        if (context.getClickedFace() == Direction.DOWN) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        Player player = context.getPlayer();
        world.playSound(player, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (!world.isClientSide()) {
            world.setBlock(pos, Blocks.FARMLAND.defaultBlockState(),
                    Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
            ErosionMapManager.get(world).removeEntry(pos);
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player,
                        context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        cir.setReturnValue(world.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }

    @Unique
    private static void trmt$flattenErodedBlocks(UseOnContext context, BlockPos pos, Level world,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        // Shovels can only flatten if the block above is air — same rule as vanilla.
        if (!world.getBlockState(pos.above()).isAir()) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        // Don't flatten the bottom face (matches vanilla shovel behaviour).
        if (context.getClickedFace() == Direction.DOWN) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        Player player = context.getPlayer();
        world.playSound(player, pos, SoundEvents.SHOVEL_FLATTEN.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (!world.isClientSide()) {
            world.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(),
                    Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
            ErosionMapManager.get(world).removeEntry(pos);
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player,
                        context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        cir.setReturnValue(world.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
    }
}
