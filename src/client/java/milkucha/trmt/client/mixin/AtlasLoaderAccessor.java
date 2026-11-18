package milkucha.trmt.client.mixin;

import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.client.texture.atlas.AtlasSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The atlas definition's source list (a mutable ArrayList built in AtlasLoader.of). */
@Mixin(AtlasLoader.class)
public interface AtlasLoaderAccessor {
    @Accessor("sources")
    List<AtlasSource> trmt$getSources();
}
