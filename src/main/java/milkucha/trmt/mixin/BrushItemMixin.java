package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTFlags;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
	private HitResult calculateHitResult(Player player) {
		throw new UnsupportedOperationException("Shadow");
	}

	@Unique
	private static final ConcurrentHashMap<UUID, Integer> trmt$brushProgress = new ConcurrentHashMap<>();

	@Inject(method = "useOn", at = @At("HEAD"))
	private void trmt$resetProgress(UseOnContext context, CallbackInfoReturnable<?> cir) {
		Player player = context.getPlayer();
		if (player != null) {
			trmt$brushProgress.put(player.getUUID(), 0);
		}
	}

	@Inject(method = "onUseTick", at = @At("HEAD"))
	private void trmt$onBrushTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration, CallbackInfo ci) {
		if (level.isClientSide()) return;
		if (!(user instanceof Player player)) return;

		int currentTick = 200 - remainingUseDuration + 1;
		if (currentTick % 10 != 5) return;

		HitResult hitResult = this.calculateHitResult(player);
		if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) return;

		BlockPos pos = blockHitResult.getBlockPos();
		BlockState state = level.getBlockState(pos);

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

		var manager = ErosionMapManager.getInstance();
		int stage = state.getValue(ErodedSandBlock.STAGE);
		if (stage > 0) {
			level.setBlock(pos, state.setValue(ErodedSandBlock.STAGE, stage - 1), TRMTFlags.BLOCK_UPDATE);
			manager.removeEntry(pos);
			manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND.get(), level.getGameTime());
		} else {
			level.setBlock(pos, Blocks.SAND.defaultBlockState(), TRMTFlags.BLOCK_UPDATE);
			manager.removeEntry(pos);
		}

		EquipmentSlot slot = stack == player.getItemBySlot(EquipmentSlot.OFFHAND)
			? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
		stack.hurtAndBreak(1, player, slot);

		((ServerLevel) level).levelEvent(2005, pos, 0);
		trmt$brushProgress.put(uuid, 0);
	}
}
