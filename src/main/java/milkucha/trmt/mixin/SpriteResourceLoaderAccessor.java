package milkucha.trmt.mixin;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The atlas definition's source list (a mutable ArrayList built in SpriteResourceLoader.load). */
@Mixin(SpriteResourceLoader.class)
public interface SpriteResourceLoaderAccessor {
    @Accessor("sources")
    List<SpriteSource> trmt$getSources();
}
