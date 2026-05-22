package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BrushItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(BrushItem.class)
public class BrushItemMixin {

    private static final int BRUSH_TICKS_TO_COMPLETE = 2;

    @Shadow
    private HitResult getHitResult(LivingEntity user) { return null; }

    // Per-player brush progress on eroded sand. Resets to 0 after each completed cycle
    // so holding the brush continuously recovers stages one per second.
    // BrushItem is a singleton so instance fields would be shared — map keyed by UUID instead.
    @Unique
    private static final ConcurrentHashMap<UUID, Integer> trmt$brushProgress = new ConcurrentHashMap<>();

    // Each new right-click resets the cycle so releasing and reapplying starts fresh.
    @Inject(method = "useOnBlock", at = @At("HEAD"))
    private void trmt$resetProgress(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        PlayerEntity player = context.getPlayer();
        if (player != null) {
            trmt$brushProgress.put(player.getUuid(), 0);
        }
    }

    @Inject(method = "usageTick", at = @At("HEAD"))
    private void trmt$onBrushTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks, CallbackInfo ci) {
        if (world.isClient()) return;
        if (!(user instanceof PlayerEntity player)) return;

        // Mirror vanilla's brush tick cadence: fires every 10 game ticks.
        int currentTick = 200 - remainingUseTicks + 1;
        if (currentTick % 10 != 5) return;

        HitResult hitResult = this.getHitResult(user);
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);

        if (!state.isOf(TRMTBlocks.ERODED_SAND)) {
            trmt$brushProgress.remove(player.getUuid());
            return;
        }

        UUID uuid = player.getUuid();
        int progress = trmt$brushProgress.getOrDefault(uuid, 0);

        progress++;
        if (progress < BRUSH_TICKS_TO_COMPLETE) {
            trmt$brushProgress.put(uuid, progress);
            return;
        }

        // 10th brush tick — cycle complete, apply de-erosion.
        ErosionMapManager manager = ErosionMapManager.getInstance();
        int stage = state.get(ErodedSandBlock.STAGE);
        if (stage > 0) {
            boolean keepWaterlogged = stage > 1 && state.get(ErodedSandBlock.WATERLOGGED);
            world.setBlockState(pos, state.with(ErodedSandBlock.STAGE, stage - 1).with(ErodedSandBlock.WATERLOGGED, keepWaterlogged), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getTime());
        } else {
            world.setBlockState(pos, Blocks.SAND.getDefaultState(), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
        }

        // 1 durability damage, matching vanilla (damage only fires on cycle completion).
        EquipmentSlot slot = stack.equals(player.getEquippedStack(EquipmentSlot.OFFHAND))
                ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        stack.damage(1, player, p -> p.sendEquipmentBreakStatus(slot));

        ((ServerWorld) world).syncWorldEvent(2005, pos, 0);
        trmt$brushProgress.put(uuid, 0);
    }
}
