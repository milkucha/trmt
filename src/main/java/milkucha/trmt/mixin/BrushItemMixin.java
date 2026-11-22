package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.ErosionRecovery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
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

    @Inject(method = "useOn", at = @At("HEAD"))
    private void trmt$resetProgress(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        if (player != null) {
            trmt$brushProgress.put(player.getUUID(), 0);
        }
    }

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void trmt$onBrushTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks, CallbackInfo ci) {
        if (world.isClientSide()) return;
        if (!(user instanceof Player player)) return;

        int currentTick = 200 - remainingUseTicks + 1;
        if (currentTick % 10 != 5) return;

        HitResult hitResult = user.pick(4.5, 0.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);

        if (!state.is(TRMTBlocks.ERODED_SAND.get())) {
            trmt$brushProgress.remove(player.getUUID());
            return;
        }

        UUID uuid = player.getUUID();
        int progress = trmt$brushProgress.getOrDefault(uuid, 0);

        progress++;
        if (progress < BRUSH_TICKS_TO_COMPLETE) {
            trmt$brushProgress.put(uuid, progress);
            return;
        }

        // Cycle complete: recover the target and its 3x3 neighbours (occupied sand is skipped).
        ErosionRecovery.recoverArea((ServerLevel) world, pos, player.getDirection(), ErosionRecovery.Tool.BRUSH);

        // 1 durability damage per cycle, matching vanilla (damage only fires on cycle completion), not per block.
        EquipmentSlot slot = stack.equals(player.getItemBySlot(EquipmentSlot.OFFHAND))
                ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(slot));

        trmt$brushProgress.put(uuid, 0);
    }
}
