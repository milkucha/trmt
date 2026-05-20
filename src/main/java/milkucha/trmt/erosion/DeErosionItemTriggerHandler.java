package milkucha.trmt.erosion;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class DeErosionItemTriggerHandler {

    private DeErosionItemTriggerHandler() {
    }

    public static void apply(ServerWorld world, BlockPos pos, LivingEntity user,
                             ItemStack stack, DeErosionRule.ItemTrigger trigger) {
        if (trigger.consume() > 0 && user instanceof PlayerEntity player && !player.isCreative()) {
            stack.decrement(trigger.consume());
        }

        if (trigger.damage() > 0 && user instanceof PlayerEntity player) {
            EquipmentSlot slot = stack.equals(player.getEquippedStack(EquipmentSlot.OFFHAND))
                    ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
            stack.damage(trigger.damage(), player, slot);
        }

        if (trigger.worldEvent() != 0) {
            world.syncWorldEvent(trigger.worldEvent(), pos, 0);
        }
    }
}
