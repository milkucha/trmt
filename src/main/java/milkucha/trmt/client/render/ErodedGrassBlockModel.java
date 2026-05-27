package milkucha.trmt.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Forge two-pass wrapper for ErodedGrassBlock baked models.
 *
 * Non-tinted quads (dirt base) route to CUTOUT_MIPPED (renders first, writes depth buffer).
 * Tinted quads (eroded_top overlay) route to CUTOUT (passes GL_LEQUAL at same depth, wins).
 * This eliminates Z-fighting on the UP face without any Y-offset hack.
 *
 * Break-animation path (null renderType): UP face returns only non-tinted quads so the
 * damage overlay renders on the dirt base without white shimmering.
 */
@OnlyIn(Dist.CLIENT)
public class ErodedGrassBlockModel implements BakedModel {

    private final BakedModel wrapped;

    public ErodedGrassBlockModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    // ── Forge two-pass render type declaration ────────────────────────────────

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutoutMipped(), RenderType.cutout());
    }

    // ── Forge 5-arg getQuads (used for normal chunk rendering) ────────────────

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face,
                                     RandomSource random, ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> all = wrapped.getQuads(state, face, random);

        if (renderType == null) {
            // Break-animation path: dedup UP face to non-tinted (dirt base) only.
            if (face == Direction.UP) {
                List<BakedQuad> nonTinted = new ArrayList<>();
                for (BakedQuad q : all) {
                    if (q.getTintIndex() == -1) {
                        nonTinted.add(q);
                    }
                }
                return nonTinted.isEmpty() ? (all.isEmpty() ? all : all.subList(0, 1)) : nonTinted;
            }
            return all;
        }
        if (renderType == RenderType.cutoutMipped()) {
            // Non-tinted quads → CUTOUT_MIPPED (rendered first, depth written).
            List<BakedQuad> result = new ArrayList<>();
            for (BakedQuad q : all) {
                if (q.getTintIndex() == -1) result.add(q);
            }
            return result;
        }
        if (renderType == RenderType.cutout()) {
            // Tinted quads → CUTOUT (passes GL_LEQUAL at same depth, consistently wins).
            List<BakedQuad> result = new ArrayList<>();
            for (BakedQuad q : all) {
                if (q.getTintIndex() != -1) result.add(q);
            }
            return result;
        }
        return Collections.emptyList();
    }

    // ── Vanilla 3-arg getQuads (required abstract impl; used as fallback) ─────

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
        return wrapped.getQuads(state, face, random);
    }

    // ── Standard BakedModel delegation ───────────────────────────────────────

    @Override public boolean useAmbientOcclusion()        { return true; }
    @Override public boolean isGui3d()                     { return wrapped.isGui3d(); }
    @Override public boolean usesBlockLight()             { return wrapped.usesBlockLight(); }
    @Override public boolean isCustomRenderer()           { return wrapped.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return wrapped.getParticleIcon(); }
    @Override public ItemTransforms getTransforms()       { return wrapped.getTransforms(); }
    @Override public ItemOverrides getOverrides()         { return wrapped.getOverrides(); }
}
