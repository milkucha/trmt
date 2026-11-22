package milkucha.trmt.client.render;

import milkucha.trmt.block.ErodedGrassBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

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
 *
 * mipmapFix (see MipmapFix; setting on AND the current atlas holds the overlay copies): everything
 * goes to CUTOUT_MIPPED (the overlay quad follows the dirt base in the same list, so it still draws
 * after it, as the vanilla grass block side overlay does) and the top overlay quad is moved onto this
 * block's copy of its stage sprite. The copy index reaches the model through ModelData (getModelData
 * is the only per-position hook: the chunk compiler passes its result to getRenderTypes and getQuads).
 * Off: the "fix active" branches never run; the two-pass split is exactly as before.
 *
 * Snowy variants (eroded_grass_block_s*_snow) have no side overlay element, only the opaque
 * vanilla grass_block_snow side plus the dirt + eroded_top UP pair (same tinted stage sprite),
 * so the same render-type split and overlay-copy transform applies.
 */
@OnlyIn(Dist.CLIENT)
public class ErodedGrassBlockModel implements BakedModel {

    /** Per-position copy index 0..VARIANTS-1, set by getModelData only while the fix is active. */
    private static final ModelProperty<Integer> COPY = new ModelProperty<>();
    private static final ModelData[] COPY_DATA = new ModelData[MipmapFix.VARIANTS];

    static {
        for (int v = 0; v < COPY_DATA.length; v++) COPY_DATA[v] = ModelData.builder().with(COPY, v).build();
    }

    private final BakedModel wrapped;

    /**
     * [stage][variant] sprites, resolved on first use (a new model instance per resource reload).
     * Copies are null when the atlas was built without them (mipmapFix off at that reload).
     */
    private volatile TextureAtlasSprite[][] variantSprites;

    /** Retextured UP quad lists per copy index (this instance is one blockstate = one stage). */
    @SuppressWarnings("unchecked")
    private final List<BakedQuad>[] upByCopy = new List[MipmapFix.VARIANTS];

    public ErodedGrassBlockModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    // ── mipmapFix helpers ─────────────────────────────────────────────────────

    private TextureAtlasSprite[][] variantSprites() {
        TextureAtlasSprite[][] s = variantSprites;
        if (s == null) {
            TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            s = new TextureAtlasSprite[MipmapFix.STAGES][MipmapFix.VARIANTS];
            for (int st = 0; st < MipmapFix.STAGES; st++) {
                for (int v = 0; v < MipmapFix.VARIANTS; v++) {
                    ResourceLocation id = MipmapFix.spriteId(st, v);
                    TextureAtlasSprite sp = atlas.getSprite(id);
                    // Missing copy (atlas built with mipmapFix off) -> null, no remap.
                    s[st][v] = sp.contents().name().equals(id) ? sp : null;
                }
            }
            variantSprites = s;
        }
        return s;
    }

    /** Whether the current atlas holds the overlay copies (it was built with mipmapFix on). */
    private boolean atlasHasCopies() {
        return variantSprites()[0][1] != null;
    }

    /** {@code all} with the tinted top overlay quad moved onto this copy's sprite (cached per copy). */
    private List<BakedQuad> upQuads(BlockState state, List<BakedQuad> all, int copy) {
        List<BakedQuad> cached = upByCopy[copy];
        if (cached != null) return cached;
        if (!state.hasProperty(ErodedGrassBlock.STAGE)) return all;
        int stage = state.getValue(ErodedGrassBlock.STAGE);
        if (stage < 0 || stage >= MipmapFix.STAGES) return all;
        TextureAtlasSprite to = variantSprites()[stage][copy];
        if (to == null) return all;
        ResourceLocation plain = MipmapFix.spriteId(stage, 0);
        List<BakedQuad> out = new ArrayList<>(all.size());
        for (BakedQuad q : all) {
            TextureAtlasSprite from = q.getSprite();
            if (q.getDirection() == Direction.UP && q.isTinted() && from.contents().name().equals(plain)) {
                // Pure UV translation, so the FACING rotation baked into the UVs is kept.
                int[] v = q.getVertices().clone();
                float du = to.getU0() - from.getU0(), dv = to.getV0() - from.getV0();
                for (int i = 0; i < 4; i++) {
                    int o = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                    v[o]     = Float.floatToRawIntBits(Float.intBitsToFloat(v[o])     + du);
                    v[o + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(v[o + 1]) + dv);
                }
                out.add(new BakedQuad(v, q.getTintIndex(), q.getDirection(), to, q.isShade(), q.hasAmbientOcclusion()));
            } else {
                out.add(q);
            }
        }
        upByCopy[copy] = out;
        return out;
    }

    // ── mipmapFix: per-position copy index ────────────────────────────────────

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        if (!MipmapFix.enabled() || !atlasHasCopies()) return data;          // off: untouched
        int v = MipmapFix.copyIndex(pos.getX(), pos.getY(), pos.getZ());
        return data == ModelData.EMPTY ? COPY_DATA[v] : data.derive().with(COPY, v).build();
    }

    // ── Forge two-pass render type declaration ────────────────────────────────

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        if (data.get(COPY) != null) return ChunkRenderTypeSet.of(RenderType.cutoutMipped());
        return ChunkRenderTypeSet.of(RenderType.cutoutMipped(), RenderType.cutout());
    }

    // ── Forge 5-arg getQuads (used for normal chunk rendering) ────────────────

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face,
                                     RandomSource random, ModelData data, @Nullable RenderType renderType) {
        Integer copy = data.get(COPY);
        if (copy != null && renderType != null && state != null) {
            // mipmapFix active: everything on cutoutMipped, overlay quad on this block's copy.
            if (renderType != RenderType.cutoutMipped()) return Collections.emptyList();
            List<BakedQuad> fixed = wrapped.getQuads(state, face, random);
            return face == Direction.UP && copy != 0 ? upQuads(state, fixed, copy) : fixed;
        }

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
