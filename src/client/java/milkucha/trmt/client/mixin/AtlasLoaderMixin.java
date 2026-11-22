package milkucha.trmt.client.mixin;

import milkucha.trmt.client.render.MipmapFix;
import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the worn-grass overlay copies to the block atlas when mipmapFix is on (see MipmapFix). */
@Mixin(AtlasLoader.class)
public abstract class AtlasLoaderMixin {

    @Inject(method = "of", at = @At("RETURN"))
    private static void trmt$addOverlayCopies(ResourceManager resourceManager, Identifier id,
                                              CallbackInfoReturnable<AtlasLoader> cir) {
        MipmapFix.onAtlasSources(id, ((AtlasLoaderAccessor) cir.getReturnValue()).trmt$getSources());
    }
}
