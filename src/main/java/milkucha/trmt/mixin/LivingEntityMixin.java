package milkucha.trmt.mixin;

import milkucha.trmt.erosion.DeErosionItemTriggerHandler;
import milkucha.trmt.erosion.DeErosionLogic;
import milkucha.trmt.erosion.DeErosionRule;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Unique
    private static final ConcurrentHashMap<UUID, HoldProgress> trmt$holdProgress = new ConcurrentHashMap<>();

    @Inject(method = "tickActiveItemStack", at = @At("HEAD"))
    private void trmt$onTickActiveItemStack(CallbackInfo ci) {
        LivingEntity user = (LivingEntity) (Object) this;
        World world = user.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (!(user instanceof PlayerEntity player)) return;

        ItemStack stack = user.getActiveItem();
        HitResult hitResult = user.raycast(4.5, 0.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            trmt$holdProgress.remove(player.getUuid());
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        Optional<DeErosionRule.ItemTrigger> trigger = DeErosionLogic.getItemTrigger(state, stack.getItem(), "hold");
        if (trigger.isEmpty()) {
            trmt$holdProgress.remove(player.getUuid());
            return;
        }

        UUID uuid = player.getUuid();
        HoldProgress progress = trmt$holdProgress.get(uuid);
        if (progress == null || !progress.matches(pos, stack) || user.getItemUseTime() <= 1) {
            progress = new HoldProgress(pos, stack.getItem(), 0);
        }

        progress = progress.increment();
        if (progress.ticks() < trigger.get().ticks()) {
            trmt$holdProgress.put(uuid, progress);
            return;
        }

        if (!DeErosionLogic.tryItemDeErode(serverWorld, ErosionMapManager.getInstance(), pos, state, stack.getItem(), "hold")) {
            trmt$holdProgress.remove(uuid);
            return;
        }

        DeErosionItemTriggerHandler.apply(serverWorld, pos, player, stack, trigger.get());
        trmt$holdProgress.put(uuid, new HoldProgress(pos, stack.getItem(), 0));
    }

    @Unique
    private record HoldProgress(BlockPos pos, Item item, int ticks) {
        private boolean matches(BlockPos otherPos, ItemStack stack) {
            return pos.equals(otherPos) && item == stack.getItem();
        }

        private HoldProgress increment() {
            return new HoldProgress(pos, item, ticks + 1);
        }
    }
}
