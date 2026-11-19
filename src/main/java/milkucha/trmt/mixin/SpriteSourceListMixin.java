package milkucha.trmt.mixin;

import milkucha.trmt.client.render.MipmapFix;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the worn-grass overlay copies to the block atlas when mipmapFix is on (see MipmapFix). */
@Mixin(SpriteSourceList.class)
public abstract class SpriteSourceListMixin {

    @Inject(method = "load", at = @At("RETURN"))
    private static void trmt$addOverlayCopies(ResourceManager resourceManager, Identifier id,
                                              CallbackInfoReturnable<SpriteSourceList> cir) {
        MipmapFix.onAtlasSources(id, ((SpriteSourceListAccessor) cir.getReturnValue()).trmt$getSources());
    }
}
