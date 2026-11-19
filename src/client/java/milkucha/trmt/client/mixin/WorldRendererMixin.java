package milkucha.trmt.client.mixin;

import milkucha.trmt.client.render.MipmapFix;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-reads mipmapFix before every chunk reload (F3+A), so the next meshes use the new value. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "reload()V", at = @At("HEAD"))
    private void trmt$refreshMipmapFix(CallbackInfo ci) {
        MipmapFix.onChunkReload();
    }
}
