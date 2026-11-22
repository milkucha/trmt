package milkucha.trmt.mixin;

import milkucha.trmt.path.PathBuffHolder;
import milkucha.trmt.path.PathSpeedBuff;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the path speed buff off a mount when its rider leaves. {@code Entity.removePassenger} is the single
 * place every dismount ends in (it is only called from {@code Entity.removeVehicle}, which is the only code
 * that clears {@code vehicle}, and nothing overrides it), whichever way the rider left: {@code stopRiding},
 * {@code /ride dismount}, ender pearl and other direct {@code removeVehicle()} calls, rider or mount removal,
 * teleport, disconnect, respawn. (MojMap {@code removeVehicle} = Yarn {@code dismountVehicle}; Forge's
 * {@code EntityMountEvent} (ForgeEventFactory.canMountEntity in removeVehicle) can cancel a dismount before it, in which case nothing is removed.)
 */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "removePassenger", at = @At("HEAD"))
    private void trmt$onRemovePassenger(Entity passenger, CallbackInfo ci) {
        if (passenger instanceof ServerPlayer player) {
            PathSpeedBuff.onDismount(((PathBuffHolder) player).trmt$getPathBuff(), (Entity) (Object) this);
        }
    }
}
