package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ErodingEntity;
import milkucha.trmt.erosion.ErosionLogic;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin implements ErodingEntity {

    @Unique
    private BlockPos trmt$lastVehicleGroundPos = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!(entity.getWorld() instanceof ServerWorld)) return;

        if (entity.hasPassengers()) {
            trmt$erodeAsVehicle(entity);
            return;
        }

        trmt$lastVehicleGroundPos = null;
        ((ErodingEntity) entity).trmt$erodeGround();
    }

    @Unique
    private void trmt$erodeAsVehicle(Entity vehicle) {
        if (!vehicle.isOnGround()) {
            trmt$lastVehicleGroundPos = null;
            return;
        }

        float multiplier = 0.0f;
        for (Entity passenger : vehicle.getPassengerList()) {
            multiplier += ((ErodingEntity) passenger).trmt$passengerErosionMultiplier();
        }

        if (multiplier <= 0.0f) return;

        BlockPos groundPos = ErosionLogic.getGroundPos(vehicle);
        if (groundPos.equals(trmt$lastVehicleGroundPos)) return;
        trmt$lastVehicleGroundPos = groundPos.toImmutable();

        World world = vehicle.getWorld();
        ErosionMapManager manager = ErosionMapManager.getInstance();
        ErosionLogic.stepGround(world, manager, groundPos, multiplier, world.getTime());
    }
}
