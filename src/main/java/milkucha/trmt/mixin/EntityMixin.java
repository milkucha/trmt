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
 * Path speed buff dismount hooks.
 * <p>
 * {@code removePassenger}: takes the buff off a mount when its rider leaves. {@code Entity.removePassenger} is the
 * single place every dismount ends in (it is only called from {@code Entity.removeVehicle}, which is the only code
 * that clears {@code vehicle}; its one override, {@code HappyGhast}, calls super first), whichever way the rider
 * left: {@code stopRiding}, {@code /ride dismount}, ender pearl and other direct {@code removeVehicle()} calls,
 * rider or mount removal, teleport, disconnect, respawn.
 * <p>
 * {@code dismountTo}: a dismount has just put the player down at their dismount spot. On 1.21.11 {@code ServerPlayer}
 * no longer overrides it (1.21.1 Yarn: {@code ServerPlayerEntity.requestTeleportAndDismount}), so the hook sits on
 * {@code Entity}; its only caller is {@code LivingEntity.dismountVehicle}, i.e. every {@code stopRiding}.
 */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "removePassenger", at = @At("HEAD"))
    private void trmt$onRemovePassenger(Entity passenger, CallbackInfo ci) {
        if (passenger instanceof ServerPlayer player) {
            PathSpeedBuff.onDismount(((PathBuffHolder) player).trmt$getPathBuff(), (Entity) (Object) this);
        }
    }

    @Inject(method = "dismountTo", at = @At("TAIL"))
    private void trmt$onPlacedAfterDismount(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            PathSpeedBuff.onPlacedAfterDismount(player, ((PathBuffHolder) player).trmt$getPathBuff());
        }
    }
}
