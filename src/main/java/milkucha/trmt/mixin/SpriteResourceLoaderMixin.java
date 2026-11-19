package milkucha.trmt.mixin;

import milkucha.trmt.client.render.MipmapFix;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the worn-grass overlay copies to the block atlas when mipmapFix is on (see MipmapFix). */
@Mixin(SpriteResourceLoader.class)
public abstract class SpriteResourceLoaderMixin {

    @Inject(method = "load", at = @At("RETURN"))
    private static void trmt$addOverlayCopies(ResourceManager resourceManager, ResourceLocation id,
                                              CallbackInfoReturnable<SpriteResourceLoader> cir) {
        MipmapFix.onAtlasSources(id, ((SpriteResourceLoaderAccessor) cir.getReturnValue()).trmt$getSources());
    }
}
