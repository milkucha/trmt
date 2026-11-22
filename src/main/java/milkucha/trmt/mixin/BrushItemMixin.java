package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.ErosionRecovery;
import net.minecraft.block.BlockState;
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

    @Unique
    private static final ConcurrentHashMap<UUID, Integer> trmt$brushProgress = new ConcurrentHashMap<>();

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

        int currentTick = 200 - remainingUseTicks + 1;
        if (currentTick % 10 != 5) return;

        HitResult hitResult = user.raycast(4.5, 0.0F, false);
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

        // Cycle complete: recover the target and its 3x3 neighbours (occupied sand is skipped).
        ErosionRecovery.recoverArea((ServerWorld) world, pos, player.getHorizontalFacing(), ErosionRecovery.Tool.BRUSH);

        // 1 durability damage per cycle, matching vanilla (damage only fires on cycle completion), not per block.
        EquipmentSlot slot = stack.equals(player.getEquippedStack(EquipmentSlot.OFFHAND))
                ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        stack.damage(1, player, slot);

        trmt$brushProgress.put(uuid, 0);
    }
}
