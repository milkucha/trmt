package milkucha.trmt.mixin;

import milkucha.trmt.path.PathBuffHolder;
import milkucha.trmt.path.PathSpeedBuff;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the path speed buff off a mount when its rider leaves. {@code Entity.removePassenger} is the single
 * place every dismount ends in (it is only called from {@code Entity.dismountVehicle}, which is the only code
 * that clears {@code vehicle}, and nothing overrides it), whichever way the rider left: {@code stopRiding},
 * {@code /ride dismount}, ender pearl and other direct {@code dismountVehicle()} calls, rider or mount removal,
 * teleport, disconnect, respawn.
 */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "removePassenger", at = @At("HEAD"))
    private void trmt$onRemovePassenger(Entity passenger, CallbackInfo ci) {
        if (passenger instanceof ServerPlayerEntity player) {
            PathSpeedBuff.onDismount(((PathBuffHolder) player).trmt$getPathBuff(), (Entity) (Object) this);
        }
    }
}
