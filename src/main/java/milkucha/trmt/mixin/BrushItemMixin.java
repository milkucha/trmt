package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(BrushItem.class)
public class BrushItemMixin {

    private static final int BRUSH_TICKS_TO_COMPLETE = 2;

    @Shadow
    private HitResult calculateHitResult(Player player) { return null; }

    // Per-player brush progress on eroded sand. BrushItem is a singleton, so instance fields
    // would be shared across all players — keep progress in a map keyed by player UUID instead.
    @Unique
    private static final ConcurrentHashMap<UUID, Integer> trmt$brushProgress = new ConcurrentHashMap<>();

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void trmt$onBrushTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int ticksRemaining, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!(livingEntity instanceof Player player)) return;

        int timeElapsed = 200 - ticksRemaining + 1;
        if (timeElapsed % 10 != 5) return;

        HitResult hitResult = this.calculateHitResult(player);
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);

        if (!state.is(TRMTBlocks.ERODED_SAND)) {
            trmt$brushProgress.remove(player.getUUID());
            return;
        }

        UUID uuid = player.getUUID();
        int progress = trmt$brushProgress.getOrDefault(uuid, 0) + 1;
        if (progress < BRUSH_TICKS_TO_COMPLETE) {
            trmt$brushProgress.put(uuid, progress);
            return;
        }

        ErosionMapManager manager = ErosionMapManager.getInstance();
        int stage = state.getValue(ErodedSandBlock.STAGE);
        if (stage > 0) {
            boolean keepWaterlogged = stage > 1 && state.getValue(ErodedSandBlock.WATERLOGGED);
            level.setBlock(pos, state.setValue(ErodedSandBlock.STAGE, stage - 1).setValue(ErodedSandBlock.WATERLOGGED, keepWaterlogged), Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, level.getGameTime());
        } else {
            level.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            manager.removeEntry(pos);
        }

        EquipmentSlot equippedHand = itemStack.equals(player.getItemBySlot(EquipmentSlot.OFFHAND))
                ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        itemStack.hurtAndBreak(1, player, equippedHand);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.levelEvent(2005, pos, 0);
        }
        trmt$brushProgress.put(uuid, 0);
    }
}
