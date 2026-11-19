package milkucha.trmt.client.mixin;

import milkucha.trmt.client.render.MipmapFix;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-reads mipmapFix before every chunk reload (F3+A), so the next meshes use the new value. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "allChanged()V", at = @At("HEAD"))
    private void trmt$refreshMipmapFix(CallbackInfo ci) {
        MipmapFix.onChunkReload();
    }
}
