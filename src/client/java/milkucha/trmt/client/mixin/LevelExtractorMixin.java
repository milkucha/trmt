package milkucha.trmt.client.mixin;

import milkucha.trmt.client.render.MipmapFix;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-reads mipmapFix before every chunk reload (F3+A), so the next meshes use the new value.
 * 26.3 moved {@code allChanged()} from {@code LevelRenderer} to {@code LevelExtractor}; it now only flags
 * the rebuild, which runs in the next {@code extract} (after this hook).
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

    @Inject(method = "allChanged()V", at = @At("HEAD"))
    private void trmt$refreshMipmapFix(CallbackInfo ci) {
        MipmapFix.onChunkReload();
    }
}
