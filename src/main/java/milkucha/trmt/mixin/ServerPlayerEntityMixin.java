package milkucha.trmt.mixin;

import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TRMTEffects;
import milkucha.trmt.erosion.ErodingEntity;
import milkucha.trmt.erosion.ErosionLogic;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements ErodingEntity {

    @Unique
    private BlockPos trmt$lastGroundPos = null;

    @Override
    public void trmt$erodeGround() {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (!player.isOnGround()) {
            trmt$lastGroundPos = null;
            return;
        }

        if (player.hasVehicle()) return;

        BlockPos groundPos = ErosionLogic.getGroundPos(player);

        if (groundPos.equals(trmt$lastGroundPos)) return;
        trmt$lastGroundPos = groundPos.toImmutable();

        if (player.hasStatusEffect(TRMTEffects.LIGHTNESS_ENTRY)) return;

        float mult = TRMTConfig.get().erosionMultipliers.player;

        World world = player.getWorld();
        ErosionMapManager manager = ErosionMapManager.getInstance();
        long gameTime = world.getTime();

        if (!ErosionLogic.stepGround(world, manager, groundPos, mult, gameTime)) {
            return;
        }

        Direction facing = player.getHorizontalFacing();
        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        ErosionLogic.stepIfTracked(world, manager, groundPos.offset(facing), 0.2f * mult, gameTime);
        ErosionLogic.stepIfTracked(world, manager, groundPos.offset(left), 0.5f * mult, gameTime);
        ErosionLogic.stepIfTracked(world, manager, groundPos.offset(right), 0.5f * mult, gameTime);
    }

    @Override
    public float trmt$passengerErosionMultiplier() {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.hasStatusEffect(TRMTEffects.LIGHTNESS_ENTRY)) {
            return 0.0f;
        }

        return TRMTConfig.get().erosionMultipliers.player
                * TRMTConfig.get().erosionMultipliers.mounted;
    }
}
