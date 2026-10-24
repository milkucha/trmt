package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Saves dirty erosion regions together with the world's persistent data.
 * {@code saveLevel()} is what vanilla calls (from {@code save(ProgressListener, boolean, boolean)})
 * to write {@code PersistentStateManager}: on autosave, /save-all and server stop, but not while
 * /save-off is active — the same rules the old PersistentState-based erosion file followed.
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    @Inject(method = "saveLevel", at = @At("TAIL"))
    private void trmt$saveErosionRegions(CallbackInfo ci) {
        ErosionMapManager.onWorldSave((ServerWorld) (Object) this);
    }
}
