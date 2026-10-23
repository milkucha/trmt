package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Saves dirty erosion regions together with the world's persistent data.
 * {@code saveLevelData(boolean flush)} is what vanilla calls (from {@code save(ProgressListener, boolean, boolean)})
 * to write {@code DimensionDataStorage}: on autosave, /save-all and server stop, but not while
 * /save-off is active — the same rules the old SavedData-based erosion file followed.
 * (Named after the Yarn class, {@code ServerWorld}, like the other mixins on this branch.)
 */
@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {

    @Inject(method = "saveLevelData", at = @At("TAIL"))
    private void trmt$saveErosionRegions(boolean flush, CallbackInfo ci) {
        ErosionMapManager.getInstance().onWorldSave((ServerLevel) (Object) this);
    }
}
